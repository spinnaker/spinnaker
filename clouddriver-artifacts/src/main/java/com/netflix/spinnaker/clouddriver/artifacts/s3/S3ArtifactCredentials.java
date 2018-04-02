/*
 * Copyright 2018 Datadog, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.s3;

import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import groovy.util.logging.Slf4j;
import lombok.Data;

import java.io.InputStream;

@Slf4j
@Data
public class S3ArtifactCredentials implements ArtifactCredentials {
  private final String name;

  public S3ArtifactCredentials(S3ArtifactAccount account) throws IllegalArgumentException {
    name = account.getName();
  }

  @Override
  public InputStream download(Artifact artifact) throws IllegalArgumentException {
    String reference = artifact.getReference();
    if (reference.startsWith("s3://")) {
      reference = reference.substring("s3://".length());
    }

    int slash = reference.indexOf("/");
    if (slash <= 0) {
      throw new IllegalArgumentException("S3 references must be of the format s3://<bucket>/<file-path>, got: " + artifact);
    }
    String bucketName = reference.substring(0, slash);
    String path = reference.substring(slash + 1);
    S3Object s3obj = AmazonS3ClientBuilder.defaultClient().getObject(bucketName, path);
    return s3obj.getObjectContent();
  }

  @Override
  public boolean handlesType(String type) {
    return type.equals("s3/object");
  }
}
