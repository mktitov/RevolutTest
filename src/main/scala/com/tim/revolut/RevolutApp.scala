package com.tim.revolut

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await
import scala.io.StdIn

/**
  * Created by Mikhail Titov on 26.06.16.
  */
object RevolutApp extends App {
  val logger = LoggerFactory.getLogger("RevolutTest")

  logger.warn("Initializing application")
  logger.info("Creating actor system")
  implicit val actorSystem = ActorSystem("revolut")
  implicit val actorMaterializer = ActorMaterializer()

  logger.info("Creating database manager layer")
  implicit val dataManager = new DataManager("h2mem1")
  dataManager.createTables().map { _ =>
    dataManager.populateTestData()
  }

  val webServer = new WebServer()
  sys addShutdownHook {
    logger.info("Stopping application")
    Await.result(webServer.stop(), 5 seconds)
    dataManager.close()
  }
  println("Press Ctrl-C to stop...")
}
