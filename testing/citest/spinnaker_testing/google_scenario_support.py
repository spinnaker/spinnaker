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

import citest.gcp_testing as gcp
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
