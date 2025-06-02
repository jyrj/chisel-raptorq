package raptorfecgen

import chisel3._
import chisel3.util._

class LTReceivedSymbolWithInfo(val p: RaptorFECParameters) extends Bundle {
  val data = UInt(p.symbolBits.W)
  val symbolInfo = Vec(p.sourceK, Bool())
  val isLastOfBlock = Bool() // From sender, indicates no more symbols for this decoding attempt
}

class LTDecoder(val p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val receivedPacketIn = Flipped(Decoupled(new LTReceivedSymbolWithInfo(p)))
    val recoveredSymbolsOut = Decoupled(new FECDataBundle(p))
    val decodingComplete = Output(Bool()) // High when all K symbols are successfully recovered AND sent
    val error = Output(Bool())
    val busy = Output(Bool())
  })

  val s_idle :: s_receiving :: s_solving :: s_outputting :: s_error_stagnation :: Nil = Enum(5)
  val state = RegInit(s_idle)

  val decoding_matrix = Reg(Vec(p.sourceK + p.ltRepairCap, Vec(p.sourceK, Bool())))
  val received_values = Reg(Vec(p.sourceK + p.ltRepairCap, UInt(p.symbolBits.W)))
  val num_received_for_matrix = RegInit(0.U(log2Ceil(p.sourceK + p.ltRepairCap + 1).W))

  val solved_mask = RegInit(VecInit(Seq.fill(p.sourceK)(false.B)))
  val recovered_block = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val num_solved = RegInit(0.U(log2Ceil(p.sourceK + 1).W)) // Tracks symbols successfully solved by s_solving
  val output_stream_count = RegInit(0.U(log2Ceil(p.sourceK + 1).W)) // Tracks symbols sent out
  val stagnation_counter = RegInit(0.U(8.W))
  val MAX_STAGNATION_CYCLES = (p.sourceK / 2 + 5).U // Heuristic

  io.receivedPacketIn.ready := (state === s_receiving)
  io.recoveredSymbolsOut.valid := (state === s_outputting) && (num_solved === p.sourceK.U) // Only valid if solving was successful
  io.recoveredSymbolsOut.bits.data := recovered_block(output_stream_count)
  io.recoveredSymbolsOut.bits.last := (output_stream_count === (p.sourceK - 1).U)
  
  // decodingComplete is true only when we are in idle AFTER successfully outputting all symbols
  io.decodingComplete := (state === s_idle) && (num_solved === p.sourceK.U) && (output_stream_count === p.sourceK.U)
  io.error := (state === s_error_stagnation)
  io.busy := (state =/= s_idle)

  // Debug prints
  (p"LTDecoder Cycle --- State=${state}, NumReceived=${num_received_for_matrix}, NumSolved=${num_solved}, OutputCount=${output_stream_count}, Stagnation=${stagnation_counter}, Complete=${io.decodingComplete}, Error=${io.error}\n")
  // printf(p"LTDecoder IOs: InValid=${io.receivedPacketIn.valid}, InReady=${io.receivedPacketIn.ready}, OutValid=${io.recoveredSymbolsOut.valid}, OutReady=${io.recoveredSymbolsOut.ready}\n")


  when(state === s_idle) {
    num_received_for_matrix := 0.U
    num_solved := 0.U
    output_stream_count := 0.U // Reset for next block
    solved_mask.foreach(_ := false.B)
    recovered_block.foreach(_ := 0.U)
    stagnation_counter := 0.U
    when(io.receivedPacketIn.valid) { // Only transition if there's actual input trying to start
      state := s_receiving
      // printf(p"LTDecoder: IDLE -> RECEIVING (Input Valid)\n")
    }
  }

  switch(state) {
    is(s_receiving) {
      io.receivedPacketIn.ready := true.B // Ready to accept symbols
      when(io.receivedPacketIn.fire) {
        // printf(p"LTDecoder: RECEIVING symbol ${num_received_for_matrix}, Data=${io.receivedPacketIn.bits.data}, Info=${io.receivedPacketIn.bits.symbolInfo.asUInt}, LastInBlock=${io.receivedPacketIn.bits.isLastOfBlock}\n")
        when(num_received_for_matrix < (p.sourceK + p.ltRepairCap).U) {
          decoding_matrix(num_received_for_matrix) := io.receivedPacketIn.bits.symbolInfo
          received_values(num_received_for_matrix) := io.receivedPacketIn.bits.data
          num_received_for_matrix := num_received_for_matrix + 1.U
        }
        // Condition to start solving:
        // 1. Received at least K symbols (heuristic, might need more for robust decoding)
        // 2. OR received "isLastOfBlock" signal from input stream.
        val will_have_enough_symbols = (num_received_for_matrix + 1.U) >= p.sourceK.U
        when(will_have_enough_symbols || io.receivedPacketIn.bits.isLastOfBlock) {
          stagnation_counter := 0.U
          state := s_solving
          // printf(p"LTDecoder: RECEIVING -> SOLVING. NumReceived will be ${num_received_for_matrix + 1.U}\n")
        }
      }
    }
    is(s_solving) {
      val can_be_newly_solved_mask = Wire(Vec(p.sourceK, Bool()))
      val can_be_newly_solved_values = Wire(Vec(p.sourceK, UInt(p.symbolBits.W)))
      can_be_newly_solved_mask.foreach(_ := false.B)
      can_be_newly_solved_values.foreach(_ := 0.U)

      // printf(p"LTDecoder SOLVING: PrevSolvedMask=${solved_mask.asUInt}, PrevNumSolved=${num_solved}\n")

      for (i <- 0 until (p.sourceK + p.ltRepairCap)) {
        when(i.U < num_received_for_matrix) {
            val matrix_row_uint = decoding_matrix(i).asUInt
            val unsolved_based_on_prior_state_mask_uint = ~solved_mask.asUInt
            val unsolved_connections_uint = matrix_row_uint & unsolved_based_on_prior_state_mask_uint
            val unsolved_degree = PopCount(unsolved_connections_uint)

            when(unsolved_degree === 1.U) {
              val solve_idx = OHToUInt(unsolved_connections_uint)
              val components_to_xor = (0 until p.sourceK).map { j => Mux(decoding_matrix(i)(j) && solved_mask(j), recovered_block(j), 0.U(p.symbolBits.W)) }
              val xor_sum_of_solved_neighbors = components_to_xor.reduce(_ ^ _)
              val resolved_value = received_values(i) ^ xor_sum_of_solved_neighbors
              can_be_newly_solved_mask(solve_idx) := true.B
              can_be_newly_solved_values(solve_idx) := resolved_value
            }
        }
      }

      val actual_newly_solved_indicators = Wire(Vec(p.sourceK, Bool()))
      val next_solved_mask_proposal = Wire(Vec(p.sourceK, Bool()))
      actual_newly_solved_indicators.foreach(_ := false.B)

      for(k_idx <- 0 until p.sourceK) {
        actual_newly_solved_indicators(k_idx) := can_be_newly_solved_mask(k_idx) && !solved_mask(k_idx)
        when(actual_newly_solved_indicators(k_idx)) {
          recovered_block(k_idx) := can_be_newly_solved_values(k_idx)
          next_solved_mask_proposal(k_idx) := true.B
        } .otherwise {
          next_solved_mask_proposal(k_idx) := solved_mask(k_idx)
        }
      }
      val num_actually_newly_solved_this_cycle = PopCount(actual_newly_solved_indicators.asUInt)
      
      solved_mask := next_solved_mask_proposal
      val progress_made = num_actually_newly_solved_this_cycle > 0.U
      val current_total_num_solved = num_solved + num_actually_newly_solved_this_cycle
      num_solved := current_total_num_solved // This register now reflects symbols solved up to THIS pass

      // printf(p"LTDecoder SOLVING: NewlySolvedThisCycle=${num_actually_newly_solved_this_cycle}, ProgressMade=${progress_made}, CurrentTotalNumSolved=${current_total_num_solved}\n")

      when(current_total_num_solved === p.sourceK.U) {
        state := s_outputting
        output_stream_count := 0.U // Reset for sending out the recovered block
        // printf(p"LTDecoder: SOLVING -> OUTPUTTING. All solved.\n")
      } .elsewhen(!progress_made) {
        stagnation_counter := stagnation_counter + 1.U
        when(stagnation_counter >= MAX_STAGNATION_CYCLES) { 
          state := s_error_stagnation 
          // printf(p"LTDecoder: SOLVING -> ERROR_STAGNATION.\n")
        }
      } .otherwise { stagnation_counter := 0.U }
    }
    is(s_outputting) {
      // Valid is asserted if num_solved === p.sourceK.U (from io.recoveredSymbolsOut.valid assignment)
      when(io.recoveredSymbolsOut.valid) { // Ensure we only proceed if output is valid (i.e., solving was successful)
        // printf(p"LTDecoder OUTPUTTING: OutputCount=${output_stream_count}, Data=${io.recoveredSymbolsOut.bits.data}, Last=${io.recoveredSymbolsOut.bits.last}\n")
        when(io.recoveredSymbolsOut.fire) {
          output_stream_count := output_stream_count + 1.U
          when(io.recoveredSymbolsOut.bits.last) { // Last symbol of the recovered block sent
            state := s_idle 
            // printf(p"LTDecoder: OUTPUTTING -> IDLE. Output complete.\n")
          }
        }
      } .otherwise { // Should not happen if state is s_outputting & num_solved was K, but as safeguard
          state := s_error_stagnation // Or some other error state
          // printf(p"LTDecoder: ERROR in s_outputting, num_solved not K but tried to output.\n")
      }
    }
    is(s_error_stagnation) {
      // printf(p"LTDecoder: In s_error_stagnation.\n")
      when(io.receivedPacketIn.valid) { // Allow resetting on new input for next block attempt
            state := s_idle // This will trigger full reset in s_idle block
      }
    }
  }
}
