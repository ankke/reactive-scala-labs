package EShop.lab6

import EShop.lab5.{JsonSupportCluster, ProductCatalog, SearchService}
import EShop.lab5.ProductCatalog.GetItems
import akka.actor.{ActorSystem, Props}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object HelloWorldAkkaHttpServerApp extends App {
  new AkkaHttpServer(Try(args(1)).getOrElse("cluster-default")).startServer("localhost", args(0).toInt)
}

class AkkaHttpServer(config_name: String) extends HttpApp with JsonSupportCluster {
  private val config = ConfigFactory.load()

  val system = ActorSystem(
    "ClusterWorkRouters",
    config
      .getConfig(config_name)
      .withFallback(config.getConfig("cluster-default"))
  )

  override protected def routes: Route = {
    implicit val timeout: Timeout                  = Timeout(5 seconds)
    implicit val context: ExecutionContextExecutor = system.dispatcher

    val productCatalog =
      system.actorOf(
        ClusterRouterPool(
          RoundRobinPool(0),
          ClusterRouterPoolSettings(totalInstances = 100, maxInstancesPerNode = 3, allowLocalRoutees = true)
        ).props(ProductCatalog.props(new SearchService())),
        name = "clusterWorkerRouter"
      )

    path("catalog") {
      get {
        parameters("brand".as[String], "words".as[String]) { (brand, words) =>
          val query  = GetItems(brand, words.split(" ").toList)
          val future = productCatalog ? query
          onComplete(future) {
            case Success(value: ProductCatalog.Items) => complete(value)
            case Failure(error)                       => complete(error.getMessage())
          }
        }
      }
    }
  }
}
