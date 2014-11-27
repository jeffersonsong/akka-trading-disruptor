package org.samples.trading.akkahawt

import org.samples.trading.akkabang._
import akka.dispatch.Dispatchers
import akka.dispatch.HawtDispatcher
import akka.dispatch.MessageDispatcher

class AkkaHawtTradingSystem extends AkkaBangTradingSystem {

  lazy val hawtDispatcher = new HawtDispatcher(false, null)

  override def createOrderReceiverDispatcher: Option[MessageDispatcher] = Option(hawtDispatcher)

  override def createMatchingEngineDispatcher: Option[MessageDispatcher] = Option(hawtDispatcher)

  override def start() {
    super.start()

    for (MatchingEngineInfo(p, s, o) ← matchingEngines) {
      if (s.isDefined) {
        HawtDispatcher.target(p, HawtDispatcher.queue(s.get))
      }
    }
  }

}