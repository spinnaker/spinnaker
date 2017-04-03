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

"""Google App Engine platform and testing support for SpinnakerTestScenario."""

import citest.gcp_testing as gcp
from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class AppEngineScenarioSupport(BaseScenarioPlatformSupport):
  """Provides SpinnakerScenarioSupport for Google App Engine."""

  @classmethod
  def add_commandline_parameters(cls, scenario_class, builder, defaults):
    """Implements BaseScenarioPlatformSupport interface.

    Args:
      scenario_class: [class spinnaker_testing.SpinnakerTestScenario]
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    #
    # Operation Parameters
    #
    builder.add_argument(
        '--spinnaker_appengine_account',
        default=defaults.get('SPINNAKER_APPENGINE_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against'
             'App Engine. Only used when managing resources on App Engine.')

    #
    # Observer parameters
    #
    builder.add_argument(
        '--appengine_credentials_path',
        default=defaults.get('APPENGINE_CREDENTIALS_PATH', None),
        help='A path to the JSON file with credentials to use for observing'
             ' tests against Google App Engine. Defaults to the value set for'
             '--gce_credentials_path, which defaults to application default'
             ' credentials.')

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(AppEngineScenarioSupport, self).__init__("appengine", scenario)

    bindings = scenario.bindings

    if not bindings.get('APPENGINE_PRIMARY_MANAGED_PROJECT_ID'):
      bindings['APPENGINE_PRIMARY_MANAGED_PROJECT_ID'] = (
          scenario.agent.deployed_config.get(
              'providers.appengine.primaryCredentials.project', None))
      # Fall back on Google project and credentials.
      if not bindings['APPENGINE_PRIMARY_MANAGED_PROJECT_ID']:
        bindings['APPENGINE_PRIMARY_MANAGED_PROJECT_ID'] = (
            bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'])
        bindings['APPENGINE_CREDENTIALS_PATH'] = (
            bindings['GCE_CREDENTIALS_PATH'])

  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings
    if not bindings.get('APPENGINE_PRIMARY_MANAGED_PROJECT_ID'):
      raise ValueError('There is no "appengine_primary_managed_project_id"')

    return gcp.GcpAppengineAgent.make_agent(
        scopes=(gcp.APPENGINE_FULL_SCOPE
                if bindings['APPENGINE_CREDENTIALS_PATH']
                else None),
        credentials_path=bindings['APPENGINE_CREDENTIALS_PATH'],
        default_variables={
            'project': bindings['APPENGINE_PRIMARY_MANAGED_PROJECT_ID']})
