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

class BaseGateStatus(sk.SpinnakerStatus):
  """Common base class for gate status objects."""

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

  @property
  def finished(self):
    """True if status indicates the request has finished."""
    return self.current_state not in ['NOT_STARTED', 'RUNNING', None]

  @property
  def finished_ok(self):
    """True if status indicates the request has finished successfully."""
    return self.current_state == 'SUCCEEDED'

  def export_summary_to_json_snapshot(self, snapshot, entity):
    """Implements JsonSnapshotableEntity"""
    super(BaseGateStatus, self).export_summary_to_json_snapshot(
        snapshot, entity)
    detail = self.detail_doc
    if not detail:
      return

    builder = snapshot.edge_builder

    if not isinstance(detail, list):
      base_time = detail.get('startTime', 0)
      self._export_status_entry_summary_to_json_snapshot(
          detail, base_time, builder, snapshot, entity)
      return


    base_time = detail[0].get('startTime', 0)
    self._export_status(detail[0], builder, entity)
    self._export_time_info(detail[0], base_time, builder, entity)

    step_list_entity = (snapshot.new_entity(
        summary='{0} parts'.format(len(detail))))

    for index, step in enumerate(detail):
      step_entity = snapshot.new_entity()
      self._export_status_entry_summary_to_json_snapshot(
          step, base_time, builder, snapshot, step_entity)
      builder.make(step_list_entity,
                   '[{0}] {1}'.format(index, step.get('name')),
                   step_entity)
    builder.make(entity, 'Steps', step_list_entity)

  def _export_status_entry_summary_to_json_snapshot(
      self, status_entry, base_time, builder, snapshot, entity):
    self._export_status(status_entry, builder, entity)
    self._export_time_info(status_entry, base_time, builder, entity)
    self._export_error_info(status_entry, builder, entity)
    if 'execution' in status_entry:
      execution = status_entry['execution']
      execution_entity = snapshot.new_entity(summary='Execution Summary')
      relation = self._STATUS_TO_RELATION.get(execution.get('status'))
      builder.make(entity, 'Execution Info', execution_entity,
                   relation=relation)
      self._export_status_entry_summary_to_json_snapshot(
          execution, base_time, builder, snapshot, execution_entity)
      return

    stages = status_entry.get('stages')
    if not stages:
      return
    stage_list_entity = snapshot.new_entity(
        summary='{0} stages'.format(len(stages)))
    worst_relation_score = self._RELATION_SCORE.get(None)
    for index, stage in enumerate(stages):
      stage_entity = snapshot.new_entity()
      relation = self._export_stage_summary_to_json_snapshot(
          stage, base_time, builder, snapshot, stage_entity)
      score = self._RELATION_SCORE.get(relation)
      if score is not None and (
          worst_relation_score is None
          or worst_relation_score < score):
        worst_relation_score = score
      decorator = '(*) ' if stage.get('status') == 'RUNNING' else ''
      builder.make(stage_list_entity,
                   '[{0}] {1}{2}'.format(index, decorator, stage.get('name')),
                   stage_entity,
                   relation=relation)
    builder.make(entity, 'Stages', stage_list_entity,
                 relation=self._SCORE_TO_RELATION.get(worst_relation_score))

  def _maybe_export_stage_context(self, stage, builder, snapshot, entity):
    context = stage.get('context')
    if not context:
      return
    ex = context.get('exception')
    if not ex:
      return
    builder.make(entity, 'Exception Type', ex.get('exceptionType'),
                 relation='ERROR')
    errors = (ex.get('defaults') or ex.get('details', {})).get('errors')
    if errors:
      message = '\n'.join([' * ' + err for err in errors])
    else:
      message = ex.get('error')
    if message:
      builder.make(entity, 'Errors', message, relation='ERROR', format='pre')

  def _export_stage_summary_to_json_snapshot(
      self, stage, base_time, builder, snapshot, entity):
    stage_relation = self._export_status(stage, builder, entity)
    self._export_time_info(stage, base_time, builder, entity)
    self._maybe_export_stage_context(stage, builder, snapshot, entity)
    tasks = stage.get('tasks')
    if not tasks:
      return stage_relation

    task_list_entity = snapshot.new_entity(
        summary='{0} tasks'.format(len(tasks)))
    worst_relation_score = self._RELATION_SCORE.get(None)
    for index, task in enumerate(tasks):
      task_name = task.get('name')
      task_entity = snapshot.new_entity()
      relation = self._export_status(task, builder, task_entity)
      score = self._RELATION_SCORE.get(relation)
      if score is not None and (
          worst_relation_score is None
          or worst_relation_score < score):
        worst_relation_score = score
      self._export_time_info(task, base_time, builder, task_entity)
      builder.make(task_entity, 'Task Name', task_name)
      decorator = '(*) ' if task.get('status') == 'RUNNING' else ''
      builder.make(task_list_entity,
                   '[{0}] {1}{2}'.format(index, decorator, task_name),
                   task_entity,
                   relation=relation)
    builder.make(entity, 'Tasks', task_list_entity,
                 relation=self._SCORE_TO_RELATION.get(worst_relation_score))
    return stage_relation


class GateTaskStatus(BaseGateStatus):
  """Specialization of BaseGateStatus for accessing Gate 'tasks' status."""

  @property
  def timed_out(self):
    """True if status indicates the request timed out."""
    return (self.current_state == 'TERMINAL'
            and str(self.detail).find(' timed out.') > 0)

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
    """Updates abstract BaseGateStatus attributes from a Gate response.

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


class GatePipelineStatus(BaseGateStatus):
  """Specialization of BaseGateStatus for accessing Gate 'pipelines' status.
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
    if len(doc) == 0 or not isinstance(doc, list):
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
                'email': email,
                'platformHealthOnly': True
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
