package org.samples.trading.common

import java.util.Random
import org.junit._
import Assert._
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math.stat.descriptive.SynchronizedDescriptiveStatistics

import org.samples.trading.domain._

trait PerformanceTest {

  //	jvm parameters
  //	-server -Xms512m -Xmx1024m -XX:+UseConcMarkSweepGC -Dbenchmark=true

  // Use longRun for benchmark
  val longRun = if (isBenchmark) 50 else 10;
  
  def isBenchmark() = 
    System.getProperty("benchmark") == "true" 
  
  def minClients() =
    Integer.parseInt(System.getProperty("minClients", "1"));
  
  def maxClients() =
    Integer.parseInt(System.getProperty("maxClients", "40"));
  
  var stat: DescriptiveStatistics = _

  type TS <: TradingSystem

  var tradingSystem: TS = _
  val random: Random = new Random()

  def createTradingSystem: TS

  def placeOrder(orderReceiver: TS#OR, order: Order): Rsp

  def runScenario(scenario: String, orders: List[Order], repeat: Int, numberOfClients: Int, delayMs: Int)

  @Before
  def setUp {
    stat = new SynchronizedDescriptiveStatistics
    tradingSystem = createTradingSystem
    tradingSystem.start
    warmUp
    TotalTradeCounter.reset
    stat = new SynchronizedDescriptiveStatistics
  }

  @After
  def tearDown {
    tradingSystem.shutdown
  }

  def warmUp {
    val bid = new Bid("A1", 100, 1000)
    val ask = new Ask("A1", 100, 1000)

    val orderReceiver = tradingSystem.orderReceivers.head
    val loopCount = if (isBenchmark) 1000 else 100;
    for (i <- 1 to loopCount) {
      placeOrder(orderReceiver, bid)
      placeOrder(orderReceiver, ask)
    }
  }

  def logMeasurement(scenario: String, numberOfClients: Int, durationNs: Long, repeat: Int, totalNumberOfRequests: Int) {
    val durationUs = durationNs / 1000
    val durationMs = durationNs / 1000000
    val durationS = durationNs.toDouble / 1000000000.0
    val duration = durationS.formatted("%.0f")
    val n = stat.getN
    val mean = (stat.getMean / 1000).formatted("%.0f")
    val tps = (stat.getN.toDouble / durationS).formatted("%.0f")
    val p5 = (stat.getPercentile(5.0) / 1000).formatted("%.0f")
    val p25 = (stat.getPercentile(25.0) / 1000).formatted("%.0f")
    val p50 = (stat.getPercentile(50.0) / 1000).formatted("%.0f")
    val p75 = (stat.getPercentile(75.0) / 1000).formatted("%.0f")
    val p95 = (stat.getPercentile(95.0) / 1000).formatted("%.0f")
    val name = getClass.getSimpleName + "." + scenario
    
    val summaryLine = name :: numberOfClients.toString :: tps :: mean :: p5 :: p25 :: p50 :: p75 :: p95 :: duration :: n :: Nil
    StatSingleton.results = summaryLine.mkString("\t") :: StatSingleton.results
    

    val spaces = "                                                                                     "
    val headerScenarioCol = ("Scenario" + spaces).take(name.length)
    
    val headerLine =  (headerScenarioCol ::        "clients" :: "TPS" :: "mean" :: "5%  " :: "25% " :: "50% " :: "75% " :: "95% " :: "Durat." :: "N" :: Nil)
      .mkString("\t")
    val headerLine2 = (spaces.take(name.length) :: "       " :: "   " :: "(us)" :: "(us)" :: "(us)" :: "(us)" :: "(us)" :: "(us)" :: "(s)   " :: " " :: Nil)
      .mkString("\t")
    val line = List.fill(StatSingleton.results.head.replaceAll("\t", "      ").length)("-").mkString 
    println(line.replace('-', '='))
    println(headerLine)
    println(headerLine2)
    println(line)
    println(StatSingleton.results.reverse.mkString("\n"))
    println(line)
  }

  def delay(delayMs: Int) {
    val adjustedDelay =
    if (delayMs >= 5) {
      val dist = 0.2 * delayMs
      (delayMs + random.nextGaussian * dist).intValue
    } else {
      delayMs
    }

    if (adjustedDelay > 0) {
      Thread.sleep(adjustedDelay)
    }
  }

}

object StatSingleton {
  var results: List[String] = Nil
}
