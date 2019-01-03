/*
 * Copyright (c) 2019 Nike, inc.
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

package com.netflix.kayenta.canaryanalysis.event;

import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionStatusResponse;
import org.springframework.context.ApplicationEvent;

public class StandaloneCanaryAnalysisExecutionCompletedEvent extends ApplicationEvent {
  private final CanaryAnalysisExecutionStatusResponse canaryAnalysisExecutionStatusResponse;

  public StandaloneCanaryAnalysisExecutionCompletedEvent(Object source,
                                                         CanaryAnalysisExecutionStatusResponse canaryAnalysisExecutionStatusResponse) {

    super(source);
    this.canaryAnalysisExecutionStatusResponse = canaryAnalysisExecutionStatusResponse;
  }

  public CanaryAnalysisExecutionStatusResponse getCanaryAnalysisExecutionStatusResponse() {
    return canaryAnalysisExecutionStatusResponse;
  }
}
