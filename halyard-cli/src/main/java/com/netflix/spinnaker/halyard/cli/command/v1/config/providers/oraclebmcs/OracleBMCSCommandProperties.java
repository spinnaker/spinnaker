/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oraclebmcs;

public class OracleBMCSCommandProperties {
  public static final String COMPARTMENT_ID_DESCRIPTION = "Provide the OCID of the Oracle BMCS Compartment to use.";
  public static final String USER_ID_DESCRIPTION = "Provide the OCID of the Oracle BMCS User you're authenticating as";
  public static final String FINGERPRINT_DESCRIPTION = "Fingerprint of the public key";
  public static final String SSH_PRIVATE_KEY_FILE_PATH_DESCRIPTION = "Path to the private key in PEM format";
  public static final String TENANCY_ID_DESCRIPTION = "Provide the OCID of the Oracle BMCS Tenancy to use.";
  public static final String REGION_DESCRIPTION = "An Oracle BMCS region (e.g., us-phoenix-1)";
}
