package com.nepalius.user

import com.nepalius.user.User.UserId
import com.nepalius.user.UserService.{
  InvalidEmailMessage,
  UserWithEmailAlreadyInUseMessage,
  UserWithIdNotFoundMessage,
}
import com.nepalius.util.Exceptions
import com.nepalius.util.Exceptions.{AlreadyInUse, BadRequest}
import org.apache.commons.validator.routines.EmailValidator
import zio.{Task, ZIO, ZLayer}

case class UserService(userRepo: UserRepo) {

  def register(user: UserRegisterData): Task[User] = {
    val emailClean = user.email.toLowerCase.trim()
    val firstNameClean = user.firstName.trim()
    val lastNameClean = user.lastName.trim()

    for {
      _ <- validateEmail(emailClean)
      _ <- checkUserDoesNotExistByEmail(emailClean)
      userDataClean = UserRegisterData(
        emailClean,
        firstNameClean,
        lastNameClean,
        user.passwordHash,
      )
      user <- userRepo.create(userDataClean)
    } yield user
  }

  private def validateEmail(email: String): Task[Unit] =
    if EmailValidator.getInstance().isValid(email)
    then ZIO.unit
    else ZIO.fail(BadRequest(InvalidEmailMessage(email)))

  private def checkUserDoesNotExistByEmail(email: String): Task[Unit] =
    for {
      maybeUserByEmail <- userRepo.findUserByEmail(email)
      _ <- ZIO
        .fail(AlreadyInUse(UserWithEmailAlreadyInUseMessage(email)))
        .when(maybeUserByEmail.isDefined)
    } yield ()

  def updateUser(user: UserUpdateData): Task[User] = {
    val emailCleanOpt = user.email.map(_.toLowerCase.trim())
    val firstNameClean = user.firstName.trim()
    val lastNameClean = user.lastName.trim()

    for
      _ <- ZIO.foreach(emailCleanOpt)(validateEmail)
      _ <- ZIO.foreach(emailCleanOpt)(email =>
        checkUserDoesNotExistByEmail(email, user.id),
      )
      oldUser <- get(user.id)
      newUserData = UserData(
        email = user.email.getOrElse(oldUser.data.email),
        firstName = firstNameClean,
        lastName = lastNameClean,
        passwordHash = user.passwordHash.getOrElse(oldUser.data.passwordHash),
      )
      updatedUser <- userRepo.update(user.id, newUserData)
    yield updatedUser
  }

  def get(userId: UserId): Task[User] =
    userRepo.findUserById(userId)
      .someOrFail(Exceptions.NotFound(UserWithIdNotFoundMessage(userId)))

  private def checkUserDoesNotExistByEmail(
      email: String,
      id: UserId,
  ): Task[Unit] =
    for {
      maybeUserByEmail <- userRepo.findUserByEmail(email)
      _ <- ZIO
        .fail(AlreadyInUse(UserWithEmailAlreadyInUseMessage(email)))
        .when(maybeUserByEmail.isDefined && maybeUserByEmail.exists(_.id != id))
    } yield ()

  def findUserByEmail(email: String): Task[Option[User]] =
    userRepo.findUserByEmail(email.toLowerCase.trim())

}

object UserService:
  val layer = ZLayer.fromFunction(UserService.apply)

  private val UserWithIdNotFoundMessage: UserId => String =
    (id: UserId) => s"User with id $id doesn't exist"
  private val UserWithEmailAlreadyInUseMessage: String => String =
    (email: String) => s"User with email $email already in use"
  private val InvalidEmailMessage: String => String =
    (email: String) => s"Email $email is not valid"
