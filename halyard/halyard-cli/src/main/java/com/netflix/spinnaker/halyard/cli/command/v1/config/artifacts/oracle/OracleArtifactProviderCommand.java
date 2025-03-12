/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.oracle;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.AbstractNamedArtifactProviderCommand;

@Parameters(separators = "=")
public class OracleArtifactProviderCommand extends AbstractNamedArtifactProviderCommand {
  @Override
  protected String getArtifactProviderName() {
    return "oracle";
  }

  public OracleArtifactProviderCommand() {
    registerSubcommand(new OracleArtifactAccountCommand());
  }
}
