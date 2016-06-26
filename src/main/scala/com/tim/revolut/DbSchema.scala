package com.tim.revolut

import java.sql.Timestamp

import slick.lifted.Tag
import slick.driver.H2Driver.api._

class Users(tag:Tag) extends Table[User](tag, "USERS") {
  def id = column[Long]("ID", O.AutoInc, O.PrimaryKey)
  def name = column[String]("NAME")
  def login = column[String]("LOGIN")
  //in this column we will store only the hash of the user password
  def pwd = column[String]("PWD")

  def uniqName = index("I_USERS_NAME", login, unique = true) //the login column must be unique
  def * = (name, login, pwd, id.?) <> (User.tupled, User.unapply)
}

class Accounts(tag:Tag) extends Table[Account](tag, "ACCOUNTS") {
  def id = column[Long]("ID", O.AutoInc, O.PrimaryKey)
  def userId = column[Long]("USER_ID")
  def number = column[String]("ACCOUNT_NUMBER")
  def balance = column[Double]("BALANCE")

  //the account number must be unique
  def uniqNumber = index("I_ACCOUNTS_NUMBER", number, true)
  //adding this table as foreign key for Users
  def user = foreignKey("FK_ACCOUNTS_USERS", userId, TableQuery[Users])(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
  def * = (userId, number, balance, id.?) <> (Account.tupled, Account.unapply)
}

class Transactions(tag:Tag) extends Table[Transaction](tag, "TRANSACTIONS") {
  def id = column[Long]("ID", O.AutoInc, O.PrimaryKey)
  def debitAccountId = column[Long]("DEBIT_ACCOUNT_ID")
  def creditAccountId = column[Long]("CREDIT_ACCOUNT_ID")
  def amount = column[Double]("AMOUNT")
  def time = column[Timestamp]("TIME")

  def creditAccount = foreignKey("FK_TRANS_C_ACC", creditAccountId, TableQuery[Accounts])(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
  def debitAccount = foreignKey("FK_TRANS_D_ACC", debitAccountId, TableQuery[Accounts])(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)

  def * = (debitAccountId, creditAccountId, amount, time, id.?) <> (Transaction.tupled, Transaction.unapply)
}

