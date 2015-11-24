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


# Provides Spinnaker interactions (using http requests).
# This module is intended to provide a base class supported
# by specializations for the individual subsystems.
#
# (e.g. gate.py)
#
# To talk to spinnaker, we make HTTP calls using urllib2
# (via SpinnakerAgent abstraction). To talk to GCE we use gcloud
# (via GCloudAgent abstraction) for convenience, especially auth.
#
# In order to talk to spinnaker, it must have network access. If you are
# running outside the project (e.g. on a laptop) then you'll probably need
# to create an ssh tunnel into the spinnaker VM because the ports are not
# exposed by default.
#
# Rather than setting up this tunnel yourself, the test will set up the
# tunnel itself. This not only guarantees the tunnel is available, but
# also ensures that the tunnel is in fact going to the instance being tested
# as opposed to some other stray tunnel. Furthermore, the test will use
# a unique local port so running this test will not interfere with other
# accesses to spinnaker.
#
# When using ssh, you must provide ssh a passphrase for the credentials.
# You can either run eval `ssh-agent -s` > /dev/null then ssh-add with the
# credentials, or you can create a file that contains the passphrase and
# pass the file with --ssh_passphrase_file. If you create a file, chmod 400
# it to keep it safe.
#
# If spinnaker is reachable without tunnelling then it will talk directly to
# it. This is determined by looking up the IP address of the instance, and
# trying to connect to gate directly.
#
# In short, run the test with the spinnaker instance project/zone/instance
# name and it will figure out the rest of the configuration needed. To do so,
# you will also need to provide it ssh credentials, either implicitly by
# running ssh-agent, or explicitly by giving it a passphrase (via file for
# security).

# Standard python modules.
import base64
import logging
import os
import os.path
import re
import tarfile
import urllib2
from json import JSONDecoder
from StringIO import StringIO

import citest.gcp_testing.gce_util as gce_util
import citest.service_testing as service_testing
import citest.gcp_testing as gcp

import spinnaker_testing.yaml_accumulator as yaml_accumulator
from scrape_spring_config import scrape_spring_config
from spinnaker_testing.expression_dict import ExpressionDict


def name_value_to_dict(content):
    """Converts a list of name=value pairs to a dictionary.

    Args:
      content: A list of name=value pairs with one per line.
               This is blank lines ignored, as is anything to right of '#'.
    """
    result = {}
    for match in re.finditer('^([A-Za-z_][A-Za-z0-9_]*) *= *([^#]*)',
                             content,
                             re.MULTILINE):
        result[match.group(1)] = match.group(2).strip()
    return result


class SpinnakerStatus(service_testing.HttpOperationStatus):
  """Provides access to Spinnaker's asynchronous task status.

  This class can be used to track an asynchronous task status.
  It can wait until the task completes, and provide current status state
  from its bound reference.
  This instance must explicitly refresh() in order to update its value.
  It will only poll the server within refresh().

  Attributes:
    current_state: The value of the JSON "state" field, or None if not known.
    exception_details: If the spinnaker task status is an error,
        this is just the exceptions clause from the detail.
  """
  @property
  def current_state(self):
    return self._current_state

  @property
  def exception_details(self):
    return self._exception_details

  @property
  def id(self):
    return self._request_id

  def _make_scribe_parts(self, scribe):
    parts = [scribe.build_json_part('Status Detail', self._json_doc,
                                    relation=scribe.part_builder.OUTPUT)]
    inherited = super(SpinnakerStatus, self)._make_scribe_parts(scribe)
    return inherited + parts

  def __init__(self, operation, original_response=None):
    """Initialize status tracker.

    Args:
      operation: The operation returning the status.
      original_response: JSON identifier object returned from the
          Spinnaker request to track. This can be none to indicate an error
          making the original request.
    """
    super(SpinnakerStatus, self).__init__(operation, original_response)
    self._request_id = original_response  # Identifies original request.
    self._current_state = None  # Last known state (after last refresh()).
    self._detail_path = None    # The URL path on spinnaker for this status.
    self._exception_details = None
    self._json_doc = None

    if not original_response or original_response.retcode < 0:
      self._current_state = 'REQUEST_FAILED'
      return

  def __str__(self):
    """Convert status to string"""
    return ('id={id} current_state={current}'
            ' error=[{error}] detail=[{detail}]').format(
      id=self.id, current=self._current_state,
      error=self.error, detail=self.detail)

  def refresh(self, trace=True):
    """Refresh the status with the current data from spinnaker.

    Args:
      trace: Whether or not to log the call into spinnaker.
    """
    if self.finished:
      return

    http_response = self.agent.get(self._detail_path, trace)
    self.set_http_response(http_response)

  def set_http_response(self, http_response):
    """Updates specialized fields from http_response.

    Args:
      http_response: The HttpResponse from the last status update.
    """
    # super(SpinnakerStatus, self).set_http_response(http_response)
    if http_response.retcode < 0:
      self._current_state = 'Unknown'
      return

    decoder = JSONDecoder()
    self._json_doc = decoder.decode(http_response.output)
    self._update_response_from_json(self._json_doc)

  def _update_response_from_json(self, doc):
    """Updates abstract SpinnakerStatus attributes.

    This is called by the base class.

    Args:
      doc: JSON document object from response payload.
    """
    raise Exception("_update_response_from_json is not specialized.")



class SpinnakerAgent(service_testing.HttpAgent):
  """A TestableAgent  to a spinnaker subsystem.

  The agent supports POST using the standard spinnaker subsystem protocol
  of returning status references and obtaining details through followup GETs.

  Class instances should be created using one of the new* factory methods.
  """

  @classmethod
  def determine_host_platform(cls, bindings):
    host_platform = bindings.get('HOST_PLATFORM', None)
    if not host_platform:
      if bindings['GCE_PROJECT']:
        host_platform = 'gce'
      elif bindings['NATIVE_HOSTNAME']:
        host_platform = 'native'
      else:
        raise ValueError('No --native_hostname nor --gce_project')
    return host_platform

  @classmethod
  def new_instance_from_bindings(cls, name, status_factory, bindings, port):
    """Create a new Spinnaker HttpAgent talking to the specified server port.
    Args:
      name: The name of agent we are creating, for logging purposes only.
      status_factory: Factory method (agent, original_response) for creating
         specialized SpinnakerStatus instances.
      bindings: Bindings that specify how to connect to the server
         The actual parameters used depend on the hosting platform.
         The hosting platform is specified with 'host_platform'
      port: The port of the endpoint we want to connect to.

    Returns:
      A SpinnakerAgent connected to the specified instance port.
    """
    host_platform = cls.determine_host_platform(bindings)
    if host_platform == 'native':
      baseUrl = (None if not bindings['NATIVE_HOSTNAME']
                 else 'http://{host}:{port}'
                      .format(host=bindings['NATIVE_HOSTNAME'],
                              port=bindings['NATIVE_PORT'] or port))
      return cls.new_native_instance(
          name, status_factory=status_factory,
          baseUrl=baseUrl, bindings=bindings)

    if host_platform == 'gce':
      return cls.new_gce_instance(
          name, status_factory,
          project=bindings['GCE_PROJECT'],
          zone=bindings['GCE_ZONE'],
          instance=bindings['GCE_INSTANCE'],
          port=port,
          ssh_passphrase_file=bindings.get('GCE_SSH_PASSPHRASE_FILE', None),
          config_bindings=bindings)

    raise ValueError('Unknown host_platform={0}'.format(host_platform))

  @classmethod
  def new_gce_instance(cls, name, status_factory,
                       project, zone, instance, port, ssh_passphrase_file,
                       config_bindings):
    """Create a new Spinnaker HttpAgent talking to the specified server port.

    Args:
      name: The name of agent we are creating, for logging purposes only.
      status_factory: Factory method (agent, original_response) for creating
         specialized SpinnakerStatus instances.
      project: The GCE project ID that the endpoint is in.
      zone: The GCE zone that the endpoint is in.
      instance: The GCE instance that the endpoint is in.
      port: The port of the endpoint we want to connect to.
      ssh_passphrase_file: If not empty, the SSH passphrase key
         for tunneling if needed in order to connect through a GCE firewall.

    Returns:
      A SpinnakerAgent connected to the specified instance port.
    """
    logger = logging.getLogger(__name__)
    logger.info('Locating %s...', name)
    gcloud = gcp.GCloudAgent(
      project=project, zone=zone, ssh_passphrase_file=ssh_passphrase_file)
    netloc = gce_util.establish_network_connectivity(
      gcloud=gcloud, instance=instance, target_port=port)
    if not netloc:
      error = 'Could not locate {0}.'.format(name)
      logger.error(error)
      raise RuntimeError(error)

    approx_config = cls._get_deployed_local_yaml_bindings(gcloud, instance)
    protocol = approx_config.get('services.default.protocol', 'http')
    baseUrl = '{protocol}://{netloc}'.format(protocol=protocol, netloc=netloc)
    logger.info('%s is available at %s', name, baseUrl)
    deployed_config = scrape_spring_config(os.path.join(baseUrl, 'env'))
    spinnaker_agent = cls(baseUrl, status_factory)
    spinnaker_agent.__deployed_config = deployed_config

    return spinnaker_agent

  @classmethod
  def new_native_instance(cls, name, status_factory, baseUrl, bindings):
    """Create a new Spinnaker HttpAgent talking to the specified server port.

    Args:
      name: The name of agent we are creating, for logging purposes only.
      status_factory: Factory method (agent, original_response) for creating
         specialized SpinnakerStatus instances.
      baseUrl: The service baseUrl to send messages to.

    Returns:
      A SpinnakerAgent connected to the specified instance port.
    """
    logger = logging.getLogger(__name__)
    logger.info('Locating %s...', name)
    if not baseUrl:
      logger.error('Could not locate %s.', name)
      return None

    logger.info('%s is available at %s', name, baseUrl)
    env_url = os.path.join(baseUrl, 'env')
    deployed_config = scrape_spring_config(env_url)
    spinnaker_agent = cls(baseUrl, status_factory)
    spinnaker_agent.__deployed_config = deployed_config

    return spinnaker_agent

  @property
  def deployed_config(self):
      return self.__deployed_config

  @property
  def runtime_config(self):
    return self._config_dict

  def __init__(self, baseUrl, status_factory):
    """Construct a an agent for talking to spinnaker.

    This could really be any spinnaker subsystem, not just the master process.
    The important consideration is that the protocol for this server is that
    posting requests returns a reference url for status updates, and accepts
    GET requests on those urls to return status details.

    Args:
      baseUrl: The baseUrl string spinnaker is running on.
      status_factory: Creates status instances from this agent.
    """
    super(SpinnakerAgent, self).__init__(baseUrl)
    self.__deployed_config = {}
    self.__default_status_factory = status_factory
    self._default_max_wait_secs = 240

  def _new_invoke_status(self, operation, http_response):
    return (operation.status_class(operation, http_response)
            if operation.status_class
            else self.__default_status_factory(operation, http_response))

  @staticmethod
  def _derive_spring_config_from_url(url):
      return {}

  @staticmethod
  def _get_deployed_local_yaml_bindings(gcloud, instance):
    """Return the contents of the spinnaker-local.yml configuration file.

    Args:
      gcloud: Specifies project and zone. Capable of remote fetching if needed.
      instance: The GCE instance name containing the configuration file.

    Returns:
      None or the configuration file contents.
    """
    config_dict = ExpressionDict()
    logger = logging.getLogger(__name__)

    if gce_util.am_i(gcloud.project, gcloud.zone, instance):
      yaml_file = os.path.expanduser('~/.spinnaker/spinnaker-local.yml')
      logger.debug('We are the instance. Config from %s', yaml_file)

      if not os.path.exists(yaml_file):
        logger.debug('%s does not exist', yaml_file)
        return None

      try:
        yaml_accumulator.load_path(yaml_file, config_dict)
        return config_dict
      except IOError as e:
        logger.error('Failed to load from %s: %s', yaml_file, e)
        return None

      logger.debug('Load spinnaker-local.yml from instance %s', instance)

    # If this is a production installation, look in:
    #    /home/spinnaker/.spinnaker
    # or /opt/spinnaker/config
    # or /etc/default/spinnaker (name/value)
    # Otherwise look in ~/.spinnaker for a development installation.
    response = gcloud.remote_command(
        instance,
        'LIST=""'
        '; for i in /etc/default/spinnaker'
           ' /home/spinnaker/.spinnaker/spinnaker-local.yml'
           ' /opt/spinnaker/config/spinnaker-local.yml'
           ' $HOME/.spinnaker/spinnaker-local.yml'
        '; do'
            ' if sudo stat $i >& /dev/null; then'
            '   LIST="$LIST $i"'
            '; fi'
        '; done'
        # tar emits warnings about the absolute paths, so we'll filter them out
        # We need to base64 the binary results so we return text.
        '; (sudo tar czf - $LIST 2> /dev/null | base64)')

    if response.retcode != 0:
      logger.error(
        'Could not determine configuration:\n%s', response.error)
      return None

    # gcloud prints an info message about upgrades to the output stream.
    # There seems to be no way to supress this!
    # Look for it and truncate the stream there if we see it.
    got = response.output
    update_msg_offset = got.find('Updates are available')
    if update_msg_offset > 0:
      got = got[0:update_msg_offset]

    # When we ssh in, there may be a message written warning us that the host
    # was added to known hosts. If so, this will be the first line. Remove it.
    eoln = got.find('\n')
    if eoln > 0 and re.match('^Warning: .+$', got[0:eoln]):
      got = got[eoln + 1:]

    if not got:
      return None

    tar = tarfile.open(mode='r', fileobj=StringIO(base64.b64decode(got)))

    try:
        file = tar.extractfile('etc/default/spinnaker')
    except KeyError:
        pass
    else:
      logger.info('Importing configuration from /etc/default/spinnaker')
      config_dict.update(name_value_to_dict(file.read()))

    file_list = ['home/spinnaker/.spinnaker/spinnaker-local.yml',
                 'opt/spinnaker/config/spinnaker-local.yml']
    log_name = os.environ.get('LOGNAME')
    if log_name is not None:
        file_list.append(os.path.join('home', log_name,
                                      '.spinnaker/spinnaker-local.yml'))

    for member in file_list:
        try:
            file = tar.extractfile(member)
        except KeyError:
            continue

        logger.info('Importing configuration from ' + member)
        yaml_accumulator.load_string(file.read(), config_dict)

    return config_dict
