package com.tim.revolut

import java.sql.Timestamp

/**
  * @author Mikhail Titov
  */

case class User(name:String, login:String, pwd:String, id:Option[Long] = None)
case class Account(userId:Long, number:String, balance:Double, id:Option[Long] = None)
case class Transaction(debitAccountId:Long, creditAccountId:Long, amount:Double, time:Timestamp, id:Option[Long] = None)

case class UserWithInfo(id:Long, name:String, login:String, accountsCount: Option[Int], totalBalance: Option[Double])
