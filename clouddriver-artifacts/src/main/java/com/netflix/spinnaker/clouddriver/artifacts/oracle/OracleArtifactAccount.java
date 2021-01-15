/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.artifacts.oracle;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

@NonnullByDefault
@Value
public class OracleArtifactAccount implements ArtifactAccount {
  private final String name;
  private final String namespace;
  private final String region;
  private final String userId;
  private final String fingerprint;
  private final String sshPrivateKeyFilePath;
  private final String privateKeyPassphrase;
  private final String tenancyId;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  OracleArtifactAccount(
      String name,
      String namespace,
      String region,
      String userId,
      String fingerprint,
      String sshPrivateKeyFilePath,
      String privateKeyPassphrase,
      String tenancyId) {
    this.name = Strings.nullToEmpty(name);
    this.namespace = Strings.nullToEmpty(namespace);
    this.region = Strings.nullToEmpty(region);
    this.userId = Strings.nullToEmpty(userId);
    this.fingerprint = Strings.nullToEmpty(fingerprint);
    this.sshPrivateKeyFilePath = Strings.nullToEmpty(sshPrivateKeyFilePath);
    this.privateKeyPassphrase = Strings.nullToEmpty(privateKeyPassphrase);
    this.tenancyId = Strings.nullToEmpty(tenancyId);
  }
}
