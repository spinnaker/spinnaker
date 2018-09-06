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

package com.netflix.kayenta.judge.scorers

import com.netflix.kayenta.canary.results.CanaryAnalysisResult

case class ScoreResult(groupScores: Option[List[GroupScore]], summaryScore: Double, numMetrics: Double, reason: Option[String])
case class GroupScore(name: String, score: Double, noData: Boolean, labelCounts: Map[String, Int], numMetrics: Double)

abstract class BaseScorer {
  def score(results: List[CanaryAnalysisResult]): ScoreResult
}
