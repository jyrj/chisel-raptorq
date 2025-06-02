package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LTEncoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RFC6330 Compliant LTEncoder"

  val testParams = RaptorFECParameters(sourceK = 8, ltRepairCap = 10)
  val testTimeoutCycles = testParams.sourceK + testParams.ltRepairCap * (testParams.sourceK + 10) + 100

  it should "generate repair symbols with symbolInfo from a stream" in {
    test(new LTEncoder(testParams)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val sourceData = Seq.tabulate(testParams.sourceK)(i => ((i * 17 + 3) % 256))
      val numRepairToGen = 5

      dut.io.numRepairSymbolsToGen.poke(numRepairToGen.U)
      dut.io.repairSymbolOut.ready.poke(true.B)
      dut.clock.step(2)

      val driver = fork {
        var cyclesInDriver = 0
        for (i <- 0 until testParams.sourceK) {
          dut.io.sourceSymbolsIn.valid.poke(true.B)
          dut.io.sourceSymbolsIn.bits.data.poke(sourceData(i).U)
          dut.io.sourceSymbolsIn.bits.last.poke(i == testParams.sourceK - 1)
          
          var readyWaitCycles = 0
          while (!dut.io.sourceSymbolsIn.ready.peek().litToBoolean && readyWaitCycles < testTimeoutCycles) {
            readyWaitCycles +=1
            dut.clock.step()
          }
          assert(readyWaitCycles < testTimeoutCycles, "Timeout waiting for LTEncoder sourceSymbolsIn.ready")
          dut.clock.step()
          cyclesInDriver +=1
        }
        dut.io.sourceSymbolsIn.valid.poke(false.B)
        println(s"LTEncoderTest: Driver finished in ${cyclesInDriver} cycles.")
      }

      var generatedRepairCount = 0
      var receiverCycles = 0
      
      while (generatedRepairCount < numRepairToGen && receiverCycles < testTimeoutCycles) {
        // FIX: Peek valid and ready separately for DecoupledIO's .fire
        if (dut.io.repairSymbolOut.valid.peek().litToBoolean && dut.io.repairSymbolOut.ready.peek().litToBoolean) {
          generatedRepairCount += 1
          // println(s"LTEncoderTest: Consumed repair symbol ${generatedRepairCount}")
        }
        receiverCycles += 1
        dut.clock.step(1)
      }
      driver.join() // Ensure driver is done
      assert(receiverCycles < testTimeoutCycles, "Timeout waiting for LTEncoder repairSymbolOut")
      assert(generatedRepairCount == numRepairToGen, s"Expected $numRepairToGen repair symbols, but got $generatedRepairCount")
      println(s"LTEncoderTest: Receiver finished after ${receiverCycles} cycles, got ${generatedRepairCount} symbols.")
    }
  }
}