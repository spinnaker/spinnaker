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

import io.cloudevents.CloudEvent;
import lombok.Getter;

public abstract class BaseCDEvent {
  /**
   * This method can be implemented to create various types of CDEvents that can return a specific
   * type of CDEvents in a CloudEvent format, more details on CDEvent types can be found in
   * Documentation at https://cdevents.dev
   *
   * @return cdEvent
   */
  abstract CloudEvent createCDEvent();

  /** Common properties used in most of the CDEvents */
  @Getter private String source;

  @Getter private String subjectId;
  @Getter private String subjectSource;
  @Getter private String subjectUrl;

  public BaseCDEvent(String source, String subjectId, String subjectSource, String subjectUrl) {
    this.source = source;
    this.subjectId = subjectId;
    this.subjectSource = subjectSource;
    this.subjectUrl = subjectUrl;
  }
}
