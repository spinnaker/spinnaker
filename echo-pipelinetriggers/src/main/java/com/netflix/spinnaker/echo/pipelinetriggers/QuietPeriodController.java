/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.pipelinetriggers;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/quietPeriod")
@RestController
public class QuietPeriodController {
  private QuietPeriodIndicator quietPeriodIndicator;

  @Autowired
  public QuietPeriodController(QuietPeriodIndicator quietPeriodIndicator) {
    this.quietPeriodIndicator = quietPeriodIndicator;
  }

  @GetMapping
  QuietPeriodStatus getQuietPeriodStatus() {
    QuietPeriodStatus qps = new QuietPeriodStatus();

    qps.isEnabled = quietPeriodIndicator.isEnabled();
    qps.isInQuietPeriod = quietPeriodIndicator.inQuietPeriod(System.currentTimeMillis());
    qps.startTime = quietPeriodIndicator.getStartTime();
    qps.endTime = quietPeriodIndicator.getEndTime();

    return qps;
  }

  @Data
  private static class QuietPeriodStatus {
    private boolean isEnabled;
    private boolean isInQuietPeriod;
    private long startTime;
    private long endTime;
  }
}
