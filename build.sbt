ThisBuild / organization := "com.nepalius"
ThisBuild / scalaVersion := "3.7.0"
ThisBuild / version := "0.0.1-SNAPSHOT"

Global / onChangedBuildSource := ReloadOnSourceChanges

val V = new {
  val CommonsValidator = "1.9.0"
  val Flyway = "11.8.2"
  val Jwt = "4.5.0"
  val Laminar = "17.2.1"
  val Logback = "1.5.18"
  val Password4J = "1.8.3"
  val Postgres = "42.7.5"
  val Quill = "4.8.6"
  val ScalaJsMacroTaskExecutor = "1.1.1"
  val Slf4j = "2.0.12"
  val Sttp = "3.9.5"
  val Tapir = "1.11.32"
  val Zio = "2.1.18"
  val ZioConfig = "4.0.3"
  val ZioLogging = "2.4.0"
  val ZioJson = "0.7.0"
}

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("./common"))
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %%% "tapir-json-zio" % V.Tapir,
      "dev.zio" %%% "zio-json" % V.ZioJson,
    ),
  )

import org.scalajs.linker.interface.ModuleSplitStyle

lazy val frontend = project
  .dependsOn(common.js)
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("com.nepalius")),
        )
    },
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % V.Laminar,
      "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % V.Tapir,
      "org.scala-js" %%% "scala-js-macrotask-executor" % V.ScalaJsMacroTaskExecutor,
    ),
  )

lazy val domain = (project in file("./backend/domain"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % V.Zio,
      "commons-validator" % "commons-validator" % V.CommonsValidator,
    ),
  )

lazy val repo = (project in file("./backend/repo"))
  .dependsOn(domain)
  .settings(
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % V.Postgres,
      "org.flywaydb" % "flyway-core" % V.Flyway,
      "org.flywaydb" % "flyway-database-postgresql" % V.Flyway,
      "io.getquill" %% "quill-jdbc-zio" % V.Quill,
    ),
  )
  .enablePlugins(FlywayPlugin)
  .settings(
    flywayUrl := "jdbc:postgresql://localhost:5432/nepalius",
    flywayUser := "postgres",
    flywayPassword := "postgres",
  )

lazy val api = (project in file("./backend/api"))
  .dependsOn(domain, common.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % V.Tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % V.Tapir,
      "com.auth0" % "java-jwt" % V.Jwt,
      "com.password4j" % "password4j" % V.Password4J,
    ),
  )

lazy val backend = project
  .aggregate(domain, repo, api)
  .dependsOn(domain, repo, api)
  .settings(reStart / aggregate := false)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-config" % V.ZioConfig,
      "dev.zio" %% "zio-config-typesafe" % V.ZioConfig,
      "dev.zio" %% "zio-config-magnolia" % V.ZioConfig,
      "dev.zio" %% "zio-logging-slf4j2" % V.ZioLogging,
      "ch.qos.logback" % "logback-classic" % V.Logback,
    ),
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    Docker / packageName := "nepalius",
    dockerBaseImage := "eclipse-temurin:21",
    dockerExposedPorts := Seq(9000),
    dockerUpdateLatest := true,
  )

lazy val root = (project in file("."))
  .aggregate(backend, frontend)
  .settings(name := "NepaliUS")

ThisBuild / assemblyMergeStrategy := {
  case "deriving.conf"                         => MergeStrategy.concat
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  // https://tapir.softwaremill.com/en/latest/docs/openapi.html#using-swaggerui-with-sbt-assembly
  case ps if ps.contains("swagger-ui")        => MergeStrategy.singleOrError
  case ps if ps.endsWith("module-info.class") => MergeStrategy.discard
  case ps if ps.startsWith("io/getquill")     => MergeStrategy.first
  case ps if ps.endsWith("unroll.tasty")      => MergeStrategy.last
  case ps if ps.endsWith("unroll.class")      => MergeStrategy.last
  case x => (ThisBuild / assemblyMergeStrategy).value(x)
}

import scala.sys.process.Process

val npmInstall = taskKey[Unit]("Run npm ci")
npmInstall := {
  val npmCiExitCode =
    Process("npm ci", cwd = (frontend / baseDirectory).value).!
  if (npmCiExitCode > 0) {
    throw new IllegalStateException(s"npm ci failed. See above for reason")
  }
}

val npmBuild = taskKey[Unit]("npm build")
npmBuild := {
  val buildExitCode =
    Process("npm run build", cwd = (frontend / baseDirectory).value).!
  if (buildExitCode > 0) {
    throw new IllegalStateException(
      s"Building frontend failed. See above for reason",
    )
  }
}

val buildFrontend = taskKey[Unit]("Build frontend")
buildFrontend := {
  npmInstall.value
  (frontend / Compile / fullLinkJS).value
  npmBuild.value

  IO.copyDirectory(
    source = (frontend / baseDirectory).value / "dist",
    target =
      (backend / baseDirectory).value / "src" / "main" / "resources" / "static",
  )
}

// Always build the frontend first before packaging the application in a fat jar
(backend / assembly) := (backend / assembly).dependsOn(buildFrontend).value

val packageApp =
  taskKey[File]("Package the whole application into a fat jar")

packageApp := {
  /*
  To package the whole application into a fat jar, we do the following things:
  - call sbt assembly to make the fat jar for us (config in the server sub-module settings)
  - we move it to the ./dist folder so that the Dockerfile can be independent of Scala versions and other details
   */
  val fatJar = (backend / assembly).value
  val target = baseDirectory.value / "dist" / "nepalius.jar"
  IO.copyFile(fatJar, target)
  target
}

// Start the backend server, and make sure to stop it afterward
addCommandAlias("be", ";backend/reStop ;~backend/reStart ;backend/reStop")
// Run the frontend development loop (also run vite: `cd frontend; npm run dev`)
addCommandAlias("fe", ";~frontend/fastLinkJS")
// Package the application into a jar. Run the jar with: `java -jar dist/app.jar`
addCommandAlias("jar", ";packageApp")
