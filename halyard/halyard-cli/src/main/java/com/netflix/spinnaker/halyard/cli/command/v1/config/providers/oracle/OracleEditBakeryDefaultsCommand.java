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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBakeryDefaultsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.BakeryCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBakeryDefaults;

/** Interact with oracle provider's bakery */
@Parameters(separators = "=")
public class OracleEditBakeryDefaultsCommand
    extends AbstractEditBakeryDefaultsCommand<OracleBakeryDefaults> {
  protected String getProviderName() {
    return Provider.ProviderType.ORACLE.getName();
  }

  @Parameter(
      names = "--instance-shape",
      description = OracleCommandProperties.INSTANCE_SHAPE_DESCRIPTION,
      required = true)
  private String instanceShape;

  @Parameter(
      names = "--availability-domain",
      description = OracleCommandProperties.AVAILABILITY_DOMAIN_DESCRIPTION,
      required = true)
  private String availabilityDomain;

  @Parameter(
      names = "--subnet-id",
      description = OracleCommandProperties.SUBNET_ID_DESCRIPTION,
      required = true)
  private String subnetId;

  @Parameter(
      names = "--template-file",
      description = BakeryCommandProperties.TEMPLATE_FILE_DESCRIPTION)
  private String templateFile;

  @Override
  protected BakeryDefaults editBakeryDefaults(OracleBakeryDefaults bakeryDefaults) {
    bakeryDefaults.setAvailabilityDomain(
        isSet(availabilityDomain) ? availabilityDomain : bakeryDefaults.getAvailabilityDomain());
    bakeryDefaults.setSubnetId(isSet(subnetId) ? subnetId : bakeryDefaults.getSubnetId());
    bakeryDefaults.setInstanceShape(
        isSet(instanceShape) ? instanceShape : bakeryDefaults.getInstanceShape());

    bakeryDefaults.setTemplateFile(
        isSet(templateFile) ? templateFile : bakeryDefaults.getTemplateFile());

    return bakeryDefaults;
  }
}
