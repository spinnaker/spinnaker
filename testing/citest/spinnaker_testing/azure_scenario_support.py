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

import citest.azure_testing as os
from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class AzureStackScenarioSupport(BaseScenarioPlatformSupport):
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
        '--azure_subscription_name', 
        dest='spinnaker_azure_account',
        help='The name of your subscriptoin.')
    builder.add_argument(
        '--azure_subscription_id', 
        dest='spinnaker_azure_subscription_id',
        help='The subscription id of your subscriptoin.')
    builder.add_argument(
        '--azure_tenant_id', 
        dest='spinnaker_azure_tenant_id',
        help='The AAD tenant id.')
    builder.add_argument(
        '--azure_client_id', 
        dest='spinnaker_azure_client_id',
        help='The service principal id.')
    builder.add_argument(
        '--azure_app_key', 
        dest='spinnaker_azure_app_key',
        help='The service principal secret.')
    builder.add_argument(
        '--azure_location', 
        dest='spinnaker_azure_location',
        help='The azure location to be used.')
    builder.add_argument(
        '--test_azure_location', 
        default = defaults.get('TEST_AZURE_LOCATION', 'westus'),
        help='The azure location to be used.')
    builder.add_argument(
        '--azure_account_name', 
        dest='spinnaker_azure_account_name',
        help='The name of the account in spinnaker.')
    builder.add_argument(
        '--azure_storage_account_key', 
        dest='spinnaker_azure_storage_account_key',
        help='The key used to access storage account used by front50.')


  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings  # base class made a copy
    if not bindings.get('TEST_AZURE_LOCATION'):
      bindings['TEST_AZURE_LOCATION'] = bindings['TEST_AZURE_LOCATION']
      self.__az_observer = None
    else:
      logger = logging.getLogger(__name__)
      logger.warning('TEST_AZURE_LOCATION binding found, Azure CLI could be used')
      self.__az_observer = az.AzAgent()

    return az.AzAgent(bindings['TEST_AZURE_LOCATION'])

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(AzureScenarioSupport, self).__init__("azure", scenario)

    if not bindings['SPINNAKER_AZURE_ACCOUNT']:
      bindings['SPINNAKER_AZURE_ACCOUNT'] = (
        scenario.AzAgent.deployed_config.get(
          'providers.azure.primaryCredentials.name', None))