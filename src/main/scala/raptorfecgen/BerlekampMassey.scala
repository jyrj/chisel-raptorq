package raptorfecgen

import chisel3._
import chisel3.util._

class GF256Inverter(p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(p.symbolBits.W))
    val out = Output(UInt(p.symbolBits.W))
  })
  val exp_table = VecInit(GaloisField.alpha_powers.map(_.U(p.symbolBits.W)))

  val log_table_values = Array.fill(256)(0)
  for (i <- 0 until 255) {
    log_table_values(GaloisField.alpha_powers(i)) = i
  }
  val log_table = VecInit(log_table_values.map(_.U(p.symbolBits.W)))

  val log_a = log_table(io.in)
  val power = 255.U - log_a
  io.out := Mux(io.in === 0.U, 0.U, exp_table(power))
}


class BerlekampMassey(val p: RaptorFECParameters) extends Module {
  private val N = p.numParitySymbolsRS
  private val t = N / 2
  private val poly_width = t + 2

  val io = IO(new Bundle {
    val start     = Input(Bool())
    val syndromes = Input(Vec(N, UInt(p.symbolBits.W)))
    val done      = Output(Bool())
    val sigma     = Output(Vec(poly_width, UInt(p.symbolBits.W)))
    val error_count_L = Output(UInt(log2Ceil(t + 1).W))
  })

  val s_idle :: s_running :: s_finish :: Nil = Enum(3)
  val state = RegInit(s_idle)

  val C = RegInit(VecInit(1.U +: Seq.fill(poly_width - 1)(0.U(p.symbolBits.W))))
  val B = RegInit(VecInit(1.U +: Seq.fill(poly_width - 1)(0.U(p.symbolBits.W))))
  val L = RegInit(0.U(log2Ceil(t + 2).W))
  val n = RegInit(0.U(log2Ceil(N).W))
  val b = RegInit(1.U(p.symbolBits.W))

  val discrepancy_mults = Seq.fill(t + 1)(Module(new GF256Multiplier(p)))
  val inv_b_inst = Module(new GF256Inverter(p))
  val update_mults = Seq.fill(poly_width)(Module(new GF256Multiplier(p)))
  val d_times_b_inv_mult = Module(new GF256Multiplier(p))

  io.done := (state === s_finish)
  io.sigma := C
  io.error_count_L := L
  
  // --- Combinational Logic Section ---
  val d_sum = Wire(UInt(p.symbolBits.W))
  for(i <- 1 to t) { // This loop correctly starts from 1
      discrepancy_mults(i).io.a := C(i)
      discrepancy_mults(i).io.b := Mux(i.U <= L, io.syndromes(n-i.U), 0.U)
  }
  discrepancy_mults(0).io.a := 0.U // Unused multiplier must be driven
  discrepancy_mults(0).io.b := 0.U
  d_sum := discrepancy_mults.map(_.io.product).reduce(_ ^ _)
  val d = io.syndromes(n) ^ d_sum
  
  inv_b_inst.io.in := b
  d_times_b_inv_mult.io.a := d
  d_times_b_inv_mult.io.b := inv_b_inst.io.out
  val d_times_b_inv = d_times_b_inv_mult.io.product
  
  val B_shifted = VecInit(0.U +: B.init)
  val T = C
  
  val update_term = Wire(Vec(poly_width, UInt(p.symbolBits.W)))
  for(i <- 0 until poly_width){
      update_mults(i).io.a := d_times_b_inv
      update_mults(i).io.b := B(i)
      update_term(i) := update_mults(i).io.product
  }
  val C_update_term_shifted = VecInit(0.U +: update_term.init)
  val next_C = VecInit((C zip C_update_term_shifted).map{ case (c, u) => c ^ u})
  
  switch(state) {
    is(s_idle) {
      when(io.start) {
        C := VecInit(1.U +: Seq.fill(poly_width-1)(0.U(p.symbolBits.W)))
        B := VecInit(1.U +: Seq.fill(poly_width-1)(0.U(p.symbolBits.W)))
        L := 0.U
        n := 0.U
        b := 1.U
        state := s_running
      }
    }

    is(s_running) {
      val d_is_not_zero = (d =/= 0.U)
      val L_needs_update = (2.U * L <= n)

      when (d_is_not_zero) {
        C := next_C
        when (L_needs_update) {
            L := n + 1.U - L
            b := d
            B := T
        }
      }
      B := Mux(d_is_not_zero && L_needs_update, T, B_shifted)
      
      when (n === (N - 1).U) {
        state := s_finish
      } .otherwise {
        n := n + 1.U
      }
    }
    is(s_finish){
      state := s_idle
    }
  }
}