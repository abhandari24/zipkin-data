
name := "sbtExample"

version := "0.1"

scalaVersion := "2.12.4"

lazy val root = (project in file("."))
  .settings(
    name := "zipkin-data",
    libraryDependencies ++= Seq(
      FileExport.ApachePoi.common,
      FileExport.ApachePoi.core,
      FileExport.ApachePoi.simpleExcel,
      elasticsearch.core,
      elasticsearch.restClient,
      PlayJson.playJson
    )
  )