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

"""Kubernetes platform and test support for SpinnakerTestScenario."""

import citest.kube_testing as kube
from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class KubernetesScenarioSupport(BaseScenarioPlatformSupport):
  """Provides SpinnakerScenarioSupport for Kubernetes."""

  @classmethod
  def add_commandline_parameters(cls, scenario_class, builder, defaults):
    """Implements BaseScenarioPlatformSupport interface.

    Args:
      scenario_class: [class spinnaker_testing.SpinnakerTestScenario]
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    builder.add_argument(
        '--kube_credentials',
        dest='spinnaker_kubernetes_account',
        help='DEPRECATED. Replaced by --spinnaker_kubernetes_account')

    #
    # Operation Parameters
    #
    builder.add_argument(
        '--spinnaker_kubernetes_account',
        default=defaults.get('SPINNAKER_KUBERNETES_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against'
             ' Kubernetes. Only used when managing jobs running on'
             ' Kubernetes.')

  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings
    if not bindings.get('SPINNAKER_KUBERNETES_ACCOUNT'):
      raise ValueError('There is no "spinnaker_kubernetes_account"')

    return kube.KubeCtlAgent()

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(KubernetesScenarioSupport, self).__init__("kubernetes", scenario)
