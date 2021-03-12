/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy;

public enum Strategy {
  RED_BLACK("redblack"),
  ROLLING_RED_BLACK("rollingredblack"),
  MONITORED("monitored"),
  CF_ROLLING_RED_BLACK("cfrollingredblack"),
  HIGHLANDER("highlander"),
  ROLLING_PUSH("rollingpush"),
  CUSTOM("custom"),
  NONE("none");

  String key;

  Strategy(String key) {
    this.key = key;
  }

  public static Strategy fromStrategyKey(String key) {
    if (key == null) {
      return NONE;
    }
    for (Strategy strategy : values()) {
      if (key.equals(strategy.key)) {
        return strategy;
      }
    }
    return NONE;
  }

  public String getKey() {
    return key;
  }
}
