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

"""Responsible for managing the source code that we are building.

This includes fetching the source to be built as well as manipulating
the git repositories for tagging and annotations.
"""

from multiprocessing.pool import ThreadPool

import logging
import os
import yaml

# pylint: disable=relative-import
from buildtool.git import RemoteGitRepository
from buildtool.util import (
    check_subprocess,
    ensure_dir_exists)


def __new_spinnaker_git_repo(name):
  return RemoteGitRepository.make_from_url(
      'https://github.com/spinnaker/%s' % name)

def _new_google_git_repo(name):
  return RemoteGitRepository.make_from_url(
      'https://github.com/google/%s' % name)


# These would be required if running from source code
SPINNAKER_RUNNABLE_REPOSITORIES = {
    repo.name: repo for repo in [
        __new_spinnaker_git_repo('clouddriver'),
        __new_spinnaker_git_repo('deck'),
        __new_spinnaker_git_repo('echo'),
        __new_spinnaker_git_repo('fiat'),
        __new_spinnaker_git_repo('front50'),
        __new_spinnaker_git_repo('gate'),
        __new_spinnaker_git_repo('igor'),
        __new_spinnaker_git_repo('orca'),
        __new_spinnaker_git_repo('rosco')
    ]
}

# These would be required if specifying a BOM or building from one
SPINNAKER_BOM_REPOSITORIES = {
    repo.name: repo for repo in [
        __new_spinnaker_git_repo('spinnaker'),
        __new_spinnaker_git_repo('spinnaker-monitoring')
    ]
}
SPINNAKER_BOM_REPOSITORIES.update(SPINNAKER_RUNNABLE_REPOSITORIES)


# These would be required for testing a deployment
SPINNAKER_TESTING_REPOSITORIES = {
    repo.name: repo for repo in [
        __new_spinnaker_git_repo('spinnaker'),
        _new_google_git_repo('citest'),
    ]
}

SPINNAKER_HALYARD_REPOSITORIES = {
    'halyard': __new_spinnaker_git_repo('halyard')
}

SPINNAKER_GITHUB_IO_REPOSITORY = __new_spinnaker_git_repo('spinnaker.github.io')


class RepositoryWorker(object):
  """A picklable callable for passing to inter-process mappers."""
  # pylint: disable=too-few-public-methods

  def __init__(self, fn, *pargs, **kwargs):
    """ Constructor

    Args:
      fn: [callable] might need to be picklable depending on use case.
         The first argument to fn should be a SourceRepository which will
         be injected by the call() method.
      pargs: [list] additional positional args required by fn
      kwargs: [kwargs] keyword args to passthrough to fn
    """
    self.__fn = fn
    self.__pargs = pargs
    self.__kwargs = kwargs

  def __call__(self, repository):
    """Call the bound function with the repository plus bound args."""
    name = repository.name
    return name, self.__fn(repository, *self.__pargs, **self.__kwargs)


class SpinnakerSourceCodeManager(object):
  """Helper class for managing spinnaker source code repositories."""

  @property
  def git(self):
    """The git controller for issuing git commands."""
    return self.__git

  @property
  def root_path(self):
    """The base directory for all the individual repositories being managed.

    Each repository will be a child directory of this path.
    """
    return self.__root_path

  @property
  def source_repositories(self):
    """Return the bound GitRemoteRepositories

    This is a dictionary {<name>: <RemoteGitRepository>}
    """
    return self.__source_repositories

  def __init__(self, git, root_path, source_repositories, **kwargs):
    if not git:
      raise ValueError('No git controller provided.')
    if not root_path:
      raise ValueError('No root_path provied.')
    if not source_repositories:
      raise ValueError('No source_repositories were provided.')
    self.__git = git
    self.__root_path = root_path
    self.__source_repositories = source_repositories
    self.__max_threads = kwargs.pop('max_threads', 100)
    self.__add_upstream = kwargs.pop('attach_upstream', False)
    if kwargs:
      raise ValueError('Unexpected arguments: {}'.format(kwargs.keys()))

  def get_local_repository_path(self, repository_name):
    """Returns the path to the desired local repository directory."""
    return os.path.join(self.__root_path, repository_name)

  def bom_from_path(self, path):
    """Load a BOM from a file."""
    logging.debug('Loading bom from %s', path)
    with open(path, 'r') as f:
      bom_yaml_string = f.read()
    return yaml.load(bom_yaml_string)

  def bom_from_version(self, version):
    """Load a BOM from halyard."""
    logging.debug('Loading bom version %s', version)
    bom_yaml_string = check_subprocess(
        'hal version bom {0} --color false --quiet'
        .format(version), echo=False)
    return yaml.load(bom_yaml_string)

  def maybe_pull_repository_source(
      self, repository,
      bom_version=None, bom_path=None,
      git_branch=None, default_branch=None):
    """Pull the source as specified, if it was specified."""
    # pylint: disable=too-many-arguments

    pull_version = 1 if bom_version else 0
    pull_path = 1 if bom_path else 0
    pull_branch = 1 if git_branch else 0
    have = pull_version + pull_path + pull_branch
    if have == 0:
      logging.debug('No source pull requests to process.')
      return

    if have > 1:
      raise ValueError('Ambiguous source code requests.')

    if default_branch and not git_branch:
      raise ValueError('A default_branch requires a git_branch')

    git_dir = self.get_local_repository_path(repository.name)
    if os.path.exists(git_dir):
      message = '%s already exists. Cannot pull source.' % git_dir
      logging.error(message)
      raise ValueError(message)

    logging.info('Pulling %s to %s', repository.url, git_dir)
    ensure_dir_exists(os.path.dirname(git_dir))

    if git_branch:
      self.git.clone_repository_to_path(
          repository.url, git_dir,
          branch=git_branch, default_branch=default_branch)
    else:
      if bom_version:
        bom = self.bom_from_version(bom_version)
      else:
        bom = self.bom_from_path(bom_path)
      self.pull_source_from_bom(repository.name, git_dir, bom)

  def pull_source_from_bom(self, repository_name, git_dir, bom):
    """Pull the source for a particular repository name from a bom."""
    if repository_name in ('monitoring-daemon', 'monitoring-third-party'):
      repository_name = 'spinnaker-monitoring'

    spec = bom['services'][repository_name]
    commit = spec['commit']

    # We're creating a local repo containing only the commit and tag.
    # The reason for this is because nebula gets confused by our tags
    # and wants to only use the latest Netflix tag, which is wrong.
    # If we could control nebula, we wouldnt need to wipe the repo and
    # add a new one, but we'd still need to add the new tag to ensure it
    # exists because the bom may not have been pushed.
    if os.path.exists(git_dir):
      return None

    base_url = bom['artifactSources']['gitPrefix']
    remote_url = spec.get(
        'sourceRepository',
        '{base}/{name}'.format(base=base_url, name=repository_name))
    branch = spec.get(
        'sourceBranch',
        bom['artifactSources'].get('gitBranch', 'master'))

    self.__git.clone_repository_to_path(remote_url, git_dir,
                                        commit=commit, branch=branch)

  def foreach_source_repository(
      self, call_function, *posargs, **kwargs):
    """Call the function on each of the SourceRepository instances."""
    worker = RepositoryWorker(call_function, *posargs, **kwargs)
    all_repos = self.__source_repositories
    num_threads = min(self.__max_threads, len(all_repos))
    if num_threads > 1:
      pool = ThreadPool(num_threads)
      logging.info('Mapping %d/%s', len(all_repos.keys()), all_repos.keys())
      try:
        raw_list = pool.map(worker, all_repos.values())
        result = {name: value for name, value in raw_list}
      except Exception:
        logging.error('Map caught exception')
        raise
      logging.info('Finished mapping')
      pool.terminate()
    else:
      # If we have only one thread, skip the pool
      # this is primarily to make debugging easier.
      result = {
          name: worker(repository)[1]
          for name, repository in all_repos.items()
      }
    return result

  def push_to_origin_if_not_upstream(self, repository, branch):
    """Push the local repository back to the ORIGIN, but not upstream."""
    git_dir = self.get_local_repository_path(repository.name)
    remote_name = self.git.ORIGIN_REMOTE_NAME
    remote_url = repository.url
    upstream = repository.upstream_url
    if remote_url == upstream:
      logging.warning('Skipping push %s %s because %s is upstream.',
                      remote_name, repository.name, remote_name)
      return
    self.__git.push_branch_to_origin(git_dir, branch)
