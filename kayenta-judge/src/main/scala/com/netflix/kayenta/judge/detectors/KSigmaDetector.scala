package com.netflix.kayenta.judge.detectors

import org.apache.commons.math3.stat.StatUtils

/**
  * KSigma Detector
  *
  * Values which are greater than or less than k standard deviations from the mean are considered outliers
  * Reference: https://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
  */
class KSigmaDetector(k: Double = 3.0) extends OutlierDetector{

  require(k > 0.0, "k must be greater than zero")

  override def detect(data: Array[Double]): Array[Boolean] = {

    //Calculate the mean and standard deviation of the input data
    val mean = StatUtils.mean(data)
    val variance = StatUtils.populationVariance(data)
    val stdDeviation = scala.math.sqrt(variance)

    //Values that fall outside of k standard deviations from the mean are considered outliers
    data.map(x => if (scala.math.abs(x - mean) > (stdDeviation * k)) true else false)
  }
}
