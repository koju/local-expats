package com.nepalius.config
import zio.*
import zio.config.*
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

final case class AppConfig(server: ServerConfig, database: DatabaseConfig)

final case class ServerConfig(port: Int)

object AppConfig:
  private def readAppConfig: IO[ReadError[String], AppConfig] = read {
    descriptor[AppConfig].from(
      TypesafeConfigSource.fromResourcePath.at(
        PropertyTreePath.$("nepalius"),
      ),
    )
  }

  val layer: ZLayer[Any, ReadError[String], DatabaseConfig & ServerConfig] =
    ZLayer {
      for appConfig <- readAppConfig
      yield ZLayer.succeed(appConfig.database) ++ ZLayer.succeed(
        appConfig.server,
      )
    }.flatten
