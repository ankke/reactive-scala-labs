package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Props}

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
}

class TypedOrderManager {

  import TypedOrderManager._

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
        case Buy(sender) =>
          cartActor ! TypedCartActor.StartCheckout(context.self)
          inCheckout(cartActor, sender)
      }
  }

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
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

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[TypedOrderManager.Command] =
    Behaviors.receive { (context, message) =>
      context.log.info("inCheckout 2 Received message: {}", message)
      message match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
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
        sender ! Done
        Behaviors.same
      case ConfirmPaymentReceived =>
        senderRef ! Done
        finished
      case _ =>
        context.log.info("Received unknown message: {}", message)
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
