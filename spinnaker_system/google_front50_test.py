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

import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.front50 as front50



class GoogleFront50TestScenario(sk.SpinnakerTestScenario):
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
        '--google_json_path', default='',
        help='The path to the google service credentials JSON file.')

    super(GoogleFront50TestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

  def _do_init_bindings(self):
    """Hook to initialize custom test bindings so journaling is scoped."""
    config = self.agent.deployed_config
    enabled = config.get('spinnaker.gcs.enabled', False)
    if not enabled:
      raise ValueError('spinnaker.gcs.enabled is not True')

    self.BUCKET = config['spinnaker.gcs.bucket']
    self.BASE_PATH = config['spinnaker.gcs.rootFolder']
    self.TEST_APP = self.bindings['TEST_APP']
    self.gcs_observer = gcp.GoogleCloudStorageAgent(
        self.bindings['GOOGLE_JSON_PATH'],
        gcp.google_cloud_storage_agent.FULL_SCOPE)

    metadata = self.gcs_observer.inspect(self.BUCKET)
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
    super(GoogleFront50TestScenario, self).__init__(bindings, agent)

    self.initial_app_spec = {
        "name" : self.TEST_APP,
        "description" : "My Application Description.",
        "email" : "test@google.com",
        "accounts" : "my-aws-account,my-google-account",
        "updateTs" : "1463667655844",
        "createTs" : "1463666817476",
        "platformHealthOnly" : False,
        "cloudProviders" : "gce,aws"
    }


  def create_app(self):
    payload = self.agent.make_json_payload_from_object(self.initial_app_spec)
    expect = dict(self.initial_app_spec)
    expect['name'] = self.initial_app_spec['name'].upper()

    contract = jc.Contract()

    # Note that curiosly the updated timestamp is not adjusted in the storage
    # file.
    gcs_builder = gcp.GoogleCloudStorageContractBuilder(self.gcs_observer)
    (gcs_builder.new_clause_builder('Created Google Cloud Storage File')
     .list(self.BUCKET, '/'.join([self.BASE_PATH, 'applications']))
     .contains_path_value('name', self.TEST_APP))
    (gcs_builder.new_clause_builder('Wrote File Content')
     .retrieve(self.BUCKET,
               '/'.join([self.BASE_PATH, 'applications', self.TEST_APP,
                         'specification.json']),
               transform=json.JSONDecoder().decode)
     .contains_path_eq('', expect))
    for clause in gcs_builder.build().clauses:
      contract.add_clause(clause)

    # The update timestamp is determined by the server,
    # and we dont know what that is, so lets ignore it
    # and assume the unit tests verify it is properly updated.
    expect = dict(expect)
    del expect['updateTs']
    f50_builder = st.http_observer.HttpContractBuilder(self.agent)

    # These clauses are querying the Front50 http server directly
    # to verify that it returns the application we added.
    # We already verified the data was stored on GCS, but while we
    # are here we will verify that it is also being returned when queried.
    (f50_builder.new_clause_builder('Lists Application')
     .get_url_path('/default/applications')
     .contains_path_value('name', self.TEST_APP.upper()))
    (f50_builder.new_clause_builder('Returns Application')
     .get_url_path('/'.join(['/default/applications/name', self.TEST_APP]))
     .contains_path_value('', expect))
    for clause in f50_builder.build().clauses:
      contract.add_clause(clause)

    path = '/'.join(['/default/applications/name', self.TEST_APP])
    return st.OperationContract(
        self.new_post_operation(
            title='create_app', data=payload, path=path),
        contract=contract)#builder.build())

  def update_app(self):
    spec = {}
    for name, value in self.initial_app_spec.items():
      if name == 'name':
        spec[name] = value
      elif name == 'cloudProviders':
        spec[name] = value + ',kubernetes'
      elif name in ['updateTs', 'createTs']:
        spec[name] = str(int(value) + 1)
      elif isinstance(value, basestring):
        spec[name] = 'NEW_' + value
    payload = self.agent.make_json_payload_from_object(spec)
    expect = dict(spec)

    # The actual update is determined by front50.
    # The createTs we gave ignored.
    # As before, the name is upper-cased.
    del expect['updateTs']
    expect['createTs'] = self.initial_app_spec['createTs']
    expect['name'] = self.initial_app_spec['name'].upper()

    # TODO(ewiseblatt) 20160524:
    # Add clauses that observe Front50 to verify the history method works
    # and that the get method is the current version.
    num_versions = 2 if self.versioning_enabled else 1
    builder = gcp.GoogleCloudStorageContractBuilder(self.gcs_observer)
    (builder.new_clause_builder('Google Cloud Storage Contains File')
     .list(self.BUCKET,
           '/'.join([self.BASE_PATH, 'applications', self.TEST_APP]),
           with_versions=True)
     .contains_path_value('name', self.TEST_APP,
                          min=num_versions, max=num_versions))
    (builder.new_clause_builder('Updated File Content')
     .retrieve(self.BUCKET,
               '/'.join([self.BASE_PATH, 'applications', self.TEST_APP,
                         'specification.json']),
               transform=json.JSONDecoder().decode)
     .contains_path_value('', expect))

    # TODO(ewiseblatt): 20160524
    # Add a mechanism here to check the previous version
    # so that we can verify version recovery as well.
    path = '/default/applications'
    return st.OperationContract(
        self.new_put_operation(
            title='update_app', data=payload, path=path),
        contract=builder.build())

  def destroy_app(self):
    contract = jc.Contract()

    app_url_path = '/'.join(['/default/applications/name', self.TEST_APP])

    f50_builder = st.http_observer.HttpContractBuilder(self.agent)
    (f50_builder.new_clause_builder('Unlists Application')
     .get_url_path('/default/applications')
     .excludes_path_value('name', self.TEST_APP.upper()))
    (f50_builder.new_clause_builder('Deletes Application')
     .get_url_path(app_url_path, allow_http_error_status=404))
    for clause in f50_builder.build().clauses:
      contract.add_clause(clause)

    gcs_builder = gcp.GoogleCloudStorageContractBuilder(self.gcs_observer)
    (gcs_builder.new_clause_builder('Deleted File')
     .list(self.BUCKET, '/'.join([self.BASE_PATH, 'applications']))
     .excludes_path_value('name', self.TEST_APP.upper()))
    for clause in gcs_builder.build().clauses:
      contract.add_clause(clause)

    return st.OperationContract(
        self.new_delete_operation(
            title='delete_app', data=None, path=app_url_path),
        contract=contract)


class GoogleFront50Test(st.AgentTestCase):
  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b_update_app(self):
    self.run_test_case(self.scenario.update_app())

  def test_z_destroy_app(self):
    self.run_test_case(self.scenario.destroy_app())


def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_APP': 'gcpfront50test' + GoogleFront50TestScenario.DEFAULT_TEST_ID
  }

  return st.ScenarioTestRunner.main(
      GoogleFront50TestScenario,
      default_binding_overrides=defaults,
      test_case_list=[GoogleFront50Test])


if __name__ == '__main__':
  sys.exit(main())
