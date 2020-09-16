/*
 * Copyright 2020 Playtika.
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

package com.netflix.kayenta.prometheus.health;

import java.util.Collections;
import java.util.List;

public class PrometheusHealthCache {

  // this needs to be volatile since we need to guarantee that changes from async job are visible to
  // health indicator
  private volatile List<PrometheusHealthJob.PrometheusHealthStatus> healthStatuses =
      Collections.emptyList();

  public void setHealthStatuses(List<PrometheusHealthJob.PrometheusHealthStatus> healthStatuses) {
    this.healthStatuses = healthStatuses;
  }

  public List<PrometheusHealthJob.PrometheusHealthStatus> getHealthStatuses() {
    return healthStatuses;
  }
}
