/*
 * Copyright 2023 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudrunMetadataAnnotations {

  @JsonProperty("run.googleapis.com/client-name")
  private String runGoogleapisComClientName;

  @JsonProperty("serving.knative.dev/creator")
  private String servingKnativeDevCreator;

  @JsonProperty("serving.knative.dev/lastModifier")
  private String servingKnativeDevLastModifier;

  @JsonProperty("client.knative.dev/user-image")
  private String clientKnativeDevUserImage;

  @JsonProperty("client.knative.dev/operation-id")
  private String runGoogleapisComOperationID;

  @JsonProperty("run.googleapis.com/ingress")
  private String runGoogleapisComIngress;

  @JsonProperty("run.googleapis.com/ingress-status")
  private String runGoogleapisComIngressStatus;

  private static final String LABELS_PREFIX = "spinnaker/";

  private String spinnakerApplication = LABELS_PREFIX + "application";
}
