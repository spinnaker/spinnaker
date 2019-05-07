/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleImageTagsDescription
import org.springframework.beans.factory.annotation.Autowired

/**
 * Update the set of labels defined on an image. The newly-specified key-value pairs are added to the existing set.
 * Values can be empty.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/beta/images/setLabels}
 */
class UpsertGoogleImageTagsAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_IMAGE_TAGS"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertGoogleImageTagsDescription description

  @Autowired
  private GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  String clouddriverUserAgentApplicationName

  UpsertGoogleImageTagsAtomicOperation(UpsertGoogleImageTagsDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertImageTags": { "imageName": "my-image-123", "tags": { "some-key-1": "some-val-2" }, "credentials": "my-account-name" } } ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of image tags for $description.imageName..."

    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def imageName = description.imageName
    def tags = description.tags
    def image = GCEUtil.queryImage(imageName,
                                   credentials,
                                   task,
                                   BASE_PHASE,
                                   clouddriverUserAgentApplicationName,
                                   googleConfigurationProperties.baseImageProjects,
                                   this)

    if (image) {
      // Image self links are constructed like this:
      // https://compute.googleapis.com/compute/alpha/projects/rosco-oss-2/global/images/spinnaker-rosco-all-20161229193556-precise
      def imageSelfLinkTokens = image.getSelfLink().split("/")
      def imageProject = imageSelfLinkTokens[imageSelfLinkTokens.length - 4]

      Map<String, String> originalLabels = image.getLabels()

      if (originalLabels == null) {
        originalLabels = [:]
      }

      Map<String, String> newLabels = originalLabels + tags

      task.updateStatus BASE_PHASE, "Upserting new labels $newLabels in place of original labels $originalLabels for image $imageName..."

      GlobalSetLabelsRequest setLabelsRequest = new GlobalSetLabelsRequest(labels: newLabels,
                                                                           labelFingerprint: image.getLabelFingerprint())

      timeExecute(
          compute.images().setLabels(imageProject, imageName, setLabelsRequest),
          "compute.images.setLabels", TAG_SCOPE, SCOPE_GLOBAL)
    }

    task.updateStatus BASE_PHASE, "Done tagging image $imageName."
    null
  }
}
