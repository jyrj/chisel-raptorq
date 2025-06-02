package raptorfecgen

import chisel3._
import chisel3.util._

class LTDecoder(p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val receivedSymbol = Flipped(Decoupled(UInt(p.symbolBits.W)))
    val symbolInfo = Input(Vec(p.sourceK, Bool()))
    val recoveredSymbols = Decoupled(Vec(p.sourceK, UInt(p.symbolBits.W)))
    val decodingComplete = Output(Bool())
    val error = Output(Bool())
  })

  val s_idle :: s_receiving :: s_solving :: s_output :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val decoding_matrix = Reg(Vec(p.sourceK + p.ltRepairCap, Vec(p.sourceK, Bool())))
  val received_values = Reg(Vec(p.sourceK + p.ltRepairCap, UInt(p.symbolBits.W)))
  val num_received = RegInit(0.U(log2Ceil(p.sourceK + p.ltRepairCap + 1).W))

  val solved_mask = RegInit(VecInit(Seq.fill(p.sourceK)(false.B)))
  val recovered_block = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val num_solved = RegInit(0.U(log2Ceil(p.sourceK + 1).W))

  val decoding_failed = RegInit(false.B)
  val solve_iterations = RegInit(0.U(8.W))

  io.receivedSymbol.ready := (state === s_receiving)
  io.recoveredSymbols.valid := (state === s_output)
  io.recoveredSymbols.bits := recovered_block
  io.decodingComplete := (state === s_output) && !decoding_failed
  io.error := decoding_failed

  when(state === s_idle) {
    state := s_receiving
    num_received := 0.U
    num_solved := 0.U
    solved_mask.foreach(_ := false.B)
    recovered_block.foreach(_ := 0.U)
    decoding_failed := false.B
  }

  switch(state) {
    is(s_receiving) {
      when(io.receivedSymbol.valid && io.receivedSymbol.ready) {
        decoding_matrix(num_received) := io.symbolInfo
        received_values(num_received) := io.receivedSymbol.bits
        num_received := num_received + 1.U
      }
      when(num_received >= p.sourceK.U) {
        solve_iterations := 0.U
        state := s_solving
      }
    }

    is(s_solving) {
      // Use temporary wires to find all solvable symbols in this cycle combinationally.
      val newly_solved_mask = Wire(Vec(p.sourceK, Bool()))
      val newly_solved_values = Wire(Vec(p.sourceK, UInt(p.symbolBits.W)))
      newly_solved_mask.foreach(_ := false.B)
      newly_solved_values.foreach(_ := 0.U)

      for (i <- 0 until (p.sourceK + p.ltRepairCap)) {
        val matrix_row = decoding_matrix(i).asUInt
        val unsolved_mask_uint = ~solved_mask.asUInt
        val unsolved_connections_uint = matrix_row & unsolved_mask_uint
        val unsolved_connections_count = PopCount(unsolved_connections_uint)

        when(unsolved_connections_count === 1.U) {
          val solve_idx = OHToUInt(unsolved_connections_uint)
          val components_to_xor = (0 until p.sourceK).map { j =>
            Mux(decoding_matrix(i)(j) && solved_mask(j), recovered_block(j), 0.U(p.symbolBits.W))
          }
          val xor_sum_of_solved = components_to_xor.reduce(_ ^ _)
          val resolved_value = received_values(i) ^ xor_sum_of_solved

          when(!solved_mask(solve_idx)) {
            newly_solved_mask(solve_idx) := true.B
            newly_solved_values(solve_idx) := resolved_value
          }
        }
      }

      val num_newly_solved = PopCount(newly_solved_mask.asUInt)
      val progress_made = num_newly_solved > 0.U

      val next_solved_mask = solved_mask.asUInt | newly_solved_mask.asUInt
      solved_mask := next_solved_mask.asBools

      for(k <- 0 until p.sourceK) {
        when(newly_solved_mask(k)) {
          recovered_block(k) := newly_solved_values(k)
        }
      }

      num_solved := num_solved + num_newly_solved
      solve_iterations := solve_iterations + 1.U

      when((num_solved + num_newly_solved) === p.sourceK.U) {
        state := s_output
      }.elsewhen(solve_iterations > (p.sourceK * 2).U && !progress_made) {
        decoding_failed := true.B
        state := s_output
      }
    }

    is(s_output) {
      when(io.recoveredSymbols.ready) {
        state := s_idle
      }
    }
  }
}