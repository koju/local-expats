package com.nepalius.user

import com.nepalius.auth.{AuthService, UserSession}
import com.nepalius.user.*
import com.nepalius.user.User.UserId
import com.nepalius.user.UserApi.UserWithEmailNotFoundMessage
import com.nepalius.user.UserMapper.*
import com.nepalius.util.*
import com.nepalius.util.ErrorMapper.*
import sttp.tapir.ztapir.*
import zio.*

import scala.util.chaining.*

final case class UserApi(
    userService: UserService,
    authService: AuthService,
) extends BaseApi
    with UserEndpoints:

  override def endpoints =
    List(
      registerServerEndpoints,
      loginServerEndpoints,
      getCurrentUserServerEndpoints,
      updateCurrentUserServerEndpoints,
    )

  private val registerServerEndpoints: ZServerEndpoint[Any, Any] =
    registerEndPoint
      .zServerLogic(
        registerUser(_)
          .logError
          .pipe(defaultErrorsMappings),
      )

  private val loginServerEndpoints: ZServerEndpoint[Any, Any] =
    loginEndpoint
      .zServerLogic(
        loginUser(_)
          .logError
          .pipe(defaultErrorsMappings),
      )

  private val getCurrentUserServerEndpoints: ZServerEndpoint[Any, Any] =
    getCurrentUserEndpoint
      .zServerSecurityLogic[Any, UserSession](handleAuth)
      .serverLogic(session =>
        _ =>
          userService.get(session.userId)
            .logError
            .mapBoth(
              {
                case e: Exceptions.NotFound => NotFound(e.message)
                case _                      => InternalServerError()
              },
              user =>
                UserResponse(
                  user.id,
                  user.data.email,
                  user.data.firstName,
                  user.data.lastName,
                ),
            ),
      )

  private val updateCurrentUserServerEndpoints: ZServerEndpoint[Any, Any] =
    updateUserEndpoint
      .zServerSecurityLogic[Any, UserSession](handleAuth)
      .serverLogic(session =>
        payload =>
          (for
            mayBeNewPassword <- payload.password
              .map(authService.encryptPassword(_).asSome)
              .getOrElse(ZIO.succeed(None))
            userUpdateData = UserUpdateData(
              session.userId,
              payload.email,
              mayBeNewPassword,
              payload.firstName,
              payload.lastName,
            )
            updatedUser <- userService.updateUser(userUpdateData)
          yield toUserResponse(updatedUser))
            .logError
            .mapError {
              case e: Exceptions.NotFound     => NotFound(e.message)
              case e: Exceptions.AlreadyInUse => Conflict(e.message)
              case _                          => InternalServerError()
            },
      )

  private def registerUser(user: UserRegisterPayload)
      : Task[UserWithAuthTokenResponse] =
    for
      passwordHash <- authService.encryptPassword(user.password)
      userWithPasswordHash = user.copy(password = passwordHash)
      user <- userService.register(toUserRegisterData(userWithPasswordHash))
      token <- authService.generateJwt(user.data.email)
    yield UserWithAuthTokenResponse(toUserResponse(user), token)

  private def loginUser(userCredentials: UserLoginPayload)
      : Task[UserWithAuthTokenResponse] =
    for
      maybeUser <- userService.findUserByEmail(userCredentials.email)
      user <- ZIO.fromOption(maybeUser).orElseFail(Exceptions.Unauthorized())
      _ <- authService.verifyPassword(
        userCredentials.password,
        user.data.passwordHash,
      )
      token <- authService.generateJwt(user.data.email)
    yield UserWithAuthTokenResponse(toUserResponse(user), token)

  private def handleAuth(token: String): IO[ErrorInfo, UserSession] =
    (for
      userEmail <- authService.verifyJwt(token)
      userId <- userIdByEmail(userEmail)
    yield UserSession(userId))
      .logError
      .mapError {
        case e: Exceptions.Unauthorized => Unauthorized(e.message)
        case e: Exceptions.NotFound     => NotFound(e.message)
        case _                          => InternalServerError()
      }

  private def userIdByEmail(email: String): Task[UserId] =
    userService.findUserByEmail(email)
      .someOrFail(Exceptions.NotFound(UserWithEmailNotFoundMessage(email)))
      .map(_.id)

object UserApi:
  val layer = ZLayer.fromFunction(UserApi.apply)

  private val UserWithEmailNotFoundMessage: String => String =
    (email: String) => s"User with email $email doesn't exist"
