package com.netflix.kayenta.judge.scorers

import com.netflix.kayenta.canary.results.CanaryAnalysisResult

case class ScoreResult(groupScores: Option[List[GroupScore]], summaryScore: Double, numMetrics: Double)
case class GroupScore(name: String, score: Double, noData: Boolean, labelCounts: Map[String, Int], numMetrics: Double)

abstract class BaseScorer {
  def score(results: List[CanaryAnalysisResult]): ScoreResult
}
