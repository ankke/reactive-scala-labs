package EShop.lab2

import EShop.lab2.CartActor.AddItem
import akka.actor.{ActorSystem, Props}

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("system")
    val cart = system.actorOf(Props[CartActor], "cart")
    cart ! AddItem("item")
    system.terminate()
  }
}
