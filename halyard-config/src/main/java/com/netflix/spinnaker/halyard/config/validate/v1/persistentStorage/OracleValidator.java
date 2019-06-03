/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.config.validate.v1.persistentStorage;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.OraclePersistentStore;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OracleValidator extends Validator<OraclePersistentStore> {

  // https://docs.us-phoenix-1.oraclecloud.com/Content/Object/Tasks/managingbuckets.htm
  private static final String BUCKET_REGEX = "[a-zA-Z0-9\\-_\\.]{1,63}+";

  @Override
  public void validate(
      ConfigProblemSetBuilder psBuilder, OraclePersistentStore oraclePersistentStore) {
    notNullOrEmpty(oraclePersistentStore.getCompartmentId(), "compartment id", psBuilder);
    notNullOrEmpty(oraclePersistentStore.getUserId(), "user id", psBuilder);
    notNullOrEmpty(oraclePersistentStore.getFingerprint(), "fingerprint", psBuilder);
    notNullOrEmpty(
        oraclePersistentStore.getSshPrivateKeyFilePath(), "ssh private key file path", psBuilder);
    notNullOrEmpty(oraclePersistentStore.getTenancyId(), "tenancy id", psBuilder);
    notNullOrEmpty(oraclePersistentStore.getNamespace(), "namespace", psBuilder);

    // region and bucketName *can* be null/empty - they then get defaulted in front50 code

    if (oraclePersistentStore.getBucketName() != null
        && !oraclePersistentStore.getBucketName().isEmpty()) {
      boolean bucketNameValid =
          Pattern.matches(BUCKET_REGEX, oraclePersistentStore.getBucketName());
      if (!bucketNameValid) {
        psBuilder.addProblem(Severity.ERROR, "bucket name is invalid");
      }
    }

    // TODO (simonlord): Once BMCS SDK is in maven we can access via
    // spinnaker.dependency("clouddriverOracleBmcs") and test ensureBucket (a la GCS)
  }

  private void notNullOrEmpty(String param, String paramName, ConfigProblemSetBuilder psBuilder) {
    if (param == null || param.isEmpty()) {
      psBuilder.addProblem(Severity.FATAL, "You must provide a " + paramName);
    }
  }
}
