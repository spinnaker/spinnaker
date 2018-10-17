/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmiTagsDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertAmiTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_AMI_TAGS"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final UpsertAmiTagsDescription description

  UpsertAmiTagsAtomicOperation(UpsertAmiTagsDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def descriptor = "${description.credentials.name}/${description.amiName}"
    task.updateStatus BASE_PHASE, "Initializing Upsert AMI Tags operation for ${descriptor}..."

    description.regions.each { String region ->
      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region, true)
      def describeImagesRequest = new DescribeImagesRequest().withFilters(
        new Filter("name", [description.amiName])
      )
      def images = amazonEC2.describeImages(describeImagesRequest).images
      if (!images) {
        task.updateStatus BASE_PHASE, "No AMI found for ${descriptor} in ${region}."
        task.fail()
        return
      }

      boolean wasSuccessful = true

      images.each {
        if (upsertAmiTags(region, it.imageId, buildTags(description))) {
          task.updateStatus BASE_PHASE, "Finished Upsert AMI Tags operation for ${descriptor} (${it.imageId}) in ${region}."
        } else {
          wasSuccessful = false
        }
      }

      if (!wasSuccessful) {
        task.fail()
      }
    }

    null
  }

  private boolean upsertAmiTags(String region, String amiId, Collection<Tag> tags) {
    try {
      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
      def createTagsRequest = new CreateTagsRequest()
        .withResources(amiId)
        .withTags(tags)

      if (!tags) {
        // createTags expects at least one tag to have been provided
        task.updateStatus BASE_PHASE, "Skipping empty tags update for ${amiId} in ${region}"
        return true
      }

      task.updateStatus BASE_PHASE, "Updating tags for ${amiId} in ${region}..."
      amazonEC2.createTags(createTagsRequest)
      task.updateStatus BASE_PHASE, "Tags updated for ${amiId} in ${region}..."
      return true
    } catch (e) {
      task.updateStatus BASE_PHASE, "Could not upsert AMI tags for ${amiId} in ${region}! Reason: $e.message"
    }
    return false
  }

  private static Collection<Tag> buildTags(UpsertAmiTagsDescription upsertAmiTagsDescription) {
    return upsertAmiTagsDescription.tags.collect {
      new Tag(it.key, it.value)
    }
  }
}
