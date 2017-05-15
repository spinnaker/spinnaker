/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.config.validate.v1.persistentStorage;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.OracleBMCSPersistentStore;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class OracleBMCSValidator extends Validator<OracleBMCSPersistentStore> {

  // https://docs.us-phoenix-1.oraclecloud.com/Content/Object/Tasks/managingbuckets.htm
  private static final String BUCKET_REGEX = "[a-zA-Z0-9\\-_\\.]{1,63}+";
  private static final List<String> REGIONS = Arrays.asList("us-phoenix-1");

  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, OracleBMCSPersistentStore oracleBMCSPersistentStore) {
    notNullOrEmpty(oracleBMCSPersistentStore.getCompartmentId(), "compartment id", psBuilder);
    notNullOrEmpty(oracleBMCSPersistentStore.getUserId(), "user id", psBuilder);
    notNullOrEmpty(oracleBMCSPersistentStore.getFingerprint(), "fingerprint", psBuilder);
    notNullOrEmpty(oracleBMCSPersistentStore.getSshPrivateKeyFilePath(), "ssh private key file path", psBuilder);
    notNullOrEmpty(oracleBMCSPersistentStore.getTenancyId(), "tenancy id", psBuilder);
    notNullOrEmpty(oracleBMCSPersistentStore.getNamespace(), "namespace", psBuilder);

    // region and bucketName *can* be null/empty - they then get defaulted in front50 code

    if (oracleBMCSPersistentStore.getRegion() != null && !oracleBMCSPersistentStore.getRegion().isEmpty() && !REGIONS.contains(oracleBMCSPersistentStore.getRegion())) {
      psBuilder.addProblem(Severity.ERROR, "the region is invalid");
    }

    if (oracleBMCSPersistentStore.getBucketName() != null && !oracleBMCSPersistentStore.getBucketName().isEmpty()) {
      boolean bucketNameValid = Pattern.matches(BUCKET_REGEX, oracleBMCSPersistentStore.getBucketName());
      if (!bucketNameValid) {
        psBuilder.addProblem(Severity.ERROR, "bucket name is invalid");
      }
    }

    // TODO (simonlord): Once BMCS SDK is in maven we can access via spinnaker.dependency("clouddriverOracleBmcs") and test ensureBucket (a la GCS)
  }

  private void notNullOrEmpty(String param, String paramName, ConfigProblemSetBuilder psBuilder) {
    if (param == null || param.isEmpty()) {
      psBuilder.addProblem(Severity.FATAL, "You must provide a " + paramName);
    }
  }
}