package EShop.lab2

import EShop.lab3.TypedOrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                                  extends Command
  case class RemoveItem(item: Any)                                               extends Command
  case object ExpireCart                                                         extends Command
  case class StartCheckout(orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                           extends Command
  case object ConfirmCheckoutClosed                                              extends Command
  case class GetItems(sender: ActorRef[Cart])                                    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }
  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))
  case class InCheckout(cart: Cart)                   extends State(None)
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[Command] = empty

  def empty: Behavior[Command] =
    Behaviors.receive(
      (context, message) =>
        message match {
          case AddItem(item) => nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
          case _ =>
            context.log.info("Received unknown message: {}", message)
            Behaviors.same
      }
    )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[Command] = Behaviors.receive { (context, message) =>
    context.log.info("nonEmpty Received message: {}", message)
    message match {
      case AddItem(item)                            => nonEmpty(cart.addItem(item), timer)
      case RemoveItem(item) if !cart.contains(item) => Behaviors.same
      case RemoveItem(_) if cart.size == 1          => empty
      case RemoveItem(item)                         => nonEmpty(cart.removeItem(item), timer)
      case StartCheckout(orderManager) =>
        context.log.info("nonEmpty Received message: {}", message)
        val checkoutActor = context.spawn(new TypedCheckout(context.self).start, "checkout")
        orderManager ! TypedOrderManager.ConfirmCheckoutStarted(checkoutActor)
        context.log.info("nonEmpty Received message: {}", message)
        checkoutActor ! TypedCheckout.StartCheckout
        inCheckout(cart)
      case ExpireCart => empty
      case _ =>
        context.log.info("Received unknown message: {}", message)
        Behaviors.same
    }
  }

  def inCheckout(cart: Cart): Behavior[Command] =
    Behaviors.receive(
      (context, message) =>
        message match {
          case ConfirmCheckoutCancelled => nonEmpty(cart, scheduleTimer(context))
          case ConfirmCheckoutClosed    => empty
          case _ =>
            context.log.info("Received unknown message: {}", message)
            Behaviors.same
      }
    )
}
