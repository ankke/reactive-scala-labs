package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._
  import context.system
  import context.dispatcher

  private val log       = Logging(system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive{
    case AddItem(item) => context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive{
    case AddItem(item) => timer.cancel()
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case StartCheckout => timer.cancel()
      context become inCheckout(cart)
    case RemoveItem(item) if !cart.contains(item) =>
    case RemoveItem(_) if cart.size == 1=> timer.cancel()
      context become empty
    case RemoveItem(item) => timer.cancel()
      context become nonEmpty(cart.removeItem(item), scheduleTimer)
    case ExpireCart => context become empty
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive{
    case ConfirmCheckoutCancelled => context become nonEmpty(cart, scheduleTimer)
    case ConfirmCheckoutClosed => context become empty
  }

}
