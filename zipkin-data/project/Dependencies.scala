import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
}

object FileExport {
  object ApachePoi {
    val version = "3.9"
    val group = "org.apache.poi"
    val core = group % "poi" % version
    val common = group % "poi-ooxml" % version
    val simpleExcel = "bad.robot" % "simple-excel" % "1.0" % Test
  }

  object PdfBox {
    val version = "1.8.6"
    val group = "org.apache.pdfbox"
    val core = group % "pdfbox" % version
  }

  object ScalaCsv {
    val version = "1.3.5"
    val group = "com.github.tototoshi"
    val core = group %% "scala-csv" % version
  }
}

object elasticsearch {
  val version = "6.5.4"
  val core = "org.elasticsearch" % "elasticsearch" % version withSources()
  val restClient = "org.elasticsearch.client" % "elasticsearch-rest-client" % version withSources()
}

object PlayJson {
  val group = "com.typesafe.play"
  val version = "2.6.6"
  val playJson = group %% "play-json" % version
  val playJsonJoda = group %% "play-json-joda" % version
}
