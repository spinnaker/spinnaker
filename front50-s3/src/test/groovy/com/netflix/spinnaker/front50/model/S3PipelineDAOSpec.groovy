/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.pipeline.PipelineDAOSpec
import static rx.schedulers.Schedulers.immediate

class S3PipelineDAOSpec extends PipelineDAOSpec<S3PipelineDAO> {

  def amazonS3 = Stub(AmazonS3)
  def objectMapper = new ObjectMapper()

  @Override
  S3PipelineDAO getInstance() {
    return new S3PipelineDAO(objectMapper, amazonS3, immediate(), 10000, "bucket", "rootFolder")
  }
}
