package CustomDP4SQL.Inference

import scala.math.{signum,abs,log}

class NoiseCalculator {
  private def laplace(scale: Double): Double = {
    val u = 0.5 - scala.util.Random.nextDouble()
    -signum(u) * scale * log(1 - 2 * abs(u))
  }

  def computeNoise(sensitivities: List[Int]): List[Double] = {
    sensitivities.map(sens => laplace(sens))
  }
}
