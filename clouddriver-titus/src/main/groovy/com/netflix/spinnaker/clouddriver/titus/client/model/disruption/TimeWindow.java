/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client.model.disruption;

import java.util.List;

public class TimeWindow {
  List<String> days;
  List<HourlyTimeWindow> hourlyTimeWindows;
  String timeZone;

  public List<String> getDays() {
    return days;
  }

  public void setDays(List<String> days) {
    this.days = days;
  }

  public List<HourlyTimeWindow> getHourlyTimeWindows() {
    return hourlyTimeWindows;
  }

  public void setHourlyTimeWindows(List<HourlyTimeWindow> hourlyTimeWindows) {
    this.hourlyTimeWindows = hourlyTimeWindows;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }
}
