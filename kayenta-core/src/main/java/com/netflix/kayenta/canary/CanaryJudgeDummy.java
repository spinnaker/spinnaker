/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.canary;

import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.canary.results.CanaryJudgeScore;
import com.netflix.kayenta.metrics.MetricSetPair;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
// TODO(duftler): Make the canary judge a pluggable component.
// TODO(mgraff, or duftler): move to its own subproject once it's pluggable.
public class CanaryJudgeDummy implements CanaryJudge {
  private Random random = new Random();

  @Override
  public CanaryJudgeResult judge(CanaryConfig canaryConfig, List<MetricSetPair> metricSetPairList) {
    // TODO: "You're the judge; so judge!"

    CanaryJudgeScore score = CanaryJudgeScore.builder().score(random.nextDouble() * 100).build();
    CanaryJudgeResult result = CanaryJudgeResult.builder().score(score).build();
    return result;
  }
}
