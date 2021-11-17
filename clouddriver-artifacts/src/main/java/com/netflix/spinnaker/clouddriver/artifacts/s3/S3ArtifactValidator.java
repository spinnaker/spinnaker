/*
 * Copyright 2021 Salesforce, Inc.
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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.io.InputStream;

@NonnullByDefault
public interface S3ArtifactValidator {
  /**
   * Validate an S3 artifact. Throw an exception if invalid.
   *
   * @param amazonS3 the S3 client used to retrieve the artifact to validate
   * @param s3Obj the artifact to validate. It is the implementation's responsibility to either
   *     return the input stream from this object to the caller, or close it.
   * @return the validated S3 artifact (e.g. s3obj.getObjectContent()). It it the caller's
   *     responsibility to close this stream as soon as possible.
   */
  InputStream validate(AmazonS3 amazonS3, S3Object s3obj);
}
