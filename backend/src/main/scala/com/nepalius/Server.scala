package com.nepalius

import com.nepalius.config.{DatabaseMigration, ServerConfig}
import com.nepalius.util.Endpoints
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import zio.*
import zio.Console.printLine
import zio.http.Server as HttpServer

case class Server(
    serverConfig: ServerConfig,
    databaseMigration: DatabaseMigration,
    endpoints: Endpoints,
):

  private val allEndpoints = ZioHttpInterpreter(
    ZioHttpServerOptions
      .customiseInterceptors
      .serverLog(
        ZioHttpServerOptions
          .defaultServerLog
          .logWhenReceived(true),
      )
      .options,
  )
    .toHttp(endpoints.endpoints)

  def start: ZIO[Any, Throwable, Unit] =
    (for
      _ <- databaseMigration.migrate()
      port <- HttpServer.install(allEndpoints)
      _ <- printLine(s"Application NepaliUS started")
      _ <- printLine(s"Go to http://localhost:${port}/docs to open SwaggerUI")
      _ <- ZIO.never
    yield ())
      .provide(HttpServer.defaultWithPort(serverConfig.port))

object Server:
  val layer = ZLayer.fromFunction(Server.apply)
