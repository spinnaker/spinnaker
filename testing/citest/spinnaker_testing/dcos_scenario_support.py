# Copyright 2017 Cerner Corporation All Rights Reserved.
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

"""DC/OS platform and test support for SpinnakerTestScenario."""

import citest_contrib.dcos_testing as dcos
from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class DcosScenarioSupport(BaseScenarioPlatformSupport):
  """Provides SpinnakerScenarioSupport for DC/OS."""

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
        '--spinnaker_dcos_account',
        default=defaults.get('SPINNAKER_DCOS_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against'
             ' DC/OS. Only used when managing jobs running on'
             ' DC/OS.')

    builder.add_argument(
        '--spinnaker_dcos_cluster',
        default=defaults.get('SPINNAKER_DCOS_CLUSTER', None),
        help='The name of the DC/OS cluster to be used. This should be'
             ' the name of a cluster defined in the Spinnaker account'
             ' configuration for the account supplied with'
             ' --spinnaker_dcos_account.')

    builder.add_argument(
        '--spinnaker_docker_account',
        default=defaults.get('SPINNAKER_DOCKER_ACCOUNT', 'my-docker-registry-account'),
        help='The name of the Spinnaker Docker registry account to use.')

  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings
    if not bindings.get('SPINNAKER_DCOS_ACCOUNT'):
      raise ValueError('There is no "spinnaker_dcos_account"')

    if not bindings.get('SPINNAKER_DCOS_CLUSTER'):
      raise ValueError('There is no "spinnaker_dcos_cluster"')

    return dcos.DcosCliAgent()

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(DcosScenarioSupport, self).__init__("dcos", scenario)
