# Copyright 2016 Google Inc. All Rights Reserved.
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

from citest import service_testing
import spinnaker_testing.spinnaker as sk


class Front50Status(service_testing.HttpOperationStatus):
  @classmethod
  def new(cls, operation, original_response):
    """Factory method.

    Args:
      operation: [AgentOperation] The operation this status is for.
      original_response: [string] The original JSON with the status identifier.

    Returns:
      sk.SpinnakerStatus for handling status from a Gate request.
    """
    return cls(operation, original_response)


class Front50Agent(sk.SpinnakerAgent):
  pass


def new_agent(bindings, port=8080):
  """Create an agent to interact with a Spinnaker Front50 server.

  Args:
    bindings: [dict] Bindings that specify how to connect to the server.
       The actual parameters used depend on the hosting platform.
       The hosting platform is specified with 'host_platform'.
    port: [int] The port the server is listening on.

  Returns:
    sk.Front50Agent connected to the specified gate server, or None.
    The agent will have an additional attributes:
       managed_project: The name of the project Spinnaker is managing.
       account_name: The account name that Spinnaker is configured to use.
  """
  spinnaker = Front50Agent.new_instance_from_bindings(
      'front50', Front50Status.new, bindings, port)
  return spinnaker

