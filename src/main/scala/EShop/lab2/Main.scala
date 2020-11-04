package EShop.lab2

import EShop.lab2.CartActor.{AddItem, RemoveItem}
import EShop.lab2.Checkout.{ConfirmPaymentReceived, SelectDeliveryMethod, SelectPayment, StartCheckout}

object Main {
  def classic() : Unit = {
    import akka.actor.{ActorSystem, Props}
    val system = ActorSystem("system")
    val cart = system.actorOf(Props[CartActor], "cart")
    val checkout = system.actorOf(Props[Checkout], "checkout")
    cart ! AddItem("item")
    cart ! RemoveItem("item")
    cart ! AddItem("item1")
    cart ! AddItem("item2")
    checkout ! StartCheckout
    checkout ! SelectDeliveryMethod("mail")
    checkout ! SelectPayment("card")
    checkout ! ConfirmPaymentReceived
    system.terminate()
  }

  def main(args: Array[String]): Unit = {
    classic()
  }
}
