/*
    Copyright (C) 2023 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/
package com.netflix.spinnaker.echo.cdevents;

import dev.cdevents.CDEvents;
import dev.cdevents.events.PipelinerunQueuedCDEvent;
import io.cloudevents.CloudEvent;
import java.net.URI;
import lombok.Getter;

public class CDEventPipelineRunQueued extends BaseCDEvent {

  @Getter private String subjectPipelineName;
  @Getter private Object customData;

  public CDEventPipelineRunQueued(
      String executionId,
      String executionUrl,
      String executionName,
      String spinnakerUrl,
      Object customData) {
    super(spinnakerUrl, executionId, spinnakerUrl, executionUrl);
    this.subjectPipelineName = executionName;
    this.customData = customData;
  }

  @Override
  public CloudEvent createCDEvent() {
    PipelinerunQueuedCDEvent cdEvent = new PipelinerunQueuedCDEvent();
    cdEvent.setSource(URI.create(getSource()));
    cdEvent.setSubjectId(getSubjectId());
    cdEvent.setSubjectSource(URI.create(getSubjectSource()));
    cdEvent.setSubjectPipelineName(getSubjectPipelineName());
    cdEvent.setSubjectUrl(URI.create(getSubjectUrl()).toString());
    cdEvent.setCustomData(customData);
    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }
}
