package raptorfecgen

import chisel3._
import chisel3.util._

/** Single-cycle GF(256) multiplier using the Russian-peasant algorithm.
  *
  * Field polynomial x⁸ + x⁴ + x³ + x² + 1 (0x11D), per RFC 6330 (RaptorQ).
  *
  * Pure logic: 8 × 8-bit XORs + steering; no table ROMs.
  */
class GF256Multiplier(val p: RaptorFECParameters) extends Module {
  require(p.symbolBits == 8, "GF256Multiplier only supports 8-bit symbols")

  val io = IO(new Bundle {
    val a       = Input(UInt(8.W))
    val b       = Input(UInt(8.W))
    val product = Output(UInt(8.W))
  })

 
  def xtime(x: UInt): UInt = {
    val msb     = x(7)
    val shifted = (x << 1)(7, 0)                // keep low 8 bits
    Mux(msb, shifted ^ "h1d".U, shifted)        // ^0x1D only if MSB was 1
  }

  /* Build the sequence  a, a·x, a·x², … , a·x⁷ */
  val aPowers = Wire(Vec(8, UInt(8.W)))
  aPowers(0) := io.a
  for (i <- 1 until 8) {
    aPowers(i) := xtime(aPowers(i - 1))
  }

  /* XOR the appropriate powers where the i-th bit of b is 1 */
  val partials = (0 until 8).map(i => Mux(io.b(i), aPowers(i), 0.U))
  io.product := partials.reduce(_ ^ _)
}
