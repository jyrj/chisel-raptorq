package raptorq
import chisel3._

/** Convenience object for Verilog generation */
object Top extends App {
  emitVerilog(new RSEncoderHW(RaptorParams()), Array("--target-dir", "generated"))
}
