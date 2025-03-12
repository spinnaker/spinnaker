/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.judge.evaluation

/**
  * Metrics - Evaluation Metrics (Binary)
  *
  * Metrics for evaluating binary classifiers. The evaluation of binary classifiers compares
  * two methods of assigning a binary attribute, one of which is usually a standard method
  * and the other is being investigated.
  */
object Metrics {

  /** Compute the number of true positives. */
  def truePositives(y_true: Array[Int], y_pred: Array[Int]): Double ={
    (y_true, y_pred).zipped.map((truth, pred) => if (truth == 1 && pred == 1) 1.0 else 0.0).sum
  }

  /** Compute the number of false positives. */
  def falsePositives(y_true: Array[Int], y_pred: Array[Int]): Double ={
    (y_true, y_pred).zipped.map((truth, pred) => if (truth == 0 && pred == 1) 1.0 else 0.0).sum
  }

  /** Compute the number of true negatives. */
  def trueNegatives(y_true: Array[Int], y_pred: Array[Int]): Double ={
    (y_true, y_pred).zipped.map((truth, pred) => if (truth == 0 && pred == 0) 1.0 else 0.0).sum
  }

  /** Compute the number of false negatives. */
  def falseNegatives(y_true: Array[Int], y_pred: Array[Int]): Double ={
    (y_true, y_pred).zipped.map((truth, pred) => if (truth == 1 && pred == 0) 1.0 else 0.0).sum
  }

  /** Compute the accuracy.
    *
    * The accuracy is the ratio of (tp + tn) / population where tp is the number of
    * true positives and tn is the number of true negatives. The accuracy is the
    * proportion of true results among the total number of cases examined.
    */
  def accuracy(y_true: Array[Int], y_pred: Array[Int]): Double ={

    val tp = truePositives(y_true, y_pred)
    val tn = trueNegatives(y_true, y_pred)
    val trueResults = tp + tn

    if (y_true.length < 1) 0.0 else trueResults.toDouble / y_true.length
  }

  /** Compute the precision.
    *
    * The precision is the ratio tp / (tp + fp) where tp is the number of
    * true positives and fp the number of false positives. The precision
    * represents the ability of the detector not to label as positive a sample
    * that is negative.
    *
    * The best value is 1 and the worst value is 0.
    */
  def precision(y_true: Array[Int], y_pred: Array[Int]): Double ={

    val tp = truePositives(y_true, y_pred)
    val fp = falsePositives(y_true, y_pred)

    if (tp == 0.0 && fp == 0.0) {
      0.0
    } else {
      tp.toDouble / (tp + fp)
    }
  }

  /**
    * Compute the recall
    *
    * The recall is the ratio tp / (tp + fn) where tp is the number of
    * true positives and fn the number of false negatives. The recall
    * represents the ability of the detector to find all the positive samples.
    *
    * The best value is 1 and the worst value is 0.
    */
  def recall(y_true: Array[Int], y_pred: Array[Int]): Double ={

    val tp = truePositives(y_true, y_pred)
    val fn = falseNegatives(y_true, y_pred)

    if (tp == 0 && fn == 0){
      0.0
    } else {
      tp.toDouble / (tp + fn)
    }
  }

  /**
    * Compute the true positive rate (recall, sensitivity)
    *
    * The true positive rate is the ratio tp / (tp + fn) where tp is the number of
    * true positives and fn the number of false negatives.
    *
    * The best value is 1 and the worst value is 0.
    */
  def truePositiveRate(y_true: Array[Int], y_pred: Array[Int]): Double ={
    recall(y_true, y_pred)
  }

  /**
    * Compute the false positive rate
    *
    * The false positive rate is the ratio fp / (fp + tn) where fp is the
    * number of false positives and tn is the number of true negatives.
    */
  def falsePositiveRate(y_true: Array[Int], y_pred: Array[Int]): Double ={
    1 - trueNegativeRate(y_true, y_pred)
  }

  /**
    * Compute the true negative rate (specificity)
    *
    * The true negative rate is the ratio tn / (fp + tn) where tn is the
    * number of true negatives and fp is the number of false positives.
    */
  def trueNegativeRate(y_true: Array[Int], y_pred: Array[Int]): Double ={

    val tn = trueNegatives(y_true, y_pred)
    val fp = falsePositives(y_true, y_pred)

    if (fp == 0 && tn == 0){
      0.0
    } else {
      tn.toDouble / (fp + tn)
    }
  }

  /**
    * Compute the F-measure, also known as balanced F-score or F1 score
    *
    * The F1 score can be interpreted as a weighted average of the precision and
    * recall, where an F1 score reaches its best value at 1 and worst score at 0.
    */
  def fMeasure(y_true: Array[Int], y_pred: Array[Int], beta: Double = 1.0): Double ={

    val beta2 = beta * beta
    val precision = Metrics.precision(y_true, y_pred)
    val recall = Metrics.recall(y_true, y_pred)

    if (precision + recall == 0) {
      0.0
    } else {
      (1.0 + beta2) * (precision * recall) / (beta2 * precision + recall)
    }
  }
}
