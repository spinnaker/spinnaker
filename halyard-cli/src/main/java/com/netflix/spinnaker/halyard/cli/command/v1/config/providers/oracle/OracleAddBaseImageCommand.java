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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractAddBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBaseImage;

@Parameters(separators = "=")
public class OracleAddBaseImageCommand extends AbstractAddBaseImageCommand {
  protected String getProviderName() {
    return Provider.ProviderType.ORACLE.getName();
  }

  @Parameter(
      names = "--base-image-id",
      required = true,
      description = OracleCommandProperties.BASE_IMAGE_ID_DESCRIPTION)
  private String baseImageId;

  @Parameter(
      names = "--ssh-user-name",
      required = true,
      description = OracleCommandProperties.SSH_USER_NAME_DESCRIPTION)
  private String sshUserName;

  @Override
  protected BaseImage buildBaseImage(String baseImageId) {
    OracleBaseImage baseImage = new OracleBaseImage();
    OracleBaseImage.OracleImageSettings imageSettings = new OracleBaseImage.OracleImageSettings();
    baseImage.setBaseImage(imageSettings);
    OracleBaseImage.OracleVirtualizationSettings virtualizationSettings =
        new OracleBaseImage.OracleVirtualizationSettings();
    virtualizationSettings.setBaseImageId(this.baseImageId);
    virtualizationSettings.setSshUserName(sshUserName);
    baseImage.setVirtualizationSettings(virtualizationSettings);

    return baseImage;
  }
}
