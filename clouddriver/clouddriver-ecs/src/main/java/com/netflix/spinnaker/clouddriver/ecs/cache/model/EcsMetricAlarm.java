/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import java.util.Collection;
import java.util.Collections;
import lombok.Data;

/**
 * Plain POJO replacement for the former v1-SDK-inheriting EcsMetricAlarm. The v2 SDK equivalent
 * ({@code software.amazon.awssdk.services.cloudwatch.model.MetricAlarm}) is {@code final} and
 * cannot be extended. This class adds Spinnaker-specific fields ({@code accountName}, {@code
 * region}) that don't exist on the SDK type.
 */
@Data
public class EcsMetricAlarm {
  private String alarmArn;
  private String alarmName;
  private String accountName;
  private String region;
  private Collection<String> alarmActions = Collections.emptyList();
  private Collection<String> okActions = Collections.emptyList();
  private Collection<String> insufficientDataActions = Collections.emptyList();

  public EcsMetricAlarm withAccountName(String accountName) {
    setAccountName(accountName);
    return this;
  }

  public EcsMetricAlarm withRegion(String region) {
    setRegion(region);
    return this;
  }

  public EcsMetricAlarm withAlarmName(String alarmName) {
    setAlarmName(alarmName);
    return this;
  }

  public EcsMetricAlarm withAlarmArn(String alarmArn) {
    setAlarmArn(alarmArn);
    return this;
  }

  // Kept setOKActions name for backward compat with cache client code
  public void setOKActions(Collection<String> okActions) {
    this.okActions = okActions;
  }

  public Collection<String> getOKActions() {
    return okActions;
  }
}
