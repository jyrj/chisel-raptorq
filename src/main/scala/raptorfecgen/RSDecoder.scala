package raptorfecgen

import chisel3._
import chisel3.util._

class RSDecoder(p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    // Input N symbols (could have erasures/errors)
    val in = Flipped(Decoupled(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W))))
    val erasures = Input(Vec(p.totalSymbolsRS, Bool())) // Erasure locations (optional for MVP)
    // Output K recovered source symbols
    val out = Decoupled(Vec(p.sourceK, UInt(p.symbolBits.W)))
    val error = Output(Bool()) // Indicates unrecoverable error
  })

  val busy = RegInit(false.B)
  val output_buffer = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val output_valid = RegInit(false.B)
  val error_reg = RegInit(false.B)

  io.in.ready := !busy && !output_valid

  when(io.in.valid && io.in.ready) {
    busy := true.B
    // Simulate processing: Assume input is correct for now or apply simple "correction"
    // This is NOT a real RS decoder.
    var can_recover = true.B // Placeholder
    // Check erasures if any (simplified)
    // val num_erasures = PopCount(io.erasures)
    // if (num_erasures > p.numParitySymbolsRS) { can_recover = false.B }

    when (can_recover) {
        for (i <- 0 until p.sourceK) {
          output_buffer(i) := io.in.bits(i) // Passthrough for now
        }
        error_reg := false.B
    } .otherwise {
        // Cannot recover, output garbage or signal error
        for (i <- 0 until p.sourceK) {
          output_buffer(i) := 0.U
        }
        error_reg := true.B
    }
    output_valid := true.B
  }

  when(output_valid && io.out.ready) {
    io.out.valid := true.B
    io.out.bits := output_buffer
    io.error := error_reg
    output_valid := false.B
    busy := false.B
  } .otherwise {
    io.out.valid := false.B
    io.error := false.B // Default no error
    io.out.bits := DontCare
  }
}