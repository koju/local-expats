ThisBuild / organization := "com.nepalius"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / version := "0.0.1-SNAPSHOT"

Global / onChangedBuildSource := ReloadOnSourceChanges

val V = new {
  val CommonsValidator = "1.7"
  val Flyway = "9.22.1"
  val Jwt = "4.4.0"
  val Password4J = "1.7.3"
  val Postgres = "42.6.0"
  val Quill = "4.6.0.1"
  val Slf4j = "2.0.9"
  val Tapir = "1.7.4"
  val Zio = "2.0.17"
  val ZioConfig = "3.0.7"
  val ZioLogging = "2.1.14"
}

lazy val domain = project
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % V.Zio,
      "commons-validator" % "commons-validator" % V.CommonsValidator,
    ),
  )

lazy val repo = project
  .dependsOn(domain)
  .settings(
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % V.Postgres,
      "org.flywaydb" % "flyway-core" % V.Flyway,
      "io.getquill" %% "quill-jdbc-zio" % V.Quill,
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
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % V.Tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % V.Tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % V.Tapir,
      "com.auth0" % "java-jwt" % V.Jwt,
      "com.password4j" % "password4j" % V.Password4J,
    ),
  )

lazy val root = (project in file("."))
  .settings(name := "NepaliUS")
  .aggregate(domain, repo, api)
  .dependsOn(domain, repo, api)
  .settings(reStart / aggregate := false)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-config" % V.ZioConfig,
      "dev.zio" %% "zio-config-typesafe" % V.ZioConfig,
      "dev.zio" %% "zio-config-magnolia" % V.ZioConfig,
      "com.github.ghik" % "silencer-lib_2.13.11" % "1.17.13" % "provided", // Fix for zio-config incompatibility with scala 3.3
      "dev.zio" %% "zio-logging-slf4j" % V.ZioLogging,
      "org.slf4j" % "slf4j-api" % V.Slf4j,
      "org.slf4j" % "slf4j-simple" % V.Slf4j,
    ),
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    Docker / packageName := "nepalius",
    dockerBaseImage := "eclipse-temurin:17",
    dockerExposedPorts := Seq(9000),
    dockerUpdateLatest := true,
  )
