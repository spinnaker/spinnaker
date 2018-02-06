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
import socket


# The build number to use if not otherwise explicitly specified
# Usually build numbers are obtained from the BOM file or --build_number
# However sometimes this isnt the case (e.g. generating the BOM).
DEFAULT_BUILD_NUMBER = str(os.environ.get(
    'BUILD_NUMBER', '{:%Y%m%d%H%M%S}'.format(datetime.datetime.utcnow())))


def add_parser_argument(parser, name, defaults, default_value, **kwargs):
  """Helper function for adding parser.add_argument with a default value.

  Args:
    name: [string] The argument name is assumed optional, without '--' prefix.
    defaults: [string] Dictionary of default value overrides keyed by name.
    default_value: [any] The default value if not overriden.
    kwargs: [kwargs] Additional kwargs for parser.add_argument
  """
  arg_type = kwargs.get('type')
  if arg_type == bool:
    kwargs['type'] = lambda value: str(value).lower() != 'false'

  parser.add_argument(
      '--%s' % name, default=defaults.get(name, default_value),
      **kwargs)


def unused_port(interface='localhost'):
  """Return an unused port number on localhost."""
  sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  sock.bind((interface, 0))
  port = sock.getsockname()[1]
  sock.close()
  return port


def log_timestring(now=None):
  """Returns timestamp as date time string in logging format."""
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


def log_embedded_output(log_level, title, output, line_prefix='    >>>  '):
  """Helper function that logs an output stream from another process."""
  newline_indent = '\n' + line_prefix
  logfile_lines = output.replace('\r', '\n').split('\n')
  logging.log(
      log_level,
      '%s:%s%s',
      title, newline_indent, newline_indent.join(logfile_lines))


def ensure_dir_exists(path):
  """Ensure a directory exists, creating it if not."""
  try:
    os.makedirs(path)
  except OSError:
    if not os.path.exists(path):
      raise


def write_to_path(content, path):
  """Write the given content to the file specified by the <path>.

  This will create the parent directory if needed.
  """
  ensure_dir_exists(os.path.dirname(os.path.abspath(path)))
  with open(path, 'w') as f:
    f.write(content)
