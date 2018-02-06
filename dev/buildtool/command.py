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

"""Abstract CommandProcessor classes for buildtool."""

import os
import logging
import sys

# pylint: disable=relative-import
from buildtool.metrics import MetricsManager
from buildtool import (
    add_parser_argument,
    maybe_log_exception,
    raise_and_log_error,
    UnexpectedError)


class CommandFactory(object):
  """Abstract base class for CLI command injection."""

  @staticmethod
  def add_argument(parser, name, defaults, default_value, **kwargs):
    """See buildtool.util.add_argument."""
    add_parser_argument(parser, name, defaults, default_value, **kwargs)

  def register(self, registry, subparsers, defaults):
    """Registers a command factory.

    Args:
      registry: [dict] The registry to add to, keyed by command name.
      subparsers: [ArgumentParser subparsers] for adding command arguments
      defaults: [dict] optional default values for command arguments
    """
    factory = self
    name = factory.name
    if name in registry.keys():
      raise_and_log_error(
          UnexpectedError(
              'CommandFactory "{name}" already exists.'.format(name=name)))

    factory.add_argparser(subparsers, defaults)
    registry[name] = factory

  @property
  def name(self):
    return self.__name

  @property
  def description(self):
    return self.__description

  def __init__(self, name, factory_method, description,
               *factory_method_pos_args,
               **factory_method_kwargs):
    self.__called_init_argparser = False
    self.__name = name
    self.__description = description
    self.__factory_method = factory_method
    self.__factory_method_pos_args = factory_method_pos_args
    self.__factory_method_kwargs = factory_method_kwargs

  def make_command(self, options):
    """Creates a new command instance with the given options.

    Args:
      options: [Namespace] containing the parser.parse_args().
    """
    return self.__factory_method(
        self, options,
        *self.__factory_method_pos_args, **self.__factory_method_kwargs)

  def add_argparser(self, subparsers, defaults):
    """Add specialized argparser for this command.

    Args:
      subparsers: The subparsers from the argparser parser.

    Returns:
      A new parser to add additional custom parameters for this command.
    """
    # pylint: disable=protected-access
    parser = subparsers.add_parser(self.name, help=self.description)
    self.init_argparser(parser, defaults)

    # pylint: disable=superfluous-parens
    # Verify call propagated through class hierarchy.
    assert(self.__called_init_argparser)

  def init_argparser(self, parser, defaults):
    """Hook for derived classes to override to add their specific arguments."""
    # pylint: disable=unused-argument
    self.__called_init_argparser = True


class CommandProcessor(object):
  """Abstract base class for CLI command implementations."""

  @property
  def name(self):
    return self.__factory.name

  @property
  def factory(self):
    return self.__factory

  @property
  def options(self):
    return self.__options

  @property
  def metrics(self):
    return self.__metrics

  def determine_tracking_metric_labels(self):
    """Returns the label bindings for the invocation tracking metrics."""
    return {'command': self.name}

  def determine_outcome_metric_labels(self):
    """Returns the label bindings for the invocation outcome metrics."""
    ex_type, _, _ = sys.exc_info()

    return {
        'command': self.name,
        'success': ex_type is None,
        'exception_type': '' if ex_type is None else ex_type.__name__
    }

  def __init__(self, factory, options):
    self.__factory = factory
    self.__options = options
    self.__metrics = MetricsManager.singleton()

  def __call__(self):
    logging.debug('Running command=%s...', self.name)
    try:
      tracking_labels = self.determine_tracking_metric_labels()
      result = self.metrics.instrument_track_and_outcome(
          'RunCommand', 'Command Invocations', tracking_labels,
          self.determine_outcome_metric_labels,
          self._do_command)
      logging.debug('Finished command=%s', self.name)
      return result
    except Exception as ex:
      maybe_log_exception(self.name, ex)
      raise

  def _do_command(self):
    """This should be overriden to implement actual behavior."""
    raise NotImplementedError('{0}._do_command is not implemented'.format(
        self.__class__.name))

  def get_logfile_path(self, basename):
    """Return the path to the logfile to write."""
    logfile = '%s-%d.log' % (basename, os.getpid())
    return os.path.join(self.get_output_dir(), logfile)

  def get_output_dir(self, command=None):
    """Return the output dir for persistent build output from this command."""
    command = command or self.__options.command
    return os.path.join(self.__options.output_dir, command)

  def get_input_dir(self, command=None):
    """Return the output dir for persistent build output from this command."""
    command = command or self.__options.command
    return os.path.join(self.__options.input_dir, command)
