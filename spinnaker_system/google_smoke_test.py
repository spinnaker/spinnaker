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
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate


class GoogleSmokeTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the smoke test.

  This scenario defines the different test operations.
  We're going to:
    Create a Spinnaker Application
    Create a Load Balancer
    Create a Server Group
    Delete each of the above (in reverse order)
  """

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
    super(GoogleSmokeTestScenario, cls).initArgumentParser(parser, defaults=defaults)

    defaults = defaults or {}
    parser.add_argument(
        '--test_component_detail',
        default='fe',
        help='Refinement for component name to create.')

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    """
    super(GoogleSmokeTestScenario, self).__init__(bindings, agent)

    bindings = self.bindings
    bindings['TEST_APP_COMPONENT_NAME'] = (
        '{app}-{stack}-{detail}'.format(
            app=bindings['TEST_APP'],
            stack=bindings['TEST_STACK'],
            detail=bindings['TEST_COMPONENT_DETAIL']))

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
    load_balancer_name = bindings['TEST_APP_COMPONENT_NAME']
    target_pool_name = '{0}/targetPools/{1}-tp'.format(
        bindings['TEST_GCE_REGION'], load_balancer_name)

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
            'detail': bindings['TEST_COMPONENT_DETAIL'],
            'credentials': bindings['GCE_CREDENTIALS'],
            'region': bindings['TEST_GCE_REGION'],
            'ipProtocol': 'TCP',
            'portRange': spec['port'],
            'loadBalancerName': load_balancer_name,
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
        description='Create Load Balancer: ' + load_balancer_name,
        application=self.TEST_APP)

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Health Check Added',
                                retryable_for_secs=30)
     .list_resources('http-health-checks')
     .contains_pred_list(
         [jc.PathContainsPredicate('name', '%s-hc' % load_balancer_name),
          jc.DICT_SUBSET(spec)]))
    (builder.new_clause_builder('Target Pool Added',
                                retryable_for_secs=30)
     .list_resources('target-pools')
     .contains_path_value('name', '%s-tp' % load_balancer_name))
    (builder.new_clause_builder('Forwarding Rules Added',
                                retryable_for_secs=30)
     .list_resources('forwarding-rules')
     .contains_pred_list([
          jc.PathContainsPredicate('name', load_balancer_name),
          jc.PathContainsPredicate('target', target_pool_name)]))

    return st.OperationContract(
        self.new_post_operation(
            title='upsert_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def delete_load_balancer(self):
    """Creates OperationContract for deleteLoadBalancer.

    To verify the operation, we just check that the GCP resources
    created by upsert_load_balancer are no longer visible on GCP.
    """
    load_balancer_name = self.bindings['TEST_APP_COMPONENT_NAME']
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'deleteLoadBalancer',
            'cloudProvider': 'gce',
            'loadBalancerName': load_balancer_name,
            'region': bindings['TEST_GCE_REGION'],
            'regions': [bindings['TEST_GCE_REGION']],
            'credentials': bindings['GCE_CREDENTIALS'],
            'user': '[anonymous]'
        }],
        description='Delete Load Balancer: {0} in {1}:{2}'.format(
            load_balancer_name,
            bindings['GCE_CREDENTIALS'],
            bindings['TEST_GCE_REGION']),
        application=self.TEST_APP)

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Health Check Removed', retryable_for_secs=30)
     .list_resources('http-health-checks')
     .excludes_path_value('name', '%s-hc' % load_balancer_name))
    (builder.new_clause_builder('TargetPool Removed')
     .list_resources('target-pools')
     .excludes_path_value('name', '%s-tp' % load_balancer_name))
    (builder.new_clause_builder('Forwarding Rule Removed')
     .list_resources('forwarding-rules')
     .excludes_path_value('name', load_balancer_name))

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
            'credentials': bindings['GCE_CREDENTIALS'],
            'strategy':'',
            'capacity': {'min':2, 'max':2, 'desired':2},
            'targetSize': 2,
            'image': bindings['TEST_GCE_IMAGE_NAME'],
            'zone': bindings['TEST_GCE_ZONE'],
            'stack': bindings['TEST_STACK'],
            'instanceType': 'f1-micro',
            'type': 'createServerGroup',
            'loadBalancers': [bindings['TEST_APP_COMPONENT_NAME']],
            'availabilityZones': {
                bindings['TEST_GCE_REGION']: [bindings['TEST_GCE_ZONE']]
            },
            'instanceMetadata': {
                'startup-script': ('sudo apt-get update'
                                   ' && sudo apt-get install apache2 -y'),
                'load-balancer-names': bindings['TEST_APP_COMPONENT_NAME']
            },
            'account': bindings['GCE_CREDENTIALS'],
            'authScopes': ['compute'],
            'user': '[anonymous]'
        }],
        description='Create Server Group in ' + group_name,
        application=self.TEST_APP)

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Managed Instance Group Added',
                                retryable_for_secs=30)
     .inspect_resource('managed-instance-groups', group_name)
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
            'credentials': bindings['GCE_CREDENTIALS'],
            'user': '[anonymous]'
        }],
        application=self.TEST_APP,
        description='DestroyServerGroup: ' + group_name)

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Managed Instance Group Removed')
     .inspect_resource('managed-instance-groups', group_name,
                       no_resource_ok=True)
     .contains_path_eq('targetSize', 0))

    (builder.new_clause_builder('Instances Are Removed',
                                retryable_for_secs=30)
     .list_resources('instances')
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

  return st.ScenarioTestRunner.main(
      GoogleSmokeTestScenario,
      default_binding_overrides=defaults,
      test_case_list=[GoogleSmokeTest])


if __name__ == '__main__':
  sys.exit(main())
