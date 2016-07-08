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

"""Test scenario to see if there is enough quota capacity on GCP.
"""
import logging
import sys

# citest modules.
import citest.gcp_testing as gcp
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk


# TODO(jacobkiefer): Make a more general 'lambda' Predicate to replace this.
class QuotaPredicate(jp.ValuePredicate):
  """Specialization to check whether GCP quotas are over-utilized."""

  COMMENT = '''Usage of {scope} metric {metr} above safe usage threshold.
Clean up {proj} and re-fire the tests.'''

  def __init__(self, scope, project, threshold, quota_set):
    """Constructor.

    Args:
      project [string]: GCE project we are checking the quota in.
      threshold [float]: Quota threshold percentage between 0 and 100. If
        exceeded, we don't have enough resources in GCP and the test fails.
    """
    self.__scope = scope
    self.__quota_set = quota_set
    self.__project = project
    self.__threshold = threshold
    self.__comment = 'GCE Quota threshold predicate.'
    self.__logger = logging.getLogger(__name__)

  def __call__(self, value):
    """Implements ValuePredicate."""
    for quota in value['quotas']:
      if self.__quota_set and quota['metric'] not in self.__quota_set:
        continue
      percent_usage = (quota['usage'] / quota['limit']) * 100.0
      if percent_usage >= self.__threshold:
        comment = QuotaPredicate.COMMENT.format(scope=self.__scope,
                                                metr=quota['metric'],
                                                proj=self.__project)
        self.__logger.error(comment)
        return jp.PredicateResult(False, comment)
    return jp.PredicateResult(True, '')

  def export_to_json_snapshot(self, snapshot, entity):
    """Implements JsonSnapshotable interface."""
    snapshot.edge_builder.make_output(entity,
                                      'Quota threshold',
                                      self.__threshold)

  def __repr__(self):
    return str(self)

  def __str__(self):
    return (self.__comment
            or '{0} is {1}'.format(self.__class__.__name__,
                                   'OK' if self.__valid else 'FAILURE'))

  def __nonzero__(self):
    return self.__valid

  def __eq__(self, result):
    if (self.__class__ != result.__class__
        or self.__valid != result.valid
        or self.__comment != result.comment):
      return False

    # If cause was an exception then just compare classes.
    # Otherwise compare causes.
    # We assume cause is None, and Exception, or another PredicateResult.
    # Exceptions do not typically support value equality.
    if self.__cause != result.cause:
      return (isinstance(self.__cause, Exception)
              and self.__cause.__class__ == result.cause.__class__)
    return self.__cause == result.cause

  def __ne__(self, result):
    return not self.__eq__(result)


class GoogleQuotaTestScenario(sk.SpinnakerTestScenario):
  """Defines the test scenario for the quota test.

  This test scenario will be included and run in each test
  case where we may experience quota throttling. It's intended
  to be a precondition that says 'we have enough quota capacity (%)
  to successfully run this test without getting 403 responses from GCP.'
  """

  @classmethod
  def new_agent(cls, bindings):
    """Implements citest.service_testing.AgentTestScenario.new_agent."""
    return None # We aren't interacting with Spinnaker at all here.

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(GoogleQuotaTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

    parser.add_argument('--quota_threshold',
                        type=float,
                        default=70.0,
                        help='Quota threshold percentage.')

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
        This is not used for this test, but needed for superclasses.
    """
    zone = bindings['GCE_ZONE']
    self.__service_account = bindings['GCE_SERVICE_ACCOUNT']
    self.__gcloud = gcp.GCloudAgent(bindings['GCE_PROJECT'],
                                    zone)
    self.__logger = logging.getLogger(__name__)
    self.__project = bindings['GCE_PROJECT']
    self.__region = zone[:zone.rfind('-')]
    self.__threshold = bindings['QUOTA_THRESHOLD']
    if self.__threshold <= 0.0 or self.__threshold >= 100.0:
      self.__logger.error(
        'Invalid value for arg quota_threshold: must be between 0.0 and 100.0.')
      raise ValueError(
        'Invalid value for arg quota_threshold: must be between 0.0 and 100.0.')
    super(GoogleQuotaTestScenario, self).__init__(bindings, agent)

  def check_quotas(self):
    """Creates an OperationContract that checks GCE quotas."""
    contract_builder = gcp.GceContractBuilder(self.__gcloud)

    global_clause_builder = contract_builder.new_clause_builder(
      'Global Quota Check'
    )
    extra_args = ['--account', self.__service_account]
    global_verifier = (global_clause_builder
                       .inspect_resource('project-info', None,
                                         extra_args=extra_args))
    global_quotas = {
      'IMAGES',
      'IN_USE_ADDRESSES',
      'INSTANCE_TEMPLATES',
      'STATIC_ADDRESSES',
      'TARGET_POOLS',
    }
    global_verifier.add_constraint(QuotaPredicate('global',
                                                  self.__project,
                                                  self.__threshold,
                                                  global_quotas))

    regional_clause_builder = contract_builder.new_clause_builder(
      'Regional Quota Check'
    )
    regional_verifier = (regional_clause_builder
                         .inspect_resource('regions', self.__region,
                                           extra_args=extra_args))
    regional_verifier.add_constraint(QuotaPredicate('regional',
                                                    self.__project,
                                                    self.__threshold,
                                                    set())) # check all quotas

    return st.OperationContract(
      st.NoOpOperation('GCE quota check.'),
      contract=contract_builder.build()
    )


class GoogleQuotaTest(st.AgentTestCase):
  """The test fixture for QuotaTest."""

  def test_a_check_quotas(self):
    self.run_test_case(self.scenario.check_quotas())


def main():
  """Implements the method running this quota test."""

  defaults = {
    'GCE_PROJECT': 'spinnaker-build',
    'GCE_ZONE': 'us-central1-f',
    'GCE_SERVICE_ACCOUNT': 'builder@spinnaker-build.iam.gserviceaccount.com',
  }

  return st.ScenarioTestRunner.main(
    GoogleQuotaTestScenario,
    default_binding_overrides=defaults,
    test_case_list=[GoogleQuotaTest]
  )

if __name__ == '__main__':
  sys.exit(main())
