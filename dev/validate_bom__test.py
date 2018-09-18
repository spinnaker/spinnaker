# Copyright 2017 Google Inc. All Rights Reserved.
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

"""This is the "test" module for the validate_bom script.

It is responsible for coordinating and running the integration tests.

The test catalog is read from --test_profiles (default all_tests.yaml).
There are two parts to the catalog: "aliases" and "tests".

The "tests" is a dictionary of tests. Each entry is keyed by the name of the
test. A test has the following structure:

test_name:
  requires:
    configuration:
      <commandline option>: <value>
    services: [<microservice name>]
  quota:
    <resource>: <uses>
  api: <primary microservice>
  args:
    alias: [<alias name>]
    <command line flag>: <value>

The test_name.requires specifies the requirements in order to run the test.
If a requirement is not satisfied, the test will be skipped.

The test_name.requires.configuration specifies expected options and values.
These are the same names as parameters to the validate_bom__main executable.
Typically this is used to guard a test for a particular configuration (e.g.
dont test against a platform if the platform was not enabled in the
deployment).

The test_name.requires.services is a list of services that the test requires
either directly or indirectly. This is used to ensure that the services are
ready before running the test. If the service is alive but not healthy then
the test will be failed automatically without even running it (provided it
wouldnt have been skipped).

The test_name.api is used to specify the primary microservice that the test
uses. This is used to determine which port to pass to the test since the remote
ports are forwarded to unused local ports known only to this test controller.

The test_name.args are the commandline arguments to pass to the test.
The names of the arguments are the test's argument names without the
prefixed '--'. If the value begins with a '$' then the remaining value
refers to the name of an option whose value should become the argument.
A special argument "aliases" is a list of aliases. These are names that
match the key of an entry in the "aliases" part of the file where all the
name/value pairs defined for the alias are bulk added as arguments.

The test_name.quota is used to rate-limit test execution where tests are
sensitive to resource costs. Arbitrary names can be limited using
--test_quota. The controller will use this as a semaphore to rate-limit
test execution for these resources. Unrestricted resources wont rate-limit.
If the cost bigger than the total semaphore capacity then the test will
be given all the quota once all is available.

There is an overall rate-limiting semaphore on --test_concurrency for
how many tests can run at a time. This is enforced at the point of execution,
after all the setup and filtering has taken place.
"""

# pylint: disable=broad-except

from multiprocessing.pool import ThreadPool

import atexit
import collections
import logging
import math
import os
import re
import subprocess
import socket
import threading
import time
import traceback
import yaml

try:
  from urllib2 import urlopen, HTTPError, URLError
except ImportError:
  from urllib.request import urlopen
  from urllib.error import HTTPError, URLError


from buildtool import (
    add_parser_argument,
    determine_subprocess_outcome_labels,
    check_subprocess,
    check_subprocesses_to_logfile,
    raise_and_log_error,
    ConfigError,
    ResponseError,
    TimeoutError,
    UnexpectedError)

from validate_bom__deploy import replace_ha_services


ForwardedPort = collections.namedtuple('ForwardedPort', ['child', 'port'])


def _unused_port():
  """Find a port that is not currently in use."""
  # pylint: disable=unused-variable
  sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  sock.bind(('localhost', 0))
  addr, port = sock.getsockname()
  sock.close()
  return port


class QuotaTracker(object):
  """Manages quota for individual resources.

  Note that this quota tracker is purely logical. It does not relate to the
  real world. Others may be using the actual quota we have. This is only
  regulating the test's use of the quota.
  """

  MAX_QUOTA_METRIC_NAME = 'ResourceQuotaMax'
  FREE_QUOTA_METRIC_NAME = 'ResourceQuotaAvailable'
  INSUFFICIENT_QUOTA_METRIC_NAME = 'ResourceQuotaShortage'

  def __init__(self, max_counts, metrics):
    """Constructor.

    Args:
      max_counts: [dict] The list of resources and quotas to manage.
    """
    self.__counts = dict(max_counts)
    self.__max_counts = dict(max_counts)
    self.__condition_variable = threading.Condition()
    self.__metrics = metrics

    for name, value in max_counts.items():
      labels = {'resource': name}
      self.__metrics.set(self.MAX_QUOTA_METRIC_NAME, labels, value)
      self.__metrics.set(self.FREE_QUOTA_METRIC_NAME, labels, value)

  def acquire_all_safe(self, who, quota):
    """Acquire the desired quota, if any.

    This is thread-safe and will block until it can be satisified.

    Args:
      who: [string] Who is asking, for logging purposes.
      quota: [dict] The desired quota for each keyed resource, if any.
    Returns:
      The quota acquired.
    """
    got = None
    with self.__condition_variable:
      got = self.acquire_all_or_none_unsafe(who, quota)
      while got is None:
        logging.info('"%s" waiting on quota %s', who, quota)
        self.__condition_variable.wait()
        got = self.acquire_all_or_none_unsafe(who, quota)
    return got

  def acquire_all_or_none_safe(self, who, quota):
    """Acquire the desired quota, if any.

    This is thread-safe, however will return None rather than block.

    Args:
      who: [string] Who is asking, for logging purposes.
      quota: [dict] The desired quota for each keyed resource, if any.
    Returns:
      The quota acquired if successful, or None if not.
    """
    with self.__condition_variable:
      return self.acquire_all_or_none_unsafe(who, quota)

  def acquire_all_or_none_unsafe(self, who, quota):
    """Acquire the desired quota, if any.

    This is not thread-safe so should be called while locked.

    Args:
      who: [string] Who is asking, for logging purposes.
      quota: [dict] The desired quota for each keyed resource, if any.
    Returns:
      The quota acquired if successful, or None if not.
    """
    if not quota:
      return {}
    logging.info('"%s" attempting to acquire quota %s', who, quota)
    acquired = {}
    have_all = True
    for key, value in quota.items():
      got = self.__acquire_resource_or_none(key, value)
      if not got:
        have_all = False  # Be lazy so we can record all the missing quota
      else:
        acquired[key] = got

    if have_all:
      return acquired

    self.release_all_unsafe(who, acquired)
    return None

  def release_all_safe(self, who, quota):
    """Release all the resource quota.

    Args:
      who: [string] Who is releasing, for logging purposes.
      quota: [dict] The non-None result from an acquire_all* method.
    """
    with self.__condition_variable:
      self.release_all_unsafe(who, quota)
      self.__condition_variable.notify_all()

  def release_all_unsafe(self, who, quota):
    """Release all the resource quota.

    This is not thread-safe so should be called while locked.

    Args:
      who: [string] Who is releasing, for logging purposes.
      quota: [dict] The non-None result from an acquire_all* method.
    """
    if not quota:
      return
    logging.debug('"%s" releasing quota %s', who, quota)
    for key, value in quota.items():
      self.__release_resource(key, value)

  def __acquire_resource_or_none(self, name, count):
    """Attempt to acquire some amount of quota.

    Args:
      name: [string] The name of the resource we're acquiring.
      count: [int] The amount of the resource

    Returns:
      The amount we were given. This is either all or none. If non-zero
      but less than we asked for, then it gave us the max quota it has.
      In order for this to be the case, it must have all the quota available.
      Otherwise it will return 0.
    """
    have = self.__counts.get(name)
    if have is None:
      return count
    if have >= count:
      self.__counts[name] = have - count
      self.__metrics.set(
          self.FREE_QUOTA_METRIC_NAME, {'resource': name}, self.__counts[name])
      return count
    max_count = self.__max_counts[name]
    if have == max_count:
      logging.warning('Quota %s has a max of %d but %d is desired.'
                      ' Acquiring all the quota as a best effort.',
                      name, max_count, count)
      self.__counts[name] = 0
      self.__metrics.set(
          self.FREE_QUOTA_METRIC_NAME, {'resource': name}, 0)
      return have
    logging.warning('Quota %s has %d remaining, but %d are needed.'
                    ' Rejecting the request for now.',
                    name, have, count)
    self.__metrics.inc_counter(
        self.INSUFFICIENT_QUOTA_METRIC_NAME, {'resource': name},
        amount=count - have)
    return 0

  def __release_resource(self, name, count):
    """Restores previously acquired resource quota."""
    have = self.__counts.get(name, None)
    if have is not None:
      self.__counts[name] = have + count
      self.__metrics.set(
          self.FREE_QUOTA_METRIC_NAME, {'resource': name}, self.__counts[name])


class ValidateBomTestController(object):
  """The test controller runs integration tests against a deployment."""

  @property
  def test_suite(self):
    """Returns the main test suite loaded from --test_suite."""
    return self.__test_suite

  @property
  def options(self):
    """The configuration options."""
    return self.__deployer.options

  @property
  def passed(self):
    """Returns the passed tests and reasons."""
    return self.__passed

  @property
  def failed(self):
    """Returns the failed tests and reasons."""
    return self.__failed

  @property
  def skipped(self):
    """Returns the skipped tests and reasons."""
    return self.__skipped

  @property
  def exit_code(self):
    """Determine final exit code for all tests."""
    return -1 if self.failed else 0

  def __close_forwarded_ports(self):
    for forwarding in self.__forwarded_ports.values():
      try:
        forwarding[0].kill()
      except Exception as ex:
        logging.error('Error terminating child: %s', ex)

  def __collect_gce_quota(self, project, region,
                          project_percent=100.0, region_percent=100.0):
    project_info_json = check_subprocess('gcloud compute project-info describe'
                                         ' --format yaml'
                                         ' --project %s' % project)
    project_info = yaml.safe_load(project_info_json)
    project_quota = {'gce_global_%s' % info['metric']:
                          int(max(1, math.floor(
                              project_percent * (info['limit'] - info['usage']))))
                     for info in project_info['quotas']}

    region_info_json = check_subprocess('gcloud compute regions describe'
                                        ' --format yaml'
                                        ' %s' % region)
    region_info = yaml.safe_load(region_info_json)
    region_quota = {
        'gce_region_%s' % info['metric']: int(max(
            1, math.floor(region_percent * (info['limit'] - info['usage']))))
        for info in region_info['quotas']
    }
    return project_quota, region_quota
    
  def __init__(self, deployer):
    options = deployer.options
    quota_spec = {}

    if options.google_account_project:
      project_quota, region_quota = self.__collect_gce_quota(
          options.google_account_project, options.test_gce_quota_region,
          project_percent=options.test_gce_project_quota_factor,
          region_percent=options.test_gce_region_quota_factor)
      quota_spec.update(project_quota)
      quota_spec.update(region_quota)

    if options.test_default_quota:
      quota_spec.update({
          parts[0].strip(): int(parts[1])
          for parts in [entry.split('=')
                        for entry in options.test_default_quota.split(',')]
      })

    if options.test_quota:
      quota_spec.update(
          {parts[0].strip(): int(parts[1])
           for parts in [entry.split('=')
                         for entry in options.test_quota.split(',')]})

    self.__quota_tracker = QuotaTracker(quota_spec, deployer.metrics)
    self.__deployer = deployer
    self.__lock = threading.Lock()
    self.__passed = []  # Resulted in success
    self.__failed = []  # Resulted in failure
    self.__skipped = []  # Will not run at all
    with open(options.test_profiles, 'r') as fd:
      self.__test_suite = yaml.safe_load(fd)
    self.__extra_test_bindings = (
        self.__load_bindings(options.test_extra_profile_bindings)
        if options.test_extra_profile_bindings
        else {}
    )

    num_concurrent = len(self.__test_suite.get('tests')) or 1
    num_concurrent = int(min(num_concurrent,
                             options.test_concurrency or num_concurrent))
    self.__semaphore = threading.Semaphore(num_concurrent)

    # dictionary of service -> ForwardedPort
    self.__forwarded_ports = {}
    atexit.register(self.__close_forwarded_ports)

    # Map of service names to native ports.
    self.__service_port_map = {
        # These are critical to most tests.
        'clouddriver': 7002,
        'clouddriver-caching': 7002,
        'clouddriver-rw': 7002,
        'clouddriver-ro': 7002,
        'gate': 8084,
        'front50': 8080,

        # Some tests needed these too.
        'orca': 8083,
        'rosco': 8087,
        'igor': 8088,
        'echo': 8089,
        'echo-scheduler': 8089,
        'echo-replica': 8089
    }

  def __replace_ha_api_service(self, service, options):
    transform_map = {}
    if options.ha_clouddriver_enabled:
      transform_map['clouddriver'] = 'clouddriver-rw'
    if options.ha_echo_enabled:
      transform_map['echo'] = ['echo-replica']

    return transform_map.get(service, service)

  def __load_bindings(self, path):
    with open(path, 'r') as stream:
      content = stream.read()
    result = {}
    for line in content.split('\n'):
      match = re.match('^([a-zA-Z][^=])+=(.*)', line)
      if match:
        result[match.group(1).strip()] = match.group(2).strip()

  def __forward_port_to_service(self, service_name):
    """Forward ports to the deployed service.

    This is private to ensure that it is called with the lock.
    The lock is needed to mitigate a race condition. See the
    inline comment around the Popen call.
    """
    local_port = _unused_port()
    remote_port = self.__service_port_map[service_name]

    command = self.__deployer.make_port_forward_command(
        service_name, local_port, remote_port)

    logging.info('Establishing connection to %s with port %d',
                 service_name, local_port)

    # There seems to be an intermittent race condition here.
    # Not sure if it is gcloud or python.
    # Locking the individual calls seems to work around it.
    #
    # We dont need to lock because this function is called from within
    # the lock already.
    logging.debug('RUNNING %s', ' '.join(command))

    # Redirect stdout to prevent buffer overflows (at least in k8s)
    # but keep errors for failures.
    class KeepAlive(threading.Thread):
      def run(self):
        while True:
          try:
            logging.info('KeepAlive %s polling', service_name)
            got = urlopen('http://localhost:{port}/health'
                          .format(port=local_port))
            logging.info('KeepAlive %s -> %s', service_name, got.getcode())
          except Exception as ex:
            logging.info('KeepAlive %s -> %s', service_name, ex)

          time.sleep(20)

    if self.options.deploy_spinnaker_type == 'distributed':
      # For now, distributed deployments are k8s
      # and K8s port forwarding with kubectl requires keep alive.
      hack = KeepAlive()
      hack.setDaemon(True)
      hack.start()

    logfile = os.path.join(
        self.options.output_dir,
        'port_forward_%s-%d.log' % (service_name, os.getpid()))
    stream = open(logfile, 'w')
    stream.write(str(command) + '\n\n')
    logging.debug('Logging "%s" port forwarding to %s', service_name, logfile)
    child = subprocess.Popen(
        command,
        stderr=stream,
        stdout=None)
    return ForwardedPort(child, local_port)

  def build_summary(self):
    """Return a summary of all the test results."""
    def append_list_summary(summary, name, entries):
      """Write out all the names from the test results."""
      if not entries:
        return
      summary.append('{0}:'.format(name))
      for entry in entries:
        summary.append('  * {0}'.format(entry[0]))

    options = self.options
    if not options.testing_enabled:
      return 'No test output: testing was disabled.', 0

    summary = ['\nSummary:']
    append_list_summary(summary, 'SKIPPED', self.skipped)
    append_list_summary(summary, 'PASSED', self.passed)
    append_list_summary(summary, 'FAILED', self.failed)

    num_skipped = len(self.skipped)
    num_passed = len(self.passed)
    num_failed = len(self.failed)

    summary.append('')
    if num_failed:
      summary.append(
          'FAILED {0} of {1}, skipped {2}'.format(
              num_failed, (num_failed + num_passed), num_skipped))
    else:
      summary.append('PASSED {0}, skipped {1}'.format(num_passed, num_skipped))
    return '\n'.join(summary)

  def wait_on_service(self, service_name, port=None, timeout=None):
    """Wait for the given service to be available on the specified port.

    Args:
      service_name: [string] The service name we we are waiting on.
      port: [int] The remote port the service is at.
      timeout: [int] How much time to wait before giving up.

    Returns:
      The ForwardedPort entry for this service.
    """
    try:
      with self.__lock:
        forwarding = self.__forwarded_ports.get(service_name)
        if forwarding is None:
          forwarding = self.__forward_port_to_service(service_name)
        self.__forwarded_ports[service_name] = forwarding
    except Exception:
      logging.exception('Exception while attempting to forward ports to "%s"',
                        service_name)
      raise

    timeout = timeout or self.options.test_service_startup_timeout
    end_time = time.time() + timeout
    logging.info('Waiting on "%s..."', service_name)
    if port is None:
      port = self.__service_port_map[service_name]

    # It seems we have a race condition in the poll
    # where it thinks the jobs have terminated.
    # I've only seen this happen once.
    time.sleep(1)

    threadid = hex(threading.current_thread().ident)
    logging.info('WaitOn polling %s from thread %s', service_name, threadid)
    while forwarding.child.poll() is None:
      try:
        # localhost is hardcoded here because we are port forwarding.
        # timeout=20 is to appease kubectl port forwarding, which will close
        #            if left idle for 30s
        urlopen('http://localhost:{port}/health'
                .format(port=forwarding.port),
                timeout=20)
        logging.info('"%s" is ready on port %d | %s',
                     service_name, forwarding.port, threadid)
        return forwarding
      except HTTPError as error:
        logging.warning('%s got %s. Ignoring that for now.',
                        service_name, error)
        return forwarding

      except (URLError, Exception) as error:
        if time.time() >= end_time:
          logging.error(
              'Timing out waiting for %s | %s', service_name, threadid)
          raise_and_log_error(TimeoutError(service_name, cause=service_name))
        time.sleep(2.0)

    logging.error('It appears %s is no longer available.'
                  ' Perhaps the tunnel closed.',
                  service_name)
    raise_and_log_error(
        ResponseError('It appears that {0} failed'.format(service_name),
                      server='tunnel'))

  def run_tests(self):
    """The actual controller that coordinates and runs the tests.

    This attempts to process all the tests concurrently across
    seperate threads, where each test will:
       (1) Determine whether or not the test is a candidate
           (passes the --test_include / --test_exclude criteria)

       (2) Evaluate the test's requirements.
           If the configuration requirements are not met then SKIP the test.

           (a) Attempt to tunnel each of the service tests, sharing existing
               tunnels used by other tests. The tunnels allocate unused local
               ports to avoid potential conflict within the local machine.

           (b) Wait for the service to be ready. Ideally this means it is
               healthy, however we'll allow unhealthy services to proceed
               as well and let those tests run and fail in case they are
               testing unhealthy service situations.

           (c) If there is an error or the service takes too long then
               outright FAIL the test.

        (3) Acquire the quota that the test requires.

            * If the quota is not currently available, then block the
              thread until it is. Since each test is in its own thread, this
              will not impact other tests.

            * Quota are only internal resources within the controller.
              This is used for purposes of rate limiting, etc. It does not
              coordinate with the underlying platforms.

            * Quota is governed with --test_quota. If a test requests
              a resource without a known quota, then the quota is assumed
              to be infinite.

        (4) Grab a semaphore used to rate limit running tests.
            This is controlled by --test_concurrency, which defaults to all.

        (5) Run the test.

        (6) Release the quota and semaphore to unblock other tests.

        (7) Record the outcome as PASS or FAIL

    If an exception is thrown along the way, the test will automatically
    be recorded as a FAILURE.

    Returns:
        (#passed, #failed, #skipped)
    """
    options = self.options
    if not options.testing_enabled:
      logging.info('--testing_enabled=false skips test phase entirely.')
      return 0, 0, 0

    all_test_profiles = self.test_suite['tests']

    logging.info(
        'Running tests (concurrency=%s).',
        options.test_concurrency or 'infinite')

    thread_pool = ThreadPool(len(all_test_profiles))
    thread_pool.map(self.__run_or_skip_test_profile_entry_wrapper,
                    all_test_profiles.items())
    thread_pool.terminate()

    logging.info('Finished running tests.')
    return len(self.__passed), len(self.__failed), len(self.__skipped)

  def __run_or_skip_test_profile_entry_wrapper(self, args):
    """Outer wrapper for running tests

    Args:
      args: [dict entry] The name and spec tuple from the mapped element.
    """
    test_name = args[0]
    spec = args[1]
    metric_labels = {'test_name': test_name, 'skipped': ''}
    try:
      self.__run_or_skip_test_profile_entry(test_name, spec, metric_labels)
    except Exception as ex:
      logging.error('%s threw an exception:\n%s',
                    test_name, traceback.format_exc())
      with self.__lock:
        self.__failed.append((test_name, 'Caught exception {0}'.format(ex)))

  def __record_skip_test(self, test_name, reason, skip_code, metric_labels):
    logging.warning(reason)
    self.__skipped.append((test_name, reason))

    copy_labels = dict(metric_labels)
    copy_labels['skipped'] = skip_code
    copy_labels['success'] = 'Skipped'
    self.__deployer.metrics.observe_timer(
        'RunTestScript' + '_Outcome', copy_labels, 0.0)

  def __run_or_skip_test_profile_entry(self, test_name, spec, metric_labels):
    """Runs a test from within the thread-pool map() function.

    Args:
      test_name: [string] The name of the test.
      spec: [dict] The test profile specification.
    """
    options = self.options
    if not re.search(options.test_include, test_name):
      reason = ('Skipped test "{name}" because it does not match explicit'
                ' --test_include criteria "{criteria}".'
                .format(name=test_name, criteria=options.test_include))
      self.__record_skip_test(test_name, reason,
                              'NotExplicitInclude', metric_labels)
      return
    if options.test_exclude and re.search(options.test_exclude, test_name):
      reason = ('Skipped test "{name}" because it matches explicit'
                ' --test_exclude criteria "{criteria}".'
                .format(name=test_name, criteria=options.test_exclude))
      self.__record_skip_test(test_name, reason,
                              'ExplicitExclude', metric_labels)
      return

    # This can raise an exception
    self.run_test_profile_helper(test_name, spec, metric_labels)

  def validate_test_requirements(self, test_name, spec, metric_labels):
    """Determine whether or not the test requirements are satisfied.

    If not, record the reason a skip or failure.
    This may throw exceptions, which are immediate failure.

    Args:
      test_name: [string] The name of the test.
      spec: [dict] The profile specification containing requirements.
            This argument will be pruned as values are consumed from it.

    Returns:
      True if requirements are satisifed, False if not.
    """
    if not 'api' in spec:
      raise_and_log_error(
          UnexpectedError('Test "{name}" is missing an "api" spec.'.format(
              name=test_name)))
    requires = spec.pop('requires', {})
    configuration = requires.pop('configuration', {})
    our_config = vars(self.options)
    for key, value in configuration.items():
      if key not in our_config:
        message = ('Unknown configuration key "{0}" for test "{1}"'
                   .format(key, test_name))
        raise_and_log_error(ConfigError(message))
      if value != our_config[key]:
        reason = ('Skipped test {name} because {key}={want} != {have}'
                  .format(name=test_name, key=key,
                          want=value, have=our_config[key]))
        with self.__lock:
          self.__record_skip_test(test_name, reason,
                                  'IncompatableConfig', metric_labels)
        return False

    services = set(replace_ha_services(
        requires.pop('services', []), self.options))
    services.add(self.__replace_ha_api_service(
        spec.pop('api'), self.options))

    if requires:
      raise_and_log_error(
          ConfigError('Unexpected fields in {name}.requires: {remaining}'
                      .format(name=test_name, remaining=requires)))
    if spec:
      raise_and_log_error(
          ConfigError('Unexpected fields in {name} specification: {remaining}'
                      .format(name=test_name, remaining=spec)))

    def wait_on_services(services):
      thread_pool = ThreadPool(len(services))
      thread_pool.map(self.wait_on_service, services)
      thread_pool.terminate()

    self.__deployer.metrics.track_and_time_call(
        'WaitingOnServiceAvailability',
        metric_labels, self.__deployer.metrics.default_determine_outcome_labels,
        wait_on_services, services)

    return True

  def add_extra_arguments(self, test_name, args, commandline):
    """Add extra arguments to the commandline.

    Args:
      test_name: [string] Name of test specifying the options.
      args: [dict] Specification of additioanl arguments to pass.
         Each key is the name of the argument, the value is the value to pass.
         If the value is preceeded with a '$' then it refers to the value of
         an option. If the value is None then just add the key without an arg.
      commandline: [list] The list of command line arguments to append to.
    """
    option_dict = vars(self.options)
    aliases_dict = self.test_suite.get('aliases', {})
    for key, value in args.items():
      if isinstance(value, (int, bool)):
        value = str(value)
      if key == 'alias':
        for alias_name in value:
          if not alias_name in aliases_dict:
            raise_and_log_error(ConfigError(
                'Unknown alias "{name}" referenced in args for "{test}"'
                .format(name=alias_name, test=test_name)))
          self.add_extra_arguments(
              test_name, aliases_dict[alias_name], commandline)
        continue
      elif value is None:
        pass
      elif value.startswith('$'):
        option_name = value[1:]
        if option_name in option_dict:
          value = option_dict[option_name] or '""'
        elif option_name in self.__extra_test_bindings:
          value = self.__extra_test_bindings[option_name] or '""'
        elif option_name in os.environ:
          value = os.environ[option_name]
        else:
          raise_and_log_error(ConfigError(
              'Unknown option "{name}" referenced in args for "{test}"'
              .format(name=option_name, test=test_name)))
      if value is None:
        commandline.append('--' + key)
      else:
        commandline.extend(['--' + key, value])

  def make_test_command_or_none(self, test_name, spec, metric_labels):
    """Returns the command to run the test, or None to skip.

    Args:
       test_name: The test to run.
       spec: The test specification profile.
             This argument will be pruned as values are consumed from it.

    Returns:
      The command line argument list, or None to skip.
      This may throw an exception if the spec is invalid.
      This does not consider quota, which is checked later.
    """
    options = self.options
    microservice_api = self.__replace_ha_api_service(spec.get('api'), options)
    test_rel_path = spec.pop('path', None) or os.path.join(
        'citest', 'tests', '{0}.py'.format(test_name))
    args = spec.pop('args', {})

    if not self.validate_test_requirements(test_name, spec, metric_labels):
      return None

    testing_root_dir = os.path.abspath(
        os.path.join(os.path.dirname(__file__), '..', 'testing'))
    test_path = os.path.join(testing_root_dir, test_rel_path)

    citest_log_dir = os.path.join(options.log_dir, 'citest_logs')
    if not os.path.exists(citest_log_dir):
      try:
        os.makedirs(citest_log_dir)
      except:
        # check for race condition
        if not os.path.exists(citest_log_dir):
          raise

    command = [
        'python', test_path,
        '--log_dir', citest_log_dir,
        '--log_filebase', test_name,
        '--native_host', 'localhost',
        '--native_port', str(self.__forwarded_ports[microservice_api].port)
    ]
    if options.test_stack:
      command.extend(['--test_stack', str(options.test_stack)])

    self.add_extra_arguments(test_name, args, command)
    return command

  def __execute_test_command(self, test_name, command, metric_labels):
    metrics = self.__deployer.metrics
    logging.debug('Running %s', ' '.join(command))
    def run_and_log_test_script(command):
      logfile = os.path.join(self.options.output_dir, 'citest_logs',
                             '%s-%s.console.log' % (test_name, os.getpid()))
      logging.info('Logging test "%s" to %s...', test_name, logfile)
      try:
        check_subprocesses_to_logfile('running test', logfile, [command])
        retcode = 0
        logging.info('Test %s PASSED -- see %s', test_name, logfile)
      except:
        retcode = -1
        logging.info('Test %s FAILED -- see %s', test_name, logfile)

      return retcode, logfile

    return metrics.track_and_time_call(
        'RunTestScript',
        metric_labels, determine_subprocess_outcome_labels,
        run_and_log_test_script, ' '.join(command))

  def run_test_profile_helper(self, test_name, spec, metric_labels):
    """Helper function for running an individual test.

    The caller wraps this to trap and handle exceptions.

    Args:
      test_name: The test being run.
      spec: The test specification profile.
            This argument will be pruned as values are consumed from it.
    """
    quota = spec.pop('quota', {})
    command = self.make_test_command_or_none(test_name, spec, metric_labels)
    if command is None:
      return

    logging.info('Acquiring quota for test "%s"...', test_name)
    quota_tracker = self.__quota_tracker
    metrics = self.__deployer.metrics
    acquired_quota = metrics.track_and_time_call(
        'ResourceQuotaWait',
        metric_labels, metrics.default_determine_outcome_labels,
        quota_tracker.acquire_all_safe, test_name, quota)
    if acquired_quota:
      logging.info('"%s" acquired quota %s', test_name, acquired_quota)

    execute_time = None
    start_time = time.time()
    try:
      logging.info('Scheduling "%s"...', test_name)

      # This will block. Note that we already acquired quota, thus
      # we are blocking holding onto that quota. However since we are
      # blocked awaiting a thread, nobody else can execute either,
      # so it doesnt matter that we might be starving them of quota.
      self.__semaphore.acquire(True)
      execute_time = time.time()
      wait_time = int(execute_time - start_time + 0.5)
      if wait_time > 1:
        logging.info('"%s" had a semaphore contention for %d secs.',
                     test_name, wait_time)
      logging.info('Executing "%s"...', test_name)
      retcode, logfile_path = self.__execute_test_command(
          test_name, command, metric_labels)
    finally:
      logging.info('Finished executing "%s"...', test_name)
      self.__semaphore.release()
      if acquired_quota:
        quota_tracker.release_all_safe(test_name, acquired_quota)

    end_time = time.time()
    delta_time = int(end_time - execute_time + 0.5)

    with self.__lock:
      if not retcode:
        logging.info('%s PASSED after %d secs', test_name, delta_time)
        self.__passed.append((test_name, logfile_path))
      else:
        logging.info('FAILED %s after %d secs', test_name, delta_time)
        self.__failed.append((test_name, logfile_path))


def init_argument_parser(parser, defaults):
  """Add testing related command-line parameters."""
  add_parser_argument(
      parser, 'test_profiles',
      defaults, os.path.join(os.path.dirname(__file__), 'all_tests.yaml'),
      help='The path to the set of test profiles.')

  add_parser_argument(
      parser, 'test_extra_profile_bindings', defaults, None,
      help='Path to a file with additional bindings that the --test_profiles'
           ' file may reference.')

  add_parser_argument(
      parser, 'test_concurrency', defaults, None, type=int,
      help='Limits how many tests to run at a time. Default is unbounded')

  add_parser_argument(
      parser, 'test_service_startup_timeout', defaults, 300, type=int,
      help='Number of seconds to permit services to startup before giving up.')

  add_parser_argument(
      parser, 'test_gce_project_quota_factor', defaults, 1.0, type=float,
      help='Default percentage of available project quota to make available'
           ' for tests.')

  add_parser_argument(
      parser, 'test_gce_region_quota_factor', defaults, 1.0, type=float,
      help='Default percentage of available region quota to make available'
           ' for tests.')

  add_parser_argument(
      parser, 'test_gce_quota_region', defaults, 'us-central1',
      help='GCE Compute Region to gather region quota limits from.')

  add_parser_argument(
      parser, 'test_default_quota',
      defaults, '',
      help='Default quota parameters for values used in the --test_profiles.'
           ' This does not include GCE quota values, which are determined'
           ' at runtime. These value can be further overriden by --test_quota.'
           ' These are meant as built-in defaults, where --test_quota as'
           ' per-execution overriden.')

  add_parser_argument(
      parser, 'test_quota', defaults, '',
      help='Comma-delimited name=value list of quota overrides.')

  add_parser_argument(
      parser, 'testing_enabled', defaults, True, type=bool,
      help='If false then dont run the testing phase.')

  add_parser_argument(
      parser, 'test_disable', defaults, False, action='store_true',
      dest='testing_enabled',
      help='DEPRECATED: Use --testing_enabled=false.')

  add_parser_argument(
      parser, 'test_include', defaults, '.*',
      help='Regular expression of tests to run or None for all.')

  add_parser_argument(
      parser, 'test_exclude', defaults, None,
      help='Regular expression of otherwise runnable tests to skip.')

  add_parser_argument(
      parser, 'test_stack', defaults, None,
      help='The --test_stack to pass through to tests indicating which'
           ' Spinnaker application "stack" to use. This is typically'
           ' to help trace the source of resources created within the'
           ' tests.')

  add_parser_argument(
      parser, 'test_jenkins_job_name', defaults, 'TriggerBake',
      help='The Jenkins job name to use in tests.')


def validate_options(options):
  """Validate testing related command-line parameters."""
  if not os.path.exists(options.test_profiles):
    raise_and_log_error(
        ConfigError('--test_profiles "{0}" does not exist.'.format(
            options.test_profiles)))
