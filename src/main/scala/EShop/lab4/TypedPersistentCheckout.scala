package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.{TypedOrderManager, TypedPayment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class TypedPersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  private def scheduleTimer(context: ActorContext[Command], duration: FiniteDuration, command: Command): Cancellable =
    context.scheduleOnce(duration, context.self, command)

  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart =>
        command match {
          case StartCheckout => Effect.persist(CheckoutStarted)
          case message =>
            context.log.info("Received unknown message: {}", message)
            Effect.none
        }

      case SelectingDelivery(_) =>
        command match {
          case SelectDeliveryMethod(method) => Effect.persist(DeliveryMethodSelected(method))
          case ExpireCheckout               => Effect.persist(CheckoutCancelled)
          case CancelCheckout               => Effect.persist(CheckoutCancelled)
          case message =>
            context.log.info("Received unknown message: {}", message)
            Effect.none
        }

      case SelectingPaymentMethod(_) =>
        command match {
          case SelectPayment(payment, orderManagerRef) =>
            val paymentActor =
              context.spawn(new TypedPayment(payment, orderManagerRef, context.self).start, "PaymentActor")
            Effect
              .persist(PaymentStarted(paymentActor))
              .thenRun(_ => orderManagerRef ! TypedOrderManager.ConfirmPaymentStarted(paymentActor))
          case ExpireCheckout => Effect.persist(CheckoutCancelled)
          case CancelCheckout => Effect.persist(CheckoutCancelled)
          case message =>
            context.log.info("Received unknown message: {}", message)
            Effect.none
        }

      case ProcessingPayment(_) =>
        command match {
          case ConfirmPaymentReceived =>
            Effect
              .persist(CheckOutClosed)
              .thenRun(_ => cartActor ! TypedCartActor.ConfirmCheckoutClosed)
          case ExpirePayment  => Effect.persist(CheckoutCancelled)
          case CancelCheckout => Effect.persist(CheckoutCancelled)
          case message =>
            context.log.info("Received unknown message: {}", message)
            Effect.none
        }

      case Cancelled =>
        command match {
          case _ => Effect.none
        }

      case Closed =>
        command match {
          case _ => Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted           => SelectingDelivery(scheduleTimer(context, timerDuration, ExpireCheckout))
      case DeliveryMethodSelected(_) => SelectingPaymentMethod(scheduleTimer(context, timerDuration, ExpirePayment))
      case PaymentStarted(_)         => ProcessingPayment(scheduleTimer(context, timerDuration, ExpirePayment))
      case CheckOutClosed            => Closed
      case CheckoutCancelled         => Cancelled
    }
  }
}
