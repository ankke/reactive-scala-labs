package EShop.lab2

import EShop.lab2.CartActor.AddItem
import EShop.lab2.CartActor.RemoveItem

object Main {
  def classic() : Unit = {
    import akka.actor.{ActorSystem, Props}
    val system = ActorSystem("system")
    val cart = system.actorOf(Props[CartActor], "cart")
    cart ! AddItem("item")
    cart ! RemoveItem("item")
    system.terminate()
  }

  def main(args: Array[String]): Unit = {
    classic()
  }
}
