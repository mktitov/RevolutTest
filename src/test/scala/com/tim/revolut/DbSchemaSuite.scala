package com.tim.revolut

import org.h2.jdbc.JdbcSQLException
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import slick.driver.H2Driver.api._
import slick.jdbc.meta._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by Mikhail Titov on 25.06.16.
  */
class DbSchemaSuite extends FunSuite with BeforeAndAfter with ScalaFutures with Matchers {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds))
  var db: Database = _

  val users = TableQuery[Users]
  val accounts = TableQuery[Accounts]

  before { db = Database.forConfig("h2mem1")  }
  after  { db.close() }

  def createDbSchema: Unit = db.run((users.schema ++ accounts.schema).create).futureValue

  test("DB Schema creating works") {
    createDbSchema
    val tables = db.run(MTable.getTables).futureValue
    assert(tables.size==2)
  }

  test("Adding User to Users table works") {
    createDbSchema
    val statements = for {
      _ <- users += User("User One", "user1", "123")
      userOption <- users.filter(_.login === "user1").take(1).result.headOption
    } yield userOption
    val user = db.run(statements).futureValue
    assert(!user.isEmpty)
    assert(user.get.login=="user1")
  }

  test("Accounts should be able to be added to Users") {
    createDbSchema
    val actions = for {
      _ <- users += User("User One", "user1", "123")
      userOption <- users.filter(_.login === "user1").take(1).result.headOption
      _ <- accounts += Account(userOption.get.id.get, "777", 0.0)
      accOption <- accounts.filter(_.userId === userOption.get.id).take(1).result.headOption
    } yield accOption
    val acc = db.run(actions).futureValue
    val user = db.run(users.result.headOption).futureValue
    assert(!acc.isEmpty)
    assert(!user.isEmpty)
    assert(acc.get.userId==user.get.id.get)
    assert(acc.get.number=="777")
    assert(acc.get.balance==0.0)
  }

  test("Accounts with the same (userId, number) should not exists") {
    createDbSchema
    db.run( users += User("User1", "user1", "123") ).futureValue
    val user = db.run( users.filter(_.login==="user1").take(1).result.head ).futureValue
    //Adding first account
    db.run( accounts += Account(user.id.get, "111", 0.0) ).futureValue
    //Adding second account with the same userId and account number
    val resFuture = db.run( accounts += Account(user.id.get, "111", 0.0) )
    resFuture.failed.futureValue shouldBe an [JdbcSQLException]
  }

}
