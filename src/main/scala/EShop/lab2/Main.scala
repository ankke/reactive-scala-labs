package EShop.lab2

import EShop.lab2.CartActor.AddItem
import EShop.lab2.CartActor.RemoveItem
import akka.actor
import akka.actor.typed

object Main {
  def main(args: Array[String]): Unit = {
    val system = actor.ActorSystem("system")
    val cart = system.actorOf(actor.Props[CartActor], "cart")
    cart ! AddItem("item")
    cart ! RemoveItem("item")
    system.terminate()

    val typedSystem = typed.ActorSystem[TypedCartActor.Command] =
  }
}
