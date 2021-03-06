package org.samples.trading.akka

import org.junit._
import Assert._
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.samples.trading.domain._
import org.samples.trading.common._
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Actor.actorOf
import akka.config.Config
import akka.config.Config._
import akka.dispatch.Dispatchers
import akka.actor.PoisonPill
import org.samples.trading.workbench.Report
import org.samples.trading.workbench.BenchResultRepository

class AkkaPerformanceTest extends BenchmarkScenarios // with OtherPerformanceScenarios 
{
  type TS = AkkaTradingSystem

  override def report = new AkkaReport(resultRepository, compareResultWith)

  val clientDispatcher = Dispatchers.newExecutorBasedEventDrivenDispatcher("client-dispatcher")
    .withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity
    .setCorePoolSize(maxClients)
    .setMaxPoolSize(maxClients)
    .build

  override def createTradingSystem: TS = new AkkaTradingSystem

  override def placeOrder(orderReceiver: ActorRef, order: Order): Rsp = {
    (orderReceiver !!! order).get
  }

  // need this so that junit will detect this as a test case
  @Test
  def dummy {}

  override def runScenario(scenario: String, orders: List[Order], repeat: Int, numberOfClients: Int, delayMs: Int) = {
    val totalNumberOfRequests = orders.size * repeat
    val repeatsPerClient = repeat / numberOfClients
    val oddRepeats = repeat - (repeatsPerClient * numberOfClients)
    val latch = new CountDownLatch(numberOfClients)
    val receivers = tradingSystem.orderReceivers.toIndexedSeq
    val clients = (for (i ← 0 until numberOfClients) yield {
      val receiver = receivers(i % receivers.size)
      actorOf(new Client(receiver, orders, latch, repeatsPerClient + (if (i < oddRepeats) 1 else 0), delayMs))
    }).toList

    clients.foreach(_.start)
    val start = System.nanoTime
    clients.foreach(_ ! "run")
    val ok = latch.await((5000 + (2 + delayMs) * totalNumberOfRequests) * timeDilation, TimeUnit.MILLISECONDS)
    val durationNs = (System.nanoTime - start)

    assertTrue(ok)
    assertEquals((orders.size / 2) * repeat, TotalTradeCounter.counter.get)
    logMeasurement(scenario, numberOfClients, durationNs)
    clients.foreach(_ ! PoisonPill)
  }

  class Client(orderReceiver: ActorRef, orders: List[Order], latch: CountDownLatch, repeat: Int, delayMs: Int) extends Actor {

    self.dispatcher = clientDispatcher

    def this(orderReceiver: ActorRef, orders: List[Order], latch: CountDownLatch, repeat: Int) {
      this(orderReceiver, orders, latch, repeat, 0)
    }

    def receive = {
      case "run" ⇒
        (1 to repeat).foreach(i ⇒
          {
            // println("Client " + Thread.currentThread + " repeat: " + i)
            for (o ← orders) {
              val t0 = System.nanoTime
              val rsp = placeOrder(orderReceiver, o)
              val duration = System.nanoTime - t0
              stat.addValue(duration)
              if (!rsp.status) {
                println("Invalid rsp")
              }
              delay(delayMs)
            }
          })
        latch.countDown()

    }
  }

}

class AkkaReport(resultRepository: BenchResultRepository,
  compareResultWith: Option[String] = None)
  extends Report(resultRepository, compareResultWith) {

  override def systemInformation: String = {
    val sb = new StringBuilder

    sb.append(super.systemInformation)

    sb.append("Akka version: ").append(Config.CONFIG_VERSION)
    sb.append("\n")
    sb.append("Akka config:")
    for (key ← config.keys) {
      sb.append("\n  ").append(key).append("=").append(config(key))
    }
    sb.append("\n")

    sb.toString
  }
}