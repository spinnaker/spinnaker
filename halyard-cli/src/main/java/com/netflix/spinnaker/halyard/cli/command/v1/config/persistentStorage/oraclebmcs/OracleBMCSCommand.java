/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.oraclebmcs;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.AbstractNamedPersistentStoreCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;

@Parameters(separators = "=")
public class OracleBMCSCommand extends AbstractNamedPersistentStoreCommand {
  protected String getPersistentStoreType() {
    return PersistentStore.PersistentStoreType.ORACLEBMCS.getId();
  }

  public OracleBMCSCommand() {
    super();
    registerSubcommand(new OracleBMCSEditCommand());
  }
}