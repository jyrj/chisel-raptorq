package raptorfecgen

import chisel3._
import chisel3.util._

class RSEncoder(p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(Vec(p.sourceK, UInt(p.symbolBits.W)))) // Input K source symbols
    val out = Decoupled(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W))) // Output N=K+P_RS symbols
  })


  val busy = RegInit(false.B)
  val output_buffer = Reg(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W)))
  val output_valid = RegInit(false.B)

  io.in.ready := !busy && !output_valid // Ready to accept if not busy and no pending output

  when(io.in.valid && io.in.ready) {
    busy := true.B
    // Simulate processing: Copy source symbols and generate dummy parity
    for (i <- 0 until p.sourceK) {
      output_buffer(i) := io.in.bits(i)
    }
    // Placeholder for parity generation using GF256Multiplier
    // Example: a very simple (incorrect) parity for demonstration
    val gf_mult = Module(new GF256Multiplier(p))
    gf_mult.io.a := io.in.bits(0)
    gf_mult.io.b := 2.U // Multiply by a constant
    
    for (i <- 0 until p.numParitySymbolsRS) {
      output_buffer(p.sourceK + i) := gf_mult.io.product + i.U
    }
    output_valid := true.B
  }

  when(output_valid && io.out.ready) {
    io.out.valid := true.B
    io.out.bits := output_buffer
    output_valid := false.B
    busy := false.B // Done with this block
  } .otherwise {
    io.out.valid := false.B
    io.out.bits := DontCare
  }
}