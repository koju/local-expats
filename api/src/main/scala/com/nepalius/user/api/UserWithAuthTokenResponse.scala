package com.nepalius.user.api

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonEncoder, JsonDecoder}

case class UserWithAuthTokenResponse(user: UserResponse, authToken: String)

object UserWithAuthTokenResponse:
  given JsonEncoder[UserWithAuthTokenResponse] = DeriveJsonEncoder.gen[UserWithAuthTokenResponse]
  given JsonDecoder[UserWithAuthTokenResponse] = DeriveJsonDecoder.gen[UserWithAuthTokenResponse]
