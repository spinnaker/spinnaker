/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.converter.Converter
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Unroll

class DetermineSourceServerGroupTaskSpec extends Specification {

  void 'should include source in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account          : account,
      application      : 'foo',
      availabilityZones: [(region): []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> new StageData.Source(account: account, region: region, asgName: asgName, serverGroupName: asgName)

    and:
    result.context.source.account == account
    result.context.source.region == region
    result.context.source.asgName == asgName
    result.context.source.serverGroupName == asgName
    result.context.source.useSourceCapacity == null

    where:
    account = 'test'
    region = 'us-east-1'
    asgName = 'foo-test-v000'
  }

  void 'should useSourceCapacity from context if not provided in Source'() {
    given:
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [useSourceCapacity: contextUseSourceCapacity, account: account, application: application, availabilityZones: [(region): []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(stage) >> new StageData.Source(account: account, region: region, asgName: "$application-v001", useSourceCapacity: sourceUseSourceCapacity)

    and:
    result.context.source.useSourceCapacity == expectedUseSourceCapacity

    where:
    account = 'test'
    region = 'us-east-1'
    application = 'foo'


    contextUseSourceCapacity | sourceUseSourceCapacity | expectedUseSourceCapacity
    null | null | null
    null | true | true
    false | true | true
    false | null | false
    true | null | true
  }

  void 'should NOT fail if there is region and no availabilityZones in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account    : 'test',
      region     : 'us-east-1',
      application: 'foo'])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    notThrown(IllegalStateException)
  }

  void 'should NOT fail if there is source and no region and no availabilityZones in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account    : 'test',
      source     : [region: 'us-east-1', account: 'test', asgName: 'foo-test-v000'],
      application: 'foo'])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    notThrown(IllegalStateException)
  }

  void 'should fail if there is no availabilityZones and no region in context'() {
    given:
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account    : 'test',
      application: 'foo'])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    def ex = thrown(IllegalStateException)
    ex.message == "No 'source' or 'region' or 'availabilityZones' in stage context"
  }

  void 'should retry on exception from source resolver'() {
    given:
    Exception expected = new Exception('kablamo')
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account          : 'test',
      application      : 'foo',
      availabilityZones: ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> { throw expected }

    result.status == ExecutionStatus.RUNNING
    result.context.lastException.contains(expected.message)
    result.context.attempt == 2
    result.context.consecutiveNotFound == 0
  }

  void 'should reset consecutiveNotFound on non 404 exception'() {
    given:
    Exception expected = new Exception('kablamo')
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account            : 'test',
      application        : 'foo',
      consecutiveNotFound: 3,
      availabilityZones  : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> { throw expected }

    result.status == ExecutionStatus.RUNNING
    result.context.lastException.contains(expected.message)
    result.context.attempt == 2
    result.context.consecutiveNotFound == 0
  }

  void 'should fail after MAX_ATTEMPTS'() {
    Exception expected = new Exception('kablamo')
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account          : 'test',
      application      : 'foo',
      attempt          : DetermineSourceServerGroupTask.MAX_ATTEMPTS,
      availabilityZones: ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> { throw expected }

    def ex = thrown(IllegalStateException)
    ex.cause.is expected
  }

  @Unroll
  void "should be #status after #attempt consecutive missing source with useSourceCapacity #useSourceCapacity"() {
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account            : 'test',
      application        : 'foo',
      useSourceCapacity  : useSourceCapacity,
      attempt            : attempt,
      consecutiveNotFound: attempt,
      availabilityZones  : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def taskResult = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> null

    taskResult.status == status

    where:
    status                    | useSourceCapacity | attempt
    ExecutionStatus.SUCCEEDED | false             | 1
    ExecutionStatus.RUNNING   | true              | 1
  }

  def 'should throw exception if no source resolved and useSourceCapacity requested after attempts limit is reached'() {
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account            : 'test',
      application        : 'foo',
      useSourceCapacity  : true,
      attempt            : DetermineSourceServerGroupTask.MIN_CONSECUTIVE_404,
      consecutiveNotFound: DetermineSourceServerGroupTask.MIN_CONSECUTIVE_404,
      availabilityZones  : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> null
    thrown IllegalStateException
  }

  @Unroll
  def 'should be #status after #attempt consecutive missing source with useSourceCapacity and preferSourceCapacity and capacity context #capacity'() {
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account             : 'test',
      application         : 'foo',
      useSourceCapacity   : true,
      preferSourceCapacity: true,
      attempt             : attempt,
      consecutiveNotFound : attempt,
      capacity            : capacity,
      availabilityZones   : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def taskResult = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> null

    taskResult.status == status

    where:
    status                    | capacity | attempt
    ExecutionStatus.RUNNING   | null     | 1
    ExecutionStatus.SUCCEEDED | [min: 0] | DetermineSourceServerGroupTask.MIN_CONSECUTIVE_404 - 1
  }

  def 'should throw exception if useSourceCapacity and preferSourceCapacity set, but source not found and no capacity specified'() {
    Stage stage = new Stage<>(new Pipeline(), 'deploy', 'deploy', [
      account             : 'test',
      application         : 'foo',
      useSourceCapacity   : true,
      preferSourceCapacity: true,
      attempt             : DetermineSourceServerGroupTask.MIN_CONSECUTIVE_404,
      consecutiveNotFound : DetermineSourceServerGroupTask.MIN_CONSECUTIVE_404,
      availabilityZones   : ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> null
    thrown IllegalStateException
  }

  def 'should fail if no source resolved and useSourceCapacity requested'() {
    Stage stage = new Stage<>(new Pipeline("orca"), 'deploy', 'deploy', [
      account          : 'test',
      application      : 'foo',
      useSourceCapacity: true,
      availabilityZones: ['us-east-1': []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> null

    result.status == ExecutionStatus.RUNNING
    result.context.lastException.contains("Cluster is configured to copy capacity from the current server group")
  }
}
