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

# Standard python modules.
import logging

# Our modules.
from citest.service_testing import cli_agent
from citest.base.json_scrubber import JsonScrubber

class DcosCliAgent(cli_agent.CliAgent):
  """Agent that uses the DC/OS CLI (dcos) program to interact with DC/OS."""

  def __init__(self, logger=None):
    """Construct instance.

    Args:
      logger: The logger to inject if other than the default.
    """
    logger = logger or logging.getLogger(__name__)
    super(DcosCliAgent, self).__init__(
        'dcos', output_scrubber=JsonScrubber(), logger=logger)

  @staticmethod
  def build_dcoscli_command_args(subcommand, resource=None, action=None, args=None):
    """Build commandline for an action.
 
    Args:
      subcommand: The dcos subcommand to execute
      resource: The dcos resource we are going to operate on (if applicable).
      action: The action to take on the resource (if applicable).
      args: The arguments following [gcloud_module, gce_type].
 
    Returns:
      list of complete command line arguments following implied 'dcos'
    """
    return [subcommand] + ([resource] if resource else []) + ([action] if action else []) + (args if args else [])
