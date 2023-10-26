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
import dev.cdevents.events.TaskRunStartedCDEvent;
import io.cloudevents.CloudEvent;
import java.net.URI;
import lombok.Getter;

public class CDEventTaskRunStarted extends BaseCDEvent {

  @Getter private String subjectTaskName;
  @Getter private String subjectPipelineRunId;

  public CDEventTaskRunStarted(
      String executionId, String executionUrl, String executionName, String spinnakerUrl) {
    super(spinnakerUrl, executionId, spinnakerUrl, executionUrl);
    this.subjectTaskName = executionName;
    this.subjectPipelineRunId = executionId;
  }

  @Override
  public CloudEvent createCDEvent() {
    TaskRunStartedCDEvent cdEvent = new TaskRunStartedCDEvent();
    cdEvent.setSource(URI.create(getSource()));

    cdEvent.setSubjectId(getSubjectId());
    cdEvent.setSubjectSource(URI.create(getSubjectSource()));
    cdEvent.setSubjectTaskName(getSubjectTaskName());
    cdEvent.setSubjectUrl(URI.create(getSubjectUrl()));
    cdEvent.setSubjectPipelineRunId(getSubjectPipelineRunId());

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }
}
