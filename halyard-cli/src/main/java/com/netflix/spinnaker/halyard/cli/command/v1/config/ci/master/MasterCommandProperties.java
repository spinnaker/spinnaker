/*
 * Copyright (c) 2019 Schibsted Media Group. All rights reserved
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master;

public class MasterCommandProperties {
  public static final String READ_PERMISSION_DESCRIPTION =
      "A user must have at least one of these roles in order to "
          + "view this build master or use it as a trigger source.";

  public static final String WRITE_PERMISSION_DESCRIPTION =
      "A user must have at least one of these roles in order "
          + "to be able to run jobs on this build master.";
}
