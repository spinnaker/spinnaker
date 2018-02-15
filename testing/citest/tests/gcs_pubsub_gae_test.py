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
#
# This test currently expects some preconfiguration to set up a GCS bucket,
# a Google pub/sub topic and subscription, and notifications enabled on the
# GCS bucket pushing to the pub/sub topic.
#
# The full setup and config instructions are listed in the following sections:
# * https://www.spinnaker.io/guides/tutorials/codelabs/pubsub-to-appengine/#create-a-gcs-bucket-to-store-artifacts
# * https://www.spinnaker.io/guides/tutorials/codelabs/pubsub-to-appengine/#set-up-google-cloud-pubsub-to-listen-to-bucket-object-changes
#
# Summarized here:
# * Create bucket: gsutil mb -p <project> <bucket_name>
# * Create topic and notification channel: gsutil notification create -t <topic> -f json <bucket_name>
# * Create subscription: gcloud beta pubsub subscriptions create <subscription> --topic <topic>
#
# In addition to the external config, this test expects a pub/sub subscription and artifacts configured
# in Spinnaker.
#
# Invoke this test by executing this python command:
#
# PYTHONPATH=$CITEST_ROOT \
# python spinnaker/testing/citest/tests/gcs_pubsub_gae_test.py
# --native_hostname
# --native_port
# --git_repo_url <app to deploy>
# --app_directory_root <root of app>
# --test_storage_account_name <configured artifact storage account>
# --appengine_primary_managed_project_id <managed gce project>
# --appengine_credentials_path <path to service account credentials>
# --test_subscription_name <configured subscription name>
# --test_gcs_bucket <bucket notifying pub/sub topic>
# --spinnaker_appengine_account
# --test_app
# --test_stack
#
# pylint: disable=bad-continuation
# pylint: disable=invalid-name
# pylint: disable=missing-docstring

# Standard python modules.
import json
import logging
import os
import subprocess
import shutil
import sys
import tempfile
import time

# citest modules.
import citest.base
import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate
import spinnaker_testing.frigga as frigga

ov_factory = jc.ObservationPredicateFactory()

class GcsPubsubGaeTestScenario(sk.SpinnakerTestScenario):
  """
  Scenario for testing GAE deploys of GCS artifacts via pub/sub triggers.
  """
  @classmethod
  def new_agent(cls, bindings):
    return gate.new_agent(bindings)

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
    parser: argparse.ArgumentParser
    """
    super(GcsPubsubGaeTestScenario, cls).initArgumentParser(
      parser, defaults=defaults)

    defaults = defaults or {}
    parser.add_argument(
      '--app_directory_root', default=None,
      help='Path from the root of source code repository to the application directory.')
    parser.add_argument(
      '--branch', default='master',
      help='Git branch to be used when deploying from source code repository.')
    parser.add_argument(
      '--git_repo_url', default=None,
      help='URL of a git source code repository used by Spinnaker to deploy to App Engine.')
    parser.add_argument(
      '--test_gcs_bucket', default=None,
      help='GCS bucket to upload GAE app source code to.')
    parser.add_argument(
      '--test_storage_account_name', default=None,
      help='Storage account when testing GCS buckets.'
      ' If not specified, use the application default credentials.')
    parser.add_argument(
      '--test_subscription_name', default=None,
      help='Google pub/sub subscription name configured in Echo.')

  def __init__(self, bindings, agent=None):
    super(GcsPubsubGaeTestScenario, self).__init__(bindings, agent)
    self.logger = logging.getLogger(__name__)

    bindings = self.bindings

    if not bindings['GIT_REPO_URL']:
      raise ValueError('Must supply value for --git_repo_url')

    if not bindings['APP_DIRECTORY_ROOT']:
      raise ValueError('Must supply value for --app_directory_root')

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    self.TEST_APP = bindings['TEST_APP']
    self.TEST_STACK = bindings['TEST_STACK']
    self.__EXPECTED_ARTIFACT_ID = 'deployable-gae-app-artifact'

    self.__gcp_project = bindings['APPENGINE_PRIMARY_MANAGED_PROJECT_ID']
    self.__cluster_name = frigga.Naming.cluster(self.TEST_APP, self.TEST_STACK)
    self.__server_group_name = frigga.Naming.server_group(self.TEST_APP, self.TEST_STACK)
    self.__lb_name = self.__cluster_name

    self.__subscription_name = bindings['TEST_SUBSCRIPTION_NAME']
    self.__gcs_pubsub_agent = sk.GcsFileUploadAgent(bindings['APPENGINE_CREDENTIALS_PATH'])

    # Python is clearly hard-coded as the runtime here, but we're just asking App Engine to be a static file server.
    self.__app_yaml = ('\n'.join(['runtime: python27',
                                  'api_version: 1',
                                  'threadsafe: true',
                                  'service: {service}',
                                  'handlers:',
                                  ' - url: /.*',
                                  '   static_dir: .']).format(service=self.__lb_name))

    self.__app_directory_root = bindings['APP_DIRECTORY_ROOT']
    self.__branch = bindings['BRANCH']

    self.pipeline_id = None

    self.bucket = bindings['TEST_GCS_BUCKET']
    self.__test_repository_url = 'gs://' + self.bucket


  def create_app(self):
    # Not testing create_app, since the operation is well tested elsewhere.
    # Retryable to handle platform flakiness.
    contract = jc.Contract()
    return st.OperationContract(
      self.agent.make_create_app_operation(
        bindings=self.bindings,
        application=self.TEST_APP,
        account_name=self.bindings['SPINNAKER_APPENGINE_ACCOUNT']),
      contract=contract)

  def delete_app(self):
    # Not testing delete_app, since the operation is well tested elsewhere.
    # Retryable to handle platform flakiness.
    contract = jc.Contract()
    return st.OperationContract(
      self.agent.make_delete_app_operation(
        application=self.TEST_APP,
        account_name=self.bindings['SPINNAKER_APPENGINE_ACCOUNT']),
      contract=contract)

  def make_deploy_stage(self):
    return {
      'clusters': [
        {
          'account': self.bindings['SPINNAKER_APPENGINE_ACCOUNT'],
          'application': self.TEST_APP,
          'cloudProvider': 'appengine',
          'configFilepaths': [],
          'configFiles': [self.__app_yaml],
          'expectedArtifactId': self.__EXPECTED_ARTIFACT_ID,
          'fromArtifact': True,
          'gitCredentialType': 'NONE',
          'interestingHealthProviderNames': [
            'App Engine Service'
          ],
          'provider': 'appengine',
          'region': self.bindings['TEST_GCE_REGION'],
          'sourceType': 'gcs',
          'stack': self.TEST_STACK,
          'storageAccountName': self.bindings.get('TEST_STORAGE_ACCOUNT_NAME')
        }
      ],
      'name': 'Deploy',
      'refId': 'DEPLOY',
      'requisiteStageRefIds': [],
      'type': 'deploy'
    }

  def make_pubsub_trigger(self):
    return {
      'attributeConstraints': {'eventType': 'OBJECT_FINALIZE'},
      'constraints': {},
      'enabled': True,
      'expectedArtifactIds': [
        self.__EXPECTED_ARTIFACT_ID
      ],
      'payloadConstraints': {},
      'pubsubSystem': 'google',
      'subscriptionName': self.__subscription_name, # Logical name assigned in Echo.
      'type': 'pubsub'
    }

  def make_expected_artifact(self):
    return {
      'defaultArtifact': {
        'kind': 'custom'
      },
      'id': self.__EXPECTED_ARTIFACT_ID,
      'matchArtifact': {
        'kind': 'gcs',
        'name': 'gs://{}/app.tar'.format(self.bucket),
        'type': 'gcs/object'
      },
      'useDefaultArtifact': False,
      'usePriorExecution': False
    }

  def make_pipeline_spec(self, name):
    return dict(
      name=name,
      stages=[self.make_deploy_stage()],
      triggers=[self.make_pubsub_trigger()],
      expectedArtifacts=[self.make_expected_artifact()],
      application=self.TEST_APP,
      stageCounter=1,
      parallel=True,
      limitConcurrent=True,
      appConfig={},
      index=0
    )

  def make_dict_matcher(self, want):
    spec = {}
    for key, value in want.items():
      if isinstance(value, dict):
        spec[key] = self.make_dict_matcher(value)
      elif isinstance(value, list):
        list_spec = []
        for elem in value:
          if isinstance(elem, dict):
            list_spec.append(self.make_dict_matcher(elem))
          else:
            list_spec.append(jp.CONTAINS(elem))
        spec[key] = jp.LIST_MATCHES(list_spec)
      else:
        spec[key] = jp.CONTAINS(value)

    return jp.DICT_MATCHES(spec)

  def create_deploy_pipeline(self):
    name = 'GcsToGaePubsubDeploy'
    self.pipeline_id = name

    pipeline_spec = self.make_pipeline_spec(name)
    payload = self.agent.make_json_payload_from_kwargs(**pipeline_spec)

    pipeline_config_path = 'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP)
    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
     .get_url_path(pipeline_config_path)
     .EXPECT(
       jp.LIST_MATCHES([self.make_dict_matcher(pipeline_spec)])))

    # Need to query Gate for the id of the pipeline we just created...
    def create_pipeline_id_extractor(_ignored, context):
      pipeline_config_resp = self.agent.get(pipeline_config_path)
      pipeline_config_list = json.JSONDecoder().decode(pipeline_config_resp.output)
      found = next(x for x in pipeline_config_list if x['name'] == self.pipeline_id)
      context['pipelineId'] = found['id'] # I don't know how to reference this later, so I'm saving it in self for now.
      self.__pipeline_id = found['id'] # I don't know how to reference this later, so I'm saving it in self for now.
      logging.info('Created pipeline config with id: %s', context['pipelineId'])

    return st.OperationContract(
      self.new_post_operation(
        title='create_gcs_gae_pubsub_pipeline', data=payload, path='pipelines',
        status_class=st.SynchronousHttpOperationStatus),
      contract=builder.build(),
      status_extractor=create_pipeline_id_extractor)

  def trigger_deploy_pipeline(self):
    name = 'app.tar'
    command = 'tar -cvf {tar} {git_dir}/{app_root}/*'.format(tar=name,
                                                             git_dir=self.temp,
                                                             app_root=self.bindings['APP_DIRECTORY_ROOT'])
    logging.info('Tar-ing %s/%s for GCS upload', self.temp, self.bindings['APP_DIRECTORY_ROOT'])
    subprocess.Popen(command, stderr=sys.stderr, shell=True).wait()

    group_name = frigga.Naming.server_group(
        app=self.TEST_APP,
        stack=self.bindings['TEST_STACK'],
        version='v000')

    # Triggered pipeline does a deploy, check for that server group.
    server_group_path = 'applications/{app}/serverGroups'.format(app=self.TEST_APP)
    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('GAE Deploy Pipeline Succeeded',
                                retryable_for_secs=300)
     .get_url_path(server_group_path)
     .EXPECT(
       ov_factory.value_list_matches(
         [jp.DICT_MATCHES({'name': jp.STR_EQ(group_name)})]
       )))

    executions_path = 'executions?pipelineConfigIds={}&limit=1&statuses=SUCCEEDED'.format(self.__pipeline_id)
    return st.OperationContract(
      self.__gcs_pubsub_agent.new_gcs_pubsub_trigger_operation(
        gate_agent=self.agent,
        title='monitor_gcs_pubsub_pipeline',
        bucket_name=self.bucket,
        upload_path='{}'.format(name),
        local_filename=os.path.abspath(name),
        status_class=None,
        status_path=executions_path
      ),
      contract=builder.build())

  def delete_load_balancer(self):
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'deleteLoadBalancer',
            'cloudProvider': 'appengine',
            'loadBalancerName': self.__lb_name,
            'account': bindings['SPINNAKER_APPENGINE_ACCOUNT'],
            'credentials': bindings['SPINNAKER_APPENGINE_ACCOUNT'],
            'user': '[anonymous]'
        }],
        description='Delete Load Balancer: {0} in {1}'.format(
            self.__lb_name,
            bindings['SPINNAKER_APPENGINE_ACCOUNT']),
        application=self.TEST_APP)

    builder = gcp.GcpContractBuilder(self.appengine_observer)
    (builder.new_clause_builder('Service Deleted', retryable_for_secs=30)
     .inspect_resource('apps.services',
                       self.__lb_name,
                       appsId=self.__gcp_project)
     .EXPECT(
         ov_factory.error_list_contains(gcp.HttpErrorPredicate(http_code=404))))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def delete_deploy_pipeline(self, pipeline_id):
    payload = self.agent.make_json_payload_from_kwargs(id=pipeline_id)
    path = os.path.join('pipelines', self.TEST_APP, pipeline_id)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline',
                                retryable_for_secs=5)
     .get_url_path(
       'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
     .excludes_path_value('name', pipeline_id))

    return st.OperationContract(
      self.new_delete_operation(
        title='delete_deploy_pipeline', data=payload, path=path,
        status_class=st.SynchronousHttpOperationStatus),
      contract=builder.build())

class GcsPubsubGaeTest(st.AgentTestCase):
  @classmethod
  def setUpClass(cls):
    runner = citest.base.TestRunner.global_runner()
    scenario = runner.get_shared_data(GcsPubsubGaeTestScenario)
    bindings = scenario.bindings

    branch = bindings['BRANCH']
    git_repo = bindings['GIT_REPO_URL']

    scenario.temp = tempfile.mkdtemp()

    gcs_path = 'gs://{bucket}'.format(bucket=scenario.bucket)
    topic = '{}-topic'.format(bindings['TEST_APP'])
    subscription = '{}-subscription'.format(bindings['TEST_APP'])

    # App to tar and upload to GCS.
    command = 'git clone {repo} -b {branch} {dir}'.format(
      repo=git_repo, branch=branch, dir=scenario.temp)
    logging.info('Fetching %s', git_repo)
    subprocess.Popen(command, stderr=sys.stderr, shell=True).wait()


  @classmethod
  def tearDownClass(cls):
    runner = citest.base.TestRunner.global_runner()
    scenario = runner.get_shared_data(GcsPubsubGaeTestScenario)
    bindings = scenario.bindings
    shutil.rmtree(scenario.temp)

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(GcsPubsubGaeTestScenario)

  @property
  def testing_agent(self):
    return self.scenario.agent

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app(),
                       retry_interval_secs=8, max_retries=8)

  def test_b_create_pipeline(self):
    self.run_test_case(self.scenario.create_deploy_pipeline(),
                       retry_interval_secs=8, max_retries=8)

  def test_d_run_pipeline(self):
    time.sleep(60)
    # Wait for Echo's cache to pick up the deploy pipeline.
    # This is generally a bad strategy for synchronizing cache timing, so we'll investigate
    # performing this check a different way in the future. One option is querying the
    # Spinnaker services' metrics endpoint and inspecting metrics related to caching
    # once they are instrumented.
    self.run_test_case(self.scenario.trigger_deploy_pipeline(), poll_every_secs=5)

  def test_x_delete_load_balancer(self):
    self.run_test_case(self.scenario.delete_load_balancer(),
                       retry_interval_secs=8, max_retries=8)

  def test_y_delete_pipeline(self):
    self.run_test_case(
      self.scenario.delete_deploy_pipeline(self.scenario.pipeline_id))

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  defaults = {
    'TEST_STACK': 'gcspubsubgaetest' + GcsPubsubGaeTestScenario.DEFAULT_TEST_ID,
    'TEST_APP': 'gcspubsubgaetest' + GcsPubsubGaeTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
    parser_inits=[GcsPubsubGaeTestScenario.initArgumentParser],
    default_binding_overrides=defaults,
    test_case_list=[GcsPubsubGaeTest])


if __name__ == '__main__':
  sys.exit(main())
