package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.TypedOrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class TypedPersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command], duration: FiniteDuration): Cancellable =
    context.scheduleOnce(duration, context.self, ExpireCart)

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted(_)        => InCheckout(state.cart)
      case ItemAdded(item)           => NonEmpty(state.cart.addItem(item), scheduleTimer(context, cartTimerDuration))
      case ItemRemoved(item)         => NonEmpty(state.cart.removeItem(item), scheduleTimer(context, cartTimerDuration))
      case CartEmptied | CartExpired => Empty
      case CheckoutClosed            => Empty
      case CheckoutCancelled         => NonEmpty(state.cart, scheduleTimer(context, cartTimerDuration))
    }
  }

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) => Effect.persist(ItemAdded(item))
          case GetItems(sender) =>
            sender ! Cart.empty
            Effect.none
          case message =>
            context.log.info("Received unknown message: {}", message)
            Effect.none
        }
      case NonEmpty(cart, _) =>
        command match {
          case AddItem(item)                            => Effect.persist(ItemAdded(item))
          case RemoveItem(item) if !cart.contains(item) => Effect.none
          case RemoveItem(_) if cart.size == 1          => Effect.persist(CartEmptied)
          case RemoveItem(item)                         => Effect.persist(ItemRemoved(item))
          case StartCheckout(orderManagerRef) =>
            val checkoutActor = context.spawn(new TypedCheckout(context.self).start, "checkout")
            Effect.persist(CheckoutStarted(checkoutActor)).thenRun { _ =>
              checkoutActor ! TypedCheckout.StartCheckout
              orderManagerRef ! TypedOrderManager.ConfirmCheckoutStarted(checkoutActor)
            }
          case ExpireCart => Effect.persist(CartExpired)
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case message =>
            context.log.info("Received unknown message: {}", message)
            Effect.none
        }
      case InCheckout(cart) =>
        command match {
          case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled)
          case ConfirmCheckoutClosed    => Effect.persist(CheckoutClosed)
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case message =>
            context.log.info("Received unknown message: {}", message)
            Effect.none
        }
    }
  }
}
