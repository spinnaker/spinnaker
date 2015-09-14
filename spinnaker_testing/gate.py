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

import json

from . import spinnaker as sk


class _GateStatus(sk.SpinnakerStatus):
  """Specialization of sk.SpinnakerStatus for accessing Gate status."""

  @classmethod
  def new(cls, operation, original_response):
    """Factory method.

    Args:
      operation: The operation this status is for.
      original_response: The original JSON string with the status identifier.

    Returns:
      sk.SpinnakerStatus for handling status from a Gate request.
    """
    return _GateStatus(operation, original_response)

  @property
  def timed_out(self):
    """True if status indicates the request timed out."""
    return (self.current_state == 'TERMINAL'
            and str(self.detail).find(' timed out.') > 0)

  @property
  def finished(self):
    """True if status indicates the request has finished."""
    return not self._current_state in ["NOT_STARTED", "RUNNING", None]

  @property
  def finished_ok(self):
    """True if status indicates the request has finished successfully."""
    return self._current_state == 'SUCCEEDED'

  def __init__(self, operation, original_response=None):
    """Construct a new Gate request status.

    Args:
      operation: The operation this status is for.
      original_response: The original JSON string with the status identifier.
    """
    super(_GateStatus, self).__init__(operation, original_response)

    doc = None
    try:
      doc = json.JSONDecoder().decode(original_response.output)
    except ValueError:
      pass
    except TypeError:
      pass

    if isinstance(doc, dict):
      self._detail_path = doc['ref']
      self._request_id = self._detail_path
    else:
      self._error = "Invalid response='{0}'".format(original_response)
      self._current_state = 'INTERNAL_ERROR'

  def _update_response_from_json(self, doc):
    """Updates abstract sk.SpinnakerStatus attributes from a Gate response.

    This is called by the base class.

    Args:
       doc: JSON Document object read from response payload.
    """
    self._current_state = doc['status']
    self._exception_details = None

    exception_details = None
    kato_exception = None
    variables = doc['variables']
    if variables:
      for elem in variables:
        if elem['key'] == 'exception':
          value = elem['value']
          exception_details = value['details']
        elif elem['key'] == 'kato.tasks':
          value = elem['value']
          for task in value:
            if 'exception' in task:
              kato_exception = task['exception']['message']
              break

    self._exception_details = exception_details or kato_exception


class GateAgent(sk.SpinnakerAgent):
  """Specialization of SpinnakerAgent for Gate subsystem.

  This class just adds convienence methods specific to Gate.
  """

  @staticmethod
  def make_payload(job, description, application):
    """Make a gate operation JSON payload string from Python objects.

    Args:
       job: A dictionary containing the JSON encodable name/value pairs.
       description: String description for the payload.
       application: String application name for processing the payload.
    Returns:
       JSON encoded payload string for Gate request.
    """
    payload_dict = {
      'job': job, 'application': application, 'description': description }
    return json.JSONEncoder().encode(payload_dict)

  def make_create_app_operation(
        self, bindings, application, description=None):
    """Create a Gate operation that will create a new application.

    Args:
      bindings: dictionary containing key/value pairs for
          GCE_CREDENTIALS and optional TEST_EMAIL.
      application: Name of application to create.
      description: Text description field for the operation payload.
    Returns:
      AgentOperation.
    """
    account_name = bindings['GCE_CREDENTIALS']
    email = bindings.get('TEST_EMAIL', 'testuser@testhost.org')
    payload = self.make_payload(
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
            title='create_app', data=payload,
            path='applications/{name}/tasks'.format(name=application))

  def make_delete_app_operation(self, bindings, application):
    """Create a Gate operation that will delete an existing application.

    Args:
      bindings: dictionary containing key/value pairs for
          GCE_CREDENTIALS and optional TEST_EMAIL.
      application: Name of application to create.
    Returns:
      AgentOperation.
    """
    account_name = bindings['GCE_CREDENTIALS']
    payload = self.make_payload(
      job=[{
          'type': 'deleteApplication',
          'account': account_name,
          'application': { 'name': application },
          'user': '[anonymous]'
      }],
      description='Delete Application: ' + application,
      application=application)

    return self.new_post_operation(
            title='delete_app', data=payload,
            path='applications/{name}/tasks'.format(name=application))


def new_agent(bindings, port=8084):
  """Create an agent to interact with a Spinnaker Gate server.

  Args:
    bindings: Bindings that specify how to connect to the server.
       The actual parameters used depend on the hosting platform.
       The hosting platform is specified with 'host_platform'.
    port: The port the server is listening on.

  Returns:
    sk.SpinnakerAgent connected to the specified gate server, or None.
    The agent will have an additional attributes:
       managed_project: The name of the project Spinnaker is managing.
       account_name: The account name that Spinnaker is configured to use.
  """
  spinnaker = GateAgent.new_instance_from_bindings(
      'gate', _GateStatus.new, bindings, port)
  return spinnaker

