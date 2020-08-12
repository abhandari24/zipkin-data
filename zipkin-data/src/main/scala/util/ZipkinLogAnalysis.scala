package util

import java.io.{FileNotFoundException, FileOutputStream}
import java.net.ConnectException
import java.util.Date

import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.util.EntityUtils
import org.apache.poi.ss.usermodel.{Row, Sheet}
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.elasticsearch.client.{Request, RestClient, RestClientBuilder}
import play.api.libs.json._

object ZipkinLogAnalysis {

  def main(args: Array[String]): Unit = {

    val dateStart = "1596430800000" //Time is in epoch time millis
    val dateEnd = "1596517199000"
    val out                    = new FileOutputStream("zipkinData"++".xls")

    val restClient: RestClient = connect
    try {

      val request = new Request("GET", "dalng-zipkin*/_search")
      val jsonQuery = "{\"size\": 0,\"query\": {\n" + "    \"bool\": {\n" + "      \"must\": [\n" + "        {\n" + "          \"range\": {\n" + "            \"timestamp_millis\": {\n" + "   " +
        "           \"gte\": "+dateStart+",\n" + "              \"lte\": "+dateEnd+",\n" + "              \"format\": \"epoch_millis\"\n" + "            }\n" + "          }\n" + "        }\n" + "    " +
        "  ]\n" + "    }\n" + "  }," + "\"aggs\": {\n" + "    \"2\": {\n" + "      \"terms\": {\n" + "        \"field\": \"tags.clientRequestId\",\n" + "        \"size\": 250,\n" + "      " +
        "  \"order\": {\n" + "          \"_count\": \"desc\"\n" + "        }\n" + "      }\n" + "    }\n" + "  }}"

      val json = executeQuery(restClient, jsonQuery, request)
      val aggs: JsObject =
        (json \ "aggregations").asOpt[JsObject].getOrElse((json \ "aggs").asOpt[JsObject].getOrElse(Json.obj()))
      val bucketMap: Map[String, Long]           = new AggregationResp(aggs).buckets
      val duplicateRequestMap: Map[String, Long] = bucketMap.filter(_._2 > 1).filterKeys(k => k != "Unknown")
      //print(duplicateRequestMap)
      val resultMap: List[(String, Long, List[Long], Date, String)] =
        duplicateRequestMap.map(m => secondExe(restClient, request, m._1, m._2)).toList

      val wb: SXSSFWorkbook = new SXSSFWorkbook()
      val sheet: Sheet      = wb.createSheet("ZipkinResult")
      val row1: Row         = sheet.createRow(0)
      row1.createCell(0).setCellValue("ClientRequestID")
      row1.createCell(1).setCellValue("Number of Attempts")
      row1.createCell(2).setCellValue("Duration of each request (ms)")
      row1.createCell(3).setCellValue("Date of request")
      row1.createCell(4).setCellValue("API Call")

      resultMap.zipWithIndex.map {
        case (obj, i) =>
          val row = sheet.createRow(i + 1)
          row.createCell(0).setCellValue(obj._1)
          row.createCell(1).setCellValue(obj._2)
          row.createCell(2).setCellValue(obj._3.mkString(", "))

          val cellStyle    = wb.createCellStyle
          val createHelper = wb.getCreationHelper
          cellStyle.setDataFormat(createHelper.createDataFormat().getFormat("m/d/yy h:mm"));
          val cell = row.createCell(3)
          cell.setCellStyle(cellStyle)

          cell.setCellValue(obj._4)
          row.createCell(4).setCellValue(obj._5)
      }

      wb.write(out)

      out.close()
      restClient.close()
    } catch {

      case e: ConnectException      => println("Couldn't connect")
      case e: FileNotFoundException => println("Couldn't find file")
    } finally {
      out.close()
      restClient.close()
    }
  }

  class ZipkinResponse(val zipkinObjects: List[ZipkinObject]) {
    def this(obj: JsArray) =
      this(ZipkinResponse.parseObject(obj))
  }

  object ZipkinResponse {
    private[ZipkinResponse] def parseObject(obj: JsArray): List[ZipkinObject] =
      obj.value
        .map(
          e =>
            new ZipkinObject(
              (e \ "_source" \ "id").as[String],
              (e \ "_source" \ "parentId").asOpt[String],
              (e \ "_source" \ "duration").asOpt[Long],
              (e \ "_source" \ "timestamp_millis").asOpt[Long],
              (e \ "_source" \ "tags" \ "resource.name").asOpt[String]
            )
        )
        .toList
  }

  class AggregationResp(val buckets: Map[String, Long]) {
    def this(aggregation: JsObject) =
      this(AggregationResp.parseAggregation(aggregation))
  }

  object AggregationResp {
    private[AggregationResp] def parseAggregation(agg: JsObject): Map[String, Long] =
      (agg \ "2" \ "buckets")
        .as[JsArray]
        .value
        .filterNot(_ == JsNull)
        .map(_.as[JsObject])
        .map { bucket =>
          ((bucket \ "key").as[String], (bucket \ "doc_count").as[Long])
        }
        .toMap

    val writer = Writes[AggregationResp](resp => Json.toJson(resp.buckets))
  }

  case class ZipkinObject(
                           val id: String,
                           val parentId: Option[String],
                           val duration: Option[Long],
                           val timestamp: Option[Long],
                           val apiName: Option[String]
                         )

  implicit val zipkinWrites = new Writes[ZipkinObject] {
    override def writes(o: ZipkinObject): JsValue = Json.obj("id" -> o.id, "parentId" -> o.parentId)
  }

  def connect: RestClient = {
    val credentials         = new UsernamePasswordCredentials("elastic", "changeme")
    val credentialsProvider = new BasicCredentialsProvider
    credentialsProvider.setCredentials(AuthScope.ANY, credentials)
    //prod
    RestClient
      .builder(new HttpHost("logsearch.dal.securustech.net", 82, "http"))
      .setHttpClientConfigCallback(
        new RestClientBuilder.HttpClientConfigCallback() {
          override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder =
            return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
        }
      )
      .build
  }

  def executeQuery(restClient: RestClient, jsonQuery: String, request: Request) = {
    val httpEntity = new NStringEntity(jsonQuery, ContentType.APPLICATION_JSON)
    request.setEntity(httpEntity)
    val response = restClient.performRequest(request)
    Json.parse(EntityUtils.toString(response.getEntity)).as[JsObject]
  }

  /**
    * Sending clientRequestId to fetch all the requests, this should return all the docs which signifies
    * how many re-attempts
    *
    * @param restClient
    * @param request
    * @param requestId
    * @param num
    * @return
    */
  def secondExe(
                 restClient: RestClient,
                 request: Request,
                 requestId: String,
                 num: Long
               ): (String, Long, List[Long], Date, String) = {
    val jsonQuery2  = "{\n        \"size\":\"20\",\n        \"query\": {\n        \"match\" : {\n        \"tags.clientRequestId\":\n        \"" + requestId + "\"\n      }\n      }\n      }"
    val httpEntity2 = new NStringEntity(jsonQuery2, ContentType.APPLICATION_JSON)
    request.setEntity(httpEntity2)
    val response2                  = restClient.performRequest(request)
    val json2                      = Json.parse(EntityUtils.toString(response2.getEntity)).as[JsObject]
    val hit: JsArray               = (json2 \ "hits" \ "hits").as[JsArray]
    val zr: ZipkinResponse         = new ZipkinResponse(hit)
    val parentIdList: List[String] = zr.zipkinObjects.map(z => z.parentId.getOrElse(""))
    //This will return all the parentIds which will help later to fetch API calls
    //filter non-empty values from parent id list
    val parentIdListFinal: List[String] = parentIdList.filter(_.nonEmpty)
    val li: List[ZipkinObject]          = parentIdListFinal.map(parentId => thirdExe(restClient, request, parentId))

    //filter non-null objects
    val finalZipkinObjectList: List[ZipkinObject] = li.filter(_ != null)
    val durationList: List[Long] = finalZipkinObjectList.map(l => l.duration.get / 1000)

    (
      requestId,
      num,
      durationList,
      new java.util.Date(finalZipkinObjectList.head.timestamp.get),
      finalZipkinObjectList.head.apiName.get
    )
  }

  /**
    * This will fetch doc for each parentid as id
    *
    * @param writer
    * @param restClient
    * @param request
    * @param id
    * @return
    */
  def thirdExe(restClient: RestClient, request: Request, id: String): ZipkinObject = {
    val jsonQuery3  = "{\n        \"size\":\"5\",\n        \"query\": {\n        \"match\" : {\n        \"id\":\n        \"" + id + "\"\n      }\n      }\n      }"
    val httpEntity3 = new NStringEntity(jsonQuery3, ContentType.APPLICATION_JSON)
    request.setEntity(httpEntity3)
    val response3 = restClient.performRequest(request)
    val json3     = Json.parse(EntityUtils.toString(response3.getEntity)).as[JsObject]
    //convert to object which has duration and date/timestamp
    val zr: ZipkinResponse = new ZipkinResponse((json3 \ "hits" \ "hits").as[JsArray])
    if (!zr.zipkinObjects.isEmpty) zr.zipkinObjects.head
    else
      null
  }
}
