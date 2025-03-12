/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oracle;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBaseImage;

@Parameters(separators = "=")
public class OracleEditBaseImageCommand extends AbstractEditBaseImageCommand<OracleBaseImage> {
  @Override
  protected String getProviderName() {
    return Provider.ProviderType.ORACLE.getName();
  }

  @Parameter(
      names = "--base-image-id",
      description = OracleCommandProperties.BASE_IMAGE_ID_DESCRIPTION)
  private String baseImageId;

  @Parameter(
      names = "--ssh-user-name",
      description = OracleCommandProperties.SSH_USER_NAME_DESCRIPTION)
  private String sshUserName;

  @Override
  protected BaseImage editBaseImage(OracleBaseImage baseImage) {
    OracleBaseImage.OracleImageSettings imageSettings = baseImage.getBaseImage();
    imageSettings =
        imageSettings != null ? imageSettings : new OracleBaseImage.OracleImageSettings();
    baseImage.setBaseImage(imageSettings);
    OracleBaseImage.OracleVirtualizationSettings virtualizationSettings =
        baseImage.getVirtualizationSettings();
    virtualizationSettings =
        virtualizationSettings != null
            ? virtualizationSettings
            : new OracleBaseImage.OracleVirtualizationSettings();
    virtualizationSettings.setBaseImageId(
        isSet(baseImageId) ? baseImageId : virtualizationSettings.getBaseImageId());
    virtualizationSettings.setSshUserName(
        isSet(sshUserName) ? sshUserName : virtualizationSettings.getSshUserName());
    baseImage.setVirtualizationSettings(virtualizationSettings);
    return baseImage;
  }
}
