package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef)      extends Event
  case object CheckoutStarted                       extends Event
  case object CheckoutCancelled                     extends Event
  case class DeliveryMethodSelected(method: String) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(
  cartActor: ActorRef
) extends Actor {
  import context.dispatcher
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become
        selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
    case message => log info s"unknown message: $message"
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) => context become selectingPaymentMethod(timer)
    case CancelCheckout               => context become cancelled
    case ExpireCheckout               => context become cancelled
    case message                      => log info s"unknown message: $message"
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      timer.cancel()
      context become processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))
    case CancelCheckout => context become cancelled
    case ExpireCheckout => context become cancelled
    case message        => log info s"unknown message: $message"
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived =>
      timer.cancel()
      context become closed
    case CancelCheckout => context become cancelled
    case ExpirePayment  => context become cancelled
    case message        => log info s"unknown message: $message"
  }

  def cancelled: Receive = LoggingReceive {
    case _ => context.stop(self)
  }

  def closed: Receive = LoggingReceive {
    case _ => context.stop(self)
  }

}
