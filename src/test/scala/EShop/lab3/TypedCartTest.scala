package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCartActorTest}
import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import scala.concurrent.duration.FiniteDuration

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActorTest._
  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val probe = testKit.createTestProbe[Any]()
    val cart  = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("item")

    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)

    cart ! RemoveItem("item")

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)
  }

  it should "be empty after adding and removing the same item" in {
    val probe = testKit.createTestProbe[Any]()
    val cart  = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("item")

    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)

    cart ! RemoveItem("item")

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)
  }

  it should "start checkout" in {
    val probe = testKit.createTestProbe[Any]()
    val cart  = cartActorWithCartSizeResponseOnStateChange(testKit, probe.ref)

    probe.expectMessage(emptyMsg)
    probe.expectMessage(0)

    cart ! AddItem("Romeo & Juliet")

    probe.expectMessage(nonEmptyMsg)
    probe.expectMessage(1)

    cart ! StartCheckout(testKit.createTestProbe[TypedOrderManager.Command]().ref)

    probe.expectMessage(inCheckoutMsg)
    probe.expectMessage(1)
  }
}

object TypedCartActorTest {
  val emptyMsg      = "empty"
  val nonEmptyMsg   = "nonEmpty"
  val inCheckoutMsg = "inCheckout"

  def cartActorWithCartSizeResponseOnStateChange(
    testKit: ActorTestKit,
    probe: ActorRef[Any]
  ): ActorRef[TypedCartActor.Command] =
    testKit.spawn {
      val cartActor = new TypedCartActor {
        override val cartTimerDuration: FiniteDuration = 1.seconds

        override def empty: Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! emptyMsg
            probe ! 0
            super.empty
          })

        override def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! nonEmptyMsg
            probe ! cart.size
            super.nonEmpty(cart, timer)
          })

        override def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] =
          Behaviors.setup(_ => {
            probe ! inCheckoutMsg
            probe ! cart.size
            super.inCheckout(cart)
          })

      }
      cartActor.start
    }
}
