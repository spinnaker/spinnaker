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

from install.install_utils import fetch
from pylib import configure_util
from pylib import spinnaker_runner


class DevInstallationParameters(configure_util.InstallationParameters):
  DEV_SCRIPT_DIR = os.path.dirname(sys.argv[0])
  SUBSYSTEM_ROOT_DIR = os.getcwd()

  CONFIG_DIR = os.path.join(os.environ['HOME'], '.spinnaker')
  LOG_DIR = os.path.join(SUBSYSTEM_ROOT_DIR, 'logs')

  SPINNAKER_INSTALL_DIR = os.path.join(DEV_SCRIPT_DIR, '..')
  CONFIG_TEMPLATE_DIR = os.path.join(DEV_SCRIPT_DIR, '../config')

  UTILITY_SCRIPT_DIR = os.path.join(DEV_SCRIPT_DIR, '../runtime')
  EXTERNAL_DEPENDENCY_SCRIPT_DIR = os.path.join(DEV_SCRIPT_DIR, '../runtime')

  DECK_INSTALL_DIR = os.path.join(SUBSYSTEM_ROOT_DIR, 'deck')
  HACK_DECK_SETTINGS_FILENAME = 'settings.js'
  DECK_PORT = 9000


class DevRunner(spinnaker_runner.Runner):
  def __init__(self, installation_parameters=None):
    self.__installation = installation_parameters or DevInstallationParameters
    super(DevRunner, self).__init__(self.__installation)

  def start_subsystem(self, subsystem):
    print 'Starting {subsystem}'.format(subsystem=subsystem)
    command = os.path.join(
        self.__installation.SUBSYSTEM_ROOT_DIR,
        subsystem,
        'start_dev.sh')
    return self.run_daemon(command, [command])

  def tail_error_logs(self):
    log_dir = self.__installation.LOG_DIR
    try:
      os.makedirs(log_dir)
    except OSError:
      pass

    tail_jobs = []
    for subsystem in self.get_all_subsystem_names():
      path = os.path.join(log_dir, subsystem + '.err')
      open(path, 'w').close()
      tail_jobs.append(self.start_tail(path))

    return tail_jobs

  def get_deck_pid(self):
    program='node ./node_modules/webpack-dev-server/bin/webpack-dev-server.js'
    stdout, stderr = subprocess.Popen(
        'ps -fwwwC node', stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        shell=True, close_fds=True).communicate()
    match = re.search('(?m)^[^ ]+ +([0-9]+) .* {program}'.format(
        program=program), stdout)
    return int(match.groups()[0]) if match else None
    
  def start_deck(self):
    pid = self.get_deck_pid()
    if pid:
      print 'Deck is already running as pid={pid}'.format(pid=pid)
      return pid

    path = os.path.join(self.__installation.SUBSYSTEM_ROOT_DIR,
                        'deck/start_dev.sh')
    return self.run_daemon(path, [path])

  def stop_deck(self):
    pid = self.get_deck_pid()
    if pid:
      print 'Terminating deck in pid={pid}'.format(pid=pid)
      os.kill(pid, signal.SIGTERM)

  def reconfigure_subsystems(self, options):
    installation = self.__installation
    try:
      os.makedirs(installation.CONFIG_DIR)
    except OSError:
      pass

    if not os.path.exists(os.path.join(installation.CONFIG_DIR,
                                       'spinnaker_config.cfg')):
       shutil.copyfile(
         os.path.join(installation.CONFIG_TEMPLATE_DIR,
                      'default_spinnaker_config.cfg'),
         os.path.join(installation.CONFIG_DIR, 'spinnaker_config.cfg'))
       os.chmod(os.path.join(installation.CONFIG_DIR, 'spinnaker_config.cfg'),
                stat.S_IRUSR | stat.S_IWUSR)

       print """
*** WARNING: ********************************************************
***  No master config file $HOME/.spinnaker/spinnaker_config.cfg
***  We will create one for you, assuming a minimal configuration.
***
*** If that is not your intention, edit the .cfg and run again.
**********************************************************************

"""
    util = configure_util.ConfigureUtil(self.__installation)
    util.validate_or_die()
    bindings = util.load_bindings()
    util.update_all_config_files(bindings)

  def start_all(self, options):
    """Starts all the components then logs stderr to the console forever.

    The subsystems are in forked processes disassociated from this, so will
    continue running even after this process exists. Only the stderr logging
    to console will stop once this process is terminated. However, the
    logging will still continue into the LOG_DIR.
    """
    self.reconfigure_subsystems(options)

    ignore_tail_jobs = self.tail_error_logs()
    super(DevRunner, self).start_all(options)

    ignore_tail_jobs.append(
        self.start_tail(os.path.join(self.__installation.LOG_DIR, 'deck.log')))

    deck_port = self.__installation.DECK_PORT
    print 'Waiting for deck to start on port {port}'.format(port=deck_port)

    # Dont just wait for port to be ready,  but for deck to repsond
    # because it takes a long time to startup once port is ready.
    while True:
      code, ignore = fetch('http://localhost:{port}/'.format(port=deck_port))
      if code == 200:
        break
      else:
        time.sleep(0.1)

    print """Spinnaker is now ready on port {port}.

You can ^C (ctrl-c) to finish the script, which will stop emitting errors.
Spinnaker will continue until you run scripts/release/stop_spinnaker.sh
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

