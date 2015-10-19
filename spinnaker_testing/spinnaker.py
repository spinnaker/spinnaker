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
import json
import logging
import os
import os.path
import re

import citest.gcp_testing.gce_util as gce_util
import citest.service_testing as service_testing
import citest.gcp_testing as gcp

import spinnaker_testing.yaml_util as yaml_util


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

    decoder = json.JSONDecoder()
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
      return cls.new_native_instance(
        name, status_factory,
        host=bindings['NATIVE_HOSTNAME'],
        port=bindings['NATIVE_PORT'] or port,
        bindings=bindings)

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
    address = gce_util.establish_network_connectivity(
      gcloud=gcloud, instance=instance, target_port=port)
    if not address:
      error = 'Could not locate {0}.'.format(name)
      logger.error(error)
      raise RuntimeError(error)

    logger.info('%s is available at %s', name, address)
    spinnaker_agent = cls(address, status_factory)
    config = cls._determine_spinnaker_configuration(gcloud, instance)
    spinnaker_agent.init_runtime_config(config_bindings, overrides=config)
    return spinnaker_agent

  @classmethod
  def new_native_instance(cls, name, status_factory, host, port, bindings):
    """Create a new Spinnaker HttpAgent talking to the specified server port.

    Args:
      name: The name of agent we are creating, for logging purposes only.
      status_factory: Factory method (agent, original_response) for creating
         specialized SpinnakerStatus instances.
      host: The hostname to connect to.
      port: The port of the endpoint we want to connect to.

    Returns:
      A SpinnakerAgent connected to the specified instance port.
    """
    logger = logging.getLogger(__name__)
    logger.info('Locating %s...', name)
    address = '{0}:{1}'.format(host, port)
    if not address:
      logger.error('Could not locate %s.', name)
      return None

    logger.info('%s is available at %s', name, address)
    spinnaker_agent = cls(address, status_factory)
    spinnaker_agent.init_runtime_config(bindings)

    return spinnaker_agent

  @property
  def runtime_config(self):
    return self._config_dict

  # This method needs to be thought out.
  # For now, the only runtime config is managed project id and account name
  # but this is specific to GCE and is limited to the tests we currently have.
  # In general, it should be able to accomodate the runtime configuration for
  # a given subsystem especially to configure an observer to validate behavior.
  def init_runtime_config(self, bindings, overrides=None):
    config_dict = {}
    if overrides:
      config_dict.update(overrides)

    logger = logging.getLogger(__name__)
    missing = []
    host_platform = self.determine_host_platform(bindings)
    logger.info('Spinnaker host_platform=%s', host_platform)

    if host_platform == 'gce':
      if not bindings['GCE_INSTANCE']:
        missing.append('--gce_instance')
      if not bindings['GCE_ZONE']:
        missing.append('--gce_instance')

    if missing:
      error = ('Additional configuration information is required.'
               ' Please specify spinnaker runtime bindings with:\n'
               '  {0}'.format('\n  '.join(missing)))
      logger.error(error)
      raise ValueError(error)

    self._config_dict = config_dict

  def __init__(self, address, status_factory):
    """Construct a an agent for talking to spinnaker.

    This could really be any spinnaker subsystem, not just the master process.
    The important consideration is that the protocol for this server is that
    posting requests returns a reference url for status updates, and accepts
    GET requests on those urls to return status details.

    Args:
      address: The host:port string spinnaker is running on.
      status_factory: Creates status instances from this agent.
    """
    super(SpinnakerAgent, self).__init__(address)
    self._status_factory = status_factory
    self._default_max_wait_secs = 240

  def _new_post_status(self, operation, http_response):
    return self._status_factory(operation, http_response)

  @staticmethod
  def _get_gce_config_file_contents(gcloud, instance):
    """Return the contents of the spinnaker configuration file.

    Args:
      gcloud: Specifies project and zone. Capable of remote fetching if needed.
      instance: The GCE instance name containing the configuration file.

    Returns:
      None or the configuration file contents.
    """
    logger = logging.getLogger(__name__)
    if gce_util.am_i(gcloud.project, gcloud.zone, instance):
      config_file = os.path.expanduser('~/.spinnaker/spinnaker_config.cfg')
      logger.debug('We are the instance. Config from %s', config_file)
      try:
        with open(config_file, 'r') as f:
          return '\n'.join(f.readlines())
      except IOError as e:
        logger.error('Failed to load from %s: %s', config_file, e)
        return None

      logger.debug('Load config from instance %s', instance)

    # If this is a production installation, look in /root/.spinnaker
    # Otherwise look in ~/.spinnaker for a development installation.
    response = gcloud.remote_command(
        instance,
        'if sudo stat /root/.spinnaker/spinnaker_config.cfg >& /dev/null; then'
        ' sudo cat /root/.spinnaker/spinnaker_config.cfg; '
        'else '
        ' cat ~/.spinnaker/spinnaker_config.cfg; '
        'fi')
    if response.retcode != 0:
      logger.error(
        'Could not determine configuration:\n%s', response.error)
      return None

    return response.output


  @staticmethod
  def _get_deployed_local_yaml_bindings(gcloud, instance):
    """Return the contents of the spinnaker-local.yml configuration file.

    Args:
      gcloud: Specifies project and zone. Capable of remote fetching if needed.
      instance: The GCE instance name containing the configuration file.

    Returns:
      None or the configuration file contents.
    """
    logger = logging.getLogger(__name__)
    if gce_util.am_i(gcloud.project, gcloud.zone, instance):
      yaml_file = os.path.expanduser('~/.spinnaker/spinnaker-local.yml')
      logger.debug('We are the instance. Config from %s', yaml_file)

      if not os.path.exists(yaml_file):
        logger.debug('%s does not exist', yaml_file)
        return None

      try:
        bindings = yaml_util.YamlBindings()
        bindings.import_path(yaml_file)
        return bindings
      except IOError as e:
        logger.error('Failed to load from %s: %s', yaml_file, e)
        return None

      logger.debug('Load spinnaker-local.yml from instance %s', instance)

    # If this is a production installation, look in /root/.spinnaker
    # Otherwise look in ~/.spinnaker for a development installation.
    response = gcloud.remote_command(
        instance,
        'if sudo stat /root/.spinnaker/spinnaker-local.yml >& /dev/null; then'
        ' sudo cat /root/.spinnaker/spinnaker-local.yml; '
        'elif sudo stat ~/.spinnaker/spinnaker-local.yml >& /dev/null; then'
        ' cat ~/.spinnaker/spinnaker-local.yml; '
        'fi')
    if response.retcode != 0:
      logger.error(
        'Could not determine configuration:\n%s', response.error)
      return None

    bindings = yaml_util.YamlBindings()
    bindings.import_string(response.output)
    return bindings


  @staticmethod
  def _determine_spinnaker_configuration(gcloud, instance):
    """Connect to the actual spinnaker instance and grab its configuration.

    Args:
      gcloud: gcp.GCloudAgent instance configured for spinnaker's project.
      instance: The name of the spinnaker instance.

    Returns:
      Dictionary with upper-case keys (as they appear in spinnaker)
    """
    logger = logging.getLogger(__name__)
    spinnaker_config = {
      # Assume the default name.
      'GOOGLE_PRIMARY_ACCOUNT_NAME': 'my-account-name',
      # Assume the default managed project is itself.
      'GOOGLE_PRIMARY_MANAGED_PROJECT_ID': gcloud.project
    }

    bindings = SpinnakerAgent._get_deployed_local_yaml_bindings(
        gcloud, instance)

    if bindings:
      try:
        spinnaker_config['GOOGLE_PRIMARY_ACCOUNT_NAME'] = (
              bindings.get('providers.google.primaryCredentials.name'))
      except KeyError:
          pass
      try:
        spinnaker_config['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = (
              bindings.get('providers.google.primaryCredentials.project'))
      except KeyError:
          pass

      logger.debug('Collected configuration from bindings %s', spinnaker_config)
      return spinnaker_config

    contents = SpinnakerAgent._get_gce_config_file_contents(gcloud, instance)
    if contents == None:
      return None

    for key in ['GOOGLE_PRIMARY_MANAGED_PROJECT_ID',
                'GOOGLE_PRIMARY_ACCOUNT_NAME']:
      m = re.search(key + '=([-\w]+)', contents)
      if m:
        spinnaker_config[key] = m.group(1)
      else:
        logger.debug(
          'Seems to be using the default %s=%s', key, spinnaker_config[key])

    logger.debug('Collected old-style configuration %s', spinnaker_config)
    return spinnaker_config
