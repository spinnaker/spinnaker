/*
 * Copyright 2020 Apple, Inc.
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

package com.netflix.spinnaker.igor.history.model;

import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import java.util.HashMap;
import java.util.Map;

public class HelmEvent extends Event {
  public Content content;
  public GenericArtifact artifact;
  public Map<String, String> details;

  public HelmEvent(Content content, GenericArtifact artifact) {
    this.content = content;
    this.artifact = artifact;

    this.details = new HashMap<>();
    this.details.put("type", "helm");
    this.details.put("source", "igor");
  }

  public static class Content {
    public String account;
    public String chart;
    public String version;
    public String digest;

    public Content(String account, String chart, String version, String digest) {
      this.account = account;
      this.chart = chart;
      this.version = version;
      this.digest = digest;
    }
  }
}
