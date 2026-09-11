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

package com.netflix.spinnaker.kork.secrets.engines;

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3SecretEngine extends AbstractStorageSecretEngine {
  private static String IDENTIFIER = "s3";

  public S3SecretEngine(Optional<S3ConfigurationProperties> s3ConfigurationProperties) {
    this.s3ConfigurationProperties = s3ConfigurationProperties;
  }

  private final Optional<S3ConfigurationProperties> s3ConfigurationProperties;

  public String identifier() {
    return S3SecretEngine.IDENTIFIER;
  }

  @Override
  protected InputStream downloadRemoteFile(EncryptedSecret encryptedSecret) throws IOException {
    String region = encryptedSecret.getParams().get(STORAGE_REGION);
    String bucket = encryptedSecret.getParams().get(STORAGE_BUCKET);
    String objName = encryptedSecret.getParams().get(STORAGE_FILE_URI);

    S3Client s3Client = buildS3Client(region);

    try {
      try {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      } catch (NoSuchBucketException e) {
        throw new SecretException(
            String.format("S3 Bucket does not exist. Bucket: %s, Region: %s", bucket, region), e);
      }

      return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(objName).build());
    } catch (S3Exception ex) {
      StringBuilder sb = new StringBuilder("Error reading contents of S3 -- ");
      int status = ex.statusCode();
      if (403 == status) {
        sb.append(
            String.format(
                "Unauthorized access. Check connectivity and permissions to the bucket. -- Bucket: %s, Object: %s, Region: %s.\n"
                    + "Error: %s ",
                bucket, objName, region, ex.toString()));
      } else if (404 == status) {
        sb.append(
            String.format(
                "Not found. Does secret file exist? -- Bucket: %s, Object: %s, Region: %s.\nError: %s",
                bucket, objName, region, ex.toString()));
      } else {
        sb.append(String.format("Error: %s", ex.toString()));
      }
      throw new SecretException(sb.toString(), ex);
    } catch (SdkException ex) {
      throw new SecretException(
          String.format(
              "Error reading contents of S3. Bucket: %s, Object: %s, Region: %s.\nError: %s",
              bucket, objName, region, ex.toString()),
          ex);
    }
  }

  private S3Client buildS3Client(String region) {
    var builder = S3Client.builder().region(Region.of(region));
    if (this.s3ConfigurationProperties.isPresent()) {
      S3ConfigurationProperties props = this.s3ConfigurationProperties.get();
      if (!StringUtils.isBlank(props.getEndpointUrl())) {
        builder =
            builder
                .endpointOverride(URI.create(props.getEndpointUrl()))
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyleAccessEnabled())
                        .build());
      } else {
        throw new SecretException("Endpoint not found in properties: s3.secret.endpoint-url");
      }
    }
    return builder.build();
  }
}
