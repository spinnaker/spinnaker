/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.aws.deploy.AmiIdResolver
import com.netflix.spinnaker.kato.aws.deploy.ResolvedAmiResult
import com.netflix.spinnaker.kato.aws.deploy.description.AllowLaunchDescription
import com.netflix.spinnaker.kato.aws.model.AwsResultsRetriever
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired

class AllowLaunchAtomicOperation implements AtomicOperation<ResolvedAmiResult> {
  private static final String BASE_PHASE = "ALLOW_LAUNCH"

  private static final int MAX_TARGET_DESCRIBE_ATTEMPTS = 2

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AllowLaunchDescription description

  AllowLaunchAtomicOperation(AllowLaunchDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  ResolvedAmiResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Allow Launch Operation..."

    def targetCredentials = accountCredentialsProvider.getCredentials(description.account) as NetflixAmazonCredentials
    def sourceAmazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region, true)
    def targetAmazonEC2 = amazonClientProvider.getAmazonEC2(targetCredentials, description.region, true)

    ResolvedAmiResult resolvedAmi = AmiIdResolver.resolveAmiId(sourceAmazonEC2, description.region, description.amiName, description.credentials.accountId)
    if (!resolvedAmi) {
      throw new IllegalArgumentException("unable to resolve AMI imageId from $description.amiName")
    }

    task.updateStatus BASE_PHASE, "Allowing launch of $description.amiName from $description.account"
    sourceAmazonEC2.modifyImageAttribute(new ModifyImageAttributeRequest().withImageId(resolvedAmi.amiId).withLaunchPermission(new LaunchPermissionModifications()
        .withAdd(new LaunchPermission().withUserId(targetCredentials.accountId))))

    if (description.credentials == targetCredentials) {
      task.updateStatus BASE_PHASE, "Tag replication not required"
    } else {
      def request = new DescribeTagsRequest().withFilters(new Filter("resource-id").withValues(resolvedAmi.amiId))
      Closure<Set<Tag>> getTags = { DescribeTagsRequest req, TagsRetriever ret ->
        new HashSet<Tag>(ret.retrieve(req).collect { new Tag(it.key, it.value) })
      }.curry(request)
      Set<Tag> sourceTags = getTags(new TagsRetriever(sourceAmazonEC2))
      if (sourceTags.isEmpty()) {
        Thread.sleep(200)
        sourceTags = getTags(new TagsRetriever(sourceAmazonEC2))
      }
      if (sourceTags.isEmpty()) {
        task.updateStatus BASE_PHASE, "WARNING: empty tag set returned from DescribeTags, skipping tag sync"
      } else {

        Set<Tag> targetTags = getTags(new TagsRetriever(targetAmazonEC2))

        Set<Tag> tagsToRemoveFromTarget = new HashSet<>(targetTags)
        tagsToRemoveFromTarget.removeAll(sourceTags)
        Set<Tag> tagsToAddToTarget = new HashSet<>(sourceTags)
        tagsToAddToTarget.removeAll(targetTags)

        if (tagsToRemoveFromTarget) {
          task.updateStatus BASE_PHASE, "Removing tags on target AMI (${tagsToRemoveFromTarget.collect { "${it.key}: ${it.value}" }.join(", ")})."
          targetAmazonEC2.deleteTags(new DeleteTagsRequest().withResources(resolvedAmi.amiId).withTags(tagsToRemoveFromTarget))
        }
        if (tagsToAddToTarget) {
          task.updateStatus BASE_PHASE, "Creating tags on target AMI (${tagsToAddToTarget.collect { "${it.key}: ${it.value}" }.join(", ")})."
          targetAmazonEC2.createTags(new CreateTagsRequest().withResources(resolvedAmi.amiId).withTags(tagsToAddToTarget))
        }
      }
    }

    task.updateStatus BASE_PHASE, "Done allowing launch of $description.amiName from $description.account."
    resolvedAmi
  }

  @Canonical
  static class TagsRetriever extends AwsResultsRetriever<TagDescription, DescribeTagsRequest, DescribeTagsResult> {
    final AmazonEC2 amazonEC2

    @Override
    protected DescribeTagsResult makeRequest(DescribeTagsRequest request) {
      amazonEC2.describeTags(request)
    }

    @Override
    protected List<TagDescription> accessResult(DescribeTagsResult result) {
      result.tags
    }
  }
}
