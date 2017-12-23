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

Commands are introduced into modules, and modules are explciitly
plugged into the command_modules[] list in main() where they
will be initialized and their commands registered into the registry.
From there this module will be able to process arguments and
dispatch commands.
"""

import argparse
import datetime
import logging
import sys
import yaml

from buildtool.git import GitRunner
from buildtool.util import (
    add_parser_argument,
    maybe_log_exception)


STANDARD_LOG_LEVELS = {
    'debug': logging.DEBUG,
    'info': logging.INFO,
    'warning': logging.WARNING,
    'error': logging.ERROR
}

def init_standard_parser(parser, defaults):
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
      help='path to YAML file containing default command-line options')

  add_parser_argument(
      parser, 'log_level', defaults, 'info',
      choices=STANDARD_LOG_LEVELS.keys(),
      help='Set the logging level')
  add_parser_argument(
      parser, 'root_path', defaults, 'build_source',
      help='Path to directory to put source code to build in.')
  add_parser_argument(
      parser, 'scratch_dir', defaults, 'scratch',
      help='Directory to write working files.')
  add_parser_argument(
      parser, 'logs_dir', defaults, None,
      help='Override director to write logfiles.'
      ' The default is <scratch_dir>/log.')
  add_parser_argument(
      parser, 'build_number', defaults,
      '{:%Y%m%d%H%M%S}'.format(datetime.datetime.utcnow()),
      help='Build number is used when generating artifacts.')
  add_parser_argument(
      parser, 'one_at_a_time', defaults, False, action='store_true',
      help='Do not perform applicable concurrency, for debugging.')


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
  if not options.default_args_file:
    defaults = {}
  else:
    defaults = __load_defaults_from_path(options.default_args_file)
    defaults['default_args_file'] = options.default_args_file

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
  init_standard_parser(parser, defaults)
  registry = make_registry(command_modules, parser, defaults)

  return parser.parse_args(args), registry


def main():
  """The main command dispatcher."""

  GitRunner.stash_and_clear_auth_env_vars()

  import buildtool.source_commands
  import buildtool.build_commands
  import buildtool.bom_commands
  import buildtool.changelog_commands
  import buildtool.apidocs_commands
  command_modules = [
      buildtool.source_commands,
      buildtool.build_commands,
      buildtool.bom_commands,
      buildtool.changelog_commands,
      buildtool.apidocs_commands
  ]

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
    retcode = -1
  else:
    command = factory.make_command(options)
    command()
    retcode = 0

  return retcode


if __name__ == '__main__':
  # pylint: disable=broad-except
  try:
    sys.exit(main())
  except Exception as ex:
    sys.stdout.flush()
    maybe_log_exception('main()', ex, action_msg='Terminating')
    logging.error("FAILED")
    sys.exit(-1)
