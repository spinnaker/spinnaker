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

public class Branch {

  @XmlElement(required = false)
  private String name;

  @XmlElement(required = false, name = "SHA1")
  private String sha1;

  /**
   * Given a full branch reference (e.g. {@code origin/master}), this method will return the branch
   * name without the repository (e.g. {@code master}).
   */
  public String getSimpleBranchName() {
    if (name == null) {
      return null;
    }
    String[] segments = name.split("/");
    return segments[segments.length - 1];
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSha1() {
    return sha1;
  }

  public void setSha1(String sha1) {
    this.sha1 = sha1;
  }
}
