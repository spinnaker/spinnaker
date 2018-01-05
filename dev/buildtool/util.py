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

"""Common helper functions across buildtool modules."""

import datetime
import logging
import os
import shlex
import socket
import subprocess
import time
import traceback


def add_parser_argument(parser, name, defaults, default_value, **kwargs):
  """Helper function for adding parser.add_argument with a default value.

  Args:
    name: [string] The argument name is assumed optional, without '--' prefix.
    defaults: [string] Dictionary of default value overrides keyed by name.
    default_value: [any] The default value if not overriden.
    kwargs: [kwargs] Additional kwargs for parser.add_argument
  """
  parser.add_argument(
      '--{name}'.format(name=name),
      default=defaults.get(name, default_value),
      **kwargs)


def ensure_options_set(options, name_list):
  """Make sure each of the options in the name_list was set."""
  option_dict = vars(options)
  missing_keys = [key for key in name_list if not option_dict.get(key)]
  if missing_keys:
    raise ValueError('The following options were not configured: {0}'.format(
        ', '.join(missing_keys)))


def determine_logfile_path(options, component, decorator):
  """Determine where a logfile should be written.

  Args:
    options: [Namespace] Contains builtin options from __main__
        used to specify logging policy.
    component: [string] The name of the component this logfile is for.
    decorator: [string] The file decorator denoting what the logfile is for.
  Returns:
    The path to where the logfile should be written.
  """
  logsdir = options.logs_dir or os.path.join(options.scratch_dir, 'logs')
  return os.path.join(
      logsdir,
      '{name}-{decorator}.log'.format(name=component, decorator=decorator))


def unused_port(interface='localhost'):
  """Return an unused port number on localhost."""
  sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  sock.bind((interface, 0))
  port = sock.getsockname()[1]
  sock.close()
  return port


def timestring(now=None):
  """Returns timestamp as date time string."""
  now = now or datetime.datetime.now()
  return '{:%Y-%m-%d %H:%M:%S}'.format(now)


def timedelta_string(delta):
  """Returns a string indicating the time duration.

  Args:
    delta: [datetime.timedelta] The time difference
  """
  delta_secs = int(delta.total_seconds())
  delta_mins = delta_secs / 60
  delta_hours = (delta_mins / 60 % 24)
  delta_days = delta.days

  day_str = '' if not delta_days else ('days=%d + ' % delta_days)
  delta_mins = delta_mins % 60
  delta_secs = delta_secs % 60

  if delta_hours or day_str:
    return day_str + '%02d:%02d:%02d' % (delta_hours, delta_mins, delta_secs)
  elif delta_mins:
    return '%02d:%02d' % (delta_mins, delta_secs)
  return '%d.%03d secs' % (delta_secs, delta.microseconds / 1000)


def maybe_log_exception(where, ex, action_msg='propagating exception'):
  """Log the exception and stackdrace if it hasnt been logged already."""
  if not hasattr(ex, 'logged'):
    text = traceback.format_exc()
    logging.error('"%s" caught exception\n%s', where, text)
    ex.logged = True
  logging.error('"%s" %s', where, action_msg)


def start_subprocess(cmd, stream=None, stdout=None, echo=False, **kwargs):
  """Starts a subprocess and returns handle to it."""
  split_cmd = shlex.split(cmd)

  log_level = logging.INFO if echo else logging.DEBUG
  extra_log_info = ''
  if 'cwd' in kwargs:
    extra_log_info += ' in cwd="{dir}"'.format(dir=kwargs['cwd'])
  logging.log(log_level, 'Running %s%s...', repr(cmd), extra_log_info)

  start_date = datetime.datetime.now()
  if stream:
    stream.write('{time} Spawning {cmd!r}{extra}\n----\n\n'.format(
        time=timestring(now=start_date), cmd=cmd, extra=extra_log_info))
    stream.flush()

  process = subprocess.Popen(
      split_cmd,
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
  stdout_lines = []
  if process.stdout is not None:
    # stdout isnt going to another stream; collect it from the pipe.
    for line in iter(process.stdout.readline, ''):
      stdout_lines.append(line)
      if stream:
        stream.write(line)
        stream.flush()
  process.wait()
  if hasattr(process, 'start_date'):
    end_date = datetime.datetime.now()
    delta_time_str = timedelta_string(end_date - process.start_date)
  else:
    delta_time_str = 'UNKNOWN'

  returncode = process.returncode
  stdout = ''.join(stdout_lines)

  if stream:
    stream.write(
        '\n\n----\n{time} Spawned process completed'
        ' with returncode {returncode} in {delta_time}.\n'
        .format(time=timestring(now=end_date), returncode=returncode,
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
  """run_subprocess and raise CalledProcessError if it fails."""
  retcode, stdout = run_subprocess(cmd, stream=stream, **kwargs)

  try:
    if retcode != 0:
      lines = stdout.split('\n')
      if lines > 5:
        lines = lines[-5:]
      logging.error('Command failed with last %d lines:\n   %s',
                    len(lines), '\n   '.join(lines))
      raise subprocess.CalledProcessError(retcode, cmd, output=stdout)
  except subprocess.CalledProcessError as ex:
    maybe_log_exception(shlex.split(cmd)[0], ex)
    raise

  return stdout.strip()


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


def ensure_dir_exists(path):
  """Ensure a directory exists, creating it if not."""
  try:
    os.makedirs(path)
  except OSError:
    if not os.path.exists(path):
      raise


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
      check_subprocess_sequence(cmds, stream=stream, **kwargs)
    except Exception:
      logging.error('%s failed. Log file [%s] follows:', what, logfile)
      with open(logfile, 'r') as readagain:
        newline_indent = '\n   >>>   '
        logfile_lines = readagain.read().split('\n')
        logging.error(
            '%s:%s%s',
            logfile, newline_indent, newline_indent.join(logfile_lines))
      # Repeat the error again for convienence reading the logs
      logging.error('%s failed. See embedded logfile above', what)

      # TODO(ewiseblatt): configure this
      ensure_dir_exists('errors')
      error_path = os.path.join('errors', os.path.basename(logfile))
      logging.info('Copying error log file to %s', error_path)
      with open(error_path, 'w') as f:
        f.write('\n'.join(logfile_lines))
      raise


def write_to_path(content, path):
  """Write the given content to the file specified by the <path>.

  This will create the parent directory if needed.
  """
  ensure_dir_exists(os.path.dirname(os.path.abspath(path)))
  with open(path, 'w') as f:
    f.write(content)
