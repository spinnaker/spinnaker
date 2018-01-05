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

from buildtool.command import (
    RepositoryCommandFactory,
    RepositoryCommandProcessor)
from buildtool.git import (
    GitRunner,
    RemoteGitRepository)
from buildtool.source_code_manager import (
    SPINNAKER_BOM_REPOSITORIES,
    SPINNAKER_HALYARD_REPOSITORIES,
    SPINNAKER_RUNNABLE_REPOSITORIES)
from buildtool.util import (
    check_subprocess,
    ensure_dir_exists)


class FetchSourceCommand(RepositoryCommandProcessor):
  """Implements the fetch_source command."""

  def _do_determine_source_repositories(self):
    """Implements RepositoryCommand interface."""
    options = self.options

    all_source_repos = dict(SPINNAKER_BOM_REPOSITORIES)
    all_source_repos.update(SPINNAKER_HALYARD_REPOSITORIES)

    if options.github_user in ('upstream', 'default'):
      source_repositories = all_source_repos
    else:
      github = 'https://github.com/{user}'.format(user=options.github_user)
      source_repositories = {
          name: RemoteGitRepository(name, '%s/%s' % (github, name), upstream)
          for name, upstream in all_source_repos.items()
      }

    return self.filter_repositories(source_repositories)

  def __init__(self, factory, options):
    """Implements CommandProcessor interface."""
    super(FetchSourceCommand, self).__init__(factory, options)
    have_bom_path = 1 if options.fetch_bom_path else 0
    have_bom_version = 1 if options.fetch_bom_version else 0
    have_branch = 1 if options.git_branch else 0
    have_refresh = 1 if options.git_refresh else 0
    if have_bom_path + have_bom_version + have_branch + have_refresh != 1:
      raise ValueError(
          '{name} requires one of:'
          ' --bom_path, --bom_version, --git_branch or --refresh'
          .format(name=self.name))

    self.__bom = None

  def _do_preprocess(self):
    """Implements RepositoryCommandProcessor interface.

    Load the bom if one was specified.
    """
    options = self.options
    bom_path = options.fetch_bom_path
    bom_version = options.fetch_bom_version
    if bom_path:
      self.__bom = self.source_code_manager.bom_from_path(bom_path)
    elif bom_version:
      self.__bom = self.source_code_manager.bom_from_version(bom_version)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    options = self.options
    scm = self.source_code_manager
    git_dir = scm.get_local_repository_path(repository.name)
    branch = options.git_branch
    default_branch = options.fallback_branch

    if branch:
      self.git.clone_repository_to_path(
          repository.url, git_dir,
          upstream_url=repository.upstream_url,
          branch=branch, default_branch=default_branch)
    elif self.__bom:
      scm.pull_source_from_bom(repository.name, git_dir, self.__bom)
    else:
      self.git.refresh_local_repository(
          git_dir, scm.git.ORIGIN_REMOTE_NAME, options.refresh_branch)

    self.__collect_halconfig_files(repository)

  def __collect_halconfig_files(self, repository):
    """Gets the component config files and writes them into the scratch_path."""
    name = repository.name
    if (name not in SPINNAKER_RUNNABLE_REPOSITORIES.keys()
        and name not in ['spinnaker-monitoring']):
      logging.debug('%s does not use config files -- skipping', name)
      return

    git_dir = self.source_code_manager.get_local_repository_path(name)
    if name == 'spinnaker-monitoring':
      config_root = os.path.join(git_dir, 'spinnaker-monitoring-daemon')
    else:
      config_root = git_dir

    options = self.options
    target_dir = os.path.join(options.scratch_dir, name, 'halconfig')
    ensure_dir_exists(target_dir)

    config_path = os.path.join(config_root, 'halconfig')
    logging.info('Copying configs from %s...', config_path)
    for profile in os.listdir(config_path):
      profile_path = os.path.join(config_path, profile)
      if os.path.isfile(profile_path):
        shutil.copyfile(profile_path, os.path.join(target_dir, profile))
        logging.debug('Copied profile to %s', profile_path)
      elif not os.path.isdir(profile_path):
        logging.warning('%s is neither file nor directory -- ignoring',
                        profile_path)
        continue
      else:
        tar_path = os.path.join(
            target_dir, '{profile}.tar.gz'.format(profile=profile))
        file_list = ' '.join(os.listdir(profile_path))

        # NOTE: For historic reasons this is not actually compressed
        # even though the tar_path says ".tar.gz"
        check_subprocess(
            'tar cf {path} -C {profile} {file_list}'.format(
                path=tar_path, profile=profile_path, file_list=file_list))
        logging.debug('Copied profile to %s', tar_path)


class FetchSourceCommandFactory(RepositoryCommandFactory):
  """Creates instances of FetchSourceCommand."""

  @staticmethod
  def add_fetch_parser_args(parser, defaults):
    """Public method for adding standard "fetch" related arguments.

    This is intended to be used by other commands wanting to be consistent.
    """
    add_argument = FetchSourceCommandFactory.add_argument
    GitRunner.add_git_parser_args(parser, defaults, pull=True)
    add_argument(
        parser, 'fetch_bom_version', defaults, None,
        help='Pull this BOM version rather than --git_branch.'
        ' This requires halyard is installed to retrieve the BOM.')
    add_argument(
        parser, 'fetch_bom_path', defaults, None,
        help='Pull versions from this BOM rather than --git_branch.')
    add_argument(
        parser, 'git_refresh', defaults, None,
        help='Refresh existing source from this branch.')

  def __init__(self):
    super(FetchSourceCommandFactory, self).__init__(
        'fetch_source', FetchSourceCommand,
        'Clone or refresh the local git repositories from the ORIGIN.')

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(FetchSourceCommandFactory, self)._do_init_argparser(parser, defaults)
    self.add_fetch_parser_args(parser, defaults)


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  FetchSourceCommandFactory().register(registry, subparsers, defaults)
