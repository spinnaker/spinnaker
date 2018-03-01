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


import json
import logging
import os

from citest.service_testing import (base_agent, http_agent)

from google.cloud import storage


class GcsFileUploadAgent(base_agent.BaseAgent):
  """Specialization of GcpStorageAgent to observe pipelines triggered via pub/sub.
  """
  def __init__(self, credentials_path=None, logger=None):
    super(GcsFileUploadAgent, self).__init__(logger=logger)
    if credentials_path:
      self.__client = storage.Client.from_service_account_json(credentials_path)
    else:
      self.__client = storage.Client()

    # Allow up to 13 minutes to wait on operations.
    # 13 minutes is arbitrary. The current test takes around 6-7 minutes
    # end-to-end. Other use cases might make it more clear what this should be.
    self.default_max_wait_secs = 780

  def upload_file(self, bucket_name, upload_path, local_filename):
    """Uploads a local file to a bucket at a relative upload path.
    """
    logging.info('Uploading local file %s to bucket %s at path %s', local_filename, bucket_name, upload_path)
    print 'Uploading local file {} to bucket {} at path {}'.format(local_filename, bucket_name, upload_path)
    bucket = self.__client.get_bucket(bucket_name)
    upload_blob = bucket.blob(upload_path)
    upload_blob.upload_from_filename(filename=local_filename)

  def export_to_json_snapshot(self, snapshot, entity):
    super(GcsFileUploadAgent, self).export_to_json_snapshot(snapshot, entity)

  def new_gcs_pubsub_trigger_operation(self, gate_agent, title, bucket_name, upload_path,
                                       local_filename, status_class, status_path):
    return GcsPubsubUploadTriggerOperation(title, self, gate_agent, bucket_name, upload_path,
                                    local_filename, status_class, status_path)


class BaseGcsPubsubTriggerOperation(base_agent.AgentOperation):
  """
  """
  def __init__(self, title, gcs_pubsub_agent, max_wait_secs=None):
    self.__title = title
    self.__agent = gcs_pubsub_agent
    super(BaseGcsPubsubTriggerOperation, self).__init__(title, gcs_pubsub_agent, max_wait_secs=max_wait_secs)
    if not gcs_pubsub_agent or not isinstance(gcs_pubsub_agent, GcsFileUploadAgent):
      raise TypeError('agent is not a GcsFileUploadAgent: ' + gcs_pubsub_agent.__class__.__name__)

  def export_to_json_snapshot(self, snapshot, entity):
    snapshot.edge_builder.make_mechanism(entity, 'Gcs Pubsub Agent', self.agent)
    super(BaseGcsPubsubTriggerOperation, self).export_to_json_snapshot(snapshot, entity)

  def execute(self, agent=None, trace=True):
    status = self._do_execute(self.agent)
    self.agent.logger.debug('Returning status %s', status)
    return status

  def _do_execute(self, agent, trace=True):
    raise UnimplementedError('{0}._do_execute'.format(type(self)))


class GcsPubsubUploadTriggerOperation(BaseGcsPubsubTriggerOperation):
  """
  """
  def __init__(self, title, gcs_pubsub_agent, gate_agent, bucket_name, upload_path,
               local_filename, status_class, status_path):
    super(GcsPubsubUploadTriggerOperation, self).__init__(title, gcs_pubsub_agent)
    self.__bucket_name = bucket_name
    self.__upload_path = upload_path
    self.__local_filename = local_filename
    self.__gate_agent = gate_agent
    self.__status_class = status_class
    self.__status_path = status_path

  def _do_execute(self, agent, trace=True):
    # self.agent is the gcs_pubsub_agent
    self.agent.upload_file(self.__bucket_name, self.__upload_path, self.__local_filename)

    return GcsPubsubTriggerOperationStatus(self, self.__gate_agent, self.__status_class, self.__status_path)


class GcsPubsubTriggerOperationStatus(base_agent.AgentOperationStatus):
  """
  """
  @property
  def finished(self):
    resp = self.__trigger_response
    executions = json.JSONDecoder().decode(resp.output) # Executions list
    return executions and len(executions) == 1

  @property
  def finished_ok(self):
    return self.__trigger_response.ok()

  @property
  def id(self):
    return self.__class__.__name__

  @property
  def detail(self):
    return 'trigger response: {}'.format(self.__trigger_response)

  @property
  def timed_out(self):
    resp = self.__trigger_response
    return resp and resp.http_code == 504

  def refresh(self, trace=True):
    self.__trigger_response = self.__gate_agent.get(self.__status_path)

  def __init__(self, operation, gate_agent, status_class, status_path):
    """Constructs a GcsPubsubTriggerOperationStatus object.

    Args:
    operation [BaseGcsPubsubTriggerOperation]: The GCS operation that this is for.
    """
    self.__gate_agent = gate_agent
    operation.bind_agent(gate_agent)
    self.__status_class = status_class
    self.__status_path = status_path
    super(GcsPubsubTriggerOperationStatus, self).__init__(operation)
    self.__trigger_response = self.__gate_agent.get(self.__status_path)

  def export_to_json_snapshot(self, snapshot, entity):
    snapshot.edge_builder.make_output(
        entity, 'Trigger Status', self.__trigger_response)
    super(GcsPubsubTriggerOperationStatus, self).export_to_json_snapshot(
      snapshot, entity)
