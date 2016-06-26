package com.tim.revolut

import java.sql.Timestamp
import java.text.SimpleDateFormat

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

import scala.util.{Failure, Success}
/**
  * Created by Mikhail Titov on 26.06.16.
  */
final case class SuccessTransfer(successfully: Boolean, txId: Long)
final case class OperationError(successfully: Boolean, error: String)

//Implementing JSON protocol for types that are not in the DefaultJsonProtocol
object JsonProto extends DefaultJsonProtocol {
  implicit object TimestampFormat extends RootJsonFormat[Timestamp] {
    def getFormatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS")
    override def write(obj: Timestamp): JsValue = JsString(getFormatter.format(obj))
    override def read(json: JsValue): Timestamp = new Timestamp(getFormatter.parse(json.convertTo[String]).getTime)
  }
  implicit val userFormat = jsonFormat4(User)
  implicit val userWithInfo = jsonFormat5(UserWithInfo)
  implicit val accountFormat = jsonFormat4(Account)
  implicit val transactionFormat = jsonFormat5(Transaction)
  implicit val successTransferFormat = jsonFormat2(SuccessTransfer)
  implicit val transferErrorFormat = jsonFormat2(OperationError)
}

//Defining simple akka http based web server
class WebServer(implicit val dataManager: DataManager, implicit val actorSystem: ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends WebService {
  val binding =  {
    logger.info("Starting WEB server on localhost:8777")

    val res = Http().bindAndHandle(routes, "localhost", 8777)
    res.foreach { r=>
      logger.info("WEB Server successfully started")
    }
    res
  }

  def stop(): Future[Unit] = binding flatMap (_.unbind()) flatMap {r=>
    logger.info("WEB server stopped")
    Future.successful()
  }
}

//Splitting web server implementation on class and trait for test purposes.
trait WebService {
  val logger = LoggerFactory.getLogger(classOf[WebService])
  implicit val dataManager: DataManager
  implicit val actorSystem: ActorSystem
  implicit val actorMaterializer: ActorMaterializer
  implicit lazy val dispatcher = actorSystem.dispatcher


  import JsonProto._

  val accountTransactionsRoute =  path(".*".r / "transactions") { accNum:String =>
    get {
      onComplete(dataManager.listTransactionByAccount(accNum)) {
        case Success(transactions) => complete(transactions)
        case Failure(e) => complete(OperationError(false, e.getMessage))
      }
    }
  }

  val routes =
    pathPrefix("users") {
      pathEndOrSingleSlash {
        get {
          onComplete(dataManager.listUsersWithInfo) {
            case Success(users) => complete(users)
            case Failure(e) => complete(OperationError(false, e.getMessage))
          }
        }
      } ~
        pathPrefix(LongNumber / "accounts") { userId:Long =>
          pathEndOrSingleSlash {
            get {
              onComplete(dataManager.listUserAccounts(userId)) {
                case Success(accs) => complete(accs)
                case Failure(e) => complete(OperationError(false, e.getMessage))
              }
            }
          } ~ accountTransactionsRoute
        }
    } ~
      pathPrefix("accounts") {
        path("") {
          get {
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>listing accounts</h1>"))
          }
        } ~
          path(".*".r / "transfer-money") { debitAcc =>
            parameters('to.as[String], 'amount.as[Double]) { (creditAcc, amount) =>
              logger.debug("Trying to transfer $$($amount) from account ($debitAcc) to ($creditAcc)")
              val txFuture = dataManager.transfer(debitAcc, creditAcc, amount)
              onComplete(txFuture) {
                case Success(txId) => complete(SuccessTransfer(true, txId))
                case Failure(e) => complete(OperationError(false, e.getMessage))
              }
            }
          } ~ accountTransactionsRoute
      }
}
