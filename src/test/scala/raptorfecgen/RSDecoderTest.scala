package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable.ArrayBuffer

class RSDecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RSDecoder with Berlekamp-Massey"

  val p = RaptorFECParameters()
  // Timeout needs to account for B-M iterations now
  val testTimeoutCycles = (p.totalSymbolsRS * 2) + (p.numParitySymbolsRS * 5) + 500

  def runDecoderTest(dut: RSDecoder, data: Seq[Int], k: Int, n: Int): Seq[Int] = {
    val recoveredData = ArrayBuffer[Int]()
    dut.io.out.ready.poke(true.B)
    dut.clock.step(1)

    val driverThread = fork {
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
    }

    var receiverCycles = 0
    while(!dut.io.decoding_done.peek().litToBoolean && receiverCycles < testTimeoutCycles) {
      if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) {
        recoveredData += dut.io.out.bits.data.peek().litValue.toInt
      }
      receiverCycles += 1
      dut.clock.step(1)
    }
    if (dut.io.out.valid.peek().litToBoolean) {
        recoveredData += dut.io.out.bits.data.peek().litValue.toInt
    }

    driverThread.join()
    if (receiverCycles >= testTimeoutCycles) {
        throw new Exception(s"Timeout waiting for DUT 'decoding_done'. Received ${recoveredData.length}/$k symbols.")
    }
    recoveredData.toSeq
  }

  it should "pass an error-free codeword" in {
    test(new RSDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val encodedData = ReferenceRS.encode(srcData)
      val recoveredData = runDecoderTest(dut, encodedData, p.sourceK, p.totalSymbolsRS)
      
      dut.io.error.expect(false.B)
      assert(recoveredData == srcData)
    }
  }

  it should "detect a single-byte error and report L=1" in {
    test(new RSDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val encodedBuffer = ReferenceRS.encode(srcData).toBuffer
      encodedBuffer(10) = encodedBuffer(10) ^ 0xAA
      
      val recoveredData = runDecoderTest(dut, encodedBuffer.toSeq, p.sourceK, p.totalSymbolsRS)
      
      // Since correction is not yet implemented, the main error flag will be set
      dut.io.error.expect(true.B)
      // Check the specific output of the Berlekamp-Massey block
      dut.io.debug_error_count.get.expect(1.U, "B-M should have found L=1 error")
    }
  }

  it should "detect two errors and report L=2" in {
    test(new RSDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val encodedBuffer = ReferenceRS.encode(srcData).toBuffer
      encodedBuffer(20) = encodedBuffer(20) ^ 0x11
      encodedBuffer(40) = encodedBuffer(40) ^ 0x22
      
      val recoveredData = runDecoderTest(dut, encodedBuffer.toSeq, p.sourceK, p.totalSymbolsRS)
      
      dut.io.error.expect(true.B)
      dut.io.debug_error_count.get.expect(2.U, "B-M should have found L=2 errors")
    }
  }
}




