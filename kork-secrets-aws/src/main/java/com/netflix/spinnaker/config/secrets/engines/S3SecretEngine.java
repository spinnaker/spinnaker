/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.config.secrets.engines;

import com.amazonaws.AmazonClientException;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.netflix.spinnaker.config.secrets.EncryptedSecret;
import com.netflix.spinnaker.config.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.config.secrets.SecretEngine;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

@Component
public class S3SecretEngine extends AbstractStorageSecretEngine {
  private static String IDENTIFIER = "s3";

  public String identifier() { return S3SecretEngine.IDENTIFIER;}


  @Override
  protected InputStream downloadRemoteFile(EncryptedSecret encryptedSecret) throws IOException {
    String region = encryptedSecret.getParams().get(STORAGE_REGION);
    String bucket = encryptedSecret.getParams().get(STORAGE_BUCKET);
    String objName = encryptedSecret.getParams().get(STORAGE_FILE_URI);

    AmazonS3ClientBuilder s3ClientBuilder = AmazonS3ClientBuilder.standard()
      .withRegion(region);

    AmazonS3 s3Client = s3ClientBuilder.build();

    try {
      S3Object s3Object = s3Client.getObject(bucket, objName);
      return s3Object.getObjectContent();

    } catch (AmazonClientException ex) {
      String msg = String.format(
        "Error reading contents of S3. Region: %s, Bucket: %s, Object: %s. " +
          "Check connectivity and permissions to that bucket: %s ",
        region, bucket, objName, ex.toString());
      throw new IOException(msg);
    }
  }
}
