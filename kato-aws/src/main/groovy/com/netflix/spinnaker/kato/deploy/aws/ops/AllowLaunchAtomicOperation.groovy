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


package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAamzonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.AllowLaunchDescription
import com.netflix.spinnaker.kato.model.aws.AwsResultsRetriever
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired

class AllowLaunchAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ALLOW_LAUNCH"

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
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Allow Launch Operation..."

    def sourceAmazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region)

    def targetCredentials = accountCredentialsProvider.getCredentials(description.account) as NetflixAssumeRoleAamzonCredentials
    def targetAmazonEC2 = amazonClientProvider.getAmazonEC2(targetCredentials, description.region)

    task.updateStatus BASE_PHASE, "Allowing launch of $description.amiName from $description.account"
    sourceAmazonEC2.modifyImageAttribute(new ModifyImageAttributeRequest().withImageId(description.amiName).withLaunchPermission(new LaunchPermissionModifications()
      .withAdd(new LaunchPermission().withUserId(String.valueOf(targetCredentials.accountId)))))

    def request = new DescribeTagsRequest(filters: [new Filter(name: "resource-id", values: [description.amiName])])

    def targetTags = new TagsRetriever(targetAmazonEC2).retrieve(request)
    def tagsToRemoveFromTarget = targetTags.collect { new Tag(key: it.key) }
    targetAmazonEC2.deleteTags(new DeleteTagsRequest(resources: [description.amiName], tags: tagsToRemoveFromTarget))

    def sourceTags = new TagsRetriever(sourceAmazonEC2).retrieve(request)
    def tagsToAddToTarget = sourceTags.collect { new Tag(key: it.key, value: it.value) }
    task.updateStatus BASE_PHASE, "Creating tags on target AMI (${tagsToAddToTarget.collect { "${it.key}: ${it.value}" }.join(", ")})."
    targetAmazonEC2.createTags(new CreateTagsRequest(resources: [description.amiName], tags: tagsToAddToTarget))

    task.updateStatus BASE_PHASE, "Done allowing launch of $description.amiName from $description.account."
    null
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
