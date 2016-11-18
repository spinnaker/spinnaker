/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1;

import lombok.Data;

/**
 * This serves to point to a location in your Halconfig. This is intended to help users of hal to troubleshoot where
 * a certain error is coming from.
 */
@Data
public class HalconfigCoordinates {
  String deployment;
  String provider;
  String webhook;
  String account;

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder();

    if (deployment != null) {
      res.append(deployment)
          .append(".");
    }

    if (provider != null) {
      res.append("provider.")
          .append(provider)
          .append(".");
    }

    if (webhook != null) {
      res.append("webhook.")
          .append(webhook)
          .append(".");
    }

    if (account != null) {
      res.append(account)
          .append(".");
    }

    String output = res.toString();
    // cut off trailing period
    return res.toString().substring(0, output.length() - 1);
  }
}
