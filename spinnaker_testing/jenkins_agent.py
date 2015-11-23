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


import collections
import logging
import urllib2
import os

from citest.base.scribe import Scribable
from citest.service_testing import (testable_agent, http_agent)

class JenkinsOperationStatus(testable_agent.AgentOperationStatus):
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
    return self.__status_agent.id

  @property
  def finished(self):
    return self.__status_agent.finished

  @property
  def finished_ok(self):
    return self.__status_agent.finished_ok

  @property
  def detail(self):
    return self.__status_agent.detail

  @property
  def error(self):
    return self.__status_agent.error

  @property
  def status_agent(self):
    """
    The endpoint where the status of the Jenkins operation can be found.
    """
    return self.__status_agent

  @property
  def timed_out(self):
    return self.__status_agent.timed_out

  def refresh(self, trace=True):
    return self.__status_agent.refresh(trace)

  def __init__(self, operation, status_agent, path, http_response):
    """Constructs a JenkinsOperationStatus object.

    Args:
      operation [BaseJenkinsOperation]: The Jenkins operation that this is for.
      status_agent [AgentOperationStatus]: The agent we will be using to poll
        the result of the Jenkins trigger.
      path [string]: The path the status_agent should poll on.
      http_response [HttpResponseType]: Response given by Jenkins to the
        trigger.
    """
    super(JenkinsOperationStatus, self).__init__(operation)
    self.__path = path
    self.__status_agent = status_agent(operation, http_response)
    self.__status_agent._detail_path = path

  def __cmp__(self, response):
    return (self.__status_agent.__cmp__(response.__status_agent))

  def __str__(self):
    return ('jenkins_status_agent={0}').format(self.__status_agent)

  def _make_scribe_parts(self, scribe):
    return ([scribe.build_part('Jenkins Status', self.__status_agent)]
            + super(JenkinsOperationStatus, self)._make_scribe_parts(scribe))

class JenkinsAgent(testable_agent.TestableAgent):
  """A specialization of TestableAgent for interacting with Jenkins."""
  def __init__(self, baseUrl, authPath, ownerAgent):
    super(JenkinsAgent, self).__init__()
    self.__jenkinsAgent = http_agent.HttpAgent(baseUrl)
    self.__ownerAgent = ownerAgent

    if authPath is None:
        auth_info = [os.environ.get('JENKINS_USER', None),
                     os.environ.get('JENNKINS_PASSWORD', None)]
        if auth_info[0] is None or auth_info[1] is None:
          raise ValueError(
                'You must either supply --jenins_auth_path'
                ' or JENKINS_USER and JENKINS_PASSWORD environment variables.')
    else:
        with open(authPath, 'r') as f:
          auth_info = f.read().split()

          if len(auth_info) != 2:
            raise ValueError(
                '--jenkins_auth_path={path} is not in the correct '
                'format. You must supply a file with the contents '
                '<username> <password'
                    .format(path=auth_path))

    self.__jenkinsAgent.add_basic_auth_header(auth_info[0], auth_info[1])

  def get(self, path, trace=True):
    return self.__ownerAgent.get(path, trace)

  def _trigger_jenkins_build(self, job, token):
    jenkins_path = 'job/{job}/build/?token={token}'.format(job=job, token=token)

    result = self.__jenkinsAgent.get(jenkins_path, trace=False)
    result.check_ok()
    return result


  def _make_scribe_parts(self, scribe):
    return ([scribe.build_control_part(
                'baseURL', self.__jenkinsAagent.baseUrl)]
            + super(JenkinsAgent, self)._make_scribe_parts(scribe))

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
    return JenkinsTriggerOperation(title=title, jenkins_agent=self,
        status_class=status_class, job=job, token=token,
        status_path=status_path)

class BaseJenkinsOperation(testable_agent.AgentOperation):
  @property
  def status_class(self):
    return self.__status_class

  @property
  def data(self):
    return self.__data

  def __init__(self, title, jenkins_agent, status_class=JenkinsOperationStatus):
    """Construct a BaseJenkinsOperation

    Args:
      title [string]: Name of operation.
      jenkins_agent [JenkinsAgent]: JenkinsAgent class that has the Jenkins
        configuration stored.
      status_class [AgentOperationStatus]: The status class that will
       confirm success of the action resulting from the Jenkins trigger.
    """
    super(BaseJenkinsOperation, self).__init__(title, jenkins_agent)
    if not jenkins_agent or not isinstance(jenkins_agent, JenkinsAgent):
      raise TypeError('agent not a  JenkinsAgent: '
          + jenkins_agent.__class__.__name__)

    self.__status_class = status_class
    self.__data = {}

  def _make_scribe_parts(self, scribe):
    return ([scribe.part_builder.build_mechanism_part(
                  'Jenkins Agent', self.agent),
             scribe.build_json_part('Payload Data', self.__data)]
            + super(BaseJenkinsOperation, self)._make_scribe_parts(scribe))

  def execute(self, agent=None, trace=True):
    if not self.agent:
      if not isinstance(agent, JenkinsAgent):
        raise TypeError('agent not a JenkinsAgent: ' + agent.__class__.__name__)
      self.bind_agent(agent)

    status = self._do_invoke(agent, trace)
    if trace:
      agent.logger.debug('Returning status %s', status)
    return status

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
      status_class [AgentOperationClass]: The status class that will confirm the 
        success of whatever action the jenkins trigger kicked off.
      status_path [string]: The path the status should poll for success on.
    """
    super(JenkinsTriggerOperation, self).__init__(jenkins_agent=jenkins_agent,
        status_class=status_class, title=title)

    self.__token = token
    self.__job = job
    self.__status_class = status_class
    self.__status_path = status_path

  def _do_invoke(self, agent, trace=True):
    http_response = self._agent._trigger_jenkins_build(job=self.__job,
        token=self.__token)
    return JenkinsOperationStatus(self, self.__status_class, self.__status_path,
        http_response)
