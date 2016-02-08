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
import shutil
import signal
import socket
import subprocess
import sys
import time
import yaml

from configurator import Configurator

from fetch import check_fetch
from fetch import get_google_project
from fetch import is_google_instance
from fetch import GOOGLE_METADATA_URL
from run import check_run_quick
from run import run_quick


__ifconfig_lines = None
def is_local(ip):
  """Determine if the given ip address refers to this machine or not.

  Args:
    ip [string]: A hostname or ip address suitable for binding to a socket.
  """
  if (ip == 'localhost' or ip == '127.0.0.1'  # variations of localhost binding
      or ip == socket.gethostname()           # external NIC binding
      or ip == '0.0.0.0'):                    # all local interfaces
    return True

  global __ifconfig_lines
  if not __ifconfig_lines:
    result = run_quick('/sbin/ifconfig | egrep -i " inet?"', echo=False)
    __ifconfig_lines = result.stdout

  # This should be quick and dirty without worrying about IP4 vs IP6 or
  # particulars about how the system ifconfig formats its output.
  # It assumes the ip is valid.
  return __ifconfig_lines.find(ip) >= 0


class Runner(object):
  """Provides routines for starting / stopping Spinnaker subsystems."""

  # Denotes we are operating on all the subsystems
  __SPINNAKER_COMPONENT = 'all'

  # These are all the standard spinnaker subsystems that can be started
  # independent of one another.
  INDEPENDENT_SUBSYSTEM_LIST=['clouddriver', 'front50', 'orca', 'rosco',
                              'echo', 'rush']

  # Denotes a process running on an external host
  EXTERNAL_PID = -123

  @property
  def _first_time_use_instructions(self):
    """Instructions for configuring Spinnaker for the first time.

    Google Cloud Platform is treated as a special case because some
    configuration parameters have defaults implied by the runtime environment.
    """
    optional_defaults_if_on_google = ''

    if is_google_instance():
      google_project = get_google_project()
      optional_defaults_if_on_google = """    #   NOTE: Since you deployed on GCE:
    #      * You do not need JSON credentials to manage project id "{google_project}".
""".format(google_project=google_project)

    return """
    {sudo}mkdir -p {config_dir}
    {sudo}cp {install_dir}/default-spinnaker-local.yml \\
       {config_dir}/spinnaker-local.yml
    {sudo}chmod 600 {config_dir}/spinnaker-local.yml

    # edit {config_dir}/spinnaker-local.yml to your liking:
    #   If you want to deploy to Amazon Web Services:
    #      * Set providers.aws.enabled = true.
    #      * Add your keys to providers.aws.primaryCredentials
    #        or write them to $HOME/.aws/credentials
    #
    #   If you want to deploy to Google Cloud Platform:
    #      * Set providers.google.enabled = true.
    #      * Add your project_id to providers.google.primaryCredentials.project
    #      * Add your the path to your json service account credentials to
    #        providers.google.primaryCredentials.jsonPath.
{optional_defaults_if_on_google}
    {sudo}{script_dir}/stop_spinnaker.sh
    {sudo}{script_dir}/reconfigure_spinnaker.sh
    {sudo}{script_dir}/start_spinnaker.sh
""".format(
  sudo='' if os.geteuid() else 'sudo ',
  install_dir=self.__configurator.installation_config_dir,
  config_dir=self.__configurator.user_config_dir,
  script_dir=self.__installation.UTILITY_SCRIPT_DIR,
  optional_defaults_if_on_google=optional_defaults_if_on_google)

  @property
  def bindings(self):
    return self.__bindings

  @property
  def installation(self):
    return self.__installation

  @property
  def configurator(self):
    return self.__configurator

  def __init__(self, installation_parameters=None):
    self.__configurator = Configurator(installation_parameters)
    self.__bindings = self.__configurator.bindings
    self.__installation = self.__configurator.installation

    local_yml_path = os.path.join(self.__installation.USER_CONFIG_DIR,
                                  'spinnaker-local.yml')
    if not os.path.exists(local_yml_path):
      # Just warn and proceed anyway.
      sys.stderr.write('WARNING: No {local_yml_path}\n'.format(
        local_yml_path=local_yml_path))

  # These are all the spinnaker subsystems in total.
  @classmethod
  def get_all_subsystem_names(cls):
    # These are always started. Order doesnt matter.
    result = list(cls.INDEPENDENT_SUBSYSTEM_LIST)

    # These are additional, optional subsystems.
    result.extend(['igor'])

    # Gate is started after everything else is up and available.
    result.append('gate')

    # deck is not included here because it is run within apache.
    # which is managed separately.
    return result

  @staticmethod
  def run_daemon(path, args, detach=True, environ=None):
    """Run a program as a long-running background process.

    Args:
      path [string]: Path to the program to run.
      args [list of string]: Arguments to pass to program
      detach [bool]: True if we're running it in separate process group.
         A separate process group will continue after we exit.
    """
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

    os.execve(path, args, environ or os.environ)

  def stop_subsystem(self, subsystem, pid):
    """Stop the specified subsystem.

    Args:
      subsystem [string]: The name of the subsystem.
      pid [int]: The process id of the running subsystem.
    """
    os.kill(pid, signal.SIGTERM)

  def start_subsystem_if_local(self, subsystem, environ=None):
    """Start the specified subsystem.

    Args:
      subsystem [string]: The name of the subsystem.
      environ [dict]: If set, use these environment variables instead.

    Returns:
      The pid of the subsystem once running or EXTERNAL_PID if run elsewhere.
    """
    host = self.__bindings.get(
        'services.{system}.host'.format(system=subsystem))
    if not is_local(host):
      print 'Expecting {subsystem} to be on external host {host}'.format(
          subsystem=subsystem, host=host)
      return self.EXTERNAL_PID

    return self.start_subsystem(subsystem, environ)

  def start_subsystem(self, subsystem, environ=None):
    """Start the specified subsystem.

    Args:
      subsystem [string]: The name of the subsystem.
      environ [dict]: If set, use these environment variables instead.

    Returns:
      The pid of the subsystem once running.
    """
    print 'Starting {subsystem}'.format(subsystem=subsystem)

    command = os.path.join(self.__installation.SUBSYSTEM_ROOT_DIR,
                           subsystem, 'bin', subsystem)
    base_log_path = os.path.join(self.__installation.LOG_DIR, subsystem)

    return self.run_daemon('/bin/bash',
                    ['/bin/bash',
                     '-c',
                     '("{command}" > "{log}.log") 2>&1 '
                     '| tee -a "{log}.log" >& "{log}.err"'
                     .format(command=command, log=base_log_path)],
                     environ=environ)

  def start_dependencies(self):
    """Start all the external dependencies running on this host."""
    run_dir = self.__installation.EXTERNAL_DEPENDENCY_SCRIPT_DIR

    cassandra_host = self.__bindings.get('services.cassandra.host')
    redis_host = self.__bindings.get('services.redis.host')

    print 'Starting external dependencies...'
    check_run_quick(
        'REDIS_HOST={host}'
        ' LOG_DIR="{log_dir}"'
        ' "{run_dir}/start_redis.sh"'
        .format(host=redis_host,
                log_dir=self.__installation.LOG_DIR,
                run_dir=run_dir),
        echo=True)

    check_run_quick(
        'CASSANDRA_HOST={host}'
        ' CASSANDRA_DIR="{install_dir}/cassandra"'
        ' "{run_dir}/start_cassandra.sh"'
        .format(host=cassandra_host,
                install_dir=self.__installation.SPINNAKER_INSTALL_DIR,
                run_dir=run_dir),
         echo=True)

  def get_subsystem_environ(self, subsystem):
    if self.__bindings and subsystem != 'clouddriver':
       return os.environ

    if not self.__bindings.get('providers.aws.enabled'):
       return os.environ

    environ = dict(os.environ)
    # Set AWS environment variables for credentials if not already there.
    key_id = self.__bindings.get(
    'providers.aws.primaryCredentials.access_key_id')
    secret_key = self.__bindings.get(
      'providers.aws.primaryCredentials.secret_key')
    if key_id:
      environ['AWS_ACCESS_KEY_ID'] = environ.get('AWS_ACCESS_KEY_ID', key_id)

      # TODO(ewiseblatt): 20151030
      # http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html
      # suggests the proper key is AWS_ACCESS_KEY_ID however this appears not
      # to work (when running from gradle). Instead the key needs to be
      # AWS_ACCESS_KEY, so we will set both for now.
      environ['AWS_ACCESS_KEY'] = environ.get('AWS_ACCESS_KEY', key_id)
    if secret_key:
      environ['AWS_SECRET_KEY'] = environ.get('AWS_SECRET_KEY', secret_key)

    return environ

  def maybe_start_job(self, jobs, subsystem):
      if subsystem in jobs:
        print '{subsystem} already running as pid {pid}'.format(
          subsystem=subsystem, pid=jobs[subsystem])
        return jobs[subsystem]
      else:
        return self.start_subsystem_if_local(
              subsystem, environ=self.get_subsystem_environ(subsystem))

  def start_spinnaker_subsystems(self, jobs):
    started_list = []
    for subsys in self.INDEPENDENT_SUBSYSTEM_LIST:
        pid = self.maybe_start_job(jobs, subsys)
        if pid:
          started_list.append((subsys, pid))

    jenkins_address = self.__bindings.get(
        'services.jenkins.defaultMaster.baseUrl')
    igor_enabled = self.__bindings.get('services.igor.enabled')

    # Conditionally run igor only if jenkins is configured.
    # A '$' indicates an unbound variable so it wasn't configured.
    have_jenkins = jenkins_address and jenkins_address[0] != '$'
    if igor_enabled != have_jenkins:
      if igor_enabled:
        igor_enabled = False
        sys.stderr.write(
          'WARNING: Not starting igor because services.jenkins.baseUrl is'
          ' not set even though services.igor.enabled = true\n')
      else:
        sys.stderr.write(
          'WARNING: Not starting igor because services.igor.enabled = false'
          ' even though services.jenkins.baseUrl = {address}\n'.format(
              address=jenkins_address))

    if igor_enabled:
      pid = self.maybe_start_job(jobs, 'igor')
      if pid:
        started_list.append(('igor', pid))

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
      name = match.groups()[1]
      pid = int(match.groups()[0])
      job_map[name] = pid

    return job_map

  def find_new_port_and_address(self, subsystem):
    """This is assuming a specific configuration practice.

    Overrides for default ports only occur in ~/<subsystem>-local.yml
    or in ~/spinnaker-local.yml or in <install>/config/spinnnaker.yml

    The actual runtime uses spring, which can be overridden for additional
    search locations.
    """
    path = os.path.join(self.__installation.USER_CONFIG_DIR,
                        subsystem + '-local.yml')
    if os.path.exists(path):
       bindings = yaml_util.YamlBindings()
       bindings.import_dict(self.__bindings.map)
       bindings.import_path(path)
    else:
       bindings = self.__bindings

    subsystem = subsystem.replace('-', '_')
    return (bindings.get('services.{subsys}.port'.format(subsys=subsystem)),
            bindings.get('services.{subsys}.host'.format(subsys=subsystem)))


  def find_port_and_address(self, subsystem):
    if self.__bindings:
      return self.find_new_port_and_address(subsystem)

    path = os.path.join(self.__installation.USER_CONFIG_DIR,
                        subsystem + '-local.yml')
    if not os.path.exists(path):
      raise SystemExit('ERROR: Expected configuration file {path}.\n'
                       '       Run {sudo}{dir}/reconfigure_spinnaker.sh'
                       .format(path=path,
                               sudo='' if os.geteuid() else 'sudo ',
                               dir=self.__installation.UTILITY_SCRIPT_DIR))

    with open(path, 'r') as f:
      data = yaml.load(f, Loader=yaml.Loader)
    return data['server']['port'], data['server'].get('address', None)

  @staticmethod
  def start_tail(path):
    return subprocess.Popen(['/usr/bin/tail', '-f', path], stdout=sys.stdout,
                            shell=False)

  def wait_for_service(self, subsystem, pid, show_log_while_waiting=True):
    try:
      port, address = self.find_port_and_address(subsystem)
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
      if address:
        host_colon = address.find(':')
        host = address if host_colon < 0 else address[:host_colon]
      else:
        host = 'localhost'

      try:
        sock.connect((host, port))
        break
      except IOError:
        if pid == self.EXTERNAL_PID:
           tail_process = True  # Pretend, but really nothing to tail.
        else:
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
            wait_msg_retries = 5  # don't display again for half a second.
          else:
            tail_process = self.start_tail(log_path)

        time.sleep(0.1)

    if tail_process and pid != self.EXTERNAL_PID:
      tail_process.kill()
    sock.close()
    print 'Spinnaker subsystem={subsys} is up.'.format(subsys=subsystem)


  def warn_if_configuration_looks_old(self):
    local_yml_path = os.path.join(self.__installation.USER_CONFIG_DIR,
                                  'spinnaker-local.yml')
    try:
      global_stat = os.stat(local_yml_path)
    except OSError:
      return

    settings_path = os.path.join(
          self.__installation.DECK_INSTALL_DIR,
      self.__installation.HACK_DECK_SETTINGS_FILENAME)
    old = False
    if os.path.exists(settings_path):
        setting_stat = os.stat(settings_path)
        if setting_stat.st_mtime < global_stat.st_mtime:
           sys.stderr.write('WARNING: {settings} is older than {baseline}\n'
                            .format(settings=settings_path,
                                    baseline=local_yml_path))
           old = True

    if old:
       sys.stderr.write("""
To fix this run the following:
   sudo {script_dir}/stop_spinnaker.sh
   sudo {script_dir}/reconfigure_spinnaker_instance.sh
   sudo {script_dir}/start_spinnaker.sh

Proceeding anyway.
""".format(script_dir=self.__installation.UTILITY_SCRIPT_DIR))


  def stop_deck(self):
    print 'Stopping apache server while starting Spinnaker.'
    run_quick('service apache2 stop', echo=True)

  def start_deck(self):
    print 'Starting apache server.'
    run_quick('service apache2 start', echo=True)

  def start_all(self, options):
    self.check_configuration(options)

    try:
      os.makedirs(self.__installation.LOG_DIR)
    except OSError:
      pass
    self.start_dependencies()

    google_enabled = self.__bindings.get('providers.google.enabled')

    jobs = self.get_all_java_subsystem_jobs()
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
            if count > 10:  # We didn't start logging until 10
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

  def check_configuration(self, options):
    local_path = os.path.join(self.__installation.USER_CONFIG_DIR,
                              'spinnaker-local.yml')
    if not os.path.exists(local_path):
       sys.stderr.write(
          'WARNING: {path} does not exist.\n'
          'To custom-configure spinnaker do the following: {first_time_use}\n'
          .format(path=local_path,
                  first_time_use=self._first_time_use_instructions))
    self.warn_if_configuration_looks_old()

  @classmethod
  def main(cls):
    cls.check_java_version()
    runner = cls()
    parser = argparse.ArgumentParser()
    runner.init_argument_parser(parser)
    options = parser.parse_args()
    runner.run(options)

  @staticmethod
  def check_java_version():
    """Ensure that we will be running the right version of Java.

    The point here is to fail quickly with a concise message if not. Otherwise,
    the runtime will perform a check and give an obscure lengthy exception
    trace about a version mismatch which is not at all apparent as to what the
    actual problem is.
    """
    try:
      p = subprocess.Popen('java -version', shell=True,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
      stdout, stderr = p.communicate()
      code = p.returncode
    except OSError as error:
      return str(error)

    info = stdout
    if code != 0:
      return 'Java does not appear to be installed.'

    m = re.search(r'(?m)^openjdk version "(.*)"', info)
    if not m:
      m = re.search(r'(?m)^java version "(.*)"', info)
    if not m:
        raise SystemExit('Unrecognized java version:\n{0}'.format(info))
    if m.group(1)[0:3] != '1.8':
        raise SystemExit('You are running Java version {version}.'
             ' However, Java version 1.8 is required for Spinnaker.'
             ' Your PATH may be wrong, or you may need to install Java 1.8.'
             .format(version=m.group(1)))


if __name__ == '__main__':
  if os.geteuid():
    sys.stderr.write('ERROR: This script must be run with sudo.\n')
    sys.exit(-1)

  Runner.main()
