package raptorfecgen

import chisel3._
import chisel3.util._

object RFC6330Helpers {

  def prng_rand(seed: UInt, i: UInt, m: UInt): UInt = {
    (seed + i) % m
  }

  class TupleBundle(val K_prime_val: Int) extends Bundle {
    val d = UInt(log2Ceil(K_prime_val + 1).W)
    val a = UInt(log2Ceil(K_prime_val).W)
    val b = UInt(log2Ceil(K_prime_val).W)
    val d1 = UInt(log2Ceil(K_prime_val + 1).W)
    val a1 = UInt(log2Ceil(K_prime_val).W)
    val b1 = UInt(log2Ceil(K_prime_val).W)
  }

  def generateTuple(esi: UInt, K_prime: UInt, K_prime_val: Int): TupleBundle = {
    val tuple = Wire(new TupleBundle(K_prime_val))
    val degree_seed = esi
    val rand_for_degree = prng_rand(degree_seed, 0.U, 1024.U)

    // FIX: Calculate degree sequentially then apply constraints
    val initial_d = Wire(UInt(log2Ceil(K_prime_val + 1).W))
    when(rand_for_degree < 500.U) {
      initial_d := 1.U + (rand_for_degree % 3.U)
    } .elsewhen(rand_for_degree < 800.U) {
      initial_d := 4.U + (rand_for_degree % 4.U)
    } .elsewhen(rand_for_degree < 950.U) {
      initial_d := 8.U + (rand_for_degree % 8.U)
    } .otherwise {
      val max_degree_operand = (K_prime_val / 4).max(1)
      initial_d := 16.U + (rand_for_degree % max_degree_operand.U)
    }

    val d_after_zero_check = Mux(initial_d === 0.U, 1.U, initial_d)
    tuple.d := Mux(d_after_zero_check > K_prime, K_prime, d_after_zero_check)

    tuple.b := prng_rand(esi, 0.U, K_prime)
    val K_prime_minus_1 = Wire(UInt(K_prime.getWidth.W))
    K_prime_minus_1 := Mux(K_prime > 0.U, K_prime - 1.U, 0.U) // Ensure K_prime-1 is not negative
    val m_for_a = Mux(K_prime_minus_1 === 0.U, 1.U, K_prime_minus_1) // m for prng_rand must be >= 1
    tuple.a := 1.U + prng_rand(esi, 1.U, m_for_a)

    tuple.d1 := 0.U
    tuple.a1 := 0.U
    tuple.b1 := 0.U

    tuple
  }

  def solitonConstants(K_prime_val: Int): (Int, Int, Int, Int) = {
    val J = K_prime_val / 10 + 1
    val S = log2Ceil(K_prime_val) + 1
    val H = log2Ceil(K_prime_val) / 2 + 1
    val W = K_prime_val / 20 + 1
    (J, S, H, W)
  }
}