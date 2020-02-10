/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.igor.jenkins.client.model;

import javax.xml.bind.annotation.XmlElement;

public class Action {
  public Revision getLastBuiltRevision() {
    return lastBuiltRevision;
  }

  public void setLastBuiltRevision(Revision lastBuiltRevision) {
    this.lastBuiltRevision = lastBuiltRevision;
  }

  public ScmBuild getBuild() {
    return build;
  }

  public void setBuild(ScmBuild build) {
    this.build = build;
  }

  public String getRemoteUrl() {
    return remoteUrl;
  }

  public void setRemoteUrl(String remoteUrl) {
    this.remoteUrl = remoteUrl;
  }

  @XmlElement(required = false)
  private Revision lastBuiltRevision;

  @XmlElement(required = false)
  private ScmBuild build;

  @XmlElement(required = false)
  private String remoteUrl;
}
