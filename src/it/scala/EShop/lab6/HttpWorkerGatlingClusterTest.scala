package EShop.lab6

import io.gatling.core.Predef.{Simulation, scenario, _}
import io.gatling.http.Predef.http

import scala.concurrent.duration._

class HttpWorkerGatlingClusterTest extends Simulation {
  val feeder = csv("/Users/ania/studia/scala/reactive-scala-labs-templates/src/it/resources/data/search.csv").random

  val httpProtocol = http  //values here are adjusted to cluster_demo.sh script
    .baseUrls("http://localhost:9006", "http://localhost:9007", "http://localhost:9005")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(feeder)
    .exec(
      http("work_basic")
        .get("/catalog?brand=${brand}&words=${words}")
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(
      incrementUsersPerSec(25)
        .times(10)
        .eachLevelLasting(1.minutes)
        .separatedByRampsLasting(30.seconds)
        .startingFrom(25)
    )
  ).protocols(httpProtocol)
}