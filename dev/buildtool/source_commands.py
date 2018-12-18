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

"""Implements fetch_source command for buildtool."""

import logging
import os
import shutil

from buildtool import (
    DEFAULT_BUILD_NUMBER,
    SPINNAKER_BOM_REPOSITORY_NAMES,
    SPINNAKER_HALYARD_REPOSITORY_NAME,
    SPINNAKER_PROCESS_REPOSITORY_NAMES,
    BranchSourceCodeManager,
    RepositoryCommandFactory,
    RepositoryCommandProcessor,

    raise_and_log_error,
    ConfigError)


class FetchSourceCommand(RepositoryCommandProcessor):
  """Implements the fetch_source command."""

  def __init__(self, factory, options):
    """Implements CommandProcessor interface."""

    all_names = list(SPINNAKER_BOM_REPOSITORY_NAMES)
    all_names.append(SPINNAKER_HALYARD_REPOSITORY_NAME)
    all_names.extend(SPINNAKER_PROCESS_REPOSITORY_NAMES)
    super(FetchSourceCommand, self).__init__(
        factory, options, source_repository_names=all_names)

  def ensure_local_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    options = self.options
    if os.path.exists(repository.git_dir):
      if options.delete_existing:
        logging.warning('Deleting existing %s', repository.git_dir)
        shutil.rmtree(repository.git_dir)
      elif options.skip_existing:
        logging.debug('Skipping existing %s', repository.git_dir)
      else:
        raise_and_log_error(
            ConfigError('"{dir}" already exists.'
                        ' Enable "skip_existing" or "delete_existing".'
                        .format(dir=repository.git_dir)))
    super(FetchSourceCommand, self).ensure_local_repository(repository)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    pass


class FetchSourceCommandFactory(RepositoryCommandFactory):
  def __init__(self):
    super(FetchSourceCommandFactory, self).__init__(
        'fetch_source', FetchSourceCommand,
        'Clone or refresh the local git repositories from the origin.',
        BranchSourceCodeManager)

  def init_argparser(self, parser, defaults):
    super(FetchSourceCommandFactory, self).init_argparser(parser, defaults)
    self.add_argument(
        parser, 'build_number', defaults, DEFAULT_BUILD_NUMBER,
        help='The build number is used when generating artifacts.')
    self.add_argument(
        parser, 'delete_existing', defaults, False, type=bool,
        help='Force a new clone by removing existing directories if present.')
    self.add_argument(
        parser, 'skip_existing', defaults, False, type=bool,
        help='Ignore directories that are already present.')


class ExtractSourceInfoCommand(RepositoryCommandProcessor):
  """Get the Git metadata for each repository, and associate a build number."""
  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    self.source_code_manager.refresh_source_info(
        repository, self.options.build_number)


class ExtractSourceInfoCommandFactory(RepositoryCommandFactory):
  """Associates the current build number with each repository."""

  def __init__(self):
    super(ExtractSourceInfoCommandFactory, self).__init__(
        'extract_source_info', ExtractSourceInfoCommand,
        'Get the repository metadata and establish a build number.',
        BranchSourceCodeManager,
        source_repository_names=SPINNAKER_BOM_REPOSITORY_NAMES)

  def init_argparser(self, parser, defaults):
    super(ExtractSourceInfoCommandFactory, self).init_argparser(
        parser, defaults)
    self.add_argument(
        parser, 'build_number', defaults, DEFAULT_BUILD_NUMBER,
        help='The build number is used when generating artifacts.')


def register_commands(registry, subparsers, defaults):
  ExtractSourceInfoCommandFactory().register(registry, subparsers, defaults)
  FetchSourceCommandFactory().register(registry, subparsers, defaults)
