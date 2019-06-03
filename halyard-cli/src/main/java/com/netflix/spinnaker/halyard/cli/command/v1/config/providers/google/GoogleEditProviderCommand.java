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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractEditProviderCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider.ProviderType;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Parameters(separators = "=")
@Data
public class GoogleEditProviderCommand
    extends AbstractEditProviderCommand<GoogleAccount, GoogleProvider> {
  String shortDescription = "Set provider-wide properties for the Google provider";

  String longDescription =
      "You can edit the list of default regions used in caching and "
          + "mutating calls here. This list will become the default for all accounts, unless"
          + "specifically overridden on a per-account basis.";

  @Parameter(
      names = "--default-regions",
      variableArity = true,
      description =
          "A list of regions for caching and mutating calls, applied to all accounts "
              + "unless overridden.")
  private List<String> defaultRegions;

  @Parameter(
      names = "--add-default-region",
      description = GoogleCommandProperties.ADD_REGION_DESCRIPTION)
  private String addDefaultRegion;

  @Parameter(
      names = "--remove-default-region",
      description = GoogleCommandProperties.REMOVE_REGION_DESCRIPTION)
  private String removeDefaultRegion;

  protected String getProviderName() {
    return ProviderType.GOOGLE.getName();
  }

  @Override
  protected Provider editProvider(GoogleProvider provider) {
    try {
      provider.setDefaultRegions(
          updateStringList(
              provider.getDefaultRegions(), defaultRegions, addDefaultRegion, removeDefaultRegion));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Set either --default-regions or --[add/remove]-default-region");
    }

    return provider;
  }
}
