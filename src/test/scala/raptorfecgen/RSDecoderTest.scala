package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable.ArrayBuffer

class RSDecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Streaming RSDecoder with Syndrome Calculation"

  val p = RaptorFECParameters()
  // Increased timeout to account for multi-cycle syndrome calculation
  val testTimeoutCycles = p.totalSymbolsRS * (p.numParitySymbolsRS + 2) + 500

  /**
    * Helper function to drive the DUT and collect results.
    */
  def runDecoderTest(dut: RSDecoder, data: Seq[Int], k: Int, n: Int): Seq[Int] = {
    val recoveredData = ArrayBuffer[Int]()
    dut.io.out.ready.poke(true.B)
    dut.clock.step(1)

    // --- Driver Thread ---
    val driverThread = fork {
      println("Test Driver: Starting to send data...")
      for(i <- 0 until n) {
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.data.poke(data(i).U)
        dut.io.in.bits.last.poke(i == n - 1)
        
        var readyWaitCycles = 0
        while(!dut.io.in.ready.peek().litToBoolean && readyWaitCycles < testTimeoutCycles) {
          readyWaitCycles +=1
          dut.clock.step(1)
        }
        if (readyWaitCycles >= testTimeoutCycles) {
          throw new Exception(s"Timeout waiting for DUT 'in.ready' (symbol $i)")
        }
        dut.clock.step(1)
      }
      dut.io.in.valid.poke(false.B)
      println("Test Driver: Finished sending data.")
    }

    // --- Receiver Logic ---
    var receiverCycles = 0
    while(!dut.io.decoding_done.peek().litToBoolean && receiverCycles < testTimeoutCycles) {
      if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) {
        recoveredData += dut.io.out.bits.data.peek().litValue.toInt
      }
      receiverCycles += 1
      dut.clock.step(1)
    }
    // Final check in case the last symbol is valid on the same cycle as decoding_done
    if (dut.io.out.valid.peek().litToBoolean) {
        recoveredData += dut.io.out.bits.data.peek().litValue.toInt
    }

    driverThread.join()
    if (receiverCycles >= testTimeoutCycles) {
        throw new Exception(s"Timeout waiting for DUT 'decoding_done'. Received ${recoveredData.length}/$k symbols.")
    }
    println(s"Test Receiver: Finished. Total cycles: ${receiverCycles}")
    recoveredData.toSeq
  }

  it should "recover an error-free codeword from a stream" in {
    test(new RSDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val encodedData = ReferenceRS.encode(srcData)

      println("--- Test: Error-Free Codeword ---")
      val recoveredData = runDecoderTest(dut, encodedData, p.sourceK, p.totalSymbolsRS)
      
      dut.io.error.expect(false.B, "Decoder should not flag an error for a valid codeword")
      assert(recoveredData.length == p.sourceK, s"Expected ${p.sourceK} symbols, but got ${recoveredData.length}")
      assert(recoveredData == srcData, "Recovered data did not match source data")
      println("--- Test: Error-Free Codeword PASSED ---\n")
    }
  }

  it should "detect (but not correct) a single-byte error" in {
    test(new RSDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val encodedBuffer = ReferenceRS.encode(srcData).toBuffer

      // Introduce a single-byte error into the source portion
      val errorPos = 10
      val originalByte = encodedBuffer(errorPos)
      val corruptedByte = originalByte ^ 0xAA // Flip some bits
      encodedBuffer(errorPos) = corruptedByte
      
      println("--- Test: Single-Error Codeword ---")
      val recoveredData = runDecoderTest(dut, encodedBuffer.toSeq, p.sourceK, p.totalSymbolsRS)

      dut.io.error.expect(true.B, "Decoder should have flagged an unrecoverable error")
      assert(recoveredData.length == p.sourceK, s"Expected ${p.sourceK} symbols, but got ${recoveredData.length}")
      assert(recoveredData != srcData, "Recovered data should not match original source data")
      assert(recoveredData(errorPos) == corruptedByte, s"The corrupted byte at pos $errorPos should have been passed through")
      println("--- Test: Single-Error Codeword PASSED ---\n")
    }
  }
}





