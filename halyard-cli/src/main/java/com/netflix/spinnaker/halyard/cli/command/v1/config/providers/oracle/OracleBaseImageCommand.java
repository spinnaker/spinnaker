/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oracle;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractBaseImageCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;

@Parameters(separators = "=")
public class OracleBaseImageCommand extends AbstractBaseImageCommand {
  protected String getProviderName() {
    return Provider.ProviderType.ORACLE.getName();
  }

  public OracleBaseImageCommand() {
    super();
    registerSubcommand(new OracleAddBaseImageCommand());
    registerSubcommand(new OracleEditBaseImageCommand());
  }
}
