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

"""The main program for organizing the buildtool.

This module is reponsible for determining the configuration
then acquiring and dispatching commands.

Commands are introduced into modules, and modules are explicitly
plugged into the command_modules[] list in main() where they
will be initialized and their commands registered into the registry.
From there this module will be able to process arguments and
dispatch commands.
"""

import argparse
import datetime
import logging
import os
import sys
import time
import yaml

from buildtool.metrics import MetricsManager
from buildtool import (
    add_parser_argument,
    maybe_log_exception,
    GitRunner)


STANDARD_LOG_LEVELS = {
    'debug': logging.DEBUG,
    'info': logging.INFO,
    'warning': logging.WARNING,
    'error': logging.ERROR
}

# This is so tests can disable it
CHECK_HOME_FOR_CONFIG = True


def add_standard_parser_args(parser, defaults):
  """Init argparser with command-independent options.

  Args:
    parser: [argparse.Parser]
    defaults: [dict]  Default value overrides keyed by option name.
  """
  parser.add_argument(
      'components', nargs='*', default=defaults.get('components', None),
      help='Restrict commands to these components or repository names')

  add_parser_argument(
      parser, 'default_args_file', defaults, None,
      help='Path to YAML file containing command-line option overrides.'
           ' The default is $HOME/.spinnaker/buildtool.yml if present.'
           ' This parameter will overload the defaults. Embedded'
           ' default_args_file will also be read at a lower precedence than'
           ' the containing file.')

  add_parser_argument(
      parser, 'log_level', defaults, 'info',
      choices=STANDARD_LOG_LEVELS.keys(),
      help='Set the logging level')
  add_parser_argument(
      parser, 'output_dir', defaults, 'output',
      help='Directory to write working files.')
  add_parser_argument(
      parser, 'input_dir', defaults, 'source_code',
      help='Directory to cache input files, such as cloning git repos.')
  add_parser_argument(
      parser, 'one_at_a_time', defaults, False, type=bool,
      help='Do not perform applicable concurrency, for debugging.')
  add_parser_argument(
      parser, 'parent_invocation_id', defaults,
      '{:%y%m%d}.{}'.format(datetime.datetime.utcnow(), os.getpid()),
      help='For identifying the context of the metrics data to be produced.')


def __load_defaults_from_path(path, visited=None):
  """Helper function for loading defaults from yaml file."""
  visited = visited or []
  if path in visited:
    raise ValueError('Circular "default_args_file" dependency in %s' % path)
  visited.append(path)

  with open(path, 'r') as f:
    defaults = yaml.load(f)

    # Allow these files to be recursive
    # So that there can be some overall default file
    # that is then overwridden by another file where
    # the override file references the default one
    # and the CLI argument points to the override file.
    base_defaults_file = defaults.get('default_args_file')

    if base_defaults_file:
      base_defaults = __load_defaults_from_path(base_defaults_file)
      base_defaults.update(defaults)  # base is lower precedence.
      defaults = base_defaults        # defaults is what we want to return.

  return defaults


def preprocess_args(args):
  """Preprocess the args to determine the defaults to use.

  This recognizes the --default_args_file override and, if present loads them.

  Returns:
    args, defaults
    Where:
      args are remaining arguments (with--default_args_file removed
      defaults are overriden defaults from the default_args_file, if present.
  """
  parser = argparse.ArgumentParser(add_help=False)
  parser.add_argument('--default_args_file', default=None)

  options, args = parser.parse_known_args(args)

  home_path = os.path.join(os.environ['HOME'], '.spinnaker', 'buildtool.yml')
  if CHECK_HOME_FOR_CONFIG and os.path.exists(home_path):
    defaults = __load_defaults_from_path(home_path)
    defaults['default_args_file'] = home_path
  else:
    defaults = {}

  if options.default_args_file:
    override_defaults = __load_defaults_from_path(options.default_args_file)
    override_defaults['default_args_file'] = options.default_args_file
    defaults.update(override_defaults)

  return args, defaults


def make_registry(command_modules, parser, defaults):
  """Creates a command registry, adding command arguments to the parser.

  Args:
    command_modules: [list of modules]  The modules that have commands
      to register. Each module should have a function
          register_commands(registry, subparsers, defaults)
      that will register CommandFactory instances into the registry.
    parser: [ArgumentParser] The parser to add commands to
       This adds a 'command' subparser to capture the requested command choice.
    defaults: [dict] Default values to specify when adding arguments.
  """
  registry = {}

  subparsers = parser.add_subparsers(title='command', dest='command')
  for module in command_modules:
    module.register_commands(registry, subparsers, defaults)
  return registry


def init_options_and_registry(args, command_modules):
  """Register command modules and determine options from commandline.

  These are coupled together for implementation simplicity. Conceptually
  they are unrelated but they share implementation details that can be
  encapsulated by combining them this way.

  Args:
    args: [list of command-line arguments]
    command_modules: See make_registry.

  Returns:
    options, registry

    Where:
      options: [Namespace] From parsed args.
      registry: [dict] of (<command-name>: <CommandFactory>)
  """
  args, defaults = preprocess_args(args)

  parser = argparse.ArgumentParser(prog='buildtool.sh')
  add_standard_parser_args(parser, defaults)
  MetricsManager.init_argument_parser(parser, defaults)

  registry = make_registry(command_modules, parser, defaults)
  options = parser.parse_args(args)
  return options, registry


def main():
  """The main command dispatcher."""

  start_time = time.time()

  from importlib import import_module
  command_modules = [
      import_module(name + '_commands') for name in [
          'apidocs',
          'bom',
          'changelog',
          'container',
          'debian',
          'halyard',
          'image',
          'rpm',
          'source',
          'spinnaker',
          'inspection',
      ]]

  GitRunner.stash_and_clear_auth_env_vars()
  options, command_registry = init_options_and_registry(
      sys.argv[1:], command_modules)

  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d'
             ' [%(threadName)s.%(process)d] %(message)s',
      datefmt='%H:%M:%S',
      level=STANDARD_LOG_LEVELS[options.log_level])

  logging.debug(
      'Running with options:\n   %s',
      '\n   '.join(yaml.dump(vars(options), default_flow_style=False)
                   .split('\n')))

  factory = command_registry.get(options.command)
  if not factory:
    logging.error('Unknown command "%s"', options.command)
    return -1

  MetricsManager.startup_metrics(options)
  labels = {'command': options.command}
  success = False
  try:
    command = factory.make_command(options)
    command()
    success = True
  finally:
    labels['success'] = success
    MetricsManager.singleton().observe_timer(
        'BuildTool_Outcome', labels, 'Program Execution',
        time.time() - start_time)
    MetricsManager.shutdown_metrics()

  return 0


def dump_threads():
  """Dump current threads to facilitate debugging possible deadlock.

  A process did not exit when log file suggested it was. Maybe there was
  a background thread it was joining on. If so, this might give a clue
  should it happen again.
  """
  import threading
  threads = []
  for thread in threading.enumerate():
    threads.append('  name={name} daemon={d} id={id}'.format(
        name=thread.name, d=thread.daemon, id=thread.ident))

  if len(threads) > 1:
    logging.info('The following threads still running:\n%s', '\n'.join(threads))


def wrapped_main():
  """Run main and dump outstanding threads when done."""
  # pylint: disable=broad-except
  try:
    retcode = main()
  except Exception as ex:
    sys.stdout.flush()
    maybe_log_exception('main()', ex, action_msg='Terminating')
    logging.error("FAILED")
    retcode = -1

  dump_threads()
  return retcode


if __name__ == '__main__':
  sys.exit(wrapped_main())
