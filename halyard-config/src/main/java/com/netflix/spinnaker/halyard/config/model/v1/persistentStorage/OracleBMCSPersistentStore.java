/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.config.model.v1.persistentStorage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OracleBMCSPersistentStore extends PersistentStore {

  private String bucketName;

  @NotNull
  @Size(min = 1)
  private String namespace;

  @NotNull
  @Size(min = 1)
  private String compartmentId;

  private String region;

  @NotNull
  @Size(min = 1)
  private String userId;

  @NotNull
  @Size(min = 1)
  private String fingerprint;

  @NotNull
  @LocalFile
  @Size(min = 1)
  private String sshPrivateKeyFilePath;

  @NotNull
  @Size(min = 1)
  private String tenancyId;

  @Override
  public PersistentStoreType persistentStoreType() {
    return PersistentStoreType.ORACLEBMCS;
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }
}
