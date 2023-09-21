package com.nepalius.util

import zio.*
import zio.http.Request
import zio.json.*

object ApiUtils:

  def parseBody[A: JsonDecoder](request: Request): IO[AppError, A] =
    for
      body <- request.body.asString.orElseFail(AppError.MissingBodyError)
      parsed <- ZIO
        .from(body.fromJson[A])
        .mapError(AppError.JsonDecodingError.apply)
    yield parsed

  def parsePageable(req: Request): Pageable = {
    val maxPageSize = 100
    Pageable(
      getQueryParam(req, "pageSize")
        .map(_.toInt)
        .getOrElse(maxPageSize)
        .min(maxPageSize),
      getQueryParam(req, "lastId")
        .map(_.toLong)
        .getOrElse(Long.MaxValue),
    )
  }

  def getQueryParam(req: Request, key: String): Option[String] = {
    req.url.queryParams.get(key).flatMap(_.headOption)
  }
