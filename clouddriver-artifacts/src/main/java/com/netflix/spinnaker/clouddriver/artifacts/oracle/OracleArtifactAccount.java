/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.artifacts.oracle;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import lombok.Data;

@Data
public class OracleArtifactAccount implements ArtifactAccount {
  private String name;

  private String namespace;
  private String region;
  private String userId;
  private String fingerprint;
  private String sshPrivateKeyFilePath;
  private String privateKeyPassphrase;
  private String tenancyId;
}
