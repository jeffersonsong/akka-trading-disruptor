package org.samples.trading.akka

import org.junit._
import Assert._

import akka.actor._
import akka.actor.Actor._

@Ignore
class AkkaPerformanceNoStandByEnginesTest extends AkkaPerformanceTest {
  override def createTradingSystem: TS = new AkkaTradingSystem {
    override def useStandByEngines: Boolean = false}

  // need this so that junit will detect this as a test case
  @Test
  override def dummy {}


}



