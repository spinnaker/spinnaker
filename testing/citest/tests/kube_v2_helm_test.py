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
Integration test to see if the helm bake process is working for the
Spinnaker Kubernetes V2 integration.

This test requires 'helm', a tool for packaging & deploying Kubernetes
manifests. Follow the instuctions here: 

  https://docs.helm.sh/using_helm/#from-the-binary-releases

to install 'helm'. You do not need to run 'helm init' or install
'tiller' (like the docs might mention) for this test to pass.
"""

# Standard python modules.
import sys
import os
import subprocess
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

class KubeV2HelmTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the kube v2 helm test.
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
    super(KubeV2HelmTestScenario, cls).initArgumentParser(
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
    super(KubeV2HelmTestScenario, self).__init__(bindings, agent)
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

  def __create_helm_chart(self):
    dir_path = os.path.dirname(os.path.realpath(__file__))
    command = 'helm package {}/kube_v2_data/helm-test'.format(dir_path)
    subprocess.Popen(command, stderr=sys.stderr, shell=True).wait()
    return os.getcwd() + '/helm-test-0.1.0.tgz'

  def __gcs_file_expected_artifact(self, contents, path):
    self.gcs.upload_string(self.BUCKET, path, contents)
    id_ = ''.join(random.choice(string.ascii_uppercase + string.digits)
                  for _ in range(10))
    artifact = {
        'type': 'gcs/object',
        'name': path,
        'reference': 'gs://{}/{}'.format(self.BUCKET, path),
    }
    return {
      'matchArtifact': artifact,
      'defaultArtifact': artifact,
      'useDefaultArtifact': True,
      'id': id_,
    }

  def __gcs_helm_chart_expected_artifact(self, path):
    chart = self.__create_helm_chart()
    self.gcs.upload_file(self.BUCKET, path, chart)
    os.remove(chart)
    id_ = ''.join(random.choice(string.ascii_uppercase + string.digits)
                  for _ in range(10))
    artifact = {
        'type': 'gcs/object',
        'name': path,
        'reference': 'gs://{}/{}'.format(self.BUCKET, path),
    }
    return {
      'matchArtifact': artifact,
      'defaultArtifact': artifact,
      'useDefaultArtifact': True,
      'id': id_,
    }

  def save_bake_deploy_manifest_pipeline(self, image):
    name = self.TEST_APP + '-deployment'
    chart_expected_artifact = self.__gcs_helm_chart_expected_artifact('helm-test.tgz')
    values_expected_artifact = self.__gcs_file_expected_artifact("""
    image: {image}
    namespace: {namespace}
    app: {app}
    name: {name}
    """.format(image=image, 
               namespace=self.TEST_NAMESPACE, 
               app=self.TEST_APP, 
               name=name),
    'helm-values.yml')
    bake_artifact_id = 'bake-artifact'
    bake_artifact_name = 'demo'
    bake_stage = {
        'type': 'bakeManifest',
        'refId': 'bake',
        'outputName': bake_artifact_name,
        'inputArtifacts': [{
            'account': self.ARTIFACT_ACCOUNT,
            'id': chart_expected_artifact['id']
        }, {
            'account': self.ARTIFACT_ACCOUNT,
            'id': values_expected_artifact['id']
        },
        ],
        'expectedArtifacts': [{
            'id': bake_artifact_id,
            'matchArtifact': {
                'type': 'embedded/base64',
                'name': bake_artifact_name,
            },
        }],
        'templateRenderer': 'HELM2',
        'overrides': {},
    }

    deploy_stage = {
        'type': 'deployManifest',
        'refId': 'deploy',
        'requisiteStageRefIds': ['bake'],
        'cloudProvider': 'kubernetes',
        'moniker': {
            'app': self.TEST_APP
        },
        'account': self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
        'source': 'artifact',
        'manifestArtifactAccount': 'embedded-artifact',
        'manifestArtifactId': bake_artifact_id,
    }

    return self.ps.submit_pipeline_contract('bake-deploy-pipeline',
            [bake_stage, deploy_stage],
            expectedArtifacts=[chart_expected_artifact, values_expected_artifact])

  def execute_bake_deploy_manifest_pipeline(self, image):
    name = self.TEST_APP + '-deployment'
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'manual',
            'user': '[anonymous]',
        }],
        description='Deploy manifest in ' + self.TEST_APP,
        application=self.TEST_APP)
    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment deployed',
                                retryable_for_secs=90)
     .get_resources(
         'deploy',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.deployment_image_predicate(image)))

    return st.OperationContract(
        self.new_post_operation(
            title='Deploy manifest', data=payload,
            path='pipelines/' + self.TEST_APP + '/bake-deploy-pipeline'),
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

class KubeV2HelmTest(st.AgentTestCase):
  """The test fixture for the KubeV2HelmTest.

  This is implemented using citest OperationContract instances that are
  created by the KubeV2HelmTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        KubeV2HelmTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b1_save_bake_deploy_manifest_pipeline(self):
    self.run_test_case(self.scenario.save_bake_deploy_manifest_pipeline('library/nginx'))

  def test_b2_execute_bake_deploy_manifest_pipeline(self):
    self.run_test_case(self.scenario.execute_bake_deploy_manifest_pipeline('library/nginx'))

  def test_b3_delete_deployment(self):
    self.run_test_case(self.scenario.delete_kind('deployment'), max_retries=2)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this helm test."""

  defaults = {
      'TEST_STACK': 'tst',
      'TEST_APP': 'kubv2helm' + KubeV2HelmTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[KubeV2HelmTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[KubeV2HelmTest])


if __name__ == '__main__':
  sys.exit(main())
