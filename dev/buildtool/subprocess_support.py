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

"""Support for running subprocess commands."""

import datetime
import logging
import os
import shlex
import subprocess
import time

from buildtool import (
    ensure_dir_exists,
    log_embedded_output,
    log_timestring,
    raise_and_log_error,
    timedelta_string,
    ExecutionError)

from buildtool.base_metrics import BaseMetricsRegistry


# Directory where error logfiles are copied to.
# This is exposed so it can be configured externally since
# this module does not offer encapsulated configuration.
ERROR_LOGFILE_DIR = 'errors'

def start_subprocess(cmd, stream=None, stdout=None, echo=False, **kwargs):
  """Starts a subprocess and returns handle to it."""
  split_cmd = shlex.split(cmd)
  actual_command = cmd if kwargs.get('shell') else split_cmd

  log_level = logging.INFO if echo else logging.DEBUG
  extra_log_info = ''
  if 'cwd' in kwargs:
    extra_log_info += ' in cwd="%s"' % kwargs['cwd']
  logging.log(log_level, 'Running %s%s...', repr(cmd), extra_log_info)

  start_date = datetime.datetime.now()
  if stream:
    stream.write('{time} Spawning {cmd!r}{extra}\n----\n\n'.format(
        time=log_timestring(now=start_date), cmd=cmd, extra=extra_log_info))
    stream.flush()

  process = subprocess.Popen(
      actual_command,
      close_fds=True,
      stdout=stdout or subprocess.PIPE,
      stderr=subprocess.STDOUT,
      **kwargs)
  logging.log(log_level, 'Running %s as pid %s', split_cmd[0], process.pid)
  process.start_date = start_date

  time.sleep(0) # yield this thread
  return process


def wait_subprocess(process, stream=None, echo=False, postprocess_hook=None):
  """Waits for subprocess to finish and returns (final status, stdout).

  This will also consume the remaining output to return it.

  Returns:
    Process exit code, stdout remaining in process prior to this invocation.
    Any previously read output from the process will not be included.
  """
  text_lines = []
  if process.stdout is not None:
    # stdout isnt going to another stream; collect it from the pipe.
    for raw_line in iter(process.stdout.readline, ''):
      if not raw_line:
        break
      decoded_line = raw_line.decode(encoding='utf-8')
      text_lines.append(decoded_line)
      if stream:
        stream.write(raw_line)
        stream.flush()
   
  process.wait()
  if stream is None and process.stdout is not None:
    # Close stdout pipe if we didnt give a stream.
    # Otherwise caller owns the stream.
    process.stdout.close()

  if hasattr(process, 'start_date'):
    end_date = datetime.datetime.now()
    delta_time_str = timedelta_string(end_date - process.start_date)
  else:
    delta_time_str = 'UNKNOWN'

  returncode = process.returncode
  stdout = ''.join(text_lines)

  if stream:
    stream.write(
        '\n\n----\n{time} Spawned process completed'
        ' with returncode {returncode} in {delta_time}.\n'
        .format(time=log_timestring(now=end_date), returncode=returncode,
                delta_time=delta_time_str))
    stream.flush()

  if echo:
    logging.info('%s returned %d with output:\n%s',
                 process.pid, returncode, stdout)
  logging.debug('Finished %s with returncode=%d in %s',
                process.pid, returncode, delta_time_str)

  if postprocess_hook:
    postprocess_hook(returncode, stdout)

  return returncode, stdout.strip()


def run_subprocess(cmd, stream=None, echo=False, **kwargs):
  """Returns retcode, stdout."""
  postprocess_hook = kwargs.pop('postprocess_hook', None)
  process = start_subprocess(cmd, stream=stream, echo=echo, **kwargs)
  return wait_subprocess(process, stream=stream, echo=echo,
                         postprocess_hook=postprocess_hook)


def check_subprocess(cmd, stream=None, **kwargs):
  """Run_subprocess and raise CalledProcessError if it fails."""
  # pylint: disable=inconsistent-return-statements
  embed_errors = kwargs.pop('embed_errors', True)
  retcode, stdout = run_subprocess(cmd, stream=stream, **kwargs)
  if retcode == 0:
    return stdout.strip()

  if embed_errors:
    log_embedded_output(logging.ERROR, 'command output', stdout)
    logging.error('Command failed. See embedded output above.')
  else:
    lines = stdout.split('\n')
    if lines > 30:
      lines = lines[-30:]
    log_embedded_output(logging.ERROR,
                        'Command failed with last %d lines' % len(lines),
                        '\n'.join(lines))

  program = os.path.basename(shlex.split(cmd)[0])
  raise_and_log_error(ExecutionError(program + ' failed.', program=program))


def check_subprocess_sequence(cmd_list, stream=None, **kwargs):
  """Run multiple commands until one fails.

  Returns:
    A list of each result in sequence if all succeeded.
  """
  response = []
  for one in cmd_list:
    response.append(check_subprocess(one, stream=stream, **kwargs))
  return response


def run_subprocess_sequence(cmd_list, stream=None, **kwargs):
  """Run multiple commands until one fails.

  Returns:
    A list of (code, output) tuples for each result in sequence.
  """
  response = []
  for one in cmd_list:
    response.append(run_subprocess(one, stream=stream, **kwargs))
  return response


def check_subprocesses_to_logfile(what, logfile, cmds, append=False, **kwargs):
  """Wrapper around check_subprocess that logs output to a logfile.

  Args:
    what: [string] For logging purposes, what is the command for.
    logfile: [path] The logfile to write to.
    cmds: [list of string] A list of commands to run.
    append: [boolean] Open the log file as append if true, write new default.
    kwargs: [kwargs] Additional keyword arguments to pass to check_subprocess.
  """
  mode = 'a' if append else 'w'
  how = 'Appending' if append else 'Logging'
  logging.info('%s %s to %s', how, what, logfile)
  ensure_dir_exists(os.path.dirname(logfile))
  with open(logfile, mode) as stream:
    try:
      check_subprocess_sequence(
          cmds, stream=stream, embed_errors=False, **kwargs)
    except Exception as ex:
      logging.error('%s failed. Log file [%s] follows:', what, logfile)
      import traceback
      traceback.print_exc()

      with open(logfile, 'rb') as readagain:
        output = bytes.decode(readagain.read(), encoding='utf-8')
        log_embedded_output(logging.ERROR, logfile, output)
      logging.error('Caught exception %s\n%s failed. See embedded logfile above',
                    ex, what)

      ensure_dir_exists(ERROR_LOGFILE_DIR)
      error_path = os.path.join('errors', os.path.basename(logfile))
      logging.info('Copying error log file to %s', error_path)
      with open(error_path, 'w') as f:
        f.write(output);
        f.write('\n--------\n')
        f.write('Exeception caught in parent process:\n%s' % ex)

      raise


def determine_subprocess_outcome_labels(result, labels):
  """For determining outcome labels when timing calls to subprocesses."""
  if result is None:
    return BaseMetricsRegistry.default_determine_outcome_labels(
        result, labels)
  outcome_labels = dict(labels)
  retcode, _ = result
  outcome_labels.update({
      'success': retcode == 0,
      'exception_type': 'BadExitCode'
  })
  return outcome_labels
