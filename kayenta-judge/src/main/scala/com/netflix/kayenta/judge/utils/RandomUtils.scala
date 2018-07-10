package com.netflix.kayenta.judge.utils

import scala.util.Random

object RandomUtils {

  private var random = new Random()

  /**
    * Initialize Random with the desired seed
    */
  def init(seed: Int): Unit = {
    random = new Random(seed)
  }

  /**
    * Draw random samples from a normal (Gaussian) distribution.
    * @param mean Mean (“centre”) of the distribution.
    * @param stdev Standard deviation (spread or “width”) of the distribution.
    * @param numSamples Number of samples to draw
    */
  def normal(mean: Double, stdev: Double, numSamples: Int): Array[Double] ={
    List.fill(numSamples)(random.nextGaussian() * stdev + mean).toArray
  }

}
