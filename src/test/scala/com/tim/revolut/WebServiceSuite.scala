package com.tim.revolut

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}
import JsonProto._
/**
  * Created by Mikhail Titov on 26.06.16.
  */
class WebServiceSuite extends FunSuite with BeforeAndAfter with ScalaFutures with Matchers with ScalatestRouteTest {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds))
  implicit val aSystem = ActorSystem("test")
  implicit val aMaterializer = ActorMaterializer()

  var dbManager: DataManager = _
  var webService: WebService = _

  before {
    dbManager = new DataManager("h2mem1")
    dbManager.createTables().futureValue
    webService = new WebService {
      override implicit val actorSystem: ActorSystem = aSystem
      override implicit val actorMaterializer: ActorMaterializer = aMaterializer
      override implicit val dataManager: DataManager = dbManager
    }
  }
  after  { dbManager.close() }

  test("Service should list /users ") {
    dbManager.addUser(User("User1", "user1", "")).futureValue
    Get("/users/") ~> webService.routes ~> check {
      responseAs[Seq[UserWithInfo]] shouldEqual Seq(UserWithInfo(1, "User1", "user1", Some(0), Some(0.0)))
    }
  }

  test("Service should list /user/{id}/accounts") {
    val userId = dbManager.addUser(User("User1", "user1", "")).futureValue
    val acc = Account(userId, "123", 100.0)
    dbManager.addUserAccount(acc).futureValue
    Get(s"/users/$userId/accounts") ~> webService.routes ~> check {
      responseAs[Seq[Account]] shouldEqual Seq(acc.copy(id=Some(1)))
    }
  }

  test("Service should transfer money using /accounts/{debitAccNumber}/transfer-money?to={creditAccNumber}&amount={amount} ") {
    val userId = dbManager.addUser(User("User1", "user1", "")).futureValue
    dbManager.addUserAccount(Account(userId, "111", 100.0)).futureValue
    dbManager.addUserAccount(Account(userId, "222", 0.0)).futureValue
    Get("/accounts/111/transfer-money?to=222&amount=100.0") ~> webService.routes ~> check {
      responseAs[SuccessTransfer] shouldEqual SuccessTransfer(true, 1)
    }
  }

  test("Service should list /accounts/{accNumber}/transactions") {
    val userId = dbManager.addUser(User("User1", "user1", "")).futureValue
    dbManager.addUserAccount(Account(userId, "111", 100.0)).futureValue
    dbManager.addUserAccount(Account(userId, "222", 0.0)).futureValue
    dbManager.transfer("111", "222", 100.0).futureValue
    val transactions = dbManager.listTransactionByAccount("111").futureValue
    assert(transactions.size==1)
    Get("/accounts/111/transactions") ~> webService.routes ~> check {
      responseAs[Seq[Transaction]] shouldEqual transactions
    }
  }
}
