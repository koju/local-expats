package com.nepalius

import com.nepalius.auth.AuthService
import com.nepalius.config.*
import com.nepalius.location.{
  LocationApi,
  LocationRepoLive,
  LocationServiceLive,
}
import com.nepalius.post.{PostApi, PostRepoLive, PostServiceLive}
import com.nepalius.user.{UserApi, UserRepoLive, UserService}
import com.nepalius.util.Endpoints
import zio.*
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  override def run: Task[Unit] =
    ZIO
      .serviceWithZIO[Server](_.start)
      .provide(
        Server.layer,
        AppConfig.layer,
        Runtime.removeDefaultLoggers,
        SLF4J.slf4j,
        // Database
        DatabaseMigration.layer,
        DataSourceContext.layer,
        QuillContext.layer,
        // API
        Endpoints.layer,
        UserApi.layer,
        PostApi.layer,
        LocationApi.layer,
        // Service
        AuthService.layer,
        PostRepoLive.layer >>> PostServiceLive.layer,
        LocationRepoLive.layer >>> LocationServiceLive.layer,
        UserRepoLive.layer >>> UserService.layer,
      )
}
