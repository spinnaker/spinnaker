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

"""Helper functions for raising and logging errors."""


import logging
import os
import traceback

from buildtool.metrics import MetricsManager


class BuildtoolError(Exception):
  RUNTIME = 'runtime'
  USAGE = 'usage'
  INTERNAL = 'internal'

  def __init__(self, message, classification, cause=None):
    super(BuildtoolError, self).__init__(message)
    labels = {
        'cause': cause,
        'classification': classification
    }
    MetricsManager.singleton().inc_counter(
        'BuildtoolError', labels, 'Errors raised during execution.')


class ConfigError(BuildtoolError):
  def __init__(self, message, cause=None):
    super(ConfigError, self).__init__(
        message, self.INTERNAL, cause=cause or 'config')

class TimeoutError(BuildtoolError):
  def __init__(self, message):
    super(TimeoutError, self).__init__(message, self.RUNTIME)

class ExecutionError(BuildtoolError):
  def __init__(self, message, program=None):
    super(ExecutionError, self).__init__(message, self.RUNTIME, cause=program)

class ResponseError(BuildtoolError):
  def __init__(self, message, server=None):
    super(ResponseError, self).__init__(message, self.RUNTIME, cause=server)

class UnexpectedError(BuildtoolError):
  def __init__(self, message, cause=None):
    super(UnexpectedError, self).__init__(message, self.INTERNAL,
                                          cause=cause or 'unexpected')


def maybe_log_exception(where, ex, action_msg='propagating exception'):
  """Log the exception and stackdrace if it hasnt been logged already."""
  if not hasattr(ex, 'loggedit'):
    text = traceback.format_exc()
    logging.error('"%s" caught exception\n%s', where, text)
    ex.loggedit = True

  logging.error('"%s" %s', where, action_msg)


def raise_and_log_error(error, *pos_args):
  if len(pos_args) > 1:
    raise ValueError('Too many positional args: {}'.format(pos_args))
  message = pos_args[0] if pos_args else error.message
  logging.debug(''.join(traceback.format_stack()))
  logging.error('*** ERROR ***: %s', message)
  error.loggedit = True
  raise error


def check_options_set(options, name_list, where=None):
  """Make sure each of the options in the name_list was set."""
  where = where or options.command
  option_dict = vars(options)
  missing_keys = [key for key in name_list if not option_dict.get(key)]
  if missing_keys:
    raise_and_log_error(
        ValueError('Missing options ' + ', '.join(missing_keys)),
        '{where} requires options that are not set: {keys}'.format(
            where=where, keys=', '.join(missing_keys)))


def check_path_exists(path, why):
  """Check path exists and if not, log and raise an error with reason for it."""
  if not os.path.exists(path):
    error = ConfigError('NotFound: "%s" for %s' % (path, why))
    raise_and_log_error(error)


def check_kwargs_empty(kwargs):
  if kwargs:
    raise_and_log_error(
        UnexpectedError('Unexpected arguments: {}'.format(kwargs.keys())))
