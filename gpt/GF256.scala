package raptorq
import chisel3._

/** Pure-Scala GF(256) arithmetic helpers (irreducible poly 0x11d). 
  * Used both by the Scala model and the Chisel RTL via `ChiselEnum`.
  */
object GF256 {
  private val prim = 0x11d
  private val exp  = Array.fill(512)(0)
  private val log  = Array.fill(256)(0)

  // build tables
  private var x = 1
  for (i <- 0 until 255) {
    exp(i) = x
    log(x) = i
    x <<= 1
    if ((x & 0x100) != 0) x ^= prim
  }
  for (i <- 255 until 512) exp(i) = exp(i - 255)

  @inline def add(a: Int, b: Int): Int = a ^ b
  @inline def sub(a: Int, b: Int): Int = a ^ b // same as add in GF(2^m)

  @inline def mul(a: Int, b: Int): Int =
    if (a == 0 || b == 0) 0 else exp(log(a) + log(b))

  /** raise to power `n` */
  def pow(a: Int, n: Int): Int =
    if (a == 0) 0 else exp((log(a) * n) % 255)

  /** Multiply-accumulate convenience for RTL */
  def mac(acc: UInt, a: UInt, b: UInt): UInt =
    (acc.litValue.toInt ^ mul(a.litValue.toInt, b.litValue.toInt)).U(8.W)
}
