package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LTEncoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "LTEncoder"

  val params = RaptorFECParameters()

  it should "instantiate and produce some output symbols (placeholder test)" in {
    test(new LTEncoder(params)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val sourceData = Seq.tabulate(params.sourceK)(i => ((i * 5 + 10) % 256).U(params.symbolBits.W))
      val numRepairToGen = 5

      // Initialize DUT inputs
      dut.io.sourceSymbols.valid.poke(false.B)
      dut.io.numRepairSymbolsToGen.poke(numRepairToGen.U)
      dut.io.repairSymbolOut.ready.poke(false.B)
      dut.clock.step(2)

      // Load source symbols into the encoder
      dut.io.sourceSymbols.valid.poke(true.B)
      dut.io.sourceSymbols.bits.zip(sourceData).foreach { case (port, data) => port.poke(data) }
      
      // Wait until the DUT is ready to accept the source block
      while(!dut.io.sourceSymbols.ready.peek().litToBoolean) {
          dut.clock.step(1)
      }
      dut.clock.step(1) // Step one more clock for the data to be consumed
      dut.io.sourceSymbols.valid.poke(false.B) // Deassert valid
      
      // Prepare to receive the repair symbols
      dut.io.repairSymbolOut.ready.poke(true.B)
      
      var generatedCount = 0
      var timeout = 0
      val maxCycles = numRepairToGen * 15 // Set a generous timeout

      // Loop until we have received the expected number of symbols or timed out
      while (generatedCount < numRepairToGen && timeout < maxCycles) {
        if (dut.io.repairSymbolOut.valid.peek().litToBoolean) {
          // A valid repair symbol is available
          println(s"LT Encoder generated repair symbol: ${dut.io.repairSymbolOut.bits.peek().litValue}")
          generatedCount += 1
        }
        dut.clock.step(1)
        timeout += 1
      }

      // Assert that we received the correct number of symbols
      assert(generatedCount == numRepairToGen, s"Expected $numRepairToGen repair symbols, but got $generatedCount within $maxCycles cycles.")
    }
  }
}