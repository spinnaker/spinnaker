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
Integration test to see if the image promotion process is working for the
Spinnaker Kubernetes integration.

See testable_service/integration_test.py and spinnaker_testing/spinnaker.py
for more details.

The test will use ssh to peek at the spinnaker configuration
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
import spinnaker_testing.frigga as frigga
import citest.base


class KubeSmokeTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the smoke test.

  This scenario defines the different test operations.
  We're going to:
    Create a Spinnaker Application
    Create a Spinnaker Load Balancer
    Create a Spinnaker Server Group
    Create a Pipeline with the following stages
      - Find Image
      - Deploy
    Delete each of the above (in reverse order)
  """

  @classmethod
  def new_agent(cls, bindings):
    """Implements citest.service_testing.AgentTestScenario.new_agent."""
    agent = gate.new_agent(bindings)
    agent.default_max_wait_secs = 180
    return agent

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(KubeSmokeTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

    defaults = defaults or {}
    parser.add_argument(
      '--test_namespace', default='default',
      help='The namespace to manage within the tests.')

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    """
    super(KubeSmokeTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    # No detail because name length is restricted too much to afford one!
    self.__lb_detail = ''
    self.__lb_name = frigga.Naming.cluster(
        app=bindings['TEST_APP'],
        stack=bindings['TEST_STACK'])

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']
    self.TEST_NAMESPACE = bindings['TEST_NAMESPACE']
    self.pipeline_id = None

    # We will deploy two images. One with a tag that we want to find,
    # and another that we don't want to find.
    self.__image_registry = 'gcr.io'
    self.__image_repository = 'kubernetes-spinnaker/test-image'
    self.__desired_image_tag = 'validated'
    self.__undesired_image_tag = 'broken'
    self.__desired_image_pattern = '.*{0}'.format(self.__desired_image_tag)

    image_id_format_string = '{0}/{1}:{2}'

    self.__desired_image_id = image_id_format_string.format(
        self.__image_registry,
        self.__image_repository,
        self.__desired_image_tag)

    self.__undesired_image_id = image_id_format_string.format(
        self.__image_registry,
        self.__image_repository,
        self.__undesired_image_tag)

  def create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_KUBERNETES_ACCOUNT']),
        contract=contract)

  def delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_KUBERNETES_ACCOUNT']),
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
            'availabilityZones': {
                self.TEST_NAMESPACE: [self.TEST_NAMESPACE]
            },
            'provider': 'kubernetes',
            'stack': bindings['TEST_STACK'],
            'detail': self.__lb_detail,
            'serviceType': 'ClusterIP',
            'account': bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
            'namespace': self.TEST_NAMESPACE,
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
     .get_resources(
         'svc', extra_args=['--namespace', self.TEST_NAMESPACE])
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
            'namespace': self.TEST_NAMESPACE,
            'account': bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
            'credentials': bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
            'regions': [self.TEST_NAMESPACE],
            'user': '[anonymous]'
        }],
        description='Delete Load Balancer: {0} in {1}'.format(
            self.__lb_name,
            bindings['SPINNAKER_KUBERNETES_ACCOUNT']),
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Service Removed', retryable_for_secs=15)
     .get_resources(
         'svc',
         extra_args=['--namespace', self.TEST_NAMESPACE],
         no_resource_ok=True))

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
    group_name = frigga.Naming.server_group(
        app=self.TEST_APP,
        stack=bindings['TEST_STACK'],
        version='v000')

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'application': self.TEST_APP,
            'account': bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
            'namespace': self.TEST_NAMESPACE,
            'strategy':'',
            'targetSize': 1,
            'containers': [{
                'name': 'validated',
                'imageDescription': {
                    'repository': self.__image_repository,
                    'tag': self.__desired_image_tag,
                    'imageId': self.__desired_image_id,
                    'registry': self.__image_registry
                },
                'requests': {'memory':None, 'cpu':None},
                'limits': {'memory':None, 'cpu':None},
                'ports':[{'name':'http', 'containerPort':80,
                          'protocol':'TCP', 'hostPort':None, 'hostIp':None}]
            },
            {
                'name': 'broken',
                'imageDescription': {
                    'repository': self.__image_repository,
                    'tag': self.__undesired_image_tag,
                    'imageId': self.__undesired_image_id,
                    'registry': self.__image_registry
                },
                'requests': {'memory':None, 'cpu':None},
                'limits': {'memory':None, 'cpu':None},
                'ports':[{'name':'http', 'containerPort':80,
                          'protocol':'TCP', 'hostPort':None, 'hostIp':None}]
            }
            ],
            'stack': bindings['TEST_STACK'],
            'loadBalancers': [self.__lb_name],
            'type': 'createServerGroup',
            'regions': [self.TEST_NAMESPACE],
            'region': self.TEST_NAMESPACE,
            'user': '[anonymous]'
        }],
        description='Create Server Group in ' + group_name,
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Replica Set Added',
                                retryable_for_secs=15)
     .get_resources(
         'rs',
         extra_args=[group_name, '--namespace', self.TEST_NAMESPACE])
     .contains_path_eq('spec/replicas', 1))

    return st.OperationContract(
        self.new_post_operation(
            title='create_server_group', data=payload, path='tasks'),
        contract=builder.build())

  def make_smoke_stage(self, requisiteStages=[], **kwargs):
    result = {
      'requisiteStageRefIds': requisiteStages,
      'refId': 'FINDIMAGE',
      'type': 'findImage',
      'name': 'Find Valid Image',
      'cloudProviderType': 'kubernetes',
      'namespaces': [self.TEST_NAMESPACE],
      'cloudProvider': 'kubernetes',
      'selectionStrategy': 'NEWEST',
      'onlyEnabled': True,
      'credentials': self.bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
      'cluster': frigga.Naming.cluster(
          app=self.TEST_APP, stack=self.bindings['TEST_STACK']),
      'imageNamePattern': self.__desired_image_pattern
    }

    result.update(kwargs)
    return result

  def make_deploy_stage(self, imageSource=None, requisiteStages=[], **kwargs):
    bindings = self.bindings
    cluster = frigga.Naming.cluster(
        app=self.TEST_APP,
        stack=self.bindings['TEST_STACK'])
    result = {
      'requisiteStageRefIds': requisiteStages,
      'refId': 'DEPLOY',
      'type': 'deploy',
      'name': 'Deploy Validated Image',
      'clusters': [
        {
          'account': bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
          'application': self.TEST_APP,
          'stack': bindings['TEST_STACK'],
          'loadBalancers': [self.__lb_name],
          'strategy': '',
          'targetSize': 1,
          'cloudProvider': 'kubernetes',
          'namespace': self.TEST_NAMESPACE,
          'containers': [
            {
              'name': 'validated',
              'imageDescription': {
                'repository': 'Find Image',
                'imageId':'{0} {1}'.format(
                    cluster,
                    self.__desired_image_pattern),
                'fromContext': True,
                'cluster': cluster,
                'pattern': self.__desired_image_pattern,
                'stageId': imageSource
               },
              'requests': {'memory':None,'cpu':None},
              'limits': {'memory':None,'cpu':None},
              'ports': [{'name':'http','containerPort':80,
                'protocol':'TCP','hostPort':None,'hostIp':None}],
              'livenessProbe': None,
              'readinessProbe': None, 'envVars': [],
              'command': [],
              'args': [],
              'volumeMounts': []
            }
          ],
          'volumeSources': [],
          'provider': 'kubernetes',
          'regions': [self.TEST_NAMESPACE],
          'region': self.TEST_NAMESPACE
        }
      ]
    }

    result.update(kwargs)
    return result

  def create_find_image_pipeline(self):
    name = 'findImagePipeline'
    self.pipeline_id = name
    smoke_stage = self.make_smoke_stage()
    deploy_stage = self.make_deploy_stage(
        imageSource='FINDIMAGE',
        requisiteStages=['FINDIMAGE'])

    pipeline_spec = dict(
      name=name,
      stages=[smoke_stage,  deploy_stage],
      triggers=[],
      application=self.TEST_APP,
      stageCounter=2,
      parallel=True,
      limitConcurrent=True,
      executionEngine='v2',
      appConfig={},
      index=0
    )

    payload = self.agent.make_json_payload_from_kwargs(**pipeline_spec)
    expect_match = {key: jp.EQUIVALENT(value)
                    for key, value in pipeline_spec.items()}
    expect_match['stages'] = jp.LIST_MATCHES(
      [jp.DICT_MATCHES({key: jp.EQUIVALENT(value)
                       for key, value in smoke_stage.items()}),
       jp.DICT_MATCHES({key: jp.EQUIVALENT(value)
                        for key, value in deploy_stage.items()})])

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline',
                                retryable_for_secs=5)
        .get_url_path(
          'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
        .contains_match(expect_match))

    return st.OperationContract(
        self.new_post_operation(
            title='create_find_image_pipeline', data=payload, path='pipelines',
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def delete_server_group(self, version='v000'):
    """Creates OperationContract for deleteServerGroup.

    To verify the operation, we just check that the Kubernetes container
    is no longer visible (or is in the process of terminating).
    """
    bindings = self.bindings
    group_name = frigga.Naming.server_group(
        app=self.TEST_APP, stack=bindings['TEST_STACK'], version=version)

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'type': 'destroyServerGroup',
            'account': bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
            'credentials': bindings['SPINNAKER_KUBERNETES_ACCOUNT'],
            'user': '[anonymous]',
            'serverGroupName': group_name,
            'asgName': group_name,
            'regions': [self.TEST_NAMESPACE],
            'namespace': self.TEST_NAMESPACE,
            'region': self.TEST_NAMESPACE,
            'zones': [self.TEST_NAMESPACE],
            'interestingHealthProviderNames': ['KubernetesService']
        }],
        application=self.TEST_APP,
        description='Destroy Server Group: ' + group_name)

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Replica Set Removed')
     .get_resources(
         'rs',
         extra_args=[group_name, '--namespace', self.TEST_NAMESPACE],
         no_resource_ok=True)
     .contains_path_eq('targetSize', 0))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_server_group', data=payload, path='tasks'),
        contract=builder.build())

  def run_find_image_pipeline(self):
    path = 'pipelines/{0}/{1}'.format(self.TEST_APP, self.pipeline_id)
    bindings = self.bindings
    group_name = frigga.Naming.server_group(
        app=self.TEST_APP,
        stack=bindings['TEST_STACK'],
        version='v001')

    payload = self.agent.make_json_payload_from_kwargs(
        type='manual',
        user='[anonymous]')

    builder = kube.KubeContractBuilder(self.kube_observer)
    (builder.new_clause_builder('Replica Set Added',
                                retryable_for_secs=15)
     .get_resources(
         'rs', extra_args=[group_name, '--namespace', self.TEST_NAMESPACE])
     .contains_path_eq(
         'spec/template/spec/containers/image',
         self.__desired_image_id))

    return st.OperationContract(
        self.new_post_operation(
            title='run_find_image_pipeline', data=payload, path=path),
            builder.build())


class KubeSmokeTest(st.AgentTestCase):
  """The test fixture for the KubeSmokeTest.

  This is implemented using citest OperationContract instances that are
  created by the KubeSmokeTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        KubeSmokeTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b_upsert_load_balancer(self):
    self.run_test_case(self.scenario.upsert_load_balancer())

  def test_c_create_server_group(self):
    self.run_test_case(self.scenario.create_server_group(),
                       max_retries=1,
                       timeout_ok=True)

  def test_d_create_find_image_pipeline(self):
    self.run_test_case(self.scenario.create_find_image_pipeline())

  def test_e_run_find_image_pipeline(self):
    self.run_test_case(self.scenario.run_find_image_pipeline())

  def test_x1_delete_server_group(self):
    self.run_test_case(self.scenario.delete_server_group('v000'), max_retries=2)

  def test_x2_delete_server_group(self):
    self.run_test_case(self.scenario.delete_server_group('v001'), max_retries=2)

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

  return citest.base.TestRunner.main(
      parser_inits=[KubeSmokeTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[KubeSmokeTest])


if __name__ == '__main__':
  sys.exit(main())
