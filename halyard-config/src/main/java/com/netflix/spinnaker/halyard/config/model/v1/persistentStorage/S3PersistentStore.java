/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.persistentStorage;

import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.ValidForSpinnakerVersion;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class S3PersistentStore extends PersistentStore {
  private String bucket;
  private String rootFolder = "front50";
  private String region;

  @ValidForSpinnakerVersion(
      lowerBound = "1.13.0",
      tooLowMessage = "Spinnaker does not support configuring this behavior before that version.")
  private Boolean pathStyleAccess;

  private String endpoint;
  private String accessKeyId;
  private ServerSideEncryption serverSideEncryption;
  @Secret private String secretAccessKey;

  @Override
  public PersistentStoreType persistentStoreType() {
    return PersistentStoreType.S3;
  }

  public enum ServerSideEncryption {
    AES256,
    AWSKMS
  }
}
