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
import os
import re
import subprocess
import socket
import sys
import threading
import time
import traceback
import urllib2
import yaml

from spinnaker.run import run_and_monitor


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

  def __init__(self, max_counts):
    """Constructor.

    Args:
      max_counts: [dict] The list of resources and quotas to manage.
    """
    self.__counts = dict(max_counts)
    self.__max_counts = dict(max_counts)
    self.__condition_variable = threading.Condition()

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
    for key, value in quota.items():
      got = self.__acquire_resource_or_none(key, value)
      if not got:
        self.release_all_unsafe(who, acquired)
        return None
      acquired[key] = got
    return acquired

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
    logging.info('"%s" releasing quota %s', who, quota)
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
      return count
    max_count = self.__max_counts[name]
    if have == max_count:
      logging.warning('Quota %s has a max of %d but %d is desired.'
                      ' Acquiring all the quota as a best effort.',
                      name, max_count, count)
      self.__counts[name] = 0
      return have
    logging.warning('Quota %s has %d remaining, but %d are needed.'
                    ' Rejecting the request for now.',
                    name, have, count)
    return 0

  def __release_resource(self, name, count):
    """Restores previously acquired resource quota."""
    have = self.__counts.get(name, None)
    if have is not None:
      self.__counts[name] = have + count


class CommandOutputMediator(object):
  """Mediate output from forked commands to our own log files."""

  def __init__(self, label):
    self.__label = label
    self.__buffered_stdout = []
    self.__buffered_stderr = []

  def capture_stdout(self, fragments):
    """Callback for adding text fragments from stdout."""
    self.__buffered_stdout = self.capture_helper(
        fragments, self.__buffered_stdout, logging.info)

  def capture_stderr(self, fragments):
    """Callback for adding text from stderr."""
    # Display this as info because python unittest.main is printing
    # normal status to stderr but we want it to show up here as info, not error
    self.__buffered_stderr = self.capture_helper(
        fragments, self.__buffered_stderr, logging.info)

  def capture_helper(self, fragments, remaining, method):
    """Helper function managing the buffers and logging."""
    text = ''.join(fragments)
    eoln = text.rfind('\n')
    if eoln < 0:
      remaining.append(text)
      return remaining

    remaining.append(text[:eoln])
    extra = [text[eoln + 1:]]
    self.write(remaining, extra, method)
    return extra

  def write(self, fragments, still_buffered, method):
    """Write fragments to the logging method."""
    buffered_text = ('' if not still_buffered
                     else ''.join(still_buffered).strip())
    joined_text = ''.join(fragments)
    if not buffered_text and not joined_text.strip():
      return

    if buffered_text:
      more_text = '<still buffering line>: %s\n' % buffered_text
    else:
      more_text = ''

    lines = joined_text.split('\n')
    indented_text = '  %s' % '\n  '.join(lines)
    method('<begin from %s>:\n'
           '%s\n'
           '%s'
           '<end from %s>\n',
           self.__label,
           indented_text,
           more_text,
           self.__label)

  def flush(self):
    """Flush any buffered output."""
    if self.__buffered_stdout:
      self.write(self.__buffered_stdout, None, logging.info)
      self.__buffered_stdout = []
    if self.__buffered_stderr:
      self.write(self.__buffered_stderr, None, logging.error)
      self.__buffered_stderr = []


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

  def __init__(self, deployer):
    options = deployer.options
    quota_spec = {parts[0]: int(parts[1])
                  for parts in [entry.split('=')
                                for entry in options.test_quota.split(',')]}
    self.__quota_tracker = QuotaTracker(quota_spec)
    self.__deployer = deployer
    self.__lock = threading.Lock()
    self.__passed = []  # Resulted in success
    self.__failed = []  # Resulted in failure
    self.__skipped = []  # Will not run at all
    self.__test_suite = yaml.safe_load(file(options.test_profiles, 'r'))
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
        'gate': 8084,
        'front50': 8080,

        # Some tests needed these too.
        'orca': 8083,
        'rosco': 8087,
        'igor': 8088,
        'echo': 8089
    }

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
    child = subprocess.Popen(
        ' '.join(command) + ' > /dev/null', shell=True,
        stderr=sys.stderr.fileno(),
        stdout=None,
        stdin=None)
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
    if options.test_disable:
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

  def wait_on_service(self, service_name, port=None, timeout=120):
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
    except Exception as ex:
      logging.exception('Exception while attempting to forward ports to "%s"',
                        service_name)
      raise

    end_time = time.time() + timeout
    logging.info('Waiting on "%s..."', service_name)
    if port is None:
      port = self.__service_port_map[service_name]

    # It seems we have a race condition in the poll
    # where it thinks the jobs have terminated.
    # I've only seen this happen once.
    time.sleep(1)

    while forwarding.child.poll() is None:
      try:
        # localhost is hardcoded here because we are port forwarding.
        urllib2.urlopen('http://localhost:{port}/health'
                        .format(port=forwarding.port))
        logging.info('"%s" is ready on port %d',
                     service_name, forwarding.port)
        return forwarding
      except urllib2.HTTPError as error:
        logging.warning('%s got %s. Ignoring that for now.',
                        service_name, error)
        return forwarding

      except (urllib2.URLError, Exception) as error:
        if time.time() >= end_time:
          logging.error('Timing out waiting for %s', service_name)
          raise error
        time.sleep(1.0)

    logging.error('It appears %s is no longer available.'
                  ' Perhaps the tunnel closed.',
                  service_name)
    raise RuntimeError('It appears that {0} failed'.format(service_name))

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
    """
    options = self.options
    if options.test_disable:
      logging.info('--test_disable skips test phase entirely.')
      return

    all_test_profiles = self.test_suite['tests']

    logging.info(
        'Running tests (concurrency=%s).',
        options.test_concurrency or 'infinite')

    thread_pool = ThreadPool(len(all_test_profiles))
    thread_pool.map(self.__run_or_skip_test_profile_entry_wrapper,
                    all_test_profiles.items())
    thread_pool.terminate()

    logging.info('Finished running tests.')

  def __run_or_skip_test_profile_entry_wrapper(self, args):
    """Outer wrapper for running tests

    Args:
      args: [dict entry] The name and spec tuple from the mapped element.
    """
    test_name = args[0]
    spec = args[1]
    try:
      self.__run_or_skip_test_profile_entry(test_name, spec)
    except Exception as ex:
      logging.error('%s threw an exception:\n%s',
                    test_name, traceback.format_exc())
      with self.__lock:
        self.__failed.append((test_name, 'Caught exception {0}'.format(ex)))

  def __run_or_skip_test_profile_entry(self, test_name, spec):
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
      logging.warning(reason)
      self.__skipped.append((test_name, reason))
      return
    if options.test_exclude and re.search(options.test_exclude, test_name):
      reason = ('Skipped test "{name}" because it matches explicit'
                ' --test_exclude criteria "{criteria}".'
                .format(name=test_name, criteria=options.test_exclude))
      logging.warning(reason)
      self.__skipped.append((test_name, reason))
      return

    # This can raise an exception
    self.run_test_profile_helper(test_name, spec)

  def validate_test_requirements(self, test_name, spec):
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
      raise ValueError('Test "{name}" is missing an "api" spec.'.format(
          name=test_name))
    requires = spec.pop('requires', {})
    configuration = requires.pop('configuration', {})
    our_config = vars(self.options)
    for key, value in configuration.items():
      if key not in our_config:
        message = ('Unknown configuration key "{0}" for test "{1}"'
                   .format(key, test_name))
        raise KeyError(message)
      if value != our_config[key]:
        reason = ('Skipped test {name} because {key}={want} != {have}'
                  .format(name=test_name, key=key,
                          want=value, have=our_config[key]))
        logging.warning(reason)
        with self.__lock:
          self.__skipped.append((test_name, reason))
        return False

    services = set(requires.pop('services', []))
    services.add(spec.pop('api'))

    if requires:
      raise ValueError('Unexpected fields in {name}.requires: {remaining}'
                       .format(name=test_name, remaining=requires))
    if spec:
      raise ValueError('Unexpected fields in {name} specification: {remaining}'
                       .format(name=test_name, remaining=spec))

    thread_pool = ThreadPool(len(services))
    thread_pool.map(self.wait_on_service, services)
    thread_pool.terminate()
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
            raise KeyError(
                'Unknown alias "{name}" referenced in args for "{test}"'
                .format(name=alias_name, test=test_name))
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
          raise KeyError(
              'Unknown option "{name}" referenced in args for "{test}"'
              .format(name=option_name, test=test_name))
      if value is None:
        commandline.append('--' + key)
      else:
        commandline.extend(['--' + key, value])

  def make_test_command_or_none(self, test_name, spec):
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
    microservice_api = spec.get('api')
    test_rel_path = spec.pop('path', None) or os.path.join(
        'citest', 'tests', '{0}.py'.format(test_name))
    args = spec.pop('args', {})

    if not self.validate_test_requirements(test_name, spec):
      return None

    testing_root_dir = os.path.abspath(
        os.path.join(os.path.dirname(__file__), '..', 'testing'))
    test_path = os.path.join(testing_root_dir, test_rel_path)

    citest_log_dir = os.path.join(options.log_dir, 'citest_logs')
    if not os.path.exists(citest_log_dir):
      os.makedirs(citest_log_dir)

    command = [
        'python', test_path,
        '--log_dir', citest_log_dir,
        '--log_filebase', test_name,
        '--native_host', 'localhost',
        '--native_port', str(self.__forwarded_ports[microservice_api].port)
    ]
    if options.test_stack:
      command.extend(['--test_stack', options.test_stack])

    self.add_extra_arguments(test_name, args, command)
    return command

  def run_test_profile_helper(self, test_name, spec):
    """Helper function for running an individual test.

    The caller wraps this to trap and handle exceptions.

    Args:
      test_name: The test being run.
      spec: The test specification profile.
            This argument will be pruned as values are consumed from it.
    """
    quota = spec.pop('quota', {})
    command = self.make_test_command_or_none(test_name, spec)
    if command is None:
      return
    capture = CommandOutputMediator(test_name)

    logging.info('Acquiring quota for test "%s"...', test_name)
    quota_tracker = self.__quota_tracker
    acquired_quota = quota_tracker.acquire_all_safe(test_name, quota)
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
      logging.debug('Running %s', ' '.join(command))
      result = run_and_monitor(' '.join(command),
                               echo=False,
                               observe_stdout=capture.capture_stdout,
                               observe_stderr=capture.capture_stderr)
    finally:
      logging.info('Finished executing "%s"...', test_name)
      self.__semaphore.release()
      if acquired_quota:
        quota_tracker.release_all_safe(test_name, acquired_quota)

    capture.flush()
    end_time = time.time()
    delta_time = int(end_time - execute_time + 0.5)

    with self.__lock:
      if result.returncode == 0:
        logging.info('%s PASSED after %d secs', test_name, delta_time)
        self.__passed.append((test_name, result.stdout))
      else:
        logging.info('FAILED %s after %d secs', test_name, delta_time)
        self.__failed.append((test_name, result.stderr))


def init_argument_parser(parser):
  """Add testing related command-line parameters."""
  parser.add_argument(
      '--test_profiles',
      default=os.path.join(os.path.dirname(__file__), 'all_tests.yaml'),
      help='The path to the set of test profiles.')

  parser.add_argument(
      '--test_extra_profile_bindings', default=None,
      help='Path to a file with additional bindings that the --test_profiles'
           ' file may reference.')

  parser.add_argument(
      '--test_concurrency', default=None, type=int,
      help='Limits how many tests to run at a time. Default is unbounded')

  parser.add_argument(
      '--test_quota', default='google_backend_services=5,google_cpu=20',
      help='Comma-delimited name=value list of quota limits. This is used'
           ' to rate-limit tests based on their profiled quota specifications.')

  parser.add_argument(
      '--test_disable', default=False, action='store_true',
      help='If true then dont run the testing phase.')

  parser.add_argument(
      '--test_include', default='.*',
      help='Regular expression of tests to run or None for all.')

  parser.add_argument(
      '--test_exclude', default=None,
      help='Regular expression of otherwise runnable tests to skip.')

  parser.add_argument(
      '--test_stack', default=None,
      help='The --test_stack to pass through to tests indicating which'
           ' Spinnaker application "stack" to use. This is typically'
           ' to help trace the source of resources created within the'
           ' tests.')


def validate_options(options):
  """Validate testing related command-line parameters."""
  if not os.path.exists(options.test_profiles):
    raise ValueError('--test_profiles "{0}" does not exist.'.format(
        options.test_profiles))
