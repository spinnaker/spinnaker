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


import os

from citest.service_testing import (base_agent, http_agent)


class JenkinsOperationStatus(base_agent.AgentOperationStatus):
  """Specialization of AgentOperationStatus for Jenkins operations.

  Jenkins operations are interesting to the effect that we use them to
  trigger steps in other services, meaning the status of Jenkins operation
  depends often on the status of another endpoint. To achieve this,
  this status class is effectively a wrapper around a second agent/status
  pair which collect information on the action resulting from the Jenkins
  trigger.
  """
  @property
  def id(self):
    return self.__trigger_status.id

  @property
  def finished(self):
    return self.__trigger_status.finished

  @property
  def finished_ok(self):
    return self.__trigger_status.finished_ok

  @property
  def detail(self):
    return self.__trigger_status.detail

  @property
  def error(self):
    return self.__trigger_status.error

  @property
  def trigger_status(self):
    """
    The endpoint where the status of the Jenkins operation can be found.
    """
    return self.__trigger_status

  @property
  def timed_out(self):
    return self.__trigger_status.timed_out

  def refresh(self, trace=True):
    return self.__trigger_status.refresh(trace)

  def __init__(self, operation, status_class, path, http_response):
    """Constructs a JenkinsOperationStatus object.

    Args:
      operation [BaseJenkinsOperation]: The Jenkins operation that this is for.
      status_class [AgentOperationClass]: The status class used to monitor the
          action that the jenkins trigger kicked off.
      path [string]: The path the trigger_status should poll on.
      http_response [HttpResponseType]: Response given by Jenkins to the
        trigger.
    """
    super(JenkinsOperationStatus, self).__init__(operation)
    self.__trigger_status = status_class(operation, http_response)
    self.__trigger_status._bind_id("n/a for synchronous request")
    self.__trigger_status._bind_detail_path(path)

  def __cmp__(self, response):
    return self.__trigger_status.__cmp__(response.__trigger_status)

  def __str__(self):
    return 'jenkins_trigger_status={0}'.format(self.__trigger_status)

  def export_to_json_snapshot(self, snapshot, entity):
    snapshot.edge_builder.make_output(
        entity, 'Trigger Status', self.__trigger_status)
    super(JenkinsOperationStatus, self).export_to_json_snapshot(
        snapshot, entity)


class JenkinsAgent(base_agent.BaseAgent):
  """A specialization of BaseAgent for interacting with Jenkins."""
  def __init__(self, baseUrl, auth_path, owner_agent):
    super(JenkinsAgent, self).__init__()
    self.__http_agent = http_agent.HttpAgent(baseUrl)
    self.__owner_agent = owner_agent

    # Allow up to 13 minutes to wait on operations.
    # 13 minutes is arbitrary. The current test takes around 6-7 minutes
    # end-to-end. Other use cases might make it more clear what this should be.
    self.default_max_wait_secs = 780

    # pylint: disable=bad-indentation
    if auth_path is None:
        auth_info = [os.environ.get('JENKINS_USER', None),
                     os.environ.get('JENKINS_PASSWORD', None)]
        if auth_info[0] is None or auth_info[1] is None:
          raise ValueError(
              'You must either supply --jenkins_auth_path'
              ' or JENKINS_USER and JENKINS_PASSWORD environment variables.')
    else:
        with open(auth_path, 'r') as f:
          auth_info = f.read().split()

          if len(auth_info) != 2:
            raise ValueError(
                '--jenkins_auth_path={path} is not in the correct '
                'format. You must supply a file with the contents '
                '<username> <password'
                .format(path=auth_path))

    self.__http_agent.add_basic_auth_header(auth_info[0], auth_info[1])

  def get(self, path, trace=True):
    return self.__owner_agent.get(path, trace)

  def _trigger_jenkins_build(self, job, token):
    jenkins_path = 'job/{job}/build/?token={token}'.format(job=job,
                                                           token=token)
    self.logger.info('Triggering Jenkins %s', jenkins_path)
    result = self.__http_agent.get(jenkins_path, trace=True)
    result.check_ok()
    return result

  def export_to_json_snapshot(self, snapshot, entity):
    snapshot.edge_builder.make_control(
        entity, 'baseUrl', self.__http_agent.base_url)
    super(JenkinsAgent, self).export_to_json_snapshot(snapshot, entity)

  def new_jenkins_trigger_operation(self, title, job, token, status_class,
                                    status_path):
    """Returns a new JenkinsTriggerOperation

    Args:
      title [string]: Name of the operation.
      job [string]: Name of the Jenkins job to trigger.
      token [string]: Token required for triggering the job.
      status_class [AgentOperationStatus]: The status object in charge of
        recording the success of the action resulting from the trigger.
      status_path [string]: The path the status_class must poll on for
        success of the trigger
    """
    return JenkinsTriggerOperation(
        title=title, jenkins_agent=self,
        status_class=status_class, job=job, token=token,
        status_path=status_path)


class BaseJenkinsOperation(base_agent.AgentOperation):
  @property
  def status_class(self):
    return self.__status_class

  @property
  def data(self):
    return self.__data

  def __init__(self, title, jenkins_agent,
               status_class=JenkinsOperationStatus, max_wait_secs=None):
    """Construct a BaseJenkinsOperation

    Args:
      title [string]: Name of operation.
      jenkins_agent [JenkinsAgent]: JenkinsAgent class that has the Jenkins
        configuration stored.
      status_class [AgentOperationStatus]: The status class that will
       confirm success of the action resulting from the Jenkins trigger.
    """
    super(BaseJenkinsOperation, self).__init__(title, jenkins_agent,
                                               max_wait_secs=max_wait_secs)
    if not jenkins_agent or not isinstance(jenkins_agent, JenkinsAgent):
      raise TypeError('agent not a  JenkinsAgent: '
                      + jenkins_agent.__class__.__name__)

    self.__status_class = status_class
    self.__data = {}

  def export_to_json_snapshot(self, snapshot, entity):
    snapshot.edge_builder.make_mechanism(entity, 'Jenkins Agent', self.agent)
    # TODO(ewiseblatt): Not sure if this is input or output.
    snapshot.edge_builder.make_data(
        entity, 'Payload Data', self.__data, format='json')
    super(BaseJenkinsOperation, self).export_to_json_snapshot(snapshot, entity)

  def execute(self, agent=None, trace=True):
    if not self.agent:
      if not isinstance(agent, JenkinsAgent):
        raise TypeError('agent not a JenkinsAgent: '
                        + agent.__class__.__name__)
      self.bind_agent(agent)

    status = self._do_execute(agent, trace)
    if trace:
      agent.logger.debug('Returning status %s', status)
    return status

  def _do_execute(self, agent, trace=True):
    raise UnimplementedError('{0}._do_execute'.format(type(self)))


class JenkinsTriggerOperation(BaseJenkinsOperation):
  """Specialization of AgentOperation that triggers a build at the specified
  project.
  """
  def __init__(self, title, jenkins_agent, token, job, status_class,
               status_path):
    """Construct a JenkinsTriggerOperation.

    Args:
      title [string]: Name of the operation.
      jenkins_agent [JenkinsAgent]: The JenkensAgent class that has the Jenkins
        configuration stored.
      job [string]: Name of the Jenkins job to be triggered.
      token [string]: The token required to trigger the Jenkins job.
      status_class [AgentOperationClass]: The status class used to monitor the
          action that the jenkins trigger kicked off.
      status_path [string]: The path the status should poll for success on.
    """
    super(JenkinsTriggerOperation, self).__init__(
        jenkins_agent=jenkins_agent, status_class=status_class, title=title)

    self.__token = token
    self.__job = job
    self.__status_class = status_class
    self.__status_path = status_path

  def _do_execute(self, agent, trace=True):
    http_response = self.agent._trigger_jenkins_build(job=self.__job,
                                                       token=self.__token)
    agent.logger.debug(
        'Checking for effect of jenkins trigger at url={url}'.format(
            url=self.__status_path))

    return JenkinsOperationStatus(
        self, self.__status_class, self.__status_path, http_response)
