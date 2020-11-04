package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Props}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{TypedOrderManager, TypedPayment}

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                       extends Command
  case class SelectDeliveryMethod(method: String)                                                 extends Command
  case object CancelCheckout                                                                      extends Command
  case object ExpireCheckout                                                                      extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command
  case object ExpirePayment                                                                       extends Command
  case object ConfirmPaymentReceived                                                              extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) =>
      message match {
        case StartCheckout =>
          selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
        case _ =>
          context.log.info("Received unknown message: {}", message)
          Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive { (context, message) =>
    message match {
      case SelectDeliveryMethod(method) => selectingPaymentMethod(timer)
      case CancelCheckout               => cancelled
      case ExpireCheckout               => cancelled
      case _ =>
        context.log.info("Received unknown message: {}", message)
        Behaviors.same
    }
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive(
      (context, message) =>
        message match {
          case SelectPayment(payment, orderManager) =>
            timer.cancel()
            orderManager ! TypedOrderManager.ConfirmPaymentStarted(
              context.spawn(new TypedPayment(payment, orderManager, context.self).start, "payment")
            )
            processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
          case CancelCheckout => cancelled
          case ExpireCheckout => cancelled
          case _ =>
            context.log.info("Received unknown message: {}", message)
            Behaviors.same
      }
    )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive { (context, message) =>
    message match {
      case ConfirmPaymentReceived =>
        timer.cancel()
        cartActor ! TypedCartActor.ConfirmCheckoutClosed
        closed
      case CancelCheckout => cancelled
      case ExpirePayment  => cancelled
      case _ =>
        context.log.info("Received unknown message: {}", message)
        Behaviors.same

    }
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

}
