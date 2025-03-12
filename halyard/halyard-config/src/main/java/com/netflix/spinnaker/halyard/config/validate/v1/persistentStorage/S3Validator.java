/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.validate.v1.persistentStorage;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.netflix.spinnaker.front50.config.S3Config;
import com.netflix.spinnaker.front50.config.S3MetadataStorageProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.S3PersistentStore;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.providers.aws.AwsAccountValidator;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class S3Validator extends Validator<S3PersistentStore> {
  @Override
  public void validate(ConfigProblemSetBuilder ps, S3PersistentStore n) {
    if (!StringUtils.isEmpty(n.getEndpoint())) {
      return;
    }

    try {
      AWSCredentialsProvider credentialsProvider =
          AwsAccountValidator.getAwsCredentialsProvider(
              n.getAccessKeyId(), secretSessionManager.decrypt(n.getSecretAccessKey()));
      S3Config s3Config = new S3Config();
      S3MetadataStorageProperties s3Properties = new S3MetadataStorageProperties();
      s3Properties.setBucket(n.getBucket());
      s3Properties.setRootFolder(n.getRootFolder());
      s3Properties.setRegion(n.getRegion());
      AmazonS3 s3Client = s3Config.awsS3MetadataClient(credentialsProvider, s3Properties);
      new S3Config().s3StorageService(s3Client, s3Properties);
    } catch (Exception e) {
      ps.addProblem(
          Problem.Severity.ERROR,
          "Failed to ensure the required bucket \""
              + n.getBucket()
              + "\" exists: "
              + e.getMessage());
    }
  }
}
