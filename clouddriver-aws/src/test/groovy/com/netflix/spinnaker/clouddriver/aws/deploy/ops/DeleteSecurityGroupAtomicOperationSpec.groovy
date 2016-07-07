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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.DeleteSecurityGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteSecurityGroupDescription
import spock.lang.Specification

class DeleteSecurityGroupAtomicOperationSpec extends Specification {
  private static final String ACCOUNT = "test"

  def credz = Stub(NetflixAmazonCredentials) {
    getName() >> ACCOUNT
  }

  def ec2 = Mock(AmazonEC2)
  def amazonClientProvider = Mock(AmazonClientProvider)

  Task task = new DefaultTask("task1")

  def setup() {
    TaskRepository.threadLocalTask.set(task)
  }

  void "should perform deletion"() {
    def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", regions: ["us-east-1"], credentials: credz)
    def op = new DeleteSecurityGroupAtomicOperation(description)
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
    1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
            securityGroups: [
                    new SecurityGroup(groupName: "foo", groupId: "123")
            ]
    )
    1 * ec2.deleteSecurityGroup(new DeleteSecurityGroupRequest(groupId: '123'))
    0 * _

    and:
    task.history*.status == [
            "Creating task task1",
            "Initializing Delete Security Group Operation...",
            "Deleting foo in us-east-1 for test.",
            "Done deleting foo in us-east-1 for test."
    ]
  }

  void "should perform deletion for non VPC"() {
    def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", regions: ["us-east-1"], credentials: credz)
    def op = new DeleteSecurityGroupAtomicOperation(description)
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
    1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
            securityGroups: [
                    new SecurityGroup(groupName: "foo", groupId: "123", vpcId: null),
                    new SecurityGroup(groupName: "foo", groupId: "456", vpcId: "vpc1")
            ]
    )
    1 * ec2.deleteSecurityGroup(new DeleteSecurityGroupRequest(groupId: '123'))
    0 * _

    and:
    task.history*.status == [
            "Creating task task1",
            "Initializing Delete Security Group Operation...",
            "Deleting foo in us-east-1 for test.",
            "Done deleting foo in us-east-1 for test."
    ]
  }

  void "should perform deletion for VPC"() {
    def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", vpcId: "vpc1", regions: ["us-east-1"], credentials: credz)
    def op = new DeleteSecurityGroupAtomicOperation(description)
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
    1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
            securityGroups: [
                    new SecurityGroup(groupName: "foo", groupId: "123", vpcId: null),
                    new SecurityGroup(groupName: "foo", groupId: "456", vpcId: "vpc1")
            ]
    )
    1 * ec2.deleteSecurityGroup(new DeleteSecurityGroupRequest(groupId: '456'))
    0 * _

    and:
    task.history*.status == [
            "Creating task task1",
            "Initializing Delete Security Group Operation...",
            "Deleting foo in us-east-1 vpc1 for test.",
            "Done deleting foo in us-east-1 vpc1 for test."
    ]
  }

  void "should be idempotent and not fail for deletion when no security group exists"() {
    def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", regions: ["us-east-1"], credentials: credz)
    def op = new DeleteSecurityGroupAtomicOperation(description)
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
    1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
            securityGroups: []
    )
    0 * _

    and:
    task.history*.status == [
            "Creating task task1",
            "Initializing Delete Security Group Operation...",
            "There is no foo in us-east-1 for test."
    ]
  }

  void "should be idempotent and not fail for deletion when no security group exists with that name"() {
      def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", regions: ["us-east-1"], credentials: credz)
      def op = new DeleteSecurityGroupAtomicOperation(description)
      op.amazonClientProvider = amazonClientProvider

      when:
      op.operate([])

      then:
      1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
      1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
              securityGroups: [
                      new SecurityGroup(groupName: "bar", groupId: "123", vpcId: null)
              ]
      )
      0 * _

      and:
      task.history*.status == [
              "Creating task task1",
              "Initializing Delete Security Group Operation...",
              "There is no foo in us-east-1 for test."
      ]
  }

  void "should be idempotent and not fail for deletion when no security group exists in VPC"() {
    def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", vpcId: "vpc1", regions: ["us-east-1"], credentials: credz)
    def op = new DeleteSecurityGroupAtomicOperation(description)
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
    1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
            securityGroups: [
                    new SecurityGroup(groupName: "foo", groupId: "123", vpcId: null)
            ]
    )
    0 * _

    and:
    task.history*.status == [
            "Creating task task1",
            "Initializing Delete Security Group Operation...",
            "There is no foo in us-east-1 vpc1 for test."
    ]
  }

  void "should be idempotent and not fail for deletion when the security group for the ID no longer exists"() {
      def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", regions: ["us-east-1"], credentials: credz)
      def op = new DeleteSecurityGroupAtomicOperation(description)
      op.amazonClientProvider = amazonClientProvider

      when:
      op.operate([])

      then:
      1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
      1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
              securityGroups: [
                      new SecurityGroup(groupName: "foo", groupId: "123", vpcId: null)
              ]
      )
      1 * ec2.deleteSecurityGroup(new DeleteSecurityGroupRequest(groupId: '123')) >> {
          def e = new AmazonServiceException("The security group '123' does not exist")
          e.errorCode = "InvalidGroup.NotFound"
          throw e
      }
      0 * _

      and:
      task.history*.status == [
              "Creating task task1",
              "Initializing Delete Security Group Operation...",
              "Deleting foo in us-east-1 for test.",
              "Done deleting foo in us-east-1 for test."
      ]
  }

  void "should fail on delete for errors other than not found"() {
      def description = new DeleteSecurityGroupDescription(securityGroupName: "foo", regions: ["us-east-1"], credentials: credz)
      def op = new DeleteSecurityGroupAtomicOperation(description)
      op.amazonClientProvider = amazonClientProvider

      when:
      op.operate([])

      then:
      thrown(AmazonServiceException)

      and:
      1 * amazonClientProvider.getAmazonEC2(credz, 'us-east-1', true) >> ec2
      1 * ec2.describeSecurityGroups() >> new DescribeSecurityGroupsResult(
              securityGroups: [
                      new SecurityGroup(groupName: "foo", groupId: "123", vpcId: null)
              ]
      )
      1 * ec2.deleteSecurityGroup(new DeleteSecurityGroupRequest(groupId: '123')) >> {
          def e = new AmazonServiceException("No idea what happened here, but you should know about it!")
          e.errorCode = "Something.Seriously.BAD"
          throw e
      }
      0 * _

      and:
      task.history*.status == [
              "Creating task task1",
              "Initializing Delete Security Group Operation...",
              "Deleting foo in us-east-1 for test.",
              "No idea what happened here, but you should know about it!"
      ]
  }
}
