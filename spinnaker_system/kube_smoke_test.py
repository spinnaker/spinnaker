# Copyright 2016 Google Inc. All Rights Reserved.
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
Smoke test to see if Spinnaker can interoperate with Kubernetes.

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
    python $CITEST_ROOT/spinnaker/spinnaker_system/kube_smoke_test.py \
    --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
    --gce_project=$PROJECT \
    --gce_zone=$ZONE \
    --gce_instance=$INSTANCE
or
  PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
    python $CITEST_ROOT/spinnaker/spinnaker_system/kube_smoke_test.py \
    --native_hostname=host-running-smoke-test
"""

# Standard python modules.
import sys

# citest modules.
import citest.kube_testing as kube
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate


class KubeSmokeTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the smoke test.

  This scenario defines the different test operations.
  We're going to:
    Create a Spinnaker Application
    Create a Spinnaker Load Balancer
    Create a Spinnaker Server Group
    Delete each of the above (in reverse order)
  """

  @classmethod
  def new_agent(cls, bindings):
    """Implements citest.service_testing.AgentTestScenario.new_agent."""
    agent = gate.new_agent(bindings)
    agent.default_max_wait_secs = 70
    return agent

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(KubeSmokeTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    """
    super(KubeSmokeTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    optional_stack = ('-{0}'.format(bindings['TEST_STACK'])
                      if bindings['TEST_STACK']
                      else '')

    # No detail because name length is restricted too much to afford one!
    self.__lb_detail = ''
    self.__lb_name = '{app}{optional_stack}'.format(
        app=bindings['TEST_APP'],
        optional_stack=optional_stack)

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
    that the expected resources and configurations are visible on Kubernetes.
    See the contract builder for more info on what the expectations are.
    """
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'availabilityZones': {'default': ['default']},
            'provider': 'kubernetes',
            'stack': bindings['TEST_STACK'],
            'detail': self.__lb_detail,
            'serviceType': 'ClusterIP',
            'account': bindings['KUBE_CREDENTIALS'],
            'namespace': 'default',
            'ports': [{'protocol':'TCP', 'port':80, 'name':'http'}],
            'externalIps': [],
            'sessionAffinity': 'None',
            'clusterIp': '',
            'loadBalancerIp': '',
            'name': self.__lb_name,
            'healthCheck': ':undefined',  # deck has this leading ':'
            'type': 'upsertLoadBalancer',
            'securityGroups': None,
            'user': '[anonymous]'
        }],
        description='Create Load Balancer: ' + self.__lb_name,
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Service Added', retryable_for_secs=15)
     .get_resources('svc')
     .contains_path_value('items/metadata/name', self.__lb_name))

    return st.OperationContract(
        self.new_post_operation(
            title='upsert_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def delete_load_balancer(self):
    """Creates OperationContract for deleteLoadBalancer.

    To verify the operation, we just check that the Kubernetes resources
    created by upsert_load_balancer are no longer visible in the cluster.
    """
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'deleteLoadBalancer',
            'cloudProvider': 'kubernetes',
            'loadBalancerName': self.__lb_name,
            'namespace': 'default',
            'account': bindings['KUBE_CREDENTIALS'],
            'credentials': bindings['KUBE_CREDENTIALS'],
            'regions': ['default'],
            'user': '[anonymous]'
        }],
        description='Delete Load Balancer: {0} in {1}'.format(
            self.__lb_name,
            bindings['KUBE_CREDENTIALS']),
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Service Removed', retryable_for_secs=15)
     .get_resources('svc', no_resource_ok=True))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def create_server_group(self):
    """Creates OperationContract for createServerGroup.

    To verify the operation, we just check that the server group was created.
    """
    bindings = self.bindings

    # Spinnaker determines the group name created,
    # which will be the following:
    group_name = '{app}-{stack}-v000'.format(
        app=self.TEST_APP, stack=bindings['TEST_STACK'])

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'application': self.TEST_APP,
            'account': bindings['KUBE_CREDENTIALS'],
            'strategy':'',
            'targetSize': 2,
            'containers': [{
                'name': 'librarynginx',
                'imageDescription': {
                    'repository': 'library/nginx',
                    'tag': 'stable',
                    'imageId': 'index.docker.io/library/nginx:stable',
                    'registry': 'index.docker.io'
                },
                'requests': {'memory':None, 'cpu':None},
                'limits': {'memory':None, 'cpu':None},
                'ports':[{'name':'http', 'containerPort':80,
                          'protocol':'TCP', 'hostPort':None, 'hostIp':None}]
            }],
            'stack': bindings['TEST_STACK'],
            # TODO(ewiseblatt): 20160316
            # We cannot create a service yet, so test without one.
            # 'loadBalancers': [self.__lb_name],
            'type': 'createServerGroup',
            'region': 'default',
            'user': '[anonymous]'
        }],
        description='Create Server Group in ' + group_name,
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Replication Controller Added',
                                retryable_for_secs=15)
     .get_resources('rc', extra_args=[group_name])
     .contains_path_eq('spec/replicas', 2))

    return st.OperationContract(
        self.new_post_operation(
            title='create_server_group', data=payload, path='tasks'),
        contract=builder.build())

  def delete_server_group(self):
    """Creates OperationContract for deleteServerGroup.

    To verify the operation, we just check that the Kubernetes container
    is no longer visible (or is in the process of terminating).
    """
    bindings = self.bindings
    group_name = '{app}-{stack}-v000'.format(
        app=self.TEST_APP, stack=bindings['TEST_STACK'])

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'type': 'destroyServerGroup',
            'account': bindings['KUBE_CREDENTIALS'],
            'credentials': bindings['KUBE_CREDENTIALS'],
            'user': '[anonymous]',
            'serverGroupName': group_name,
            'asgName': group_name,
            'regions': ['default'],
            'region': 'default',
            'zones': ['default'],
            'interestingHealthProviderNames': ['KubernetesService']
        }],
        application=self.TEST_APP,
        description='Destroy Server Group: ' + group_name)

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Replication Controller Removed')
     .get_resources('rc', extra_args=[group_name],
                    no_resource_ok=True)
     .contains_path_eq('targetSize', 0))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_server_group', data=payload, path='tasks'),
        contract=builder.build())


class KubeSmokeTest(st.AgentTestCase):
  """The test fixture for the KubeSmokeTest.

  This is implemented using citest OperationContract instances that are
  created by the KubeSmokeTestScenario.
  """
  # pylint: disable=missing-docstring

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  # TODO(ewiseblatt): 20160316
  # There is a server side bug preventing this from working, so
  # leaving it disabled for now.
  def test_b_upsert_load_balancer(self):
    self.run_test_case(self.scenario.upsert_load_balancer())

  def test_c_create_server_group(self):
    self.run_test_case(self.scenario.create_server_group(),
                       max_retries=1,
                       timeout_ok=True)

  def test_x_delete_server_group(self):
    self.run_test_case(self.scenario.delete_server_group(), max_retries=2)

  def test_y_delete_load_balancer(self):
    self.run_test_case(self.scenario.delete_load_balancer(),
                       max_retries=2)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_STACK': 'tst',
      'TEST_APP': 'kubsmok' + KubeSmokeTestScenario.DEFAULT_TEST_ID
  }

  return st.ScenarioTestRunner.main(
      KubeSmokeTestScenario,
      default_binding_overrides=defaults,
      test_case_list=[KubeSmokeTest])


if __name__ == '__main__':
  sys.exit(main())
