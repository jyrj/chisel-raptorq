package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LTEncoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "LTEncoder"

  val params = RaptorFECParameters(sourceK = 16, ltRepairCap = 20)

  it should "generate the requested number of repair symbols" in {
    test(new LTEncoder(params)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val sourceData = Seq.tabulate(params.sourceK)(i => ((i * 5 + 10) % 256))
      val numRepairToGen = 10

      // Initialize DUT inputs
      dut.io.sourceSymbols.valid.poke(false.B)
      dut.io.numRepairSymbolsToGen.poke(numRepairToGen.U)
      dut.io.repairSymbolOut.ready.poke(false.B)
      dut.clock.step(2)

      // Load source symbols into the encoder
      dut.io.sourceSymbols.valid.poke(true.B)
      dut.io.sourceSymbols.bits.zip(sourceData).foreach { case (port, data) => port.poke(data.U) }

      // Wait until the DUT is ready to accept the source block
      while(!dut.io.sourceSymbols.ready.peek().litToBoolean) {
          dut.clock.step(1)
      }
      dut.clock.step(1) // Step one more clock for the data to be consumed
      dut.io.sourceSymbols.valid.poke(false.B)

      dut.io.repairSymbolOut.ready.poke(true.B)

      var generatedCount = 0
      var timeout = 0
      val maxCycles = numRepairToGen * params.sourceK * 2 // Generous timeout

      while (generatedCount < numRepairToGen && timeout < maxCycles) {
        if (dut.io.repairSymbolOut.valid.peek().litToBoolean) {
          generatedCount += 1
        }
        dut.clock.step(1)
        timeout += 1
      }
      assert(generatedCount == numRepairToGen, s"Expected $numRepairToGen repair symbols, but got $generatedCount within $maxCycles cycles.")
    }
  }
}