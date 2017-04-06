#!/usr/bin/python
#
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
import re
import shutil
import signal
import stat
import subprocess
import sys
import time

from spinnaker.configurator import InstallationParameters
from spinnaker.fetch import AWS_METADATA_URL
from spinnaker.fetch import GOOGLE_METADATA_URL
from spinnaker.fetch import GOOGLE_INSTANCE_METADATA_URL
from spinnaker.fetch import is_aws_instance
from spinnaker.fetch import is_google_instance
from spinnaker.fetch import check_fetch
from spinnaker.fetch import fetch
from spinnaker.run import run_quick
from spinnaker.yaml_util import YamlBindings
from spinnaker.validate_configuration import ValidateConfig
from spinnaker import spinnaker_runner


def populate_aws_yml(content):
  aws_dict = {'enabled': False}
  if is_aws_instance():
      zone = (check_fetch(AWS_METADATA_URL + '/placement/availability-zone')
              .content)
      aws_dict['enabled'] = 'true'
      aws_dict['defaultRegion'] = zone[:-1]
  elif os.path.exists(os.path.join(os.environ['HOME'], '.aws/credentials')):
      aws_dict['enabled'] = 'true'
      aws_dict['defaultRegion'] = 'us-east-1'

  bindings = YamlBindings()
  bindings.import_dict({'providers': {'aws': aws_dict}})
  content = bindings.transform_yaml_source(content, 'providers.aws.enabled')
  content = bindings.transform_yaml_source(content,
                                           'providers.aws.defaultRegion')

  return content


def populate_google_yml(content):
  credentials = {'project': '', 'jsonPath': ''}
  google_dict = {'enabled': False,
                 'defaultRegion': 'us-central1',
                 'defaultZone': 'us-central1-f',}

  google_dict['primaryCredentials'] = credentials
  front50_dict = {}
  if is_google_instance():
      zone = os.path.basename(
           check_fetch(GOOGLE_INSTANCE_METADATA_URL + '/zone',
                       google=True).content)
      google_dict['enabled'] = 'true'
      google_dict['defaultRegion'] = zone[:-2]
      google_dict['defaultZone'] = zone
      credentials['project'] = check_fetch(
            GOOGLE_METADATA_URL + '/project/project-id', google=True).content
      front50_dict['storage_bucket'] = '${{{env}:{default}}}'.format(
          env='SPINNAKER_DEFAULT_STORAGE_BUCKET',
          default=credentials['project'].replace(':', '-').replace('.', '-'))

  bindings = YamlBindings()
  bindings.import_dict({'providers': {'google': google_dict}})
  bindings.import_dict({'services': {'front50': front50_dict}})
  content = bindings.transform_yaml_source(content, 'providers.google.enabled')
  content = bindings.transform_yaml_source(
      content, 'providers.google.defaultRegion')
  content = bindings.transform_yaml_source(
      content, 'providers.google.defaultZone')
  content = bindings.transform_yaml_source(
      content, 'providers.google.primaryCredentials.project')
  content = bindings.transform_yaml_source(
      content, 'providers.google.primaryCredentials.jsonPath')
  content = bindings.transform_yaml_source(
      content, 'services.front50.storage_bucket')

  return content


class DevInstallationParameters(InstallationParameters):
  """Specialization of the normal production InstallationParameters.

  This is a developer deployment where the paths are setup to run directly
  out of this repository rather than a standard system installation.

  Also, custom configuration parameters come from the $HOME/.spinnaker
  rather than the normal installation location of /opt/spinnaker/config.
  """
  DEV_SCRIPT_DIR = os.path.abspath(os.path.dirname(__file__))
  SUBSYSTEM_ROOT_DIR = os.getcwd()

  USER_CONFIG_DIR = os.path.join(os.environ['HOME'], '.spinnaker')
  LOG_DIR = os.path.join(SUBSYSTEM_ROOT_DIR, 'logs')

  SPINNAKER_INSTALL_DIR = os.path.abspath(
      os.path.join(DEV_SCRIPT_DIR, '..'))
  INSTALLED_CONFIG_DIR = os.path.abspath(
      os.path.join(DEV_SCRIPT_DIR, '../config'))

  UTILITY_SCRIPT_DIR = os.path.abspath(
      os.path.join(DEV_SCRIPT_DIR, '../runtime'))
  EXTERNAL_DEPENDENCY_SCRIPT_DIR = os.path.abspath(
      os.path.join(DEV_SCRIPT_DIR, '../runtime'))

  DECK_INSTALL_DIR = os.path.join(SUBSYSTEM_ROOT_DIR, 'deck')
  HACK_DECK_SETTINGS_FILENAME = 'settings.js'
  DECK_PORT = 9000


class DevRunner(spinnaker_runner.Runner):
  """Specialization of the normal spinnaker runner for development use.

  This class has different behaviors than the normal runner.
  It follows similar heuristics for launching and stopping jobs,
  however, the details differ in fundamental ways.

    * The subsystems are run from their source (using gradle)
      and will attempt to rebuild before running.

    * Spinnaker will be reconfigured on each invocation.

  The runner will display all the events to the subsystem error logs
  to the console for as long as this script is running. When the script
  terminates, the console will no longer show the error log, but the processes
  will remain running, and continue logging to the logs directory.
  """

  @staticmethod
  def maybe_generate_clean_user_local():
    """Generate a spinnaker-local.yml file without environment variables refs"""
    user_dir = DevInstallationParameters.USER_CONFIG_DIR
    user_config_path = os.path.join(user_dir, 'spinnaker-local.yml')
    if os.path.exists(user_config_path):
      return
    if not os.path.exists(user_dir):
      os.mkdir(user_dir)

    with open('{config_dir}/default-spinnaker-local.yml'.format(
                  config_dir=DevInstallationParameters.INSTALLED_CONFIG_DIR),
              'r') as f:
      content = f.read()

    content = populate_aws_yml(content)
    content = populate_google_yml(content)

    with open(user_config_path, 'w') as f:
      f.write(content)
    os.chmod(user_config_path, 0600)

    change_path = os.path.join(os.path.dirname(os.path.dirname(__file__)),
                               'install', 'change_cassandra.sh')
    got = run_quick(change_path
                    + ' --echo=inMemory --front50=gcs'
                    + ' --change_defaults=false --change_local=true',
                    echo=False)

  def __init__(self, installation_parameters=None):
    self.maybe_generate_clean_user_local()
    installation = installation_parameters or DevInstallationParameters
    super(DevRunner, self).__init__(installation)

  def start_subsystem(self, subsystem, environ=None):
    """Starts the specified subsystem.

    Args:
      subsystem [string]: The repository name of the subsystem to run.
    """
    print 'Starting {subsystem}'.format(subsystem=subsystem)
    command = os.path.join(
        self.installation.SUBSYSTEM_ROOT_DIR,
        subsystem,
        'start_dev.sh')
    return self.run_daemon(command, [command], environ=environ)

  def tail_error_logs(self):
    """Start a background tail job of all the component error logs."""
    log_dir = self.installation.LOG_DIR
    try:
      os.makedirs(log_dir)
    except OSError:
      pass

    tail_jobs = []
    for subsystem in self.get_all_subsystem_names():
      path = os.path.join(log_dir, subsystem + '.err')
      if not os.path.exists(path):
        open(path, 'w').close()
      tail_jobs.append(self.start_tail(path))

    return tail_jobs

  def get_deck_pid(self):
    """Return the process id for deck, or None."""
    program='deck/node_modules/.bin/webpack-dev-server'
    stdout, stderr = subprocess.Popen(
        'ps -fwwwC node', stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        shell=True, close_fds=True).communicate()
    match = re.search('(?m)^[^ ]+ +([0-9]+) .*/{program}'.format(
        program=program), stdout)
    return int(match.group(1)) if match else None

  def start_deck(self):
    """Start subprocess for deck."""
    pid = self.get_deck_pid()
    if pid:
      print 'Deck is already running as pid={pid}'.format(pid=pid)
      return pid

    path = os.path.join(self.installation.SUBSYSTEM_ROOT_DIR,
                        'deck/start_dev.sh')
    return self.run_daemon(path, [path])

  def stop_deck(self):
    """Stop subprocess for deck."""
    pid = self.get_deck_pid()
    if pid:
      print 'Terminating deck in pid={pid}'.format(pid=pid)
      os.kill(pid, signal.SIGTERM)
    else:
      print 'deck was not running'

  def start_all(self, options):
    """Starts all the components then logs stderr to the console forever.

    The subsystems are in forked processes disassociated from this, so will
    continue running even after this process exists. Only the stderr logging
    to console will stop once this process is terminated. However, the
    logging will still continue into the LOG_DIR.
    """

    ValidateConfig(self.configurator).check_validate()
    self.configurator.update_deck_settings()

    ignore_tail_jobs = self.tail_error_logs()
    super(DevRunner, self).start_all(options)

    deck_port = self.installation.DECK_PORT
    print 'Waiting for deck to start on port {port}'.format(port=deck_port)

    # Tail the log file while we wait and run.
    # But the log file might not yet exist if deck hasn't started yet.
    # So wait for the log file to exist before starting to tail it.
    # Deck cant be ready yet if it hasn't started yet anyway.
    deck_log_path = os.path.join(self.installation.LOG_DIR, 'deck.log')
    while not os.path.exists(deck_log_path):
      time.sleep(0.1)
    ignore_tail_jobs.append(self.start_tail(deck_log_path))

    # Don't just wait for port to be ready,  but for deck to respond
    # because it takes a long time to startup once port is ready.
    while True:
      code, ignore = fetch('http://localhost:{port}/'.format(port=deck_port))
      if code == 200:
        break
      else:
        time.sleep(0.1)

    print """Spinnaker is now ready on port {port}.

You can ^C (ctrl-c) to finish the script, which will stop emitting errors.
Spinnaker will continue until you run ./spinnaker/dev/stop_dev.sh
""".format(port=deck_port)

    while True:
      time.sleep(3600)

  def program_to_subsystem(self, program):
    return program

  def subsystem_to_program(self, subsystem):
    return subsystem


if __name__ == '__main__':
  if not os.path.exists('deck'):
     sys.stderr.write('This script needs to be run from the root of'
                      ' your build directory.\n')
     sys.exit(-1)

  DevRunner.main()
