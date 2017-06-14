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

package com.netflix.kayenta.stackdriver.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class StackdriverCanaryScopeFactory implements CanaryScopeFactory {

  @Override
  public boolean handles(String serviceType) {
    return "stackdriver".equals(serviceType);
  }

  @Override
  public CanaryScope buildCanaryScope(String scope,
                                      String startTimeMillis,
                                      String endTimeMillis,
                                      String step,
                                      Map<String, String> extendedScopeParams) {
    Instant intervalStartTimeIso = Instant.ofEpochMilli(Long.parseLong(startTimeMillis));
    Instant intervalEndTimeIso = Instant.ofEpochMilli(Long.parseLong(endTimeMillis));

    StackdriverCanaryScope stackdriverCanaryScope = new StackdriverCanaryScope();
    stackdriverCanaryScope.setScope(scope);
    stackdriverCanaryScope.setStart(startTimeMillis);
    stackdriverCanaryScope.setEnd(endTimeMillis);
    stackdriverCanaryScope.setIntervalStartTimeIso(intervalStartTimeIso + "");
    stackdriverCanaryScope.setIntervalEndTimeIso(intervalEndTimeIso + "");
    stackdriverCanaryScope.setStep(step);

    return stackdriverCanaryScope;
  }
}
