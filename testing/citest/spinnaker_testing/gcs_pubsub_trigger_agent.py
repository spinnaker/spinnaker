# Copyright 2018 Google Inc. All Rights Reserved.
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


import logging
import time

from citest.service_testing import base_agent

from google.cloud import storage

from spinnaker_testing.gate import GateAgent


class GcsFileUploadAgent(base_agent.BaseAgent):
  """Specialization to upload files to GCS.
  """
  def __init__(self, credentials_path=None, logger=None):
    super(GcsFileUploadAgent, self).__init__(logger=logger)
    if credentials_path:
      self.__client = storage.Client.from_service_account_json(
          credentials_path)
    else:
      self.__client = storage.Client()

    # Allow up to 13 minutes to wait on operations.
    # 13 minutes is arbitrary. The current test takes around 6-7 minutes
    # end-to-end. Other use cases might make it more clear what this should be.
    self.default_max_wait_secs = 780

  def upload_string(self, bucket_name, upload_path, contents):
    """Uploads a local file to a bucket at a relative upload path.
    """
    logging.info('Uploading string to bucket %s at path %s',
                 bucket_name, upload_path)
    bucket = self.__client.get_bucket(bucket_name)
    upload_blob = bucket.blob(upload_path)
    upload_blob.upload_from_string(contents)

  def upload_file(self, bucket_name, upload_path, local_filename):
    """Uploads a local file to a bucket at a relative upload path.
    """
    logging.info('Uploading local file %s to bucket %s at path %s',
                 local_filename, bucket_name, upload_path)
    bucket = self.__client.get_bucket(bucket_name)
    upload_blob = bucket.blob(upload_path)
    upload_blob.upload_from_filename(filename=local_filename)

  def export_to_json_snapshot(self, snapshot, entity):
    super(GcsFileUploadAgent, self).export_to_json_snapshot(snapshot, entity)

  def new_gcs_pubsub_trigger_operation(
      self, gate_agent, title, bucket_name, upload_path,
      local_filename, status_class, status_path):
    return GcsPubsubUploadTriggerOperation(
        title, self, gate_agent, bucket_name, upload_path,
        local_filename, status_class, status_path)


class BaseGcsPubsubTriggerOperation(base_agent.AgentOperation):
  """Specialization for base gcs pubsub trigger operations.
  """
  def __init__(self, title, gcs_pubsub_agent, gate_agent, **kwargs):
    """Construct operation

    Args:
      gcs_pubsub_agent: [GcsFileUploadAgent] agent to perform operation
      gate_agent: [GateAgent] agent used for monitoring status
    """
    if (not gcs_pubsub_agent
        or not isinstance(gcs_pubsub_agent, GcsFileUploadAgent)):
      raise TypeError('gcs_pubsub_agent is not a GcsFileUploadAgent: '
                      + gcs_pubsub_agent.__class__.__name__)
    if (not gate_agent or not isinstance(gate_agent, GateAgent)):
      raise TypeError('gate_agent is not a GateAgent: '
                      + gate_agent.__class__.__name__)
    super(BaseGcsPubsubTriggerOperation, self).__init__(
        title, gate_agent, **kwargs)
    self.__pubsub_agent = gcs_pubsub_agent

  def export_to_json_snapshot(self, snapshot, entity):
    snapshot.edge_builder.make_mechanism(
        entity, 'Gcs Pubsub Agent', self.__pubsub_agent)
    super(BaseGcsPubsubTriggerOperation, self).export_to_json_snapshot(
        snapshot, entity)

  def execute(self, agent=None):
    status = self._do_execute(self.__pubsub_agent)
    self.__pubsub_agent.logger.debug('Returning status %s', status)
    return status

  def _do_execute(self, agent):
    raise NotImplementedError('{0}._do_execute'.format(type(self)))


class GcsPubsubUploadTriggerOperation(BaseGcsPubsubTriggerOperation):
  """Specialization for main logic of gcs pubsub trigger operations.
  """
  def __init__(
      self, title, gcs_pubsub_agent, gate_agent, bucket_name, upload_path,
      local_filename, status_class, status_path):
    super(GcsPubsubUploadTriggerOperation, self).__init__(
        title, gcs_pubsub_agent, gate_agent)
    self.__bucket_name = bucket_name
    self.__upload_path = upload_path
    self.__local_filename = local_filename
    self.__status_class = status_class
    self.__status_path = status_path

  def _do_execute(self, agent):
    agent.upload_file(
        self.__bucket_name, self.__upload_path, self.__local_filename)

    return GcsPubsubTriggerOperationStatus(
        self, self.__status_class, self.__status_path)


class GcsPubsubTriggerOperationStatus(base_agent.AgentOperationStatus):
  """Status of pipeline executions triggered via gcs -> pub/sub events
  """
  @property
  def finished(self):
    return self.__trigger_status.finished or self.__is_timed_out

  @property
  def finished_ok(self):
    return self.__trigger_status.finished_ok

  @property
  def id(self):
    return self.__trigger_status.id

  @property
  def detail(self):
    return 'trigger response: {response}'.format(
        response=self.__trigger_response)

  @property
  def timed_out(self):
    return self.__trigger_status.timed_out or self.__is_timed_out

  def refresh(self):
    if self.finished:
      return

    self.__trigger_status.refresh()
    detail_path = self.__trigger_status.detail_path

    self.__trigger_response = self.operation.agent.get(detail_path)
    if not self.__trigger_status.finished:
      self.__is_timed_out = time.time() >= self.__timeout_time

  def __init__(self, operation, status_class, status_path,
               **kwargs):
    """Constructs a GcsPubsubTriggerOperationStatus object.

    Args:
    operation [BaseGcsPubsubTriggerOperation]: The GCS operation this is for.
    """
    super(GcsPubsubTriggerOperationStatus, self).__init__(operation)
    max_wait_secs = kwargs.pop('max_wait_secs', 300)
    self.__timeout_time = time.time() + max_wait_secs
    self.__trigger_response = operation.agent.get(status_path)
    self.__trigger_status = status_class(operation, original_response=self.__trigger_response)
    self.__trigger_status._bind_id("n/a")
    self.__trigger_status._bind_detail_path(status_path)
    self.__is_timed_out = False

  def export_summary_to_json_snapshot(self, snapshot, entity):
    """Implements JsonSnapshotableEntity interface."""
    (super(GcsPubsubTriggerOperationStatus, self)
     .export_summary_to_json_snapshot(snapshot, entity))

    if self.__trigger_response:
      summary = snapshot.make_entity_for_object_summary(self.__trigger_response)
      relation = 'VALID' if self.__trigger_response.ok() else 'INVALID'
    else:
      summary = 'No Trigger Response Yet'
      relation = None
    snapshot.edge_builder.make(
      entity, 'Trigger Response', summary, relation=relation)

  def export_to_json_snapshot(self, snapshot, entity):
    snapshot.edge_builder.make_output(
        entity, 'Trigger Response', self.__trigger_response)
    super(GcsPubsubTriggerOperationStatus, self).export_to_json_snapshot(
        snapshot, entity)
