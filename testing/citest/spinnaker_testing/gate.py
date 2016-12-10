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


"""Specialization of SpinnakerAgent for interacting with Kato subsystem."""


import json
import logging

from . import spinnaker as sk


class GateTaskStatus(sk.SpinnakerStatus):
  """Specialization of sk.SpinnakerStatus for accessing Gate 'tasks' status."""

  @classmethod
  def new(cls, operation, original_response):
    """Factory method.

    Args:
      operation: [AgentOperation] The operation this status is for.
      original_response: [string] The original JSON with the status identifier.

    Returns:
      sk.SpinnakerStatus for handling status from a Gate request.
    """
    return GateTaskStatus(operation, original_response)

  @property
  def timed_out(self):
    """True if status indicates the request timed out."""
    return (self.current_state == 'TERMINAL'
            and str(self.detail).find(' timed out.') > 0)

  @property
  def finished(self):
    """True if status indicates the request has finished."""
    return not self.current_state in ["NOT_STARTED", "RUNNING", None]

  @property
  def finished_ok(self):
    """True if status indicates the request has finished successfully."""
    return self.current_state == 'SUCCEEDED'

  def __init__(self, operation, original_response=None):
    """Construct a new Gate request status.

    Args:
      operation: [AgentOperation] The operation this status is for.
      original_response: [string] The original JSON with the status identifier.
    """
    super(GateTaskStatus, self).__init__(operation, original_response)

    if not original_response.ok():
      self._bind_error(original_response.error_message)
      self.current_state = 'HTTP_ERROR'
      return

    doc = None
    try:
      doc = json.JSONDecoder().decode(original_response.output)
    except (ValueError, TypeError):
      pass

    if isinstance(doc, dict):
      self._bind_detail_path(doc['ref'])
      self._bind_id(self.detail_path)
    else:
      self._bind_error("Invalid response='{0}'".format(original_response))
      self.current_state = 'CITEST_INTERNAL_ERROR'

  def _update_response_from_json(self, doc):
    """Updates abstract sk.SpinnakerStatus attributes from a Gate response.

    This is called by the base class.

    Args:
       doc: [dict] JSON Document object read from response payload.
    """
    self.current_state = doc['status']
    self._bind_exception_details(None)

    exception_details = None
    gate_exception = None
    variables = doc.get('variables', None)
    if variables:
      for elem in variables:
        if elem['key'] == 'exception':
          value = elem['value']
          exception_details = value['details']
        elif elem['key'] == 'kato.tasks':
          value = elem['value']
          for task in value:
            if 'exception' in task:
              gate_exception = task['exception']['message']
              break

    self._bind_exception_details(exception_details or gate_exception)


class GatePipelineStatus(sk.SpinnakerStatus):
  """Specialization of SpinnakerStatus for accessing Gate 'pipelines' status.
  """

  @classmethod
  def new(cls, operation, original_response):
    """Factory method.

    Args:
      operation: [AgentOperation] The operation this status is for.
      original_response: [string] The original JSON with the status identifier.

    Returns:
      sk.SpinnakerStatus for handling status from a Gate request.
    """
    return GatePipelineStatus(operation, original_response)

  @property
  def timed_out(self):
    """True if status indicates the request timed out."""
    return (self.current_state == 'TERMINAL'
            and str(self.detail).find(' timed out.') > 0)

  @property
  def finished(self):
    """True if status indicates the request has finished."""
    return not self.current_state in ["NOT_STARTED", "RUNNING", None]

  @property
  def finished_ok(self):
    """True if status indicates the request has finished successfully."""
    return self.current_state == 'SUCCEEDED'

  def __init__(self, operation, original_response=None):
    """Construct a new Gate request status.

    Args:
      operation: [AgentOperation] The operation this status is for.
      original_response: [string] The original JSON with the status identifier.
    """
    super(GatePipelineStatus, self).__init__(operation, original_response)
    self.__saw_pipeline = False

  def _update_response_from_json(self, doc):
    """Updates abstract sk.SpinnakerStatus attributes from a Gate response.

    This is called by the base class.

    Args:
       doc: [dict] JSON Document object read from response payload.
    """
    self._bind_exception_details(None)

    if not isinstance(doc, list):
      self._bind_error("Invalid response='{0}'".format(doc))
      self.current_state = 'CITEST_INTERNAL_ERROR'

    # It can take a while for the running pipelines to show up.
    if len(doc) == 0:
      return

    if not self.__saw_pipeline:
      logger = logging.getLogger(__name__)
      logger.info('Pipeline is now visible.')
      self.__saw_pipeline = True

    # TODO(lwander): We need a way to ensure we are monitoring the correct
    #                pipeline.
    doc = doc[0]
    if self.current_state != doc['status']:
      self.current_state = doc['status']
      logger = logging.getLogger(__name__)
      logger.info('Pipeline state is now %s', self.current_state)

    # TODO(ewiseblatt): 20160308
    # It looks like sometimes when we get an exception, the exception is
    # burried in one of the interior stages and not propagated back up to
    # the top level status. It's context is empty. So this might not
    # be working at all, or at least is incomplete.
    context = doc.get('context', {})
    exception_details = context.get('exception', None)
    self._bind_exception_details(exception_details)


class GateAgent(sk.SpinnakerAgent):
  """Specialization of SpinnakerAgent for Gate subsystem.

  This class just adds convienence methods specific to Gate.
  """

  def make_create_app_operation(self, bindings, application, account_name,
                                description=None):
    """Create a Gate operation that will create a new application.

    Args:
      bindings: [dict] key/value pairs including optional TEST_EMAIL.
      application: [string] Name of application to create.
      account_name: [string] Account name owning the application.
      description: [string] Text description field for the operation payload.

    Returns:
      AgentOperation.
    """
    email = bindings.get('TEST_EMAIL', 'testuser@testhost.org')
    payload = self.make_json_payload_from_kwargs(
        job=[{
            'type': 'createApplication',
            'account': account_name,
            'application': {
                'name': application,
                'description': description or 'Gate Testing Application',
                'email': email
            },
            'user': '[anonymous]'
        }],
        description='Create Application: ' + application,
        application=application)

    return self.new_post_operation(
        title='create_app', data=payload, path='tasks')

  def make_delete_app_operation(self, application, account_name):
    """Create a Gate operation that will delete an existing application.

    Args:
      application: [string] Name of application to create.
      account_name: [string] Spinnaker account deleting the application.

    Returns:
      AgentOperation.
    """
    payload = self.make_json_payload_from_kwargs(
        job=[{
            'type': 'deleteApplication',
            'account': account_name,
            'application': {'name': application},
            'user': '[anonymous]'
        }],
        description='Delete Application: ' + application,
        application=application)

    return self.new_post_operation(
        title='delete_app', data=payload, path='tasks')


def new_agent(bindings, port=8084):
  """Create an agent to interact with a Spinnaker Gate server.

  Args:
    bindings: [dict] Bindings that specify how to connect to the server.
       The actual parameters used depend on the hosting platform.
       The hosting platform is specified with 'host_platform'.
    port: [int] The port the server is listening on.

  Returns:
    sk.SpinnakerAgent connected to the specified gate server, or None.
    The agent will have an additional attributes:
       managed_project: The name of the project Spinnaker is managing.
       account_name: The account name that Spinnaker is configured to use.
  """
  spinnaker = GateAgent.new_instance_from_bindings(
      'gate', GateTaskStatus.new, bindings, port)
  return spinnaker

