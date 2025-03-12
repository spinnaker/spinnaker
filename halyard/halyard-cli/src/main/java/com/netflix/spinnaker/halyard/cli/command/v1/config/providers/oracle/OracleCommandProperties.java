/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oracle;

public class OracleCommandProperties {
  public static final String COMPARTMENT_ID_DESCRIPTION =
      "Provide the OCID of the Oracle Compartment to use.";
  public static final String USER_ID_DESCRIPTION =
      "Provide the OCID of the Oracle User you're authenticating as";
  public static final String FINGERPRINT_DESCRIPTION = "Fingerprint of the public key";
  public static final String SSH_PRIVATE_KEY_FILE_PATH_DESCRIPTION =
      "Path to the private key in PEM format";
  public static final String PRIVATE_KEY_PASSPHRASE_DESCRIPTION =
      "Passphrase used for the private key, if it is encrypted";
  public static final String TENANCY_ID_DESCRIPTION =
      "Provide the OCID of the Oracle Tenancy to use.";
  public static final String REGION_DESCRIPTION = "An Oracle region (e.g., us-phoenix-1)";

  public static final String INSTANCE_SHAPE_DESCRIPTION =
      "The shape for allocated to a newly created instance.";
  public static final String AVAILABILITY_DOMAIN_DESCRIPTION =
      "The name of the Availability Domain within which a new instance is launched and provisioned.";
  public static final String SUBNET_ID_DESCRIPTION =
      "The name of the subnet within which a new instance is launched and provisioned.";

  public static final String BASE_IMAGE_ID_DESCRIPTION =
      "The OCID of the base image ID for the baking configuration.";
  public static final String SSH_USER_NAME_DESCRIPTION =
      "The ssh username for the baking configuration.";

  public static final String NAMESPACE_DESCRIPTION =
      "The namespace the bucket and objects should be created in";
  public static final String BUCKET_NAME_DESCRIPTION =
      "The bucket name to store persistent state object in";
}
