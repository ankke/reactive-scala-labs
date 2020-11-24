package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedPayment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentConfirmed extends Event

}

class TypedPayment(
  method: String,
  orderManager: ActorRef[TypedOrderManager.Command],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import TypedPayment._

  def start: Behavior[TypedPayment.Command] = Behaviors.receive { (context, message) =>
    message match {
      case DoPayment =>
        checkout ! TypedCheckout.ReceivePayment
        orderManager ! TypedOrderManager.ConfirmPaymentReceived
        Behaviors.same
      case _ =>
        context.log.info("Received unknown message: {}", message)
        Behaviors.same
    }
  }

}
