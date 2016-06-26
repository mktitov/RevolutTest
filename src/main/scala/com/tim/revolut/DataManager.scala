package com.tim.revolut

/**
  * @author Mikhail Titov
  */
import java.sql.Timestamp

import org.slf4j.LoggerFactory
import slick.driver.H2Driver.api._
import slick.jdbc.GetResult

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class InvalidTransactionException(message:String, cause:Throwable) extends Exception(message, cause)

class DataManager(dbConfigName: String) {
  val logger = LoggerFactory.getLogger(classOf[DataManager])

  //Configuring database manager
  val db =  Database.forConfig(dbConfigName)
  val users = TableQuery[Users]
  val accounts = TableQuery[Accounts]
  val transactions = TableQuery[Transactions]
  implicit val accountGetResult: GetResult[Account] = GetResult( r=> {
    Account(r.nextLong(), r.nextString(), r.nextDouble(), r.nextLongOption())
  })
  implicit val userWithInfoGetResult: GetResult[UserWithInfo] = GetResult( r => {
    UserWithInfo(r.nextLong(), r.nextString(), r.nextString(), r.nextIntOption(), r.nextDoubleOption())
  })

  def close() {
    logger.info("Database layer closed")
    db.close()
  }

  def createTables(): Future[Unit] = {
    logger.info("Creating database schema")
    db.run( (users.schema ++ accounts.schema ++ transactions.schema).create )
  }

  def populateTestData(): Unit = {
    logger.info("Populating test data to the database")
    val resultFutures = Seq(
      ("Bill Gates", "bill", Seq(("1001", 37.0), ("1002", 30.0))),
      ("Larry Ellison", "larry", Seq(("2001", 20.0), ("2002", 30.0))),
      ("Jeff Bezos", "jeff", Seq(("3001", 47.0))),
      ("Mark Zuckerberg", "mark", Seq(("4001", 41.0))),
      ("Larry Page", "page", Seq(("5001", 33.2))),
      ("Sergey Brin", "sergey", Seq(("6001", 32.8))),
      ("Pupkin Ivan", "ivan", Seq())
    ) map { d =>
      addUser(User(d._1, d._2, "")) flatMap { userId =>
        Future.sequence(
          d._3 map { a =>
            addUserAccount(Account(userId, a._1, a._2))
          }
        )
      }
    }
    val doneFuture = Future.sequence(resultFutures)
    Await.result(doneFuture, 5 seconds)
    logger.info("Done")
  }

  def curtime = new Timestamp(System.currentTimeMillis())

  def addUser(user: User): Future[Long] =  db.run( (users returning users.map(_.id)) += user)
  def addUserAccount(account:Account): Future[Int] = db.run(accounts += account)
  def findUserByLogin(login:String): Future[Option[User]] = db.run(users.filter(_.login===login).result.headOption)
  def findAccountByNumber(accountNumber: String): Future[Option[Account]] = db.run(accounts.filter(_.number===accountNumber).result.headOption)
  def listUserAccounts(userId:Long): Future[Seq[Account]] =  db.run(accounts.filter(_.userId===userId).result)
  def getTransactionById(id: Long): Future[Option[Transaction]] = db.run(transactions.filter(_.id===id).result.headOption)
  def listUsers: Future[Seq[User]] = db.run(users.result)
  def listUsersWithInfo: Future[Seq[UserWithInfo]] = { db.run (
    sql"""
        select u.id, u.name, u.login, count(distinct a.id), ifnull(sum(a.balance), 0)
        from users u
             left outer join accounts a on a.user_id = u.id
        group by u.id, u.name, u.login
        order by u.id
    """.as[UserWithInfo]
  )}

  def listTransactionByAccount(accountNumber:String): Future[Seq[Transaction]] =  {
    val query = for {
      a <- accounts if a.number===accountNumber
      t <- transactions if a.id===t.creditAccountId || a.id===t.debitAccountId
    } yield t
    db.run(query.result)
  }

  def transfer(debitAccountNumber:String, creditAccountNumber:String, amount:Double): Future[Long] = {
    val statements = for {
      //we should lock db row with debit account
      debitAcc <- sql"""
        select user_id, account_number, balance, id
        from accounts
        where account_number = $debitAccountNumber and balance >= $amount
        for update
        """.as[Account].head
      //we should lock db row with the credit account
      creditAcc <- sql"""
        select user_id, account_number, balance, id
        from accounts
        where account_number = $creditAccountNumber for update
        """.as[Account].head
      _ <- accounts.insertOrUpdate(debitAcc.copy(balance = debitAcc.balance - amount))
      _ <- accounts.insertOrUpdate(creditAcc.copy(balance = creditAcc.balance + amount))
      transactionId <- (transactions returning transactions.map(_.id)) += Transaction(debitAcc.id.get, creditAcc.id.get, amount, curtime)
    } yield (transactionId)
    //handling actions in single transaction and tuning the exception
    db.run(statements.transactionally).recover {
      case e: Throwable =>
        val mess = s"Error transferring ($amount) from account ($debitAccountNumber) to account ($creditAccountNumber)."
        logger.error(mess)
        throw new InvalidTransactionException(mess, e)
    }
  }
}
