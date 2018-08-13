# Copyright 2018 Google Inc. All Rights Reserved.
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
Spinnaker Kubernetes V2 integration.
"""

# Standard python modules.
import sys
import random
import string

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

ov_factory = jc.ObservationPredicateFactory()

class KubeV2ArtifactTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the kube v2 artifact test.
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
    super(KubeV2ArtifactTestScenario, cls).initArgumentParser(
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
    super(KubeV2ArtifactTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']

    # Take just the first if there are multiple
    # because some uses below assume just one.
    self.TEST_NAMESPACE = bindings['TEST_NAMESPACE'].split(',')[0]

    self.mf = sk.KubernetesManifestFactory(self)
    self.mp = sk.KubernetesManifestPredicateFactory()
    self.ps = sk.PipelineSupport(self)

  def create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']),
        contract=contract)

  def delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']),
        contract=contract)

  def __docker_image_artifact(self, name, image):
    id_ = ''.join(random.choice(string.ascii_uppercase + string.digits)
                  for _ in range(10))
    return {
      'type': 'docker/image',
      'name': name,
      'reference': image,
      'uuid': id_
    }

  def deploy_unversioned_config_map(self, value):
    """Creates OperationContract for deploying an unversioned configmap

    To verify the operation, we just check that the configmap was created with
    the correct 'value'.
    """
    name = self.TEST_APP + '-configmap'
    manifest = self.mf.config_map(name, {'value': value})
    manifest['metadata']['annotations'] = {'strategy.spinnaker.io/versioned': 'false'}

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'source': 'text',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'manifests': [manifest],
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('ConfigMap created',
                                retryable_for_secs=15)
     .get_resources(
         'configmap',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.config_map_key_value_predicate('value', value)))

    return st.OperationContract(
        self.new_post_operation(
            title='deploy_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def deploy_deployment_with_config_map(self, versioned):
    """Creates OperationContract for deploying a configmap along with a deployment
    mounting this configmap.

    To verify the operation, we just check that the deployment was created with
    the correct configmap mounted
    """
    deployment_name = self.TEST_APP + '-deployment'
    deployment = self.mf.deployment(deployment_name, 'library/nginx')
    configmap_name = self.TEST_APP + '-configmap'
    configmap = self.mf.config_map(configmap_name, {'key': 'value'})

    if not versioned:
      configmap['metadata']['annotations'] = {'strategy.spinnaker.io/versioned': 'false'}

    self.mf.add_configmap_volume(deployment, configmap_name)

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'source': 'text',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'manifests': [deployment, configmap],
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment created',
                                retryable_for_secs=15)
     .get_resources(
         'deploy',
         extra_args=[deployment_name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.deployment_configmap_mounted_predicate(configmap_name)))

    return st.OperationContract(
        self.new_post_operation(
            title='deploy_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def deploy_config_map(self, version):
    """Creates OperationContract for deploying a versioned configmap

    To verify the operation, we just check that the deployment was created with
    the correct image.
    """
    bindings = self.bindings
    name = self.TEST_APP + '-configmap'
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'source': 'text',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'manifests': [self.mf.config_map(name, {'version': version})],
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('ConfigMap created',
                                retryable_for_secs=15)
     .get_resources(
         'configmap',
         extra_args=[name + '-' + version, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.config_map_key_value_predicate('version', version)))

    return st.OperationContract(
        self.new_post_operation(
            title='deploy_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def save_configmap_deployment_pipeline(self, pipeline_name, versioned=True):
    deployment_name = self.TEST_APP + '-deployment'
    deployment = self.mf.deployment(deployment_name, 'library/nginx')
    configmap_name = self.TEST_APP + '-configmap'
    configmap = self.mf.config_map(configmap_name, {'key': 'value'})

    if not versioned:
      configmap['metadata']['annotations'] = {'strategy.spinnaker.io/versioned': 'false'}

    self.mf.add_configmap_volume(deployment, configmap_name)
    configmap_stage = {
        'refId': 'configmap',
        'name': 'Deploy configmap',
        'type': 'deployManifest',
        'cloudProvider': 'kubernetes',
        'moniker': {
            'app': self.TEST_APP
        },
        'account': self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
        'source': 'text',
        'manifests': [configmap],
    }

    deployment_stage = {
        'refId': 'deployment',
        'name': 'Deploy deployment',
        'requisiteStageRefIds': ['configmap'],
        'type': 'deployManifest',
        'cloudProvider': 'kubernetes',
        'moniker': {
            'app': self.TEST_APP
        },
        'account': self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
        'source': 'text',
        'manifests': [deployment],
    }

    return self.ps.submit_pipeline_contract(pipeline_name, [configmap_stage, deployment_stage])

  def execute_deploy_manifest_pipeline(self, pipeline_name):
    deployment_name = self.TEST_APP + '-deployment'
    configmap_name = self.TEST_APP + '-configmap'
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'manual',
            'user': '[anonymous]'
        }],
        description='Deploy manifest in ' + self.TEST_APP,
        application=self.TEST_APP)
    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment created',
                                retryable_for_secs=60)
     .get_resources(
         'deploy',
         extra_args=[deployment_name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.deployment_configmap_mounted_predicate(configmap_name)))

    return st.OperationContract(
        self.new_post_operation(
            title='Deploy manifest', data=payload,
            path='pipelines/' + self.TEST_APP + '/' + pipeline_name),
        contract=builder.build())

  def deploy_deployment_with_docker_artifact(self, image):
    """Creates OperationContract for deploying and substituting one image into
    a Deployment object

    To verify the operation, we just check that the deployment was created with
    the correct image.
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
    image_name = 'placeholder'
    docker_artifact = self.__docker_image_artifact(image_name, image)
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'source': 'text',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'manifests': [self.mf.deployment(name, image_name)],
            'artifacts': [docker_artifact]
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment created',
                                retryable_for_secs=15)
     .get_resources(
         'deploy',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.deployment_image_predicate(image)))

    return st.OperationContract(
        self.new_post_operation(
            title='deploy_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def delete_kind(self, kind, version=None):
    """Creates OperationContract for deleteManifest

    To verify the operation, we just check that the Kubernetes deployment
    is no longer visible (or is in the process of terminating).
    """
    bindings = self.bindings
    name = self.TEST_APP + '-' + kind
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'type': 'deleteManifest',
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'user': '[anonymous]',
            'kinds': [ kind ],
            'location': self.TEST_NAMESPACE,
            'options': { },
            'labelSelectors': {
                'selectors': [{
                    'kind': 'EQUALS',
                    'key': 'app',
                    'values': [ self.TEST_APP ]
                }]
            }
        }],
        application=self.TEST_APP,
        description='Destroy Manifest')

    if version is not None:
      name = name + '-' + version

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Manifest Removed')
     .get_resources(
         kind,
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.not_found_observation_predicate()))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_kind', data=payload, path='tasks'),
        contract=builder.build())

class KubeV2ArtifactTest(st.AgentTestCase):
  """The test fixture for the KubeV2ArtifactTest.

  This is implemented using citest OperationContract instances that are
  created by the KubeV2ArtifactTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        KubeV2ArtifactTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b1_deploy_deployment_with_docker_artifact(self):
    self.run_test_case(self.scenario.deploy_deployment_with_docker_artifact('library/nginx'))

  def test_b2_update_deployment_with_docker_artifact(self):
    self.run_test_case(self.scenario.deploy_deployment_with_docker_artifact('library/redis'))

  def test_b3_delete_deployment(self):
    self.run_test_case(self.scenario.delete_kind('deployment'), max_retries=2)

  def test_c1_create_config_map(self):
    self.run_test_case(self.scenario.deploy_config_map('v000'))

  def test_c2_noop_update_config_map(self):
    self.run_test_case(self.scenario.deploy_config_map('v000'))

  def test_c3_update_config_map(self):
    self.run_test_case(self.scenario.deploy_config_map('v001'))

  def test_c4_delete_configmap(self):
    self.run_test_case(self.scenario.delete_kind('configmap', version='v001'), max_retries=2)

  def test_d1_create_unversioned_configmap(self):
    self.run_test_case(self.scenario.deploy_unversioned_config_map('1'))

  def test_d2_update_unversioned_configmap(self):
    self.run_test_case(self.scenario.deploy_unversioned_config_map('2'))

  def test_d3_delete_unversioned_configmap(self):
    self.run_test_case(self.scenario.delete_kind('configmap'), max_retries=2)

  def test_e1_create_deployment_with_versioned_configmap(self):
    self.run_test_case(self.scenario.deploy_deployment_with_config_map(True))

  def test_e2_delete_deployment(self):
    self.run_test_case(self.scenario.delete_kind('deployment'), max_retries=2)

  def test_e3_delete_configmap(self):
    self.run_test_case(self.scenario.delete_kind('configmap', version='v000'), max_retries=2)

  def test_f1_create_configmap_deployment_pipeline(self):
    self.run_test_case(self.scenario.save_configmap_deployment_pipeline('deploy-configmap-deployment'))

  def test_f2_execute_configmap_deployment_pipeline(self):
    self.run_test_case(self.scenario.execute_deploy_manifest_pipeline('deploy-configmap-deployment'))

  def test_f3_delete_deployment(self):
    self.run_test_case(self.scenario.delete_kind('deployment'), max_retries=2)

  def test_f4_delete_configmap(self):
    self.run_test_case(self.scenario.delete_kind('configmap', version='v000'), max_retries=2)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this artifact test."""

  defaults = {
      'TEST_STACK': 'tst',
      'TEST_APP': 'kubv2arti' + KubeV2ArtifactTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[KubeV2ArtifactTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[KubeV2ArtifactTest])


if __name__ == '__main__':
  sys.exit(main())
