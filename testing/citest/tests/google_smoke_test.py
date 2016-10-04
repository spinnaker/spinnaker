# Copyright 2015 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Smoke test to see if Spinnaker can interoperate with Google Cloud Platform.

See testable_service/integration_test.py and spinnaker_testing/spinnaker.py
for more details.

The smoke test will use ssh to peek at the spinnaker configuration
to determine the managed project it should verify, and to determine
the spinnaker account name to use when sending it commands.

Sample Usage:
    Assuming you have created $PASSPHRASE_FILE (which you should chmod 400)
    and $CITEST_ROOT points to the root directory of this repository
    (which is . if you execute this from the root)

  PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
    python $CITEST_ROOT/spinnaker/spinnaker_system/google_smoke_test.py \
    --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
    --gce_project=$PROJECT \
    --gce_zone=$ZONE \
    --gce_instance=$INSTANCE
or
  PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
    python $CITEST_ROOT/spinnaker/spinnaker_system/google_smoke_test.py \
    --native_hostname=host-running-smoke-test
    --managed_gce_project=$PROJECT \
    --test_gce_zone=$ZONE
"""

# Standard python modules.
import sys

# citest modules.
import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
from google_http_lb_upsert_scenario import GoogleHttpLoadBalancerTestScenario

import spinnaker_testing as sk
import spinnaker_testing.gate as gate
import citest.base


class GoogleSmokeTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the smoke test.

  This scenario defines the different test operations.
  We're going to:
    Create a Spinnaker Application
    Create a Load Balancer
    Create a Server Group
    Delete each of the above (in reverse order)
  """

  MINIMUM_PROJECT_QUOTA = {
      'INSTANCE_TEMPLATES': 1,
      'HEALTH_CHECKS': 1,
      'FORWARDING_RULES': 1,
      'IN_USE_ADDRESSES': 2,
      'TARGET_POOLS': 1,
  }

  MINIMUM_REGION_QUOTA = {
      'CPUS': 2,
      'IN_USE_ADDRESSES': 2,
      'INSTANCE_GROUP_MANAGERS': 1,
      'INSTANCES': 2,
  }

  @classmethod
  def new_agent(cls, bindings):
    """Implements citest.service_testing.AgentTestScenario.new_agent."""
    return gate.new_agent(bindings)

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(GoogleSmokeTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    """
    super(GoogleSmokeTestScenario, self).__init__(bindings, agent)

    bindings = self.bindings

    self.__lb_detail = 'lb'
    self.__lb_name = '{app}-{stack}-{detail}'.format(
        app=bindings['TEST_APP'], stack=bindings['TEST_STACK'],
        detail=self.__lb_detail)

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']

  def create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.TEST_APP),
        contract=contract)

  def delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            bindings=self.bindings, application=self.TEST_APP),
        contract=contract)

  def upsert_load_balancer(self):
    """Creates OperationContract for upsertLoadBalancer.

    Calls Spinnaker's upsertLoadBalancer with a configuration, then verifies
    that the expected resources and configurations are visible on GCE. See
    the contract builder for more info on what the expectations are.
    """
    bindings = self.bindings
    target_pool_name = '{0}/targetPools/{1}-tp'.format(
        bindings['TEST_GCE_REGION'], self.__lb_name)

    spec = {
        'checkIntervalSec': 9,
        'healthyThreshold': 3,
        'unhealthyThreshold': 5,
        'timeoutSec': 2,
        'port': 80
    }

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'gce',
            'provider': 'gce',
            'stack': bindings['TEST_STACK'],
            'detail': self.__lb_detail,
            'credentials': bindings['SPINNAKER_GOOGLE_ACCOUNT'],
            'region': bindings['TEST_GCE_REGION'],
            'ipProtocol': 'TCP',
            'portRange': spec['port'],
            'loadBalancerName': self.__lb_name,
            'healthCheck': {
                'port': spec['port'],
                'timeoutSec': spec['timeoutSec'],
                'checkIntervalSec': spec['checkIntervalSec'],
                'healthyThreshold': spec['healthyThreshold'],
                'unhealthyThreshold': spec['unhealthyThreshold'],
            },
            'type': 'upsertLoadBalancer',
            'availabilityZones': {bindings['TEST_GCE_REGION']: []},
            'user': '[anonymous]'
        }],
        description='Create Load Balancer: ' + self.__lb_name,
        application=self.TEST_APP)

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Health Check Added',
                                retryable_for_secs=30)
     .list_resource('httpHealthChecks')
     .contains_pred_list(
         [jp.PathContainsPredicate('name', '%s-hc' % self.__lb_name),
          jp.DICT_SUBSET(spec)]))
    (builder.new_clause_builder('Target Pool Added',
                                retryable_for_secs=30)
     .list_resource('targetPools')
     .contains_path_value('name', '%s-tp' % self.__lb_name))
    (builder.new_clause_builder('Forwarding Rules Added',
                                retryable_for_secs=30)
     .list_resource('forwardingRules')
     .contains_pred_list([
          jp.PathContainsPredicate('name', self.__lb_name),
          jp.PathContainsPredicate('target', target_pool_name)]))

    return st.OperationContract(
        self.new_post_operation(
            title='upsert_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def delete_load_balancer(self):
    """Creates OperationContract for deleteLoadBalancer.

    To verify the operation, we just check that the GCP resources
    created by upsert_load_balancer are no longer visible on GCP.
    """
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'deleteLoadBalancer',
            'cloudProvider': 'gce',
            'loadBalancerName': self.__lb_name,
            'region': bindings['TEST_GCE_REGION'],
            'regions': [bindings['TEST_GCE_REGION']],
            'credentials': bindings['SPINNAKER_GOOGLE_ACCOUNT'],
            'user': '[anonymous]'
        }],
        description='Delete Load Balancer: {0} in {1}:{2}'.format(
            self.__lb_name,
            bindings['SPINNAKER_GOOGLE_ACCOUNT'],
            bindings['TEST_GCE_REGION']),
        application=self.TEST_APP)

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Health Check Removed', retryable_for_secs=30)
     .list_resource('httpHealthChecks')
     .excludes_path_value('name', '%s-hc' % self.__lb_name))
    (builder.new_clause_builder('TargetPool Removed')
     .list_resource('targetPools')
     .excludes_path_value('name', '%s-tp' % self.__lb_name))
    (builder.new_clause_builder('Forwarding Rule Removed')
     .list_resource('forwardingRules')
     .excludes_path_value('name', self.__lb_name))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def create_server_group(self):
    """Creates OperationContract for createServerGroup.

    To verify the operation, we just check that Managed Instance Group
    for the server was created.
    """
    bindings = self.bindings

    # Spinnaker determines the group name created,
    # which will be the following:
    group_name = '{app}-{stack}-v000'.format(
        app=self.TEST_APP, stack=bindings['TEST_STACK'])

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'gce',
            'application': self.TEST_APP,
            'credentials': bindings['SPINNAKER_GOOGLE_ACCOUNT'],
            'strategy':'',
            'capacity': {'min':2, 'max':2, 'desired':2},
            'targetSize': 2,
            'image': bindings['TEST_GCE_IMAGE_NAME'],
            'zone': bindings['TEST_GCE_ZONE'],
            'stack': bindings['TEST_STACK'],
            'instanceType': 'f1-micro',
            'type': 'createServerGroup',
            'loadBalancers': [self.__lb_name],
            'availabilityZones': {
                bindings['TEST_GCE_REGION']: [bindings['TEST_GCE_ZONE']]
            },
            'instanceMetadata': {
                'startup-script': ('sudo apt-get update'
                                   ' && sudo apt-get install apache2 -y'),
                'load-balancer-names': self.__lb_name
            },
            'account': bindings['SPINNAKER_GOOGLE_ACCOUNT'],
            'authScopes': ['compute'],
            'user': '[anonymous]'
        }],
        description='Create Server Group in ' + group_name,
        application=self.TEST_APP)

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Managed Instance Group Added',
                                retryable_for_secs=30)
     .inspect_resource('instanceGroupManagers', group_name)
     .contains_path_eq('targetSize', 2))

    return st.OperationContract(
        self.new_post_operation(
            title='create_server_group', data=payload, path='tasks'),
        contract=builder.build())

  def delete_server_group(self):
    """Creates OperationContract for deleteServerGroup.

    To verify the operation, we just check that the GCP managed instance group
    is no longer visible on GCP (or is in the process of terminating).
    """
    bindings = self.bindings
    group_name = '{app}-{stack}-v000'.format(
        app=self.TEST_APP, stack=bindings['TEST_STACK'])

    # TODO(ttomsu): Change this back from asgName to serverGroupName
    #               once it is fixed in orca.
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'gce',
            'serverGroupName': group_name,
            'region': bindings['TEST_GCE_REGION'],
            'zone': bindings['TEST_GCE_ZONE'],
            'asgName': group_name,
            'type': 'destroyServerGroup',
            'regions': [bindings['TEST_GCE_REGION']],
            'zones': [bindings['TEST_GCE_ZONE']],
            'credentials': bindings['SPINNAKER_GOOGLE_ACCOUNT'],
            'user': '[anonymous]'
        }],
        application=self.TEST_APP,
        description='DestroyServerGroup: ' + group_name)

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Managed Instance Group Removed')
     .inspect_resource('instanceGroupManagers', group_name,
                       no_resource_ok=True)
     .contains_path_eq('targetSize', 0))

    (builder.new_clause_builder('Instances Are Removed',
                                retryable_for_secs=30)
     .list_resource('instances')
     .excludes_path_value('name', group_name))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_server_group', data=payload, path='tasks'),
        contract=builder.build())


class GoogleSmokeTest(st.AgentTestCase):
  """The test fixture for the SmokeTest.

  This is implemented using citest OperationContract instances that are
  created by the GoogleSmokeTestScenario.
  """
  # pylint: disable=missing-docstring

  @staticmethod
  def setUpClass():
    runner = citest.base.TestRunner.global_runner()
    scenario = runner.get_shared_data(GoogleSmokeTestScenario)
    managed_region = runner.bindings['TEST_GCE_REGION']
    title = 'Check Quota for {0}'.format(scenario.__class__.__name__)

    verify_results = gcp.verify_quota(
        title,
        scenario.gcp_observer,
        project_quota=GoogleSmokeTestScenario.MINIMUM_PROJECT_QUOTA,
        regions=[(managed_region,
                  GoogleSmokeTestScenario.MINIMUM_REGION_QUOTA)])
    if not verify_results:
      raise RuntimeError('Insufficient Quota: {0}'.format(verify_results))

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        GoogleSmokeTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b_upsert_load_balancer(self):
    self.run_test_case(self.scenario.upsert_load_balancer())

  def test_c_create_server_group(self):
    # We'll permit this to timeout for now
    # because it might be waiting on confirmation
    # but we'll continue anyway because side effects
    # should have still taken place.
    self.run_test_case(self.scenario.create_server_group(), timeout_ok=True)

  def test_d_upsert_http_load_balancer(self):
    self.run_test_case(GoogleHttpLoadBalancerTestScenario(self.scenario.bindings)
                       .upsert_min_load_balancer())

  def test_e_delete_http_load_balancer(self):
    self.run_test_case(GoogleHttpLoadBalancerTestScenario(self.scenario.bindings)
                       .delete_http_load_balancer())

  def test_x_delete_server_group(self):
    self.run_test_case(self.scenario.delete_server_group(), max_retries=5)

  def test_y_delete_load_balancer(self):
    self.run_test_case(self.scenario.delete_load_balancer(),
                       max_retries=5)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_STACK': str(GoogleSmokeTestScenario.DEFAULT_TEST_ID),
      'TEST_APP': 'gcpsmoketest' + GoogleSmokeTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[GoogleSmokeTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[GoogleSmokeTest])


if __name__ == '__main__':
  sys.exit(main())
