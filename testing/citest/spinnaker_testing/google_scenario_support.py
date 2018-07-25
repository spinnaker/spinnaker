# Copyright 2017 Google Inc. All Rights Reserved.
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

"""Google Compute Engine platform and test support for SpinnakerTestScenario."""

import __main__
import atexit
import collections
import datetime
import json
import logging
import os

from citest.base import ExecutionContext
from citest.base import JournalLogger
import citest.gcp_testing as gcp


from citest.gcp_testing.api_investigator import ApiInvestigatorBuilder
from citest.gcp_testing.api_resource_scanner import ApiResourceScanner
from citest.gcp_testing.api_resource_diff import ApiDiff

from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class GoogleScenarioSupport(BaseScenarioPlatformSupport):
  """Provides SpinnakerScenarioSupport for Google Compute Engine."""

  @classmethod
  def add_commandline_parameters(cls, scenario_class, builder, defaults):
    """Implements BaseScenarioPlatformSupport interface.

    Args:
      scenario_class: [class spinnaker_testing.SpinnakerTestScenario]
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    # TODO(ewiseblatt): 20160923
    # This is probably obsoleted. It is only used by the gcloud agent,
    # which is only used to establish a tunnel. I dont think the credentials
    # are needed there, but your ssh passphrase is (which is unrelated).
    builder.add_argument(
        '--gce_service_account',
        default=defaults.get('GCE_SERVICE_ACCOUNT', None),
        help='The GCE service account to use when interacting with the'
             ' gce_instance. The default will be the default configured'
             ' account on the local machine. To change the default account,'
             ' use "gcloud config set account". To active service accounts,'
             ' use "gcloud auth activate-service-account".'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.')

    builder.add_argument(
        '--gce_credentials',
        dest='spinnaker_google_account',
        help='DEPRECATED. Replaced by --spinnaker_google_account')

    #
    # Location Parameters
    #
    builder.add_argument(
        '--gce_instance', default=defaults.get('GCE_INSTANCE', None),
        help='The GCE instance name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=scenario_class.ENDPOINT_SUBSYSTEM))

    builder.add_argument(
        '--gce_project', default=defaults.get('GCE_PROJECT', None),
        help='The GCE project that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=scenario_class.ENDPOINT_SUBSYSTEM))

    builder.add_argument(
        '--gce_zone', default=defaults.get('GCE_ZONE', None),
        help='The GCE zone that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=scenario_class.ENDPOINT_SUBSYSTEM))

    builder.add_argument(
        '--gce_ssh_passphrase_file',
        default=defaults.get('GCE_SSH_PASSPHRASE_FILE', None),
        help='Specifying a file containing the SSH passphrase'
             ' will permit tunneling or the execution of remote'
             ' commands into the --gce_instance if needed.')

    #
    # Operation Parameters
    #
    builder.add_argument(
        '--spinnaker_google_account',
        default=defaults.get('SPINNAKER_GOOGLE_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against GCE.'
             ' Only used when managing resources on GCE.'
             ' If left empty then use the configured primary account.')

    builder.add_argument(
        '--test_gce_image_name',
        default=defaults.get('TEST_GCE_IMAGE_NAME',
                             'ubuntu-1404-trusty-v20160919'),
        help='Default Google Compute Engine image name to use when'
             ' creating test instances.')

    builder.add_argument(
        '--test_gce_region',
        default=defaults.get('TEST_GCE_REGION', ''),
        help='The GCE region to test generated instances in (when managing'
             ' GCE). If not specified, then derive it from --test_gce_zone.')

    builder.add_argument(
        '--test_gce_zone',
        default=defaults.get('TEST_GCE_ZONE', 'us-central1-f'),
        help='The GCE zone to test generated instances in (when managing GCE).'
             ' This implies the GCE region as well.')

    #
    # Observer Parameters
    #
    builder.add_argument(
        '--managed_gce_project', dest='google_primary_managed_project_id',
        help='GCE project to test instances in (when managing GCE).')

    builder.add_argument(
        '--gce_credentials_path',
        default=defaults.get('GCE_CREDENTIALS_PATH', None),
        help='A path to the JSON file with credentials to use for observing'
             ' tests run against Google Cloud Platform.')

    #
    # Debugging Parameters
    #
    builder.add_argument(
        '--record_gcp_resource_usage',
        default=defaults.get(
            'RECORD_GCP_RESOURCE_USAGE',
            os.environ.get('RECORD_GCP_RESOURCE_USAGE', '').lower() == 'true'),
        help='Record difference in GCP resources observed before and after'
        ' test. Tests should be run in isolation to interpret literally.')
    builder.add_argument(
        '--gcp_resource_usage_log_path',
        default=defaults.get(
            'GCP_RESOURCE_USAGE_LOG_PATH',
            os.environ.get('GCP_RESOURCE_USAGE_LOG_PATH', '') or None),
        help='Append resource usage summaries into the file at path.')

  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings
    if not bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      raise ValueError('There is no "google_primary_managed_project_id"')

    return gcp.GcpComputeAgent.make_agent(
        scopes=(gcp.COMPUTE_READ_WRITE_SCOPE
                if bindings['GCE_CREDENTIALS_PATH'] else None),
        credentials_path=bindings['GCE_CREDENTIALS_PATH'],
        default_variables={
            'project': bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'],
            'region': bindings['TEST_GCE_REGION'],
            'zone': bindings['TEST_GCE_ZONE']
            })

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(GoogleScenarioSupport, self).__init__("google", scenario)
    bindings = scenario.bindings

    if not bindings['TEST_GCE_ZONE']:
      bindings['TEST_GCE_ZONE'] = bindings['GCE_ZONE']

    if not bindings.get('TEST_GCE_REGION', ''):
      bindings['TEST_GCE_REGION'] = bindings['TEST_GCE_ZONE'][:-2]

    if not bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      # Default to the project we are managing.
      bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = (
          scenario.agent.deployed_config.get(
              'providers.google.primaryCredentials.project', None))
      if not bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID']:
        # But if that wasnt defined then default to the subsystem's project.
        bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = bindings['GCE_PROJECT']



GcpApiResourceList = collections.namedtuple(
    'GcpApiResourceList', ['resource_list', 'errors'])

GcpResourceUsage = collections.namedtuple(
    'GcpResourceUsage',
    ['project_quota', 'region_quota', 'api_resource_list_map'])


class GcpResourceUsageAnalyzer(object):
  """A nonstandard class for inspecting Google Quota and Resources.

     This isnt for testing, rather is for analyzing the tests to help
     deterimine the quota requirements needed to run them so that we
     can orchestrate their execution consistent with the quota available.
  """

  @property
  def running_quota(self):
    """Return map of cumulative sum of diffed quota values seen so far."""
    return self.__running_quota

  @property
  def max_quota(self):
    """Return map of max running quota values seen for each region."""
    return self.__max_quota

  def __init__(self, scenario):
    self.__running_quota = {}
    self.__max_quota = {}
    self.__scenario = scenario
    self.__log_path = None
    if scenario.bindings.get('RECORD_GCP_RESOURCE_USAGE'):
      self.__log_path = scenario.bindings.get('GCP_RESOURCE_USAGE_LOG_PATH')
    self.__to_log_path('\n{now}\n{decorator}  {scenario} in {main}  {decorator}'.format(
        now=datetime.datetime.now(),
        decorator='*' * 5,
        scenario=scenario.__class__.__name__,
        main=os.path.splitext(os.path.basename(__main__.__file__))[0]))
    atexit.register(self.__log_quota_summary)

  def __log_quota_summary(self):
    self.__to_log_path(
        '--- FINAL RUNNING QUOTA --',
        detail=json.JSONEncoder(
            indent=2, separators=(',', ': ')).encode(self.__running_quota),
        indent=2)
    self.__to_log_path(
        '--- MAX FIXTURE QUOTA --',
        detail=json.JSONEncoder(
            indent=2, separators=(',', ': ')).encode(self.__max_quota),
        indent=2)

  def __to_log_path(self, heading, detail=None, indent=0):
    if self.__log_path:
      padding = '  ' * indent
      with open(self.__log_path, 'a') as fd:
        fd.write('%s%s\n' % (padding, heading))
        if detail:
          padding += '  '
          fd.write('%s%s\n' % (padding, detail.replace('\n', '\n' + padding)))

  def make_gcp_api_scanner(self, project, credentials_path,
                           include_apis=None, exclude_apis=None):
    """Create a GcpApiScanner for the given project and credentials."""
    builder = ApiInvestigatorBuilder()
    builder.include_apis = include_apis or ['compute']
    builder.exclude_apis = exclude_apis or ['compute.*Operations']
    investigator = builder.build()

    default_variables = {'project': project}
    return ApiResourceScanner(investigator, credentials_path,
                              default_variables=default_variables)

  def collect_resource_usage(self, gcp_agent, scanner, apis=None):
    """Returns current GcpResourceUsage"""

    def extract_quota(region_info):
      return {info['metric']: info['usage'] for info in region_info['quotas']}

    apis = apis or ['compute']
    item_filter = lambda item: True
    context = ExecutionContext()

    project_quota = extract_quota(gcp_agent.invoke_resource(
        context, 'get', 'projects'))

    region_info = gcp_agent.invoke_resource(context, 'list', 'regions')
    region_quota = {elem['name']: extract_quota(elem)
                    for elem in region_info['items']}

    api_resource_list_map = {}
    for api in apis:
      resources, errs = scanner.list_api(api, item_filter=item_filter)
      api_resource_list_map[api] = GcpApiResourceList(resources, errs)

    return GcpResourceUsage(project_quota, region_quota, api_resource_list_map)

  def log_delta_resource_usage(self, test_case, scanner, before, after):
    """Log quota usage and instances affected."""
    before_region = dict(before.region_quota)
    before_region['global'] = before.project_quota

    after_region = dict(after.region_quota)
    after_region['global'] = after.project_quota

    before_resources = {}
    after_resources = {}
    for api in before.api_resource_list_map.keys():
      before_resources[api] = before.api_resource_list_map[api].resource_list
      after_resources[api] = after.api_resource_list_map[api].resource_list

    api_diff = ApiDiff.make_api_resources_diff_map(
        scanner, before_resources, after_resources)

    self.__to_log_path('\nTEST: %s' % test_case.title, indent=1)
    self.__log_delta_quota(before_region, after_region)
    self.__log_api_diff(api_diff)

  def __update_running_quota(self, diff):
    for region, delta in diff.items():
      max_region = self.__max_quota.get(region, {})
      running_region = self.__running_quota.get(region, {})
      for name, value in delta.items():
        before = running_region.get(name, 0)
        after = before + value
        running_region[name] = after
        max_region[name] = max(after, max_region.get(name, 0))
      self.__running_quota[region] = running_region
      self.__max_quota[region] = max_region

  def __log_delta_quota(self, before, after):
    if before == after:
      logging.info('No GCP quota impact.')
      return

    diff = {}
    for region in after.keys():
      before_quota = before.get(region, {})
      after_quota = after.get(region, {})
      if before_quota == after_quota:
        continue
      delta = {metric: after_quota[metric] - before_quota[metric]
               for metric in after_quota.keys()
               if after_quota.get(metric) != before_quota.get(metric)}
      if delta:
        diff[region] = delta

    self.__update_running_quota(diff)
    self.__to_log_path(
        '--- QUOTA ---',
        detail=json.JSONEncoder(indent=2, separators=(',', ': ')).encode(diff),
        indent=2)
    JournalLogger.journal_or_log_detail('GCP Quota Impact',
                                        str(diff), format='json')

  def __log_api_diff(self, api_diff):
    text_list = []
    for api, diff in api_diff.items():
      added = diff.to_instances_added()
      removed = diff.to_instances_removed()
      if not (added or removed):
        continue
      text_list.append(api + ' Changes:')
      if added:
        text_list.append('+ ADDED:')
        for resource, instances in added.items():
          text_list.append('  %s' % resource)
          text_list.extend(['  - {!r}'.format(name) for name in instances])

      if removed:
        text_list.append('- REMOVED:')
        for resource, instances in removed.items():
          text_list.append('  %s' % resource)
          text_list.extend(['  - {!r}'.format(name) for name in instances])

    self.__to_log_path('--- RESOURCES ---',
                       detail='\n'.join(text_list) if text_list else 'None',
                       indent=2)

    if text_list:
      JournalLogger.journal_or_log_detail(
          'GCP Resource Impact', '\n'.join(text_list), format='pre')
    else:
      logging.info('No GCP resource impact')
