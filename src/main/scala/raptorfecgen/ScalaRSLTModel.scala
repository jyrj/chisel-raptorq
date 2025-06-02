package raptorfecgen

/**
  * A software reference model for testing FEC components.
  */
object ScalaRSLTModel {
  def gf256Multiply(a: Int, b: Int): Int = {
    // ... (rest of this function is unchanged)
    val P = 0x11D
    var res = 0
    var temp_a = a
    var temp_b = b
    for (i <- 0 until 8) {
      if ((temp_b & 1) != 0) res = res ^ temp_a
      val msb_set = (temp_a & 0x80) != 0
      temp_a = temp_a << 1
      if (msb_set) temp_a = temp_a ^ P
      temp_b = temp_b >> 1
    }
    res & 0xFF
  }
  // ... (other placeholder functions are unchanged)
}

/**
  * Reference implementation for the RS(255,223) encoder.
  * Now lives in this file to be accessible by all tests.
  */
object ReferenceRS {
  import RSEncoder.genCoeffs
  private def mul(a: Int, b: Int): Int = ScalaRSLTModel.gf256Multiply(a, b)
  def encode(src: Seq[Int]): Seq[Int] = {
    require(src.length == 223)
    val parity = Array.fill(32)(0)
    for (byte <- src) {
      val fb = byte ^ parity(31)
      for (i <- 31 until 0 by -1)
        parity(i) = parity(i-1) ^ mul(fb, genCoeffs(31 - i))
      parity(0) = mul(fb, genCoeffs.last)
    }
    src ++ parity.reverse
  }
}