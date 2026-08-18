/*
 * Copyright (c) 2019 Schibsted Media Group.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException
import software.amazon.awssdk.services.cloudformation.model.ChangeSetType
import software.amazon.awssdk.services.cloudformation.model.CreateChangeSetRequest
import software.amazon.awssdk.services.cloudformation.model.CreateChangeSetResponse
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest
import software.amazon.awssdk.services.cloudformation.model.CreateStackResponse
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse
import software.amazon.awssdk.services.cloudformation.model.Parameter
import software.amazon.awssdk.services.cloudformation.model.Stack
import software.amazon.awssdk.services.cloudformation.model.Tag
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest
import software.amazon.awssdk.services.cloudformation.model.UpdateStackResponse
import software.amazon.awssdk.services.cloudformation.model.ValidateTemplateRequest
import spock.lang.Specification
import spock.lang.Unroll

class DeployCloudFormationAtomicOperationSpec extends Specification {
  void setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "should build a CreateStackRequest if stack doesn't exist and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)
    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: '{"key":"value"}',
          roleARN: roleARN,
          parameters: [ key: "value"],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> { throw new IllegalArgumentException() }
    1 * cloudFormationClient.validateTemplate(_ as ValidateTemplateRequest) >> { null }
    1 * cloudFormationClient.createStack(_ as CreateStackRequest) >> { CreateStackRequest request ->
      assert request.stackName() == "stackTest"
      assert request.templateBody() == '{"key":"value"}'
      assert request.roleARN() == expectedRoleARN
      assert request.parameters() == [ Parameter.builder().parameterKey("key").parameterValue("value").build() ]
      assert request.tags() == [ Tag.builder().key("key").value("value").build() ]
      assert request.capabilitiesAsStrings() == ["cap1", "cap2"]
      CreateStackResponse.builder().stackId(stackId).build()
    }

    where:
    roleARN                              || expectedRoleARN
    "arn:aws:iam:123456789012:role/test" || "arn:aws:iam:123456789012:role/test"
    ""                                   || null
    "    "                               || null
    null                                 || null
  }

  @Unroll
  void "should build an UpdateStackRequest if stack exists and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)
    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: '{"key":"value"}',
          roleARN: roleARN,
          parameters: [ key: "value" ],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([Stack.builder().stackId("stackId").build()]).build()
    }
    1 * cloudFormationClient.validateTemplate(_ as ValidateTemplateRequest) >> { null }
    1 * cloudFormationClient.updateStack(_ as UpdateStackRequest) >> { UpdateStackRequest request ->
      assert request.stackName() == "stackTest"
      assert request.templateBody() == '{"key":"value"}'
      assert request.roleARN() == expectedRoleARN
      assert request.parameters() == [ Parameter.builder().parameterKey("key").parameterValue("value").build() ]
      assert request.tags() == [ Tag.builder().key("key").value("value").build() ]
      assert request.capabilitiesAsStrings() == ["cap1", "cap2"]
      UpdateStackResponse.builder().stackId(stackId).build()
    }

    where:
    roleARN                              || expectedRoleARN
    "arn:aws:iam:123456789012:role/test" || "arn:aws:iam:123456789012:role/test"
    ""                                   || null
    "    "                               || null
    null                                 || null
  }

  @Unroll
  void "should build a CreateChangeSetRequest if it's a changeset and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)

    def awsConfigurationProperties = new AwsConfigurationProperties()

    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: 'key: "value"',
          roleARN: roleARN,
          parameters: [ key: "value" ],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test"),
          isChangeSet: true,
          changeSetName: "changeSetTest"
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.awsConfigurationProperties = awsConfigurationProperties
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.validateTemplate(_ as ValidateTemplateRequest) >> { null }
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      if (existingStack) {
        DescribeStacksResponse.builder().stacks([Stack.builder().stackId("stackId").build()]).build()
      } else {
        DescribeStacksResponse.builder().stacks([]).build()
      }
    }
    1 * cloudFormationClient.createChangeSet(_ as CreateChangeSetRequest) >> { CreateChangeSetRequest request ->
      assert request.stackName() == "stackTest"
      assert request.templateBody() == 'key: "value"'
      assert request.roleARN() == expectedRoleARN
      assert request.parameters() == [ Parameter.builder().parameterKey("key").parameterValue("value").build() ]
      assert request.tags() == [ Tag.builder().key("key").value("value").build() ]
      assert request.capabilitiesAsStrings() == ["cap1", "cap2"]
      assert request.changeSetName() == "changeSetTest"
      assert request.changeSetType() == expectedChangeSetType
      assert request.includeNestedStacks() == false
      CreateChangeSetResponse.builder().stackId(stackId).build()
    }

    where:
    roleARN                              | expectedRoleARN                      | existingStack || expectedChangeSetType
    "arn:aws:iam:123456789012:role/test" | "arn:aws:iam:123456789012:role/test" | true          || ChangeSetType.UPDATE
    ""                                   | null                                 | true          || ChangeSetType.UPDATE
    "   "                                | null                                 | true          || ChangeSetType.UPDATE
    "arn:aws:iam:123456789012:role/test" | "arn:aws:iam:123456789012:role/test" | true          || ChangeSetType.UPDATE
    "arn:aws:iam:123456789012:role/test" | "arn:aws:iam:123456789012:role/test" | false         || ChangeSetType.CREATE
  }

  @Unroll
  void "should fail when AWS fails to update stack"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: '{"key":"value"}',
          roleARN: "arn:aws:iam:123456789012:role/test",
          parameters: [ key: "value" ],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.validateTemplate(_ as ValidateTemplateRequest) >> { null }
    1 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([Stack.builder().stackId("stackId").build()]).build()
    }
    1 * cloudFormationClient.updateStack(_ as UpdateStackRequest) >> {
      throw CloudFormationException.builder().message("error").build()
    }
    thrown(CloudFormationException)
  }

  @Unroll
  void "should success when updating stack and no change needed"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: '{"key":"value"}',
          roleARN: "arn:aws:iam:123456789012:role/test",
          parameters: [ key: "value" ],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.validateTemplate(_ as ValidateTemplateRequest) >> { null }
    2 * cloudFormationClient.describeStacks(_ as DescribeStacksRequest) >> {
      DescribeStacksResponse.builder().stacks([Stack.builder().stackId("stackId").build()]).build()
    }
    1 * cloudFormationClient.updateStack(_ as UpdateStackRequest) >> {
      throw CloudFormationException.builder().message("No updates are to be performed").build()
    }
  }

  @Unroll
  void "should fail when invalid template"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def cloudFormationClient = Mock(CloudFormationClient)
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: '{"key":"value"}',
          roleARN: "arn:aws:iam:123456789012:role/test",
          parameters: [ key: "value" ],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormationV2(_, _) >> cloudFormationClient
    1 * cloudFormationClient.validateTemplate(_ as ValidateTemplateRequest) >> {
      throw CloudFormationException.builder().message("invalid template").build()
    }
    thrown(CloudFormationException)
  }
}
