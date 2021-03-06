package EShop.lab5

import java.net.URI

import EShop.lab5.ProductCatalog.{GetItems, Item, Items}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)

    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }
  implicit val itemFormat   = jsonFormat5(Item)
  implicit val returnFormat = jsonFormat1(Items)
}

object HelloWorldAkkaHttpServerApp extends App {
  new AkkaHttpServer().startServer("localhost", 9000)
}

class AkkaHttpServer extends HttpApp with JsonSupport {

  override protected def routes: Route = {
    implicit def system = systemReference.get
    implicit val timeout: Timeout = Timeout(5 seconds)
    implicit val context: ExecutionContextExecutor = system.dispatcher

    path("catalog") {
      get {
        parameters("brand".as[String], "words".as[String]) { (brand, words) =>
          val query = GetItems(brand, words.split(" ").toList)
          val productCatalog = system.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog")
          val future = productCatalog ? query
          onSuccess(future) {
            case items: ProductCatalog.Items => complete(items)
          }
        }
      }
    }
  }
}

