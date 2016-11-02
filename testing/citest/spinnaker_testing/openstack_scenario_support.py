# Copyright 2017 Veritas Inc. All Rights Reserved.
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

"""Openstack platform and test support for SpinnakerTestScenario."""

import citest.openstack_testing as os
from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class OpenStackScenarioSupport(BaseScenarioPlatformSupport):
  """Provides SpinnakerScenarioSupport for OpenStack."""

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

    # pylint: disable=line-too-long
    builder.add_argument(
        '--spinnaker_os_account',
        default=defaults.get('SPINNAKER_OS_ACCOUNT', None),
        help='Spinnaker account name to use when testing operations against'
             ' OpenStack. Only used when managing resources on OpenStack.')

    builder.add_argument(
        '--os_region_name',
        default=defaults.get('OS_REGION_NAME', None),
        help='The OpenStack region to test generated instances in '
             '  (when managing OpenStack).')

    builder.add_argument(
        '--test_os_username',
        default=defaults.get('TEST_OS_USERNAME', 'my-openstack-account'),
        help='The OpenStack authentication username for test operations.')

    #
    # Observer Parameters
    #
    builder.add_argument(
        '--os_cloud', default=defaults.get('OS_CLOUD', None),
        help='Cloud name. OpenStack will look for a clouds.yaml file that'
             ' contains a cloud configuration to use for authentication.')


  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings
    if not bindings.get('SPINNAKER_OS_ACCOUNT'):
      raise ValueError('There is no "spinnaker_os_account"')

    return os.OsAgent(bindings['OS_CLOUD'])

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(OpenStackScenarioSupport, self).__init__("openstack", scenario)

    bindings = scenario.bindings
    if not bindings['OS_REGION_NAME']:
      bindings['OS_REGION_NAME'] = scenario.agent.deployed_config.get(
          'providers.openstack.primaryCredentials.regions', None)
