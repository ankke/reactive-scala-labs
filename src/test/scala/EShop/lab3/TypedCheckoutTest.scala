package EShop.lab3

import EShop.lab2.TypedCartActor.ConfirmCheckoutClosed
import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckoutTest._
  import EShop.lab2.TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val probe          = testKit.createTestProbe[String]
    val cartActorProbe = testKit.createTestProbe[TypedCartActor.Command]
    val orderManagerActorProbe = testKit.createTestProbe[TypedOrderManager.Command]
    val checkoutActor  = checkoutActorWithResponseOnStateChange(testKit, probe.ref, cartActorProbe.ref)

    probe.expectMessage(emptyMsg)
    checkoutActor ! StartCheckout
    probe.expectMessage(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod("order")
    probe.expectMessage(selectingPaymentMethodMsg)
    checkoutActor ! SelectPayment("paypal", orderManagerActorProbe.ref)
    probe.expectMessage(processingPaymentMsg)
    checkoutActor ! ConfirmPaymentReceived
    cartActorProbe.expectMessage(ConfirmCheckoutClosed)
  }

}

object TypedCheckoutTest {

  val emptyMsg                  = "empty"
  val selectingDeliveryMsg      = "selectingDelivery"
  val selectingPaymentMethodMsg = "selectingPaymentMethod"
  val processingPaymentMsg      = "processingPayment"
  val cancelledMsg              = "cancelled"
  val closedMsg                 = "closed"

  def checkoutActorWithResponseOnStateChange(
    testkit: ActorTestKit,
    probe: ActorRef[String],
    cartActorProbe: ActorRef[TypedCartActor.Command]
  ): ActorRef[TypedCheckout.Command] =
    testkit.spawn {
      val checkout = new TypedCheckout(cartActorProbe) {

        override def start: Behavior[TypedCheckout.Command] =
          Behaviors.setup(_ => {
            probe ! emptyMsg
            super.start
          })

        override def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
          Behaviors.setup(_ => {
            val result = super.selectingDelivery(timer)
            probe ! selectingDeliveryMsg
            result
          })

        override def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
          Behaviors.setup(_ => {
            probe ! selectingPaymentMethodMsg
            super.selectingPaymentMethod(timer)
          })

        override def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
          Behaviors.setup(_ => {
            probe ! processingPaymentMsg
            super.processingPayment(timer)
          })

      }
      checkout.start
    }
}
