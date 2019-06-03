/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OraclePersistentStore extends PersistentStore {

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
  @SecretFile
  @Size(min = 1)
  private String sshPrivateKeyFilePath;

  @Secret private String privateKeyPassphrase;

  @NotNull
  @Size(min = 1)
  private String tenancyId;

  @Override
  public PersistentStoreType persistentStoreType() {
    return PersistentStoreType.ORACLE;
  }

  public static OraclePersistentStore mergeOracleBMCSPersistentStore(
      OraclePersistentStore oracle, OracleBMCSPersistentStore bmcs) {
    if (oracle.getTenancyId() == null && bmcs.getTenancyId() != null) {
      return convertFromOracleBMCSPersistentStore(bmcs);
    } else {
      return oracle;
    }
  }

  private static OraclePersistentStore convertFromOracleBMCSPersistentStore(
      OracleBMCSPersistentStore bmcs) {
    OraclePersistentStore store = new OraclePersistentStore();
    store.setBucketName(bmcs.getBucketName());
    store.setNamespace(bmcs.getNamespace());
    store.setCompartmentId(bmcs.getCompartmentId());
    store.setRegion(bmcs.getRegion());
    store.setUserId(bmcs.getUserId());
    store.setFingerprint(bmcs.getFingerprint());
    store.setSshPrivateKeyFilePath(bmcs.getSshPrivateKeyFilePath());
    store.setTenancyId(bmcs.getTenancyId());
    return store;
  }
}
