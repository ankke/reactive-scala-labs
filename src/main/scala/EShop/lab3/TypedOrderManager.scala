package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

object TypedOrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[TypedPayment.Command])                        extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK

  def apply(): Behavior[TypedOrderManager.Command] = Behaviors.setup { context =>
    new TypedOrderManager(context).start
  }
}

class TypedOrderManager(context: ActorContext[TypedOrderManager.Command]) {

  import TypedOrderManager._

  val cartEventAdapter: ActorRef[TypedCartActor.Event] =
    context.messageAdapter {
      case TypedCartActor.CheckoutStarted(checkoutRef) => ConfirmCheckoutStarted(checkoutRef)
    }

  val checkoutEventAdapter: ActorRef[TypedCheckout.Event] =
    context.messageAdapter {
      case TypedCheckout.PaymentStarted(paymentRef) => ConfirmPaymentStarted(paymentRef)
    }

  val paymentEventAdapter: ActorRef[TypedPayment.Event] =
    context.messageAdapter {
      case TypedPayment.ConfirmPaymentReceived => ConfirmPaymentReceived
    }

  def start: Behavior[TypedOrderManager.Command] = uninitialized

  def uninitialized: Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    context.log.info("uninitialized Received message: {}", message)
    message match {
      case AddItem(id, sender) =>
        val cartActor = context.spawn(new TypedCartActor().start, "cart")
        cartActor ! TypedCartActor.AddItem(id)
        sender ! Done
        open(cartActor)
    }
  }

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[TypedOrderManager.Command] = Behaviors.receive {
    (context, message) =>
      context.log.info("open Received message: {}", message)
      message match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same
        case RemoveItem(item, sender) =>
          cartActor ! TypedCartActor.RemoveItem(item)
          sender ! Done
          Behaviors.same
        case Buy(sender) => inCheckout(cartActor, sender)
      }
  }

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[TypedOrderManager.Command] =
    Behaviors.setup { _ =>
      cartActorRef ! TypedCartActor.StartCheckout(cartEventAdapter)
      Behaviors.receive { (context, message) =>
        context.log.info("inCheckout Received message: {}", message)
        message match {
          case ConfirmCheckoutStarted(checkoutActorRef) =>
            senderRef ! Done
            inCheckout(checkoutActorRef)
          case _ =>
            context.log.info("Received unknown message: {}", message)
            Behaviors.same
        }
      }
  }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[TypedOrderManager.Command] =
    Behaviors.receive { (context, message) =>
      context.log.info("inCheckout 2 Received message: {}", message)
      message match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, checkoutEventAdapter, paymentEventAdapter)
          inPayment(sender)
        case _ =>
          context.log.info("Received unknown message: {}", message)
          Behaviors.same
      }
    }

  def inPayment(senderRef: ActorRef[Ack]): Behavior[TypedOrderManager.Command] = Behaviors.receive {
    (context, message) =>
      context.log.info("inPayment Received message: {}", message)
      message match {
        case ConfirmPaymentStarted(paymentActorRef) =>
          senderRef ! Done
          inPayment(paymentActorRef, senderRef)
        case _ =>
          context.log.info("Received unknown message: {}", message)
          Behaviors.same
      }
  }

  def inPayment(
    paymentActorRef: ActorRef[TypedPayment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    context.log.info("inPayment 2 Received message: {}", message)
    message match {
      case Pay(sender) =>
        paymentActorRef ! TypedPayment.DoPayment
        inPayment(paymentActorRef, sender)
      case ConfirmPaymentReceived =>
        senderRef ! Done
        finished
      case _ =>
        context.log.info(" inPayment 2 Received unknown message: {}", message)
        Behaviors.same
    }
  }

  def finished: Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    context.log.info("finished Received message: {}", message)
    message match {
      case _ => Behaviors.same
    }
  }
}
