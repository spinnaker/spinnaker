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

"""Interface for platform-specific support in SpinnakerTestScenario."""

import logging
import threading


class BaseScenarioPlatformSupport(object):
  """Interface for adding a specific platform to SpinnakerTestScenario."""

  @property
  def platform_name(self):
    """Returns the platform name bound at construction."""
    return self.__platform_name

  @property
  def scenario(self):
    """Returns the scenario instance bound at construction."""
    return self.__scenario

  @property
  def observer(self):
    """Returns the default observer for this platform as configured.

    Raises:
      This will throw an exception if the observer is not available for
      whatever reason. The reason may vary depending on the platform.
    """
    with self.__lock:
      if self.__observer is None:
        logger = logging.getLogger(__name__)
        logger.info('Initializing observer for "%s"', self.__platform_name)
        try:
          self.__observer = self._make_observer()
        except:
          logger.exception('Failed to create observer for "%s"',
                           self.__platform_name)
          raise
      return self.__observer

  @classmethod
  def init_bindings_builder(cls, scenario_class, builder, defaults):
    """Mediates to the specific methods in this interface.

    This is not intended to be overriden further. Instead, override
    the remaining methods.

    Args:
      scenario_class: [class spinnaker_testing.SpinnakerTestScenario]
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    cls.add_commandline_parameters(builder, defaults)

  @classmethod
  def add_commandline_parameters(cls, scenario_class, builder, defaults):
    """Adds commandline arguments to the builder.

    Args:
      scenario_class: [class spinnaker_testing.SpinnakerTestScenario]
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    raise NotImplementedError('{0} not implemented'.format(cls))

  def __init__(self, platform_name, scenario):
    """Constructor.

    This ensures the local bindings for:
       SPINNAKER_<platform>_ACCOUNT
       SPINNAKER_<platform>_ENABLED
    where <platform> is the platform_name or OS for openstack.
    It will use the scenario's deployed configuration if available and needed
    so that these variables will correspond to the agent's target.
    The default ACCOUNT is the configured primary account.

    Args:
      platform_name: [string]  Identifies which platform this is.
          This should be the name used in the Spinnaker "provider".

      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    self.__lock = threading.Lock()
    self.__observer = None
    self.__scenario = scenario
    self.__platform_name = platform_name
    test_platform_key = platform_name if platform_name != 'openstack' else 'os'

    bindings = scenario.bindings
    agent = scenario.agent
    account_key = 'spinnaker_{0}_account'.format(test_platform_key)
    if not bindings.get(account_key):
      bindings[account_key] = agent.deployed_config.get(
        'providers.{0}.primaryCredentials.name'.format(platform_name))

    enabled_key = 'spinnaker_{0}_enabled'.format(test_platform_key)
    if bindings.get(enabled_key, None) is None:
      bindings[enabled_key] =  agent.deployed_config.get(
          'providers.{0}.enabled'.format(platform_name))

  def _make_observer(self):
    """Hook for specialized classes to instantiate their observer.

    This method is called internally as needed when accessing the
    observer property.
    """
    raise NotImplementedError('{0} not implemented'.format(cls))

