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
import traceback

from citest.base import JournalLogger

import citest.service_testing as sk
import citest.service_testing.http_agent as http_agent

from spinnaker_testing import aws_scenario_support
from spinnaker_testing import appengine_scenario_support
from spinnaker_testing import google_scenario_support
from spinnaker_testing import kubernetes_scenario_support
from spinnaker_testing import openstack_scenario_support
from spinnaker_testing import azure_scenario_support

PLATFORM_SUPPORT_CLASSES = [
    aws_scenario_support.AwsScenarioSupport,
    # appengine depends on google so order it after
    google_scenario_support.GoogleScenarioSupport,
    appengine_scenario_support.AppEngineScenarioSupport,
    kubernetes_scenario_support.KubernetesScenarioSupport,
    openstack_scenario_support.OpenStackScenarioSupport,
    azure_scenario_support.AzureScenarioSupport
]


class SpinnakerTestScenario(sk.AgentTestScenario):
  """Specialization of AgentTestScenario to facilitate testing Spinnaker.

  Adds standard command line arguments for locating the deployed system, and
  setting up observers.
  """
  ENDPOINT_SUBSYSTEM = 'the server to test'

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
  def new_put_operation(cls, title, data, path, status_class=None):
    """Creates an operation that puts data to the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP PUT.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpPutOperation(title=title, data=data, path=path,
                                       status_class=status_class)

  @classmethod
  def new_patch_operation(cls, title, data, path, status_class=None):
    """Creates an operation that patches data to the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP PATCH.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpPatchOperation(title=title, data=data, path=path,
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
  def _init_spinnaker_bindings_builder(cls, builder, defaults):
    """Initialize bindings for spinnaker itself.

    Args:
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    # This could probably be removed fairly easily.
    # It probably isnt useful anymore.
    builder.add_argument(
        '--host_platform', default=defaults.get('HOST_PLATFORM', None),
        help='Platform running spinnaker (gce, native).'
             ' If this is not explicitly set, then try to'
             ' guess based on other parameters set.')

    builder.add_argument(
        '--native_hostname', default=defaults.get('NATIVE_HOSTNAME', None),
        help='Host name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native".'.format(system=cls.ENDPOINT_SUBSYSTEM))

    builder.add_argument(
        '--network_protocol', default='http',
        help='The network protocol to use talking to the native port.'
        ' The default is "http", but this could be changed to "https".')

    builder.add_argument(
        '--native_port', default=defaults.get('NATIVE_PORT', None),
        help='Port number that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native". It is not needed if the system is using its'
             ' standard port.'.format(system=cls.ENDPOINT_SUBSYSTEM))

    builder.add_argument(
        '--test_stack', default=defaults.get('TEST_STACK', 'test'),
        help='Default Spinnaker stack decorator.')

    builder.add_argument(
        '--test_app', default=defaults.get('TEST_APP', cls.__name__.lower()),
        help='Default Spinnaker application name to use with test.')

  @classmethod
  def init_bindings_builder(cls, builder, defaults=None):
    """Initialize command line bindings.

    Args:
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    defaults = defaults or {}

    for klas in PLATFORM_SUPPORT_CLASSES:
      klas.add_commandline_parameters(cls, builder, defaults)

    cls._init_spinnaker_bindings_builder(builder, defaults=defaults)

    super(SpinnakerTestScenario, cls).init_bindings_builder(
        builder, defaults=defaults)

  @property
  def gcp_observer(self):
    """The observer for inspecting GCE platform stated.

    Raises:
      Exception if the observer is not available.
    """
    return self.__platform_support['google'].observer

  @property
  def kube_observer(self):
    """The observer for inspecting Kubernetes platform state."

    Raises:
      Exception if the observer is not available.
    """
    return self.__platform_support['kubernetes'].observer

  @property
  def aws_observer(self):
    """The observer for inspecting AWS platform state.

    Raises:
      Exception if the observer is not available.
    """
    return self.__platform_support['aws'].observer

  @property
  def appengine_observer(self):
    """The observer for inspecting App Engine platform state.

    Raises:
      Exception if the observer is not available.
    """
    return self.__platform_support['appengine'].observer

  @property
  def os_observer(self):
    """The observer for inspecting OpenStack platform state.

    Raises:
      Exception if the observer is not available.
    """
    return self.__platform_support['openstack'].observer

  @property
  def az_observer(self):
    """The observer for inspecting Azure platform state.

    Raises:
      Exception if the observer is not available.
    """
    return self.__platform_support['azure'].observer

  def __init__(self, bindings, agent=None):
    """Constructor

    Args:
      bindings: [dict] The parameter bindings for overriding the test
         scenario configuration.
      agent: [SpinnakerAgent] The Spinnaker agent to bind to the scenario.
    """
    super(SpinnakerTestScenario, self).__init__(bindings, agent)
    agent = self.agent
    bindings = self.bindings

    # For read-only tests that don't make mutating calls to Spinnaker,
    # there is nothing to update in the bindings, e.g. GCP quota test.
    if agent is not None:
      for key, value in agent.runtime_config.items():
        try:
          if bindings[key]:
            continue  # keep existing value already set within citest
        except KeyError:
          pass
        bindings[key] = value  # use value from agent's configuration

    JournalLogger.begin_context('Configure Scenario Bindings')
    self.__platform_support = {}
    for klas in PLATFORM_SUPPORT_CLASSES:
      try:
        support = klas(self)
        self.__platform_support[support.platform_name] = support
      except:
        logger = logging.getLogger(__name__)
        
        logger.exception('Failed to initialize support class %s:\n%s',
                         str(klas), traceback.format_exc())

    try:
      self._do_init_bindings()
    except:
      logger = logging.getLogger(__name__)
      logger.exception('Failed to initialize spinnaker agent.')
      raise
    finally:
      JournalLogger.end_context()

  def _do_init_bindings(self):
    """Hook for specific tests to add additional bindings."""
    pass
