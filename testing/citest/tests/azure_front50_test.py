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

# pylint: disable=missing-docstring
# pylint: disable=invalid-name

import json
import logging
import sys
import urllib

from citest.base import ExecutionContext

import citest.base
import citest.azure_testing as az
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.front50 as front50


class AzureFront50TestScenario(sk.SpinnakerTestScenario):
  @classmethod
  def new_agent(cls, bindings):
    """Implements citest.service_testing.AgentTestScenario.new_agent."""
    return front50.new_agent(bindings)

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    parser.add_argument(
        '--azure_subscription_id', default='',
        help='The subscription id of your subscriptoin.')
    parser.add_argument(
        '--azure_tenant_id', default='',
        help='The AAD tenant id.')
    parser.add_argument(
        '--azure_client_id', default='',
        help='The service principal id.')
    parser.add_argument(
        '--azure_app_key', default='',
        help='The service principal secret.')

    super(AzureFront50TestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

  def _do_init_bindings(self):
    """Hook to initialize custom test bindings so journaling is scoped."""
    context = ExecutionContext()
    config = self.agent.deployed_config
    enabled = config.get('spinnaker.azs.enabled', False)
    if not enabled:
      raise ValueError('spinnaker.azs.enabled is not True')

    self.STORAGE_ACCOUNT_NAME = config['spinnaker.azs.storageAccountName']
    self.STORAGE_ACCOUNT_KEY = config['spinnaker.azs.storageAccountKey']
    self.STORAGE_CONTAINER_NAME = config['spinnaker.azs.storageContainerName']
    self.TEST_APP = self.bindings['TEST_APP']
    self.TEST_PIPELINE_NAME = 'My {app} Pipeline'.format(app=self.TEST_APP)
    self.TEST_PIPELINE_ID = '{app}-pipeline-id'.format(app=self.TEST_APP)
    #Integrate with Damien citest
    self.az_observer = az.AzureStorageAgent.make_agent(
        credentials_path=self.bindings['GCS_JSON_PATH'],
        scopes=(az.azure_storage_agent.STORAGE_FULL_SCOPE
                if self.bindings['GCS_JSON_PATH'] else None)
        )

    metadata = self.az_observer.inspect_bucket(context, self.BUCKET)
    self.versioning_enabled = (metadata.get('versioning', {})
                               .get('enabled', False))
    if not self.versioning_enabled:
      self.logger.info('bucket=%s versioning enabled=%s',
                       self.BUCKET, self.versioning_enabled)

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [Front50Agent] The agent for invoking the test operations on
          Front50
    """
    self.logger = logging.getLogger(__name__)
    super(AzureFront50TestScenario, self).__init__(bindings, agent)
    self.app_history = []
    self.pipeline_history = []

    self.initial_app_spec = {
        "name" : self.TEST_APP,
        "description" : "My Application Description.",
        "email" : "test@microsoft.com",
        "accounts" : "my-azure-account",
        "updateTs" : "1463667655844",
        "createTs" : "1463666817476",
        "platformHealthOnly" : False,
        "cloudProviders" : "gce,aws"
    }

    self.initial_pipeline_spec = {
        "keepWaitingPipelines": False,
        "limitConcurrent": True,
        "executionEngine": "v2",
        "application": self.TEST_APP,
        "parallel": True,
        "lastModifiedBy": "anonymous",
        "name": self.TEST_PIPELINE_NAME,
        "stages": [],
        "index": 0,
        "id": self.TEST_PIPELINE_ID,
        "triggers": []
    }


  def create_app(self):
    payload = self.agent.make_json_payload_from_object(self.initial_app_spec)
    expect = dict(self.initial_app_spec)
    expect['name'] = self.initial_app_spec['name'].upper()
    expect['lastModifiedBy'] = 'anonymous'

    contract = jc.Contract()

    # Note that curiosly the updated timestamp is not adjusted in the storage
    # file.
    azure_builder = az.AzureStorageContractBuilder(self.gcs_observer)
    (gcs_builder.new_clause_builder('Created Azure Storage Account Blob',
                                    retryable_for_secs=5)
     .list_bucket(self.BUCKET, '/'.join([self.BASE_PATH, 'applications']))
     .contains_path_value('name', self.TEST_APP))
    (az_builder.new_clause_builder('Wrote File Content')
     .retrieve_content(self.BUCKET,
                       '/'.join([self.BASE_PATH, 'applications', self.TEST_APP,
                                 'specification.json']),
                       transform=json.JSONDecoder().decode)
     .contains_match({key: jp.EQUIVALENT(value)
                      for key, value in expect.items()}))
    for clause in az_builder.build().clauses:
      contract.add_clause(clause)

    # The update timestamp is determined by the server,
    # and we dont know what that is, so lets ignore it
    # and assume the unit tests verify it is properly updated.
    expect = dict(expect)
    del expect['updateTs']
    self.app_history.insert(0, expect)
    f50_builder = st.http_observer.HttpContractBuilder(self.agent)

    # These clauses are querying the Front50 http server directly
    # to verify that it returns the application we added.
    # We already verified the data was stored on GCS, but while we
    # are here we will verify that it is also being returned when queried.
    (f50_builder.new_clause_builder('Lists Application')
     .get_url_path('/v2/applications')
     .contains_path_value('name', self.TEST_APP.upper()))
    (f50_builder.new_clause_builder('Returns Application')
     .get_url_path('/v2/applications')
     .contains_match({key: jp.EQUIVALENT(value)
                      for key, value in self.app_history[0].items()}))
    for clause in f50_builder.build().clauses:
      contract.add_clause(clause)

    path = '/v2/applications'
    return st.OperationContract(
        self.new_post_operation(
            title='create_app', data=payload, path=path),
        contract=contract)

class AzureFront50Test(st.AgentTestCase):
  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        AzureFront50TestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())


def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_APP': 'azureFront50test' + AzureFront50TestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[AzureFront50TestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[AzureFront50TestScenario])


if __name__ == '__main__':
  sys.exit(main())
