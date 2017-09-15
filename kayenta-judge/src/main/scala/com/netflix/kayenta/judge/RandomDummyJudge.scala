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

package com.netflix.kayenta.judge

import java.util

import com.netflix.kayenta.canary.results.{CanaryJudgeResult, CanaryJudgeScore}
import com.netflix.kayenta.canary.{CanaryClassifierThresholdsConfig, CanaryConfig, CanaryJudge}
import com.netflix.kayenta.metrics.MetricSetPair
import org.springframework.stereotype.Component

@Component
class RandomDummyJudge extends CanaryJudge{
  private final val _judgeName = "dredd-v1.0"

  override def getName: String = _judgeName

  val random = new scala.util.Random()

  override def judge(canaryConfig: CanaryConfig,
                     scoreThresholds: CanaryClassifierThresholdsConfig,
                     metricSetPairList: util.List[MetricSetPair]): CanaryJudgeResult = {

    val randomValue = "%.2f".format(random.nextDouble * 100).toDouble
    val score = CanaryJudgeScore.builder.score(randomValue).build
    CanaryJudgeResult.builder.judgeName(_judgeName).score(score).build
  }

}
