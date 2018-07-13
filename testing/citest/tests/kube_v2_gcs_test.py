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
import json

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

class KubeV2GcsTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the kube v2 gcs test.
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
    super(KubeV2GcsTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

    defaults = defaults or {}
    parser.add_argument(
      '--test_namespace', default='default',
      help='The namespace to manage within the tests.')

    parser.add_argument(
        '--test_gcs_artifact_account',
        default='gcs-artifact-account',
        help='Spinnaker GCS artifact account name to use for test operations'
             ' against artifacts stored in GCS.')

    parser.add_argument(
        '--test_gcs_bucket',
        help='GCS bucket to upload & read manifests from.')

    parser.add_argument(
        '--test_gcs_credentials_path', default=None,
        help='GCS bucket to upload & read manifests from.')

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    """
    super(KubeV2GcsTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']

    # Take just the first if there are multiple
    # because some uses below assume just one.
    self.TEST_NAMESPACE = bindings['TEST_NAMESPACE'].split(',')[0]

    self.ARTIFACT_ACCOUNT = bindings['TEST_GCS_ARTIFACT_ACCOUNT']
    self.BUCKET = bindings['TEST_GCS_BUCKET']

    self.mf = sk.KubernetesManifestFactory(self)
    self.mp = sk.KubernetesManifestPredicateFactory()
    self.gcs = sk.GcsFileUploadAgent(bindings['TEST_GCS_CREDENTIALS_PATH'])

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

  def __gcs_manifest_expected_artifact(self, contents, path):
    self.gcs.upload_string(self.BUCKET, path, contents)
    id_ = ''.join(random.choice(string.ascii_uppercase + string.digits)
                  for _ in range(10))
    return {
      'boundArtifact': {
        'type': 'gcs/object',
        'name': path,
        'reference': 'gs://{}/{}'.format(self.BUCKET, path),
      },
      'id': id_
    }

  def deploy_spel_deployment_from_gcs(self, image):
    """Creates OperationContract for deploying and substituting one image into
    a Deployment object using a SpEL expression.

    To verify the operation, we just check that the deployment was created with
    the correct image.
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
    manifest = self.mf.deployment(name, '${image}')
    manifest_artifact = self.__gcs_manifest_expected_artifact(json.dumps(manifest), 'manifest-spel.yaml')
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'source': 'artifact',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'image': image,
            'manifestArtifactAccount': self.ARTIFACT_ACCOUNT,
            'manifestArtifactId': manifest_artifact['id'],
            'resolvedExpectedArtifacts': [manifest_artifact]
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

  def deploy_deployment_from_gcs(self, image):
    """Creates OperationContract for deploying and substituting one image into
    a Deployment object

    To verify the operation, we just check that the deployment was created with
    the correct image.
    """
    bindings = self.bindings
    name = self.TEST_APP + '-deployment'
    manifest = self.mf.deployment(name, image)
    manifest_artifact = self.__gcs_manifest_expected_artifact(json.dumps(manifest), 'manifest.yaml')
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'source': 'artifact',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'manifestArtifactAccount': self.ARTIFACT_ACCOUNT,
            'manifestArtifactId': manifest_artifact['id'],
            'resolvedExpectedArtifacts': [manifest_artifact]
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

class KubeV2GcsTest(st.AgentTestCase):
  """The test fixture for the KubeV2GcsTest.

  This is implemented using citest OperationContract instances that are
  created by the KubeV2GcsTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        KubeV2GcsTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b1_deploy_from_gcs(self):
    self.run_test_case(self.scenario.deploy_deployment_from_gcs('library/nginx'))

  def test_b2_delete_deployment(self):
    self.run_test_case(self.scenario.delete_kind('deployment'), max_retries=2)

  def test_c1_spel_deploy_from_gcs(self):
    self.run_test_case(self.scenario.deploy_spel_deployment_from_gcs('library/nginx'))

  def test_c2_delete_deployment(self):
    self.run_test_case(self.scenario.delete_kind('deployment'), max_retries=2)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this gcs test."""

  defaults = {
      'TEST_STACK': 'tst',
      'TEST_APP': 'kubv2gcs' + KubeV2GcsTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[KubeV2GcsTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[KubeV2GcsTest])


if __name__ == '__main__':
  sys.exit(main())
