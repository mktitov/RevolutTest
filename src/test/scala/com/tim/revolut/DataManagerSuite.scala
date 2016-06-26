package com.tim.revolut

import org.h2.jdbc.JdbcSQLException
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

/**
  * Created by Mikhail Titov on 26.06.16.
  */

class DataManagerSuite extends FunSuite with BeforeAndAfter with ScalaFutures with Matchers {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds))

  var dbManager: DataManager = _
  before {
    dbManager = new DataManager("h2mem1")
    dbManager.createTables().futureValue
  }
  after  { dbManager.close() }

  def createAccounts(): User = {
    dbManager.addUser(User("User 1", "user1", "123")).futureValue
    val user = dbManager.findUserByLogin("user1").futureValue.get
    dbManager.addUserAccount(Account(user.id.get, "111", 100.0)).futureValue
    dbManager.addUserAccount(Account(user.id.get, "222", 30.0)).futureValue
    user
  }

  test("User inserting should work") {
    dbManager.addUser(User("User 1", "user1", "123")).futureValue
    val userOpt = dbManager.findUserByLogin("user1").futureValue
    assert(!userOpt.isEmpty)
    val user = userOpt.get
    assert(user.name=="User 1")
    assert(user.login=="user1")
    assert(user.pwd=="123")
    assert(!user.id.isEmpty)
  }

  test("Adding two users with the same login should not be able") {
    dbManager.addUser(User("User 1", "user1", "123")).futureValue
    val result = dbManager.addUser(User("User 2", "user1", "321"))
    result.failed.futureValue shouldBe an [JdbcSQLException]
  }

  test("Adding accounts to user should work") {
    val user = createAccounts()
    val accounts = dbManager.listUserAccounts(user.id.get).futureValue
    assert(accounts.size==2)
  }

  test("Search by account number must work") {
    createAccounts()
    val accOpt = dbManager.findAccountByNumber("111").futureValue
    assert(!accOpt.isEmpty)
    assert(accOpt.get.number=="111")
    val accOpt2 = dbManager.findAccountByNumber("222").futureValue
    assert(!accOpt2.isEmpty)
    assert(accOpt2.get.number=="222")
  }

  test("Adding two accounts with the same account number should not be able") {
    dbManager.addUser(User("User 1", "user1", "123")).futureValue
    val user = dbManager.findUserByLogin("user1").futureValue.get
    dbManager.addUserAccount(Account(user.id.get, "111", 100.0)).futureValue
    val result = dbManager.addUserAccount(Account(user.id.get, "111", 30.0))
    result.failed.futureValue shouldBe an [JdbcSQLException]
  }

  test("Funds could be transferred between accounts") {
    createAccounts()
    val txId = dbManager.transfer("111", "222", 100.0).futureValue
    val txOpt = dbManager.getTransactionById(txId).futureValue
    assert(!txOpt.isEmpty)
    assert(txOpt.get.amount==100.0)
    val acc1 = dbManager.findAccountByNumber("111").futureValue.get
    val acc2 = dbManager.findAccountByNumber("222").futureValue.get
    assert(acc1.balance==0.0)
    assert(acc2.balance==130.0)
  }

  test("Transfer from account with insufficient funds should not be able") {
    createAccounts()
    val result = dbManager.transfer("111", "222", 100.01)
    result.failed.futureValue shouldBe an [InvalidTransactionException]
  }

  test("Transfer from nonexisten account should not be able") {
    createAccounts()
    val result = dbManager.transfer("333", "222", 100.01)
    result.failed.futureValue shouldBe an [InvalidTransactionException]
  }

  test("Transfer to nonexisten account should not be able") {
    createAccounts()
    val result = dbManager.transfer("111", "333", 50.00)
    result.failed.futureValue shouldBe an [InvalidTransactionException]
    val acc = dbManager.findAccountByNumber("111").futureValue.get
    assert(acc.balance==100.0)
  }

  test("Transactions list should work") {
    val user = createAccounts()
    var transactions = dbManager.listTransactionByAccount("111").futureValue
    assert(transactions.isEmpty)

    dbManager.addUserAccount(Account(user.id.get, "333", 0.0)).futureValue

    dbManager.transfer("111", "222", 100.00).futureValue
    dbManager.transfer("222", "111", 30.00).futureValue
    dbManager.transfer("222", "333", 50.00).futureValue
    transactions = dbManager.listTransactionByAccount("111").futureValue
    assert(transactions.size==2)
    transactions = dbManager.listTransactionByAccount("222").futureValue
    assert(transactions.size==3)
    transactions = dbManager.listTransactionByAccount("000").futureValue
    assert(transactions.isEmpty)
    transactions = dbManager.listTransactionByAccount("333").futureValue
    assert(transactions.size==1)
  }
}
