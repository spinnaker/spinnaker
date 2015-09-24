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

import argparse
import os
import re
import resource
import signal
import socket
import subprocess
import sys
import time
import yaml

import configure_util
from install.install_utils import run
from install.install_utils import run_or_die


class Runner(object):
  __SPINNAKER_COMPONENT = 'all'

  # These are all the standard spinnaker subsystems that can be started
  # independent of one another (but after gce-kms)
  INDEPENDENT_SUBSYSTEM_LIST=['clouddriver', 'front50', 'orca', 'rosco',
                              'echo']

  # These are additional, optional subsystems.
  OPTIONAL_SUBSYSTEM_LIST=['rush', 'igor']

  def __init__(self, installation_parameters=None):
    self.__installation = (installation_parameters
                           or configure_util.InstallationParameters())
    self.__bindings = configure_util.ConfigureUtil(
        self.__installation).load_bindings()

    for name,value in self.__bindings.items():
      # Add bindings as environment variables so they can be picked up by
      # embedded YML files and maybe internally within the implementation
      # (e.g. amos needs the AWS_*_KEY but isnt clear if that could be
      # injected through a yaml).
      os.environ[name] = value

  # These are all the spinnaker subsystems in total.
  @classmethod
  def get_all_subsystem_names(cls):
    result = ['gce-kms']
    result.extend(cls.INDEPENDENT_SUBSYSTEM_LIST)
    result.extend(cls.OPTIONAL_SUBSYSTEM_LIST)
    result.append('gate')

    return result

  @staticmethod
  def run_daemon(path, args, detach=True):
    pid = os.fork()
    if pid == 0:
      if detach:
        os.setsid()
    else:
      return pid

    # Iterate through and close all file descriptors
    # (other than stdin/out/err).
    maxfd = resource.getrlimit(resource.RLIMIT_NOFILE)[1]
    if (maxfd == resource.RLIM_INFINITY):
       maxfd = 1024
    for fd in range(3, maxfd):
       try:
         os.close(fd)
       except OSError:
         pass

    os.execv(path, args)

  def stop_subsystem(self, subsystem, pid):
    os.kill(pid, signal.SIGTERM)

  def start_subsystem(self, subsystem):
    program = self.subsystem_to_program(subsystem)

    if program == subsystem:
      print 'Starting {subsystem}'.format(subsystem=subsystem)
    else:
      print 'Starting {subsystem} as "{program}"'.format(
        subsystem=subsystem, program=program)

    command = os.path.join(self.__installation.SUBSYSTEM_ROOT_DIR,
                           program, 'bin', program)
    base_log_path = os.path.join(self.__installation.LOG_DIR, subsystem)

    return self.run_daemon('/bin/bash',
                    ['/bin/bash',
                     '-c',
                     '({command} > {log}.log) 2>&1 '
                     '| tee -a {log}.log >& {log}.err'
                     .format(command=command, log=base_log_path)])

  def start_dependencies(self):
    run_dir = self.__installation.EXTERNAL_DEPENDENCY_SCRIPT_DIR

    run_or_die(
        'REDIS_HOST={host}'
        ' LOG_DIR={log_dir}'
        ' {run_dir}/start_redis.sh'
        .format(host=self.__bindings.get('REDIS_HOST', 'localhost'),
                log_dir=self.__installation.LOG_DIR,
                run_dir=run_dir),
        echo=True)

    run_or_die(
        'CASSANDRA_HOST={host}'
        ' CASSANDRA_DIR={install_dir}/cassandra'
        ' {run_dir}/start_cassandra.sh'
        .format(host=self.__bindings.get('CASSANDRA_HOST', 'localhost'),
                install_dir=self.__installation.SPINNAKER_INSTALL_DIR,
                run_dir=run_dir),
         echo=True)

  def maybe_start_job(self, jobs, subsystem):
      if subsystem in jobs:
        print '{subsystem} already running as pid {pid}'.format(
          subsystem=subsystem, pid=jobs[subsystem])
        return jobs[subsystem]
      else:
        return self.start_subsystem(subsystem)

  def start_spinnaker_subsystems(self, jobs):
    started_list = []
    for subsys in self.INDEPENDENT_SUBSYSTEM_LIST:
        pid = self.maybe_start_job(jobs, subsys)
        if pid:
          started_list.append((subsys, pid))

    if self.__bindings.get('DOCKER_ADDRESS', ''):
      pid = self.maybe_start_job(jobs, 'rush')
      if pid:
         started_list.append(('rush', pid))
    else:
      print 'Not using rush because docker is not configured.'

    if self.__bindings.get('JENKINS_ADDRESS', ''):
        if self.__bindings.get('IGOR_ENABLED', 'false') == 'false':
            sys.stderr.write(
                'WARNING: Not starting igor because IGOR_ENABLED=false'
                ' even though JENKINS_ADDRESS="{address}"'.format(
                      self.__bindings['JENKINS_ADDRESS']))
        else:
            pid = self.maybe_start_job(jobs, 'igor')
            if pid:
               started_list.append(('igor', pid))
    else:
      print 'Not using igor because jenkins is not configured.'

    for subsystem in started_list:
      self.wait_for_service(subsystem[0], pid=subsystem[1])

    pid = self.maybe_start_job(jobs, 'gate')
    self.wait_for_service('gate', pid=pid)

  def get_all_java_subsystem_jobs(self):
    """Look up all the running java jobs.

    Returns:
       dictionary keyed by package name (spinnaker subsystem) with pid values.
    """
    re_pid_and_subsystem = None

    # If we use jps then some classnames dont match the subsystems we expect.
    hack_java_package_to_subsystem = {'gcekms': 'gce-kms'}

    # Try jps, but this is not currently available on openjdk-8-jre
    # so depending on the JRE environment, this might not work.
    p = subprocess.Popen(
        'jps -l', stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        shell=True, close_fds=True)
    stdout, stderr = p.communicate()
    if p.returncode == 0:
      re_pid_and_subsystem = re.compile(
          '([0-9]+) com\.netflix\.spinnaker\.([^\.]+)\.')
    else:
      # If jps did not work, then try using ps instead.
      # ps can be flaky because it truncates the commandline to 4K, which
      # is typically too short for the spinnaker classpath alone, never mind
      # additional arguments. The reliable part of the command is in the
      # truncated region, so we'll look for something in a potentially
      # brittle part of the commandline.
      stdout, stderr = subprocess.Popen(
          'ps -fwwC java', stdout=subprocess.PIPE, stderr=subprocess.PIPE,
          shell=True, close_fds=True).communicate()
      re_pid_and_subsystem = re.compile(
          '([0-9]+) .* -classpath {install_root}/([^/]+)/'
          .format(install_root=self.__installation.SUBSYSTEM_ROOT_DIR))

    job_map = {}
    for match in re_pid_and_subsystem.finditer(stdout):
      name = self.program_to_subsystem(match.groups()[1])
      name = hack_java_package_to_subsystem.get(name, name)
      pid = int(match.groups()[0])
      job_map[name] = pid

    return job_map

  def find_port(self, subsystem):
    path = os.path.join(self.__installation.CONFIG_DIR,
                        subsystem + '-local.yml')
    with open(path, 'r') as f:
      data = yaml.load(f, Loader=yaml.Loader)
    return data['server']['port']

  @staticmethod
  def start_tail(path):
    return subprocess.Popen(['/usr/bin/tail', '-f', path], stdout=sys.stdout,
                            shell=False)

  def wait_for_service(self, subsystem, pid, show_log_while_waiting=True):
    try:
      port = self.find_port(subsystem)
    except KeyError:
      error = ('A port for {subsystem} is not explicit in the configuration.'
               ' Assuming it is up since it isnt clear how to test for it.'
               .format(subsystem=subsystem))
      sys.stderr.write(error)
      raise SystemExit(error)

    log_path = os.path.join(self.__installation.LOG_DIR, subsystem + '.log')
    print ('Waiting for {subsys} to start accepting requests on port {port}...'
           .format(subsys=subsystem, port=port))
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    tail_process = None
    wait_msg_retries = 5 # Give half a second before showing log/warning.
    while True:
      try:
        sock.connect(('localhost', port))
        break
      except IOError:
        try:
          os.kill(pid, 0)
        except OSError:
          raise SystemExit('{subsys} failed to start'.format(
              subsys=subsystem))

        if show_log_while_waiting and not tail_process:
          if wait_msg_retries > 0:
            # Give an initial delay before checking,
            # as well as a delay between checks.
            # The initial delay is because of a race condition having an old
            # log file and starting to write a new one.
            wait_msg_retries -= 1
          elif not os.path.exists(log_path):
            print '{path} does not yet exist..'.format(path=log_path)
            wait_msg_retries = 5  # dont display again for half a second.
          else:
            tail_process = self.start_tail(log_path)

        time.sleep(0.1)

    if tail_process:
      tail_process.kill()
    sock.close()
    print 'Spinnaker subsystem={subsys} is up.'.format(subsys=subsystem)


  def warn_if_configuration_looks_old(self):
      global_config = '{config_dir}/spinnaker_config.cfg'.format(
          config_dir=self.__installation.CONFIG_DIR)
      global_stat = os.stat(global_config)
      errors = 0

      for subsys in self.get_all_subsystem_names():
         config_path = os.path.join(self.__installation.CONFIG_DIR,
                                    subsys + '-local.yml')
         if os.path.exists(config_path):
           config_stat = os.stat(config_path)
           if config_stat.st_mtime < global_stat.st_mtime:
              errors += 1
              sys.stderr.write('WARNING: {config} is older than {baseline}\n'
                               .format(config=config_path,
                               baseline=global_config))
      if errors > 0:
         sys.stderr.write("""
To fix this run the following:
   sudo {script_dir}/stop_spinnaker.sh
   sudo {script_dir}/reconfigure_spinnaker_instance.sh
   sudo {script_dir}/start_spinnaker.sh

Proceeding anyway.
""".format(script_dir=self.__installation.UTILITY_SCRIPT_DIR))


  def stop_deck(self):
    print 'Stopping apache server while starting Spinnaker.'
    run('service apache2 stop', echo=True)

  def start_deck(self):
    print 'Starting apache server.'
    run('service apache2 start', echo=True)

  def start_all(self, options):
    self.warn_if_configuration_looks_old()

    self.stop_deck()
    try:
      os.makedirs(self.__installation.LOG_DIR)
    except OSError:
      pass
    self.start_dependencies()

    jobs = self.get_all_java_subsystem_jobs()
    pid = self.maybe_start_job(jobs, 'gce-kms')
    self.wait_for_service('gce-kms', pid)

    self.start_spinnaker_subsystems(jobs)
    self.start_deck()
    print 'Started all Spinnaker components.'

  def maybe_stop_subsystem(self, name, jobs):
    pid = jobs.get(name, 0)
    if not pid:
      print '{name} was not running'.format(name=name)
      return 0

    print 'Terminating {name} in pid={pid}'.format(name=name, pid=pid)
    self.stop_subsystem(name, pid)
    return pid

  def stop(self, options):
    stopped_list = []
    component = options.component.lower()
    if not component:
      component = self.__SPINNAKER_COMPONENT

    jobs = self.get_all_java_subsystem_jobs()
    if component != self.__SPINNAKER_COMPONENT:
        if component == 'deck':
          self.stop_deck()
        else:
          pid = self.maybe_stop_subsystem(component, jobs)
          if pid:
            stopped_list.append((component, pid))
    else:
        self.stop_deck()
        for name in self.get_all_subsystem_names():
          pid = self.maybe_stop_subsystem(name, jobs)
          if pid:
            stopped_list.append((name, pid))

    for name,pid in stopped_list:
        count = 0
        while True:
          try:
            os.kill(pid, 0)
            count += 1
            if count % 10 == 0:
                if count == 0:
                  sys.stdout.write('Waiting on {name}, pid={pid}..'.format(
                      name=name, pid=pid))
                else:
                  sys.stdout.write('.')
                sys.stdout.flush()
            time.sleep(0.1)
          except OSError:
            if count > 10:  # We didnt start logging until 10
              sys.stdout.write('{pid} stopped.\n'.format(pid=pid))
              sys.stdout.flush()
            break


  def run(self, options):
    action = options.action.upper()
    component = options.component.lower()
    if action == 'RESTART':
      self.stop(options)
      action = 'START'

    if action == 'START':
      if component == self.__SPINNAKER_COMPONENT:
        self.start_all(options)
      else:
        self.maybe_start_job(self.get_all_java_subsystem_jobs(), component)

    if action == 'STOP':
      self.stop(options)

  def init_argument_parser(self, parser):
    parser.add_argument('action', help='START or STOP or RESTART')
    parser.add_argument('component',
                        help='Name of component to start or stop, or ALL')

  @classmethod
  def main(cls):
    runner = cls()
    parser = argparse.ArgumentParser()
    runner.init_argument_parser(parser)
    options = parser.parse_args()
    runner.run(options)

  def program_to_subsystem(self, program):
    if program == 'front50-web':
      return 'front50'
    else:
      return program

  def subsystem_to_program(self, subsystem):
    if subsystem == 'front50':
      return 'front50-web'
    else:
      return subsystem


if __name__ == '__main__':
  Runner.main()
