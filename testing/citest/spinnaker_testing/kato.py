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


# TODO(ewiseblatt): 20151125
# This could probably become clouddriver.
"""Specialization of SpinnakerAgent for interacting with Kato subsystem."""


import json
import spinnaker_testing.spinnaker as sk


class _KatoStatus(sk.SpinnakerStatus):
  """Specialization of sk.SpinnakerStatus for accessing Kato status."""

  @classmethod
  def new(cls, operation, original_response):
    """Factory method.

    Args:
      operation: The operation this status is for.
      original_response: The original JSON string with the status identifier.

    Returns:
      sk.SpinnakerStatus for handling status from a Kato request.
    """
    return _KatoStatus(operation, original_response)

  @property
  def finished(self):
    return self.__finished

  @property
  def finished_ok(self):
    return self.__finished and not self.__failed

  @property
  def timed_out(self):
    return False

  def __init__(self, operation, original_response=None):
    """Construct a new Kato request status.

    Args:
      operation: The operation this status is for.
      original_response: The original JSON string with the status identifier.
    """
    super(_KatoStatus, self).__init__(operation, original_response)

    if not original_response.ok():
      self.__finished = True
      self.__failed = True
      return

    self.__finished = False
    self.__failed = False

    doc = None
    try:
      doc = json.JSONDecoder().decode(original_response.output)
    except ValueError:
      pass
    except TypeError:
      pass

    if isinstance(doc, dict):
      self._bind_detail_path(doc['resourceUri'])
      self._bind_id(doc['id'])
    else:
      self._bind_error('Invalid response="{0}"'.format(original_response))
      self.__finished = True
      self.__failed = True
      self.current_state = 'CITEST_INTERNAL_ERROR'

  def _update_response_from_json(self, doc):
    """Updates abstract SpinnakerStatus attributes from a Kato response.

    This is called by the base class.
    """
    status = doc['status']
    failed = status['failed']
    self.current_state = status['phase']
    self._bind_exception_details(None)
    if status['completed']:
      self.__finished = True
      self.__failed = failed
    if failed:
      self._bind_exception_details(status['status'])


class KatoAgent(sk.SpinnakerAgent):
  """Specialization of SpinnakerAgent for Kato subsystem.

  This class just adds convienence methods specific to Kato.
  """

  @staticmethod
  def type_to_payload(name, payload_dict):
    """Make a kato operation JSON payload string.

    Args:
       name: The kato type name of the payload is used to
         build a payload dictionary in the form {[name: payload_dict]}.
       payload_dict: The value of the payload content.

    Returns:
       JSON encoded payload string for Kato request.
    """
    return KatoAgent.make_json_payload_from_object([{name: payload_dict}])


def new_agent(bindings, port=7002):
  """Create agent to interact with a Spinnaker Kato server.

  Args:
    bindings: Bindings that specify how to connect to the server.
       The actual parameters used depend on the hosting platform.
       The hosting platform is specified with 'host_platform'.
    port: The port the server is listening on.

  Returns:
    sk.SpinnakerAgent connected to the specified kato server, or None.
  """
  kato = KatoAgent.new_instance_from_bindings(
      'kato', _KatoStatus.new, bindings, port)
  return kato
