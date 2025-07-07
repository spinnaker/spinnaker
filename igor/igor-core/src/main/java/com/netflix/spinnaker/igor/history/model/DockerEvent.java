/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.igor.history.model;

import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DockerEvent extends Event {
  private Map<String, String> details;
  private Content content;
  private GenericArtifact artifact;

  public static final String DEFAULT_TYPE = "docker";

  public DockerEvent() {
    details = new HashMap<>(2);
    details.put("type", DEFAULT_TYPE);
    details.put("source", "igor");
  }

  /**
   * Sets the type of this event
   *
   * @param type the event type (e.g., "docker", "helm/oci")
   */
  public void setType(String type) {
    if (details == null) {
      details = new HashMap<>(2);
      details.put("source", "igor");
    }
    details.put("type", type);
  }

  @Data
  public static class Content {
    private String account;
    private String registry;
    private String repository;
    private String tag;
    private String digest;
  }
}
