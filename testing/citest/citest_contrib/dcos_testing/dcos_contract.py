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

"""Provides a means for specifying and verifying expectations of DC/OS."""

# Standard python modules.
import json
import logging
import traceback

# Our modules.
import citest.json_predicate as jp
import citest.json_contract as jc
from citest.service_testing import cli_agent

class DcosObjectObserver(jc.ObjectObserver):
  """Observe DC/OS resources."""

  def __init__(self, dcoscli, args, filter=None):
    """Construct observer.

    Args:
      dcoscli: DcosCliAgent instance to use.
      args: Command-line argument list to execute.
    """
    super(DcosObjectObserver, self).__init__(filter)
    self.__dcoscli = dcoscli
    self.__args = args

  def export_to_json_snapshot(self, snapshot, entity):
    """Implements JsonSnapshotableEntity interface."""
    snapshot.edge_builder.make_control(entity, 'Args', self.__args)
    super(DcosObjectObserver, self).export_to_json_snapshot(snapshot, entity)

  def __str__(self):
    return 'DcosObjectObserver({0})'.format(self.__args)

  def collect_observation(self, context, observation):
    args = context.eval(self.__args)
    dcos_response = self.__dcoscli.run(args)
    if not dcos_response.ok():
      observation.add_error(
          cli_agent.CliAgentRunError(self.__dcoscli, dcos_response))
      return []

    decoder = json.JSONDecoder()
    try:
      doc = decoder.decode(dcos_response.output)
      if not isinstance(doc, list):
        doc = [doc]
      self.filter_all_objects_to_observation(context, doc, observation)
    except ValueError as vex:
      error = 'Invalid JSON in response: %s' % str(dcos_response)
      logging.getLogger(__name__).info('%s\n%s\n----------------\n',
                                       error, traceback.format_exc())
      observation.add_error(jp.JsonError(error, vex))
      return []

    return observation.objects


class DcosObjectFactory(object):
  # pylint: disable=too-few-public-methods

  def __init__(self, dcoscli):
    self.__dcoscli = dcoscli

  def new_get_marathon_resources(self, type, action, extra_args=None):
    """Specify a resource list to be returned later.

    Args:
      type: dcos's name for the DC/OS resource type.

    Returns:
      A jc.ObjectObserver to return the specified resource list when called.
    """
    if extra_args is None:
      extra_args = []

    cmd = self.__dcoscli.build_dcoscli_command_args(
        subcommand='marathon', resource=type, action=action, args=['--json'] + extra_args)
    return DcosObjectObserver(self.__dcoscli, cmd)


class DcosClauseBuilder(jc.ContractClauseBuilder):
  """A ContractClause that facilitates observing DC/OS state."""

  def __init__(self, title, dcoscli, retryable_for_secs=0, strict=False):
    """Construct new clause.

    Args:
      title: The string title for the clause is only for reporting purposes.
      dcoscli: The DcosCliAgent to make the observation for the clause to
         verify.
      retryable_for_secs: Number of seconds that observations can be retried
         if their verification initially fails.
      strict: DEPRECATED flag indicating whether the clauses (added later)
         must be true for all objects (strict) or at least one (not strict).
         See ValueObservationVerifierBuilder for more information.
         This is deprecated because in the future this should be on a per
         constraint basis.
    """
    super(DcosClauseBuilder, self).__init__(
        title=title, retryable_for_secs=retryable_for_secs)
    self.__factory = DcosObjectFactory(dcoscli)
    self.__strict = strict

  def get_marathon_resources(self, type, extra_args=None):
    """Observe resources of a particular type.

    This ultimately calls a "dcos marathon |type| |extra_args|"

    """
    self.observer = self.__factory.new_get_marathon_resources(
        type, action='list', extra_args=extra_args)

    get_builder = jc.ValueObservationVerifierBuilder(
        'Get {0} {1}'.format(type, extra_args), strict=self.__strict)
    self.verifier_builder.append_verifier_builder(get_builder)

    return get_builder


class DcosContractBuilder(jc.ContractBuilder):
  """Specialized contract that facilitates observing DC/OS."""

  def __init__(self, dcoscli):
    """Constructs a new contract.

    Args:
      kubectl: The DcosCliAgent to use for communicating with DC/OS.
    """
    super(DcosContractBuilder, self).__init__(
        lambda title, retryable_for_secs=0, strict=False:
        DcosClauseBuilder(
            title, dcoscli=dcoscli,
            retryable_for_secs=retryable_for_secs, strict=strict))
