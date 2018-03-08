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

import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.Collections;
import java.util.List;

public enum Strategy implements StrategyFlowComposer{
  RED_BLACK("redblack"),
  ROLLING_RED_BLACK("rollingredblack"),
  HIGHLANDER("highlander"),
  ROLLING_PUSH("rollingpush"),
  CUSTOM("custom"),
  NONE("none");

  String key;

  Strategy(String key) {
    this.key = key;
  }

  public static Strategy fromStrategy(String key) {
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


  @Override
  public boolean replacesBasicSteps() {
    return this == ROLLING_PUSH || this == CUSTOM;
  }

  @Override
  public List<Stage> composeFlow(DeployStrategyStage builder, Stage stage) {
    switch (this) {
      case RED_BLACK:
        return builder.composeRedBlackFlow(stage);
      case HIGHLANDER:
        return builder.composeHighlanderFlow(stage);
      case ROLLING_PUSH:
        return builder.composeRollingPushFlow(stage);
      case CUSTOM:
        return builder.composeCustomFlow(stage);
    }

    return Collections.emptyList();
  }
}
