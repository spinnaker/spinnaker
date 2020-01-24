/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"jobs", "managedPipelineTemplatesV2UI"})
public class Features extends Node {
  @Override
  public String getNodeName() {
    return "features";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  private boolean auth;
  private boolean fiat;
  private boolean chaos;
  private boolean entityTags;

  @ValidForSpinnakerVersion(
      lowerBound = "1.2.0",
      tooLowMessage = "Pipeline templates are not stable prior to this release.")
  private Boolean pipelineTemplates;

  @ValidForSpinnakerVersion(
      lowerBound = "1.5.0",
      tooLowMessage =
          "Artifacts are not configurable prior to this release. Will be stable at a later release.")
  private Boolean artifacts;

  @ValidForSpinnakerVersion(
      lowerBound = "1.15.0",
      tooLowMessage =
          "Artifacts rewrite is a rewrite of the artifacts ui. Artifacts are not configurable prior to this release. Will be stable at a later release.")
  private Boolean artifactsRewrite;

  @ValidForSpinnakerVersion(
      lowerBound = "1.5.0",
      tooLowMessage =
          "Canary is not configurable prior to this release. Will be stable at a later release.")
  private Boolean mineCanary;

  @ValidForSpinnakerVersion(
      lowerBound = "1.7.0",
      upperBound = "1.11.0",
      tooLowMessage =
          "Appengine container URL deployments were not supported prior to this version.",
      tooHighMessage =
          "Appengine container URL deployments are stable; the feature flag will be removed in a future version of Halyard.")
  private Boolean appengineContainerImageUrlDeployments;

  @ValidForSpinnakerVersion(
      lowerBound = "1.7.0",
      tooLowMessage =
          "Infrastructure Stages is not configurable prior to this release. Will be stable at a later release.")
  private Boolean infrastructureStages;

  @ValidForSpinnakerVersion(
      lowerBound = "1.9.0",
      tooLowMessage = "Travis stage is not available prior to this release.")
  private Boolean travis;

  @ValidForSpinnakerVersion(
      lowerBound = "1.9.0",
      tooLowMessage = "Wercker stage is not available prior to this release.")
  private Boolean wercker;

  @ValidForSpinnakerVersion(
      lowerBound = "1.13.0",
      tooLowMessage = "Gremlin is not available prior to this release.")
  private Boolean gremlin;

  public boolean isAuth(DeploymentConfiguration deploymentConfiguration) {
    return deploymentConfiguration.getSecurity().getAuthn().isEnabled();
  }
}
