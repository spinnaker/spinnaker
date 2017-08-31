package com.netflix.kayenta.judge.classifiers.score

import com.netflix.kayenta.judge.scorers.ScoreResult

sealed trait ScoreClassificationLabel
case object Pass extends ScoreClassificationLabel
case object Fail extends ScoreClassificationLabel
case object Marginal extends ScoreClassificationLabel
case object Nodata extends ScoreClassificationLabel
case object Error extends ScoreClassificationLabel

case class ScoreClassification(classification: ScoreClassificationLabel, reason: Option[String], score: Double)

abstract class BaseScoreClassifier {
  def classify(scoreResults: ScoreResult): ScoreClassification
}
