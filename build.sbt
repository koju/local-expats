ThisBuild / organization := "com.nepalius"
ThisBuild / scalaVersion := "3.2.1"
ThisBuild / version := "0.0.1-SNAPSHOT"

Global / onChangedBuildSource := ReloadOnSourceChanges

val V = new {
  val Doobie = "1.0.0-RC2"
  val Flyway = "9.8.2"
  val Postgres = "42.5.0"
  val Quill = "4.6.0"
  val Slf4j = "2.0.5"
  val Zio = "2.0.3"
  val ZioCats = "3.3.0"
  val ZioConfig = "3.0.2"
  val ZioHttp = "2.0.0-RC11"
  val ZioJson = "0.3.0"
  val ZioLogging = "2.1.4"
}

lazy val domain = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % V.Zio,
    ),
  )

lazy val repo = project
  .dependsOn(domain)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % V.Postgres,
      "org.flywaydb" % "flyway-core" % V.Flyway,
      "io.getquill" %% "quill-jdbc-zio" % V.Quill,
      "org.tpolecat" %% "doobie-core" % V.Doobie,
      "org.tpolecat" %% "doobie-postgres" % V.Doobie,
      "org.tpolecat" %% "doobie-hikari" % V.Doobie,
      "dev.zio" %% "zio-interop-cats" % V.ZioCats,
    ),
  )
  .enablePlugins(FlywayPlugin)
  .settings(
    flywayUrl := "jdbc:postgresql://localhost:5432/nepalius",
    flywayUser := "postgres",
    flywayPassword := "postgres",
  )

lazy val api = project
  .dependsOn(domain)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.d11" %% "zhttp" % V.ZioHttp,
      "dev.zio" %% "zio-json" % V.ZioJson,
    ),
  )

lazy val root = (project in file("."))
  .settings(name := "NepaliUS")
  .settings(commonSettings)
  .aggregate(domain, api, repo)
  .settings(reStart / aggregate := false)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-config" % V.ZioConfig,
      "dev.zio" %% "zio-config-typesafe" % V.ZioConfig,
      "dev.zio" %% "zio-config-magnolia" % V.ZioConfig,
      "dev.zio" %% "zio-logging-slf4j" % V.ZioLogging,
      "org.slf4j" % "slf4j-api" % V.Slf4j,
      "org.slf4j" % "slf4j-simple" % V.Slf4j,
    ),
  )
  .dependsOn(domain, api, repo)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    Docker / packageName := "nepalius",
    dockerBaseImage := "eclipse-temurin:17",
    dockerExposedPorts := Seq(9000),
    dockerUpdateLatest := true,
  )

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-new-syntax",
    "-source:future",
  ),
)
