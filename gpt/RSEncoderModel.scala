package raptorq
import GF256._

/** Simple polynomial-division RS(255,223) encoder in pure Scala.
  * Generates 32 parity symbols (repairCap) given 223 data symbols.
  */
object RSEncoderModel {

  private val params = RaptorParams()
  private val n  = params.sourceK + params.repairCap // 255
  private val k  = params.sourceK                    // 223
  private val genPoly: Array[Int] = {                // generator polynomial
    var gp = Array(1)
    for (i <- 0 until (n - k)) {
      val term = Array(1, pow(2, i))                 // (x + α^i)
      gp = polyMul(gp, term)
    }
    gp
  }

  private def polyMul(a: Array[Int], b: Array[Int]): Array[Int] = {
    val res = Array.fill(a.length + b.length - 1)(0)
    for (i <- a.indices; j <- b.indices)
      res(i + j) = add(res(i + j), mul(a(i), b(j)))
    res
  }

  /** returns data ++ parity */
  def encode(data: Seq[Int]): Seq[Int] = {
    require(data.length == k)
    val buf  = data.toArray ++ Array.fill(n - k)(0)
    // polynomial long division
    for (i <- 0 until k) {
      val coef = buf(i)
      if (coef != 0)
        for (j <- genPoly.indices)
          buf(i + j) = add(buf(i + j), mul(coef, genPoly(j)))
    }
    data ++ buf.drop(k) // original data followed by parity
  }
}
