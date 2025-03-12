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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBakeryDefaultsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.BakeryCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleBakeryDefaults;

/** Interact with the google provider's bakery */
@Parameters(separators = "=")
public class GoogleEditBakeryDefaultsCommand
    extends AbstractEditBakeryDefaultsCommand<GoogleBakeryDefaults> {
  protected String getProviderName() {
    return "google";
  }

  @Parameter(names = "--zone", description = "Set the default zone your images will be baked in.")
  private String zone;

  @Parameter(
      names = "--network",
      description = "Set the default network your images will be baked in.")
  private String network;

  @Parameter(
      names = "--network-project-id",
      description =
          "Set the default project id for the network and subnet to use for the VM baking your image.")
  private String networkProjectId;

  @Parameter(
      names = "--use-internal-ip",
      description = "Use the internal rather than external IP of the VM baking your image.",
      arity = 1)
  private Boolean useInternalIp;

  @Parameter(
      names = "--template-file",
      description = BakeryCommandProperties.TEMPLATE_FILE_DESCRIPTION)
  private String templateFile;

  @Override
  protected BakeryDefaults editBakeryDefaults(GoogleBakeryDefaults bakeryDefaults) {
    bakeryDefaults.setZone(isSet(zone) ? zone : bakeryDefaults.getZone());
    bakeryDefaults.setNetwork(isSet(network) ? network : bakeryDefaults.getNetwork());
    bakeryDefaults.setNetworkProjectId(
        isSet(networkProjectId) ? networkProjectId : bakeryDefaults.getNetworkProjectId());
    bakeryDefaults.setUseInternalIp(
        isSet(useInternalIp) ? useInternalIp : bakeryDefaults.isUseInternalIp());
    bakeryDefaults.setTemplateFile(
        isSet(templateFile) ? templateFile : bakeryDefaults.getTemplateFile());

    return bakeryDefaults;
  }
}
