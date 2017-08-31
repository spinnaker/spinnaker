package com.netflix.kayenta.judge.classifiers.score

import com.netflix.kayenta.judge.scorers.ScoreResult


class ThresholdScoreClassifier(passThreshold: Double, warningThreshold: Double) extends BaseScoreClassifier{

  override def classify(scoreResults: ScoreResult): ScoreClassification = {

    val score = scoreResults.summaryScore
    if(score >= passThreshold){
      ScoreClassification(Pass, None, score)
    }else if(score >= warningThreshold){
      ScoreClassification(Marginal, None, score)
    }else{
      ScoreClassification(Fail, None, score)
    }
  }

}
