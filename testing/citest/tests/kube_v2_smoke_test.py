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

class KubeV2SmokeTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the kube v2 smoke test.
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
    super(KubeV2SmokeTestScenario, cls).initArgumentParser(
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
    super(KubeV2SmokeTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']

    # Take just the first if there are multiple
    # because some uses below assume just one.
    self.TEST_NAMESPACE = bindings['TEST_NAMESPACE'].split(',')[0]
    self.pipeline_id = None

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

  def deploy_manifest(self, image):
    """Creates OperationContract for deployManifest.

    To verify the operation, we just check that the deployment was created.
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
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
            'manifests': [self.mf.deployment(name, image)],
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

  def patch_manifest(self):
    """Creates OperationContract for patchManifest.

    To verify the operation, we just check that the deployment was created.
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
    test_label = 'patchedLabel'
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
          'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
          'cloudProvider': 'kubernetes',
          'kind': 'deployment',
          'location': self.TEST_NAMESPACE,
          'manifestName': 'deployment ' + name,
          'type': 'patchManifest',
          'user': '[anonymous]',
          'source': 'text',
          'patchBody': {
            'metadata': {
              'labels': {
                'testLabel': test_label,
              }
            }
          },
          'options': {
            'mergeStrategy': 'strategic',
            'record': True
          }
        }],
        description='Patch manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment patched',
                                retryable_for_secs=15)
     .get_resources(
        'deploy',
        extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(ov_factory.value_list_contains(jp.DICT_MATCHES({
      'metadata': jp.DICT_MATCHES({
        'labels': jp.DICT_MATCHES({
          'testLabel': jp.STR_EQ(test_label)
        })
      })
    }))))

    return st.OperationContract(
        self.new_post_operation(
            title='patch_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def undo_rollout_manifest(self, image):
    """Creates OperationContract for undoRolloutManifest.

    To verify the operation, we just check that the deployment has changed size
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'manifestName': 'deployment ' + name,
            'location': self.TEST_NAMESPACE,
            'type': 'undoRolloutManifest',
            'user': '[anonymous]',
            'numRevisionsBack': 1
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment rolled back',
                                retryable_for_secs=15)
     .get_resources(
         'deploy',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.deployment_image_predicate(image)))

    return st.OperationContract(
        self.new_post_operation(
            title='undo_rollout_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def scale_manifest(self):
    """Creates OperationContract for scaleManifest.

    To verify the operation, we just check that the deployment has changed size
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'manifestName': 'deployment ' + name,
            'location': self.TEST_NAMESPACE,
            'type': 'scaleManifest',
            'user': '[anonymous]',
            'replicas': 2
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment scaled',
                                retryable_for_secs=15)
     .get_resources(
         'deploy',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(ov_factory.value_list_contains(jp.DICT_MATCHES(
         { 'spec': jp.DICT_MATCHES({ 'replicas': jp.NUM_EQ(2) }) }))))

    return st.OperationContract(
        self.new_post_operation(
            title='scale_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def save_deploy_manifest_pipeline(self, image):
    name = self.TEST_APP + '-deployment'
    stage = {
        'type': 'deployManifest',
        'cloudProvider': 'kubernetes',
        'moniker': {
            'app': self.TEST_APP
        },
        'account': self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
        'source': 'text',
        'manifests': [self.mf.deployment(name, image)],
    }

    return self.ps.submit_pipeline_contract('deploy-manifest-pipeline', [stage])

  def execute_deploy_manifest_pipeline(self, image):
    name = self.TEST_APP + '-deployment'
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'manual',
            'user': '[anonymous]'
        }],
        description='Deploy manifest in ' + self.TEST_APP,
        application=self.TEST_APP)
    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment deployed',
                                retryable_for_secs=15)
     .get_resources(
         'deploy',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.deployment_image_predicate(image)))

    return st.OperationContract(
        self.new_post_operation(
            title='Deploy manifest', data=payload,
            path='pipelines/' + self.TEST_APP + '/deploy-manifest-pipeline'),
        contract=builder.build())

  def delete_manifest(self):
    """Creates OperationContract for deleteManifest

    To verify the operation, we just check that the Kubernetes deployment
    is no longer visible (or is in the process of terminating).
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'type': 'deleteManifest',
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'user': '[anonymous]',
            'kinds': [ 'deployment' ],
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

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Replica Set Removed')
     .get_resources(
         'deployment',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.not_found_observation_predicate()))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_manifest', data=payload, path='tasks'),
        contract=builder.build())

class KubeV2SmokeTest(st.AgentTestCase):
  """The test fixture for the KubeV2SmokeTest.

  This is implemented using citest OperationContract instances that are
  created by the KubeV2SmokeTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        KubeV2SmokeTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b1_deploy_manifest(self):
    self.run_test_case(self.scenario.deploy_manifest('library/nginx'),
                       max_retries=1,
                       timeout_ok=True)

  def test_b2_update_manifest(self):
    self.run_test_case(self.scenario.deploy_manifest('library/redis'),
                       max_retries=1,
                       timeout_ok=True)

  def test_b3_undo_rollout_manifest(self):
    self.run_test_case(self.scenario.undo_rollout_manifest('library/nginx'),
                       max_retries=1)

  def test_b4_scale_manifest(self):
    self.run_test_case(self.scenario.scale_manifest(), max_retries=1)

  def test_b5_patch_manifest(self):
    self.run_test_case(self.scenario.patch_manifest(), max_retries=1)

  def test_b6_delete_manifest(self):
    self.run_test_case(self.scenario.delete_manifest(), max_retries=2)

  def test_c1_save_deploy_manifest_pipeline(self):
    self.run_test_case(self.scenario.save_deploy_manifest_pipeline('library/nginx'))

  def test_c2_execute_deploy_manifest_pipeline(self):
    self.run_test_case(self.scenario.execute_deploy_manifest_pipeline('library/nginx'))

  def test_c3_delete_manifest(self):
    self.run_test_case(self.scenario.delete_manifest(), max_retries=2)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_STACK': 'tst',
      'TEST_APP': 'kubv2smok' + KubeV2SmokeTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[KubeV2SmokeTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[KubeV2SmokeTest])


if __name__ == '__main__':
  sys.exit(main())
