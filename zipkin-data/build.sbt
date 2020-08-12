import Dependencies._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "zipkin-data",
    libraryDependencies ++= Seq(scalaTest % Test,
      FileExport.ApachePoi.common,
      FileExport.ApachePoi.core,
      FileExport.ApachePoi.simpleExcel,
      elasticsearch.core,
      elasticsearch.restClient,
      PlayJson.playJson
    )
  )