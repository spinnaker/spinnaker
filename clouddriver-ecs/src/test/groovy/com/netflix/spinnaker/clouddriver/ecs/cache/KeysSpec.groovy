/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache

import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.*
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.SEPARATOR
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH

class KeysSpec extends Specification {

  def buildKey(String namespace, String account, String region, String identifier) {
    return ID + SEPARATOR + namespace + SEPARATOR + account + SEPARATOR + region + SEPARATOR + identifier
  }

  def buildParsedKey(String account, String region, String namespace, Map keySpecificMap) {
    return [provider: ID, type: namespace, account: account, region: region] << keySpecificMap
  }

  def 'should parse a given key properly'() {
    expect:
    Keys.parse(buildKey(namespace, account, region, identifier as String)) == parsedKey

    where:
    account          | region      | namespace              | identifier                                                                                        | parsedKey
    'test-account-1' | 'us-west-1' | TASKS.ns               | '1dc5c17a-422b-4dc4-b493-371970c6c4d6'                                                            | buildParsedKey(account, region, namespace, [taskId: identifier])
    'test-account-2' | 'us-west-2' | SERVICES.ns            | 'test-stack-detail-v001'                                                                          | buildParsedKey(account, region, namespace, [serviceName: identifier])
    'test-account-3' | 'us-west-3' | ECS_CLUSTERS.ns        | 'test-cluster-1'                                                                                  | buildParsedKey(account, region, namespace, [clusterName: identifier])
    'test-account-4' | 'us-west-4' | CONTAINER_INSTANCES.ns | 'arn:aws:ecs:' + region + ':012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98' | buildParsedKey(account, region, namespace, [containerInstanceArn: identifier])
    'test-account-5' | 'us-west-5' | TASK_DEFINITIONS.ns    | 'arn:aws:ecs:' + region + ':012345678910:task-definition/hello_world:10'                          | buildParsedKey(account, region, namespace, [taskDefinitionArn: identifier])
    'test-account-6' | 'us-west-6' | ALARMS.ns    | 'arn:aws:ecs:' + region + ':012345678910:alarms/14e8cce9-0b16-4af4-bfac-a85f7587aa98'                          | buildParsedKey(account, region, namespace, [alarmArn: identifier])
    'test-account-7' | 'us-west-7' | SCALABLE_TARGETS.ns    | 'service/test-cluster/test-service'                          | buildParsedKey(account, region, namespace, [resource: identifier])
    'test-account-8' | 'us-west-8' | SECRETS.ns             | 'my-secret'                                                                                       | buildParsedKey(account, region, namespace, [secretName: identifier])
    'test-account-9' | 'us-west-9' | SERVICE_DISCOVERY_REGISTRIES.ns | 'srv-123'                                                                                  | buildParsedKey(account, region, namespace, [serviceId: identifier])
  }

  def 'should parse a given iam role key properly'() {
    expect:
    Keys.parse(ID + SEPARATOR + IAM_ROLE.ns + SEPARATOR + account + SEPARATOR + roleName) == [provider: ID, type: IAM_ROLE.ns, account: account, roleName: roleName]

    where:
    account          | roleName
    'test-account-1' | 'iam-role-name-1'
    'test-account-2' | 'iam-role-name-2'
  }

  def 'should generate the proper iam role key'() {
    expect:
    Keys.getIamRoleKey(account, roleName) == ID + SEPARATOR + IAM_ROLE.ns + SEPARATOR + account + SEPARATOR + roleName

    where:
    account          | roleName
    'test-account-1' | 'am-role-name-1'
    'test-account-2' | 'am-role-name-2'
  }

  def 'should generate the proper task key'() {
    expect:
    Keys.getTaskKey(account, region, taskId) == buildKey(TASKS.ns, account, region, taskId)

    where:
    region      | account          | taskId
    'us-west-1' | 'test-account-1' | '1dc5c17a-422b-4dc4-b493-371970c6c4d6'
    'us-west-2' | 'test-account-2' | 'deadbeef-422b-4dc4-b493-371970c6c4d6'
  }

  def 'should generate the proper service key'() {
    expect:
    Keys.getServiceKey(account, region, serviceName) == buildKey(SERVICES.ns, account, region, serviceName)

    where:
    region      | account          | serviceName
    'us-west-1' | 'test-account-1' | '1dc5c17a-422b-4dc4-b493-371970c6c4d6'
    'us-west-2' | 'test-account-2' | 'deadbeef-422b-4dc4-b493-371970c6c4d6'
  }

  def 'should generate the proper cluster key'() {
    expect:
    Keys.getClusterKey(account, region, clusterName) == buildKey(ECS_CLUSTERS.ns, account, region, clusterName)

    where:
    region      | account          | clusterName
    'us-west-1' | 'test-account-1' | 'test-cluster-1'
    'us-west-2' | 'test-account-2' | 'test-cluster-2'
  }

  def 'should generate the proper container instance key'() {
    expect:
    Keys.getContainerInstanceKey(account, region, containerArn) == buildKey(CONTAINER_INSTANCES.ns, account, region, containerArn)

    where:
    region      | account          | containerArn
    'us-west-1' | 'test-account-1' | 'arn:aws:ecs:' + region + ':012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98'
    'us-west-2' | 'test-account-2' | 'arn:aws:ecs:' + region + ':012345678910:container-instance/deadbeef-0b16-4af4-bfac-a85f7587aa98'
  }

  def 'should generate the proper task definition key'() {
    expect:
    Keys.getTaskDefinitionKey(account, region, taskDefArn) == buildKey(TASK_DEFINITIONS.ns, account, region, taskDefArn)

    where:
    region      | account          | taskDefArn
    'us-west-1' | 'test-account-1' | 'arn:aws:ecs:' + region + ':012345678910:task-definition/hello_world:10'
    'us-west-2' | 'test-account-2' | 'arn:aws:ecs:' + region + ':012345678910:task-definition/hello_world:20'
  }

  def 'should generate the proper task health key'() {
    expect:
    Keys.getTaskHealthKey(account, region, taskId) == buildKey(HEALTH.ns, account, region, taskId)

    where:
    region      | account          | taskId
    'us-west-1' | 'test-account-1' | '1dc5c17a-422b-4dc4-b493-371970c6c4d6'
    'us-west-2' | 'test-account-2' | 'deadbeef-422b-4dc4-b493-371970c6c4d6'
  }

  def 'should generate the proper scalable target key'() {
    expect:
    Keys.getScalableTargetKey(account, region, taskId) == buildKey(SCALABLE_TARGETS.ns, account, region, taskId)

    where:
    region      | account          | taskId
    'us-west-1' | 'test-account-1' | 'service/test-cluster/test-service'
    'us-west-2' | 'test-account-2' | 'service/mycluster/myservice'
  }

  def 'should generate the proper secret key'() {
    expect:
    Keys.getSecretKey(account, region, secretName) == buildKey(SECRETS.ns, account, region, secretName)

    where:
    region      | account          | secretName
    'us-west-1' | 'test-account-1' | 'my-first-secret'
    'us-west-2' | 'test-account-2' | 'my-second-secret'
  }

  def 'should generate the proper service discovery key'() {
    expect:
    Keys.getServiceDiscoveryRegistryKey(account, region, serviceId) == buildKey(SERVICE_DISCOVERY_REGISTRIES.ns, account, region, serviceId)

    where:
    region      | account          | serviceId
    'us-west-1' | 'test-account-1' | 'my-first-service'
    'us-west-2' | 'test-account-2' | 'my-second-service'
  }
}
