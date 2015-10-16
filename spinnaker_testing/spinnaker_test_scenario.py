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

import logging

import citest.service_testing as sk
import citest.service_testing.http_agent as http_agent
import citest.aws_testing as aws
import citest.gcp_testing as gcp


class SpinnakerTestScenario(sk.AgentTestScenario):
  @classmethod
  def new_post_operation(cls, title, data, path):
    return http_agent.HttpPostOperation(title=title, data=data, path=path)

  @classmethod
  def initArgumentParser(cls, parser, subsystem_name='the server'):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
      subsystem_name: The name of the subsystem we're testing is used to
         customize help messages for the argument parser.
    """
    super(SpinnakerTestScenario, cls).initArgumentParser(parser)

    parser.add_argument(
      '--host_platform',
      help='Platform running spinnaker (gce, native).'
           ' If this is not explicitly set, then try to'
           ' guess based on other parameters set.')

    # Native stuff
    parser.add_argument(
        '--native_hostname',
        help='Host name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native".'.format(system=subsystem_name))

    parser.add_argument(
        '--native_port',
        help='Port number that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native". It is not needed if the system is using its'
             ' standard port.'.format(system=subsystem_name))


    # GCE stuff
    parser.add_argument(
        '--gce_project',
        help='The GCE project that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))

    parser.add_argument(
        '--gce_zone',
        help='The GCE zone that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))
    parser.add_argument(
        '--gce_instance',
        help='The GCE instance name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))

    parser.add_argument(
        '--gce_ssh_passphrase_file',
        help='Specifying a file containing the SSH passphrase'
             ' will permit tunneling or the execution of remote'
             ' commands into the --gce_instance if needed.')

    parser.add_argument(
        '--gce_credentials',
        default='',
        help='Spinnaker account name to use for test operations.'
             ' Only used when managing jobs running on GCE.'
             ' If left empty then use the configured primary account.')

    # AWS stuff
    parser.add_argument(
        '--aws_profile',
        help='aws command-line tool --profile parameter when observing AWS.')
    parser.add_argument(
        '--aws_credentials', default='default',
        help='Spinnaker account name to use for test operations.'
             ' Only used when managing jobs running on AWS.')

    # Spinnaker Stuff
    parser.add_argument(
        '--managed_gce_project', dest='google_primary_managed_project_id',
        help='GCE project to test instances in'
             ' if not determined by {system}.'.format(system=subsystem_name))

    parser.add_argument(
        '--test_gce_zone',
        default='us-central1-f',
        help='The GCE zone to test generated instances in (when managing GCE).'
             ' This implies the GCE region as well.')

    parser.add_argument(
        '--test_aws_zone',
        default='us-east-1c',
        help='The AWS zone to test generated instances in (when managing AWS).'
             ' This implies the AWS region as well.')

  @property
  def gce_observer(self):
    return self._gce_observer

  @property
  def aws_observer(self):
    return self._aws_observer

  def __init__(self, bindings, agent):
    """Constructor

    Args:
      bindings: The parameter bindings for overriding the test scenario config.
      agent: The Spinnaker agent to bind to the scenario.
    """
    super(SpinnakerTestScenario, self).__init__(bindings, agent)
    bindings = self._bindings  # base class made a copy

    if not self._bindings['TEST_GCE_ZONE']:
      self._bindings['TEST_GCE_ZONE'] = self._bindings['GCE_ZONE']
    if not self._bindings['TEST_AWS_ZONE']:
      self._bindings['TEST_AWS_ZONE'] = self._bindings['AWS_ZONE']

    self._bindings['TEST_GCE_REGION'] = self._bindings['TEST_GCE_ZONE'][:-2]
    self._bindings['TEST_AWS_REGION'] = self._bindings['TEST_AWS_ZONE'][:-1]
    self._update_bindings_with_subsystem_configuration(agent)

    # !!! DEPRECATED(20150921)
    if not self._bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = self._bindings.get('MANAGED_GCE_PROJECT', '') or self._bindings.get('GOOGLE_MANAGED_PROJECT_ID')
    if not self._bindings.get('GOOGLE_PRIMARY_ACCOUNT_NAME'):
      self._bindings['GOOGLE_PRIMARY_ACCOUNT_NAME'] = self._bindings.get('ACCOUNT_NAME', '') or self._bindings.get('GOOGLE_ACCOUNT_NAME', '')

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
    for key,value in agent.runtime_config.items():
        try:
          if self._bindings[key]:
            continue
        except KeyError:
          pass
        self._bindings[key] = value

    if not self._bindings['GCE_CREDENTIALS']:
      self._bindings['GCE_CREDENTIALS'] = self._bindings.get(
          'GOOGLE_PRIMARY_ACCOUNT_NAME', None)

    if not self._bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      # Default to the project we are managing.
      self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = agent.runtime_config.get(
          'GOOGLE_PRIMARY_MANAGED_PROJECT_ID')
      if not self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID']:
        # But if that wasnt defined then default to the subsystem's project.
        self._bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = self._bindings['GCE_PROJECT']

