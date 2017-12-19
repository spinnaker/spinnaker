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

import logging

# pylint: disable=relative-import
from buildtool.git import GitRunner
from buildtool.source_code_manager import SpinnakerSourceCodeManager
from buildtool.util import maybe_log_exception


class CommandFactory(object):
  """Abstract base class for CLI command injection."""

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
      raise ValueError(
          'CommandFactory "{name}" already exists.'.format(name=name))

    factory.add_argparser(subparsers, defaults)
    registry[name] = factory

  @staticmethod
  def add_argument(parser, name, defaults, default_value, **kwargs):
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

  @property
  def name(self):
    """The bound command name."""
    return self.__name

  @property
  def description(self):
    """The bound command description."""
    return self.__description

  def __init__(self, name, factory_method, description,
               *factory_method_pos_args,
               **factory_method_kwargs):
    self.__called_do_init_argparser = False
    self.__name = name
    self.__description = description
    self.__parent_arguments = factory_method_kwargs.pop('parent_arguments', [])
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
    parser = subparsers.add_parser(
        self.name,
        parents=[subparsers._name_parser_map[name]
                 for name in self.__parent_arguments],
        help=self.description)
    self._do_init_argparser(parser, defaults)

    # pylint: disable=superfluous-parens
    # Verify call propagated through class hierarchy.
    assert(self.__called_do_init_argparser)

  def _do_init_argparser(self, parser, defaults):
    """Hook for derived classes to override to add their specific arguments."""
    # pylint: disable=unused-argument
    self.__called_do_init_argparser = True


class CommandProcessor(object):
  """Abstract base class for CLI command implementations."""

  @property
  def name(self):
    """The bound command name."""
    return self.__factory.name

  @property
  def factory(self):
    """The bound command factory."""
    return self.__factory

  @property
  def options(self):
    """The bound command options."""
    return self.__options

  def __init__(self, factory, options):
    self.__factory = factory
    self.__options = options

  def __call__(self):
    logging.debug('Running command=%s...', self.name)
    try:
      result = self._do_command()
      logging.debug('Finished command=%s', self.name)
      return result
    except Exception as ex:
      maybe_log_exception(self.name, ex)
      raise

  def _do_command(self):
    """This should be overriden."""
    # pylint: disable=unused-variable
    raise NotImplementedError('{0}._do_command is not implemented'.format(
        self.__class__.name))


def _do_call_do_repository(repository, command):
  """Run the command's _do_repository on the given repository."""
  # pylint: disable=protected-access
  logging.info('%s processing %s', command.name, repository.name)
  try:
    result = command._do_repository(repository)
    logging.info('%s finished %s', command.name, repository.name)
    return result
  except Exception as ex:
    maybe_log_exception(
        '{command} on repo={repo}'.format(
            command=command.name, repo=repository.name),
        ex)
    raise


class RepositoryCommandProcessor(CommandProcessor):
  """And abstract command processor that run a command for each repository."""

  @property
  def git(self):
    """Returns GitRunner."""
    return self.__git

  @property
  def source_repositories(self):
    """Returns list of RemoteGitRepository this command should operate on."""
    if self.__source_repositories is None:
      self.__source_repositories = self._do_determine_source_repositories()
    return self.__source_repositories

  @property
  def source_code_manager(self):
    """Returns the SourceCodeManager this command uses."""
    if self.__scm is None:
      options = self.options
      self.__scm = SpinnakerSourceCodeManager(
          self.__git,
          options.root_path,
          self.source_repositories,
          max_threads=self.__max_threads)
    return self.__scm

  def __init__(self, factory, options, **kwargs):
    self.__max_threads = kwargs.pop('max_threads', 64)
    if options.one_at_a_time:
      logging.debug('Limiting %s to one thread.', factory.name)
      self.__max_threads = 1

    self.__use_threadpool = kwargs.pop('use_threadpool', False)
    self.__git = kwargs.pop('git', None) or GitRunner()
    self.__source_repositories = kwargs.pop('source_repositories', None)
    self.__scm = None
    super(RepositoryCommandProcessor, self).__init__(
        factory, options, **kwargs)

  def filter_repositories(self, source_repositories):
    """Filter a list of source_repositories using option constriants."""
    # pylint: disable=unused-argument
    return source_repositories

  def _do_determine_source_repositories(self):
    """Determine which repositories this command should operate on."""
    # pylint: disable=unused-argument
    if self.__source_repositories is None:
      raise NotImplementedError(
          '{0}.determine_source_repositories not implemented'.format(
              self.__class__.__name__))
    raise Exception('Unexpected call.')

  def _do_command(self):
    """Implements CommandProcessor interface.

    Derived classes should instead implement _do_repository to operate
    on individual repositories.

    They can also implement _do_preprocess or _do_postprocess to inject
    behavior before processing any repositories or after processing all them.
    """
    self._do_preprocess()
    result_dict = self.source_code_manager.foreach_source_repository(
        _do_call_do_repository, self,
        use_threadpool=self.__use_threadpool)
    return self._do_postprocess(result_dict)

  def _do_preprocess(self):
    """Prepares the command with any pre-requisites that can be factored out.

    Returns:
      Argument to pass into per-repository calls
    """
    pass

  def _do_postprocess(self, result_dict):
    """Perform any post-process after all the repos have been processed.

    Args:
      result_dict: [dict] The result of the foreach_source_repository call.

    Returns:
      The final result for the command.
    """
    return result_dict

  def _do_repository(self, repository):
    # pylint: disable=unused-argument
    raise NotImplementedError(
        '{0}._do_repository is not implemented'.format(self.__class__.name))


class RepositoryCommandFactory(CommandFactory):
  """Creates RepostioryCommand instances."""
  pass
