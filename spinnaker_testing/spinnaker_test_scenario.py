# Copyright 2015 Google Inc. All Rights Reserved.
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


"""Specialization of AgentTestScenario to facilitate testing Spinnaker.

This provides means for locating spinnaker and extracting configuration
information so that the tests can adapt to the deployment information
to make appropriate observations.
"""

import logging

import citest.service_testing as sk
import citest.service_testing.http_agent as http_agent
import citest.aws_testing as aws
import citest.gcp_testing as gcp


class SpinnakerTestScenario(sk.AgentTestScenario):
  """Specialization of AgentTestScenario to facilitate testing Spinnaker.

  Adds standard command line arguments for locating the deployed system, and
  setting up observers.
  """
  @classmethod
  def new_post_operation(cls, title, data, path, status_class=None):
    """Creates an operation that posts data to the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP POST.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpPostOperation(title=title, data=data, path=path,
                                        status_class=status_class)

  @classmethod
  def new_delete_operation(cls, title, data, path, status_class=None):
    """Creates an operation that deletes from the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP DELETE.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpDeleteOperation(title=title, data=data, path=path,
                                          status_class=status_class)

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    super(SpinnakerTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

    defaults = defaults or {}
    subsystem_name = 'the server to test'
    parser.add_argument(
        '--host_platform', default=defaults.get('HOST_PLATFORM', None),
        help='Platform running spinnaker (gce, native).'
             ' If this is not explicitly set, then try to'
             ' guess based on other parameters set.')

    # Native provider paramters used to locate Spinnaker.
    parser.add_argument(
        '--native_hostname', default=defaults.get('NATIVE_HOSTNAME', None),
        help='Host name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native".'.format(system=subsystem_name))

    parser.add_argument(
        '--native_port', default=defaults.get('NATIVE_PORT', None),
        help='Port number that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native". It is not needed if the system is using its'
             ' standard port.'.format(system=subsystem_name))


    # GCE Provider parameters used to locate Spinnaker itself.
    parser.add_argument(
        '--gce_project', default=defaults.get('GCE_PROJECT', None),
        help='The GCE project that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))

    parser.add_argument(
        '--gce_zone', default=defaults.get('GCE_ZONE', None),
        help='The GCE zone that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))
    parser.add_argument(
        '--gce_instance', default=defaults.get('GCE_INSTANCE', None),
        help='The GCE instance name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))

    parser.add_argument(
        '--gce_ssh_passphrase_file',
        default=defaults.get('GCE_SSH_PASSPHRASE_FILE', None),
        help='Specifying a file containing the SSH passphrase'
             ' will permit tunneling or the execution of remote'
             ' commands into the --gce_instance if needed.')

    # Google Cloud management parameters
    parser.add_argument(
        '--gce_credentials', default=defaults.get('GCE_CREDENTIALS', None),
        help='Spinnaker account name to use for test operations.'
             ' Only used when managing jobs running on GCE.'
             ' If left empty then use the configured primary account.')

    # AWS management parameters
    parser.add_argument(
        '--aws_profile', default=defaults.get('AWS_PROFILE', None),
        help='aws command-line tool --profile parameter when observing AWS.')

    parser.add_argument(
        '--aws_credentials', default=defaults.get('AWS_CREDENTIALS', 'default'),
        help='Spinnaker account name to use for test operations.'
             ' Only used when managing jobs running on AWS.')

    # Spinnaker Stuff
    parser.add_argument(
        '--managed_gce_project', dest='google_primary_managed_project_id',
        help='GCE project to test instances in'
             ' if not determined by {system}.'.format(system=subsystem_name))

    parser.add_argument(
        '--test_gce_zone',
        default=defaults.get('TEST_GCE_ZONE', 'us-central1-f'),
        help='The GCE zone to test generated instances in (when managing GCE).'
             ' This implies the GCE region as well.')

    parser.add_argument(
        '--test_gce_region',
        default=defaults.get('TEST_GCE_REGION', ''),
        help='The GCE region to test generated instances in (when managing'
             ' GCE). If not specified, then derive it fro --test_gce_zone.')

    parser.add_argument(
        '--test_aws_zone',
        default=defaults.get('TEST_AWS_ZONE', 'us-east-1c'),
        help='The AWS zone to test generated instances in (when managing AWS).'
             ' This implies the AWS region as well.')

    parser.add_argument(
        '--test_aws_region',
        default=defaults.get('TEST_AWS_REGION', ''),
        help='The GCE region to test generated instances in (when managing'
             ' AWS). If not specified, then derive it fro --test_aws_zone.')

    parser.add_argument(
        '--test_gce_image_name',
        default=defaults.get('TEST_GCE_IMAGE_NAME',
                             'ubuntu-1404-trusty-v20150909a'),
        help='Default Google Compute Engine image name to use when'
             ' creating test instances.')

    parser.add_argument(
        '--test_stack', default=defaults.get('TEST_STACK', 'test'),
        help='Default Spinnaker stack decorator.')

    parser.add_argument(
        '--test_app', default=defaults.get('TEST_APP', cls.__name__.lower()),
        help='Default Spinnaker application name to use with test.')


  @property
  def gce_observer(self):
    """The observer for inspecting GCE platform state, if configured."""
    return self._gce_observer

  @property
  def aws_observer(self):
    """The observer for inspecting AWS platform state, if configured."""
    return self._aws_observer

  def __init__(self, bindings, agent=None):
    """Constructor

    Args:
      bindings: [dict] The parameter bindings for overriding the test
         scenario configuration.
      agent: [SpinnakerAgent] The Spinnaker agent to bind to the scenario.
    """
    super(SpinnakerTestScenario, self).__init__(bindings, agent)
    agent = self.agent
    bindings = self._bindings  # base class made a copy

    if not self._bindings['TEST_GCE_ZONE']:
      self._bindings['TEST_GCE_ZONE'] = self._bindings['GCE_ZONE']
    if not self._bindings['TEST_AWS_ZONE']:
      self._bindings['TEST_AWS_ZONE'] = self._bindings['AWS_ZONE']

    if not self._bindings.get('TEST_GCE_REGION', ''):
      self._bindings['TEST_GCE_REGION'] = self._bindings['TEST_GCE_ZONE'][:-2]

    if not self._bindings.get('TEST_AWS_REGION', ''):
      self._bindings['TEST_AWS_REGION'] = self._bindings['TEST_AWS_ZONE'][:-1]
    self._update_bindings_with_subsystem_configuration(agent)

    if self._bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      self._gce_observer = gcp.GCloudAgent(
          project=self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'],
          zone=self._bindings['TEST_GCE_ZONE'],
          ssh_passphrase_file=self._bindings['GCE_SSH_PASSPHRASE_FILE'])
    else:
      self._gce_observer = None
      logger = logging.getLogger(__name__)
      logger.warning(
          '--managed_gce_project was not set nor could it be inferred.'
          ' Therefore, we will not be able to observe Google Compute Engine.')

    if self._bindings.get('AWS_PROFILE'):
      self._aws_observer = aws.AwsAgent(
          self._bindings['AWS_PROFILE'], self._bindings['TEST_AWS_REGION'])
    else:
      self._aws_observer = None
      logger = logging.getLogger(__name__)
      logger.warning(
          '--aws_profile was not set.'
          ' Therefore, we will not be able to observe Amazon Web Services.')

  def _update_bindings_with_subsystem_configuration(self, agent):
    """Helper function for setting agent bindings from actual configuration.

    This uses the agent's runtime_config, if available, to supply some
    abstract binding information so that the test can adapt to the deployment
    it is testing.
    """
    # pylint: disable=bad-indentation
    for key, value in agent.runtime_config.items():
        try:
          if self._bindings[key]:
            continue
        except KeyError:
          pass
        self._bindings[key] = value

    if not self._bindings['GCE_CREDENTIALS']:
      self._bindings['GCE_CREDENTIALS'] = self.agent.deployed_config.get(
          'providers.google.primaryCredentials.name', None)

    if not self._bindings['AWS_CREDENTIALS']:
      self._bindings['AWS_CREDENTIALS'] = self.agent.deployed_config.get(
          'providers.aws.primaryCredentials.name', None)

    if not self._bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      # Default to the project we are managing.
      self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = (
          self.agent.deployed_config.get(
              'providers.google.primaryCredentials.project', None))
      if not self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID']:
        # But if that wasnt defined then default to the subsystem's project.
        self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = (
            self._bindings['GCE_PROJECT'])
