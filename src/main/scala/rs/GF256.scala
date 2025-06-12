package rs
import chisel3._
import chisel3.util._

/* ------------------------------------------------------------- *
 *  GF(2⁸) – log / antilog implementation, primitive 0x11D       *
 *  Each caller gets its own ROM Vec, so nothing is shared        *
 *  across modules → no visibility exceptions.                    *
 * ------------------------------------------------------------- */
object GF256 {

  /* scala-side lookup tables --------------------------------------- */
  private val alphaToSymbArr: Array[Int] = {
    val t = new Array[Int](256); var v = 1
    for (i <- 0 until 255) {
      t(i) = v
      v <<= 1
      if ((v & 0x100) != 0) v ^= 0x11d
      v &= 0xff
    }
    t(255) = t(0); t
  }

  private val symbToAlphaArr: Array[Int] = {
    val t = Array.fill(256)(0)
    for (i <- 0 until 255) t(alphaToSymbArr(i)) = i
    t
  }

  /* helpers that create a ROM Vec local to the calling module ------ */
  private def alphaVec(): Vec[UInt] = VecInit(alphaToSymbArr.map(_.U(8.W)))
  private def logVec()  : Vec[UInt] = VecInit(symbToAlphaArr.map(_.U(8.W)))

  /* α^idx with run-time UInt index                                   */
  def alpha(idx: UInt): UInt = alphaVec()(idx)

  /* α^idx with compile-time Int index → pure literal                 */
  def alphaConst(idx: Int): UInt = alphaToSymbArr(idx).U(8.W)

  /* multiply via log/antilog                                         */
  def mul(a: UInt, b: UInt): UInt = {
    val zero = 0.U(8.W)
    val logs = logVec()
    val αto  = alphaVec()

    val z = Wire(UInt(8.W))
    when(a === 0.U || b === 0.U) {
      z := zero
    }.otherwise {
      val s  = logs(a) +& logs(b)          // 0 … 508
      val sm = Mux(s > 254.U, s - 255.U, s)(7, 0)
      z := αto(sm)
    }
    z
  }

  def inv(x: UInt): UInt = {
    val logs = logVec(); val αto = alphaVec()
    Mux(x === 0.U, 0.U, αto((255.U - logs(x))(7, 0)))
  }

  def div(a: UInt, b: UInt): UInt = mul(a, inv(b))
}