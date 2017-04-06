# Copyright 2017 Microsoft Inc. All Rights Reserved.
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

"""Azure platform and test support for SpinnakerTestScenario."""

import citest.azure_testing as az
from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class AzureScenarioSupport(BaseScenarioPlatformSupport):
  """Provides SpinnakerScenarioSupport for Azure."""

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
        '--test_azure_rg_location', 
        default = defaults.get('TEST_AZURE_RG_LOCATION', 'westus'),
        help='The location of the azure resource group where test resources should be created.')
    builder.add_argument(
        '--azure_storage_account_name', 
        dest='azure_storage_account_name',
        help='The name of the Azure storage account used by Front50 in Spinnaker.')
    builder.add_argument(
        '--azure_storage_account_key', 
        dest='spinnaker_azure_storage_account_key',
        help='The key used to access storage account used by front50.')

  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings 
    if not bindings.get('TEST_AZURE_RG_LOCATION'):
      raise ValueError('There is no location specified')

    return az.AzAgent(bindings['TEST_AZURE_RG_LOCATION'])

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(AzureScenarioSupport, self).__init__("azure", scenario)
    self.__az_observer = None

    bindings = scenario.bindings
    if not bindings['SPINNAKER_AZURE_ACCOUNT']:
      bindings['SPINNAKER_AZURE_ACCOUNT'] = (scenario.agent.deployed_config.get(
          'providers.azure.primaryCredentials.name', None))
