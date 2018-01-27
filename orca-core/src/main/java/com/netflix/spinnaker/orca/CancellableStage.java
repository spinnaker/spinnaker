/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca;

import java.util.Map;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public interface CancellableStage {
  Result cancel(Stage stage);

  class Result {
    private final String stageId;
    private final Map details;

    public Result(Stage stage, Map details) {
      this.stageId = stage.getId();
      this.details = details;
    }

    public final String getStageId() {
      return stageId;
    }

    public final Map getDetails() {
      return details;
    }
  }
}
