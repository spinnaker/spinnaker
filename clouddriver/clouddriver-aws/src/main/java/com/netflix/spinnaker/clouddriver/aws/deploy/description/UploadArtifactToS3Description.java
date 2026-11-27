/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Description for the Upload Artifact to S3 atomic operation.
 *
 * <p>This operation downloads a Spinnaker artifact (ZIP, TAR, or TAR.GZ), extracts its contents,
 * and uploads the extracted files to an S3 bucket while preserving the directory structure.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UploadArtifactToS3Description extends AbstractAmazonCredentialsDescription {

  /** The AWS region where the S3 bucket is located. */
  private String region;

  /** The name of the S3 bucket to upload files to. */
  private String bucket;

  /**
   * Optional prefix (folder path) for the uploaded objects. If empty, files are uploaded to the
   * bucket root. Does not need a trailing slash.
   */
  private String objectKeyPrefix = "";

  /** The artifact to download and extract. Must be a ZIP, TAR, or TAR.GZ archive. */
  private Artifact artifact;

  /**
   * When true, objects in the destination that are not present in the uploaded artifact will be
   * deleted after upload completes.
   */
  private boolean deleteStaleFiles = false;
}
