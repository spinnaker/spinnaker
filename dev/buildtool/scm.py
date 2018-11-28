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

import collections
import logging
import os
import yaml

# pylint: disable=relative-import
from buildtool import (
    GitRepositorySpec,
    GitRunner,
    RepositorySummary,

    add_parser_argument,
    check_kwargs_empty,
    raise_and_log_error,
    write_to_path,
    UnexpectedError)


class SourceInfo(
    collections.namedtuple('SourceInfo', ['build_number', 'summary'])):
  """Basic things about the state of a source repository."""

  def to_build_version(self):
    """Return the build-number specific version name."""
    return '%s-%s' % (self.summary.version, self.build_number)


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

  AUTO = '_auto_'

  @staticmethod
  def add_parser_args(parser, defaults):
    """Add standard parser arguments used by SourceCodeManager."""
    if hasattr(parser, 'added_scm'):
      return
    parser.added_scm = True
    GitRunner.add_parser_args(parser, defaults)
    add_parser_argument(parser, 'github_upstream_owner',
                        defaults, 'spinnaker',
                        help='The standard upstream repository owner.')

  @property
  def git(self):
    return self.__git

  @property
  def options(self):
    return self.__options

  @property
  def root_source_dir(self):
    """The base directory for all the source repositories.

    Each repository will be a child directory of this path.
    """
    return self.__root_source_dir

  def __init__(self, options, root_source_dir, **kwargs):
    self.__max_threads = kwargs.pop('max_threads', 100)
    self.__add_upstream = kwargs.pop('attach_upstream', False)
    check_kwargs_empty(kwargs)

    self.__options = options
    self.__git = GitRunner(options)
    self.__root_source_dir = root_source_dir

  def service_name_to_repository_name(self, service_name):
    if service_name == 'monitoring-daemon':
      return 'spinnaker-monitoring'
    return service_name

  def repository_name_to_service_name(self, repository_name):
    if repository_name == 'spinnaker-monitoring':
      return 'monitoring-daemon'
    return repository_name

  def check_repository_is_current(self, repository):
    raise NotImplementedError(self.__class__.__name__)

  def determine_build_number(self, repository):
    raise NotImplementedError(self.__class__.__name__)

  def determine_origin(self, name):
    """Determine the origin URL for the given repository name."""
    raise NotImplementedError(self.__class__.__name__)

  def make_repository_spec(self, name, **kwargs):
    """Create GitRepositorySpec based on the name and configuration.

    Args:
      git_dir: if supplied then use it, otherwise default under the root path.
      origin: if supplied then use it, even if None. Otherwise default
      upstream: if supplied then use it, even if None. Otherwise default.
      kwargs: Additional repository attributes
    """
    git_dir = kwargs.pop('git_dir', os.path.join(self.__root_source_dir, name))
    origin = kwargs.pop('origin', self.AUTO)
    upstream = kwargs.pop('upstream', self.AUTO)

    if origin == self.AUTO:
      origin = self.determine_origin(name)

    if os.path.exists(git_dir):
      logging.info('Confirming existing %s matches expectations', git_dir)
      existing = self.__git.determine_git_repository_spec(git_dir)
      if existing.origin != origin:
        raise_and_log_error(
            UnexpectedError(
                'Repository "{dir}" origin="{have}" expected="{want}"'.format(
                    dir=git_dir, have=existing.origin, want=origin)))

    if upstream == self.AUTO:
      upstream = self.determine_upstream_url(name)

    return GitRepositorySpec(
        name, origin=origin, upstream=upstream, git_dir=git_dir, **kwargs)

  def determine_upstream_url(self, name):
    upstream_owner = (self.__options.github_upstream_owner
                      if name not in ('citest')
                      else 'google')
    return 'https://github.com/{upstream}/{name}'.format(
        upstream=upstream_owner, name=name)

  def ensure_git_path(self, repository, **kwargs):
    """Make sure the repository is checked out.

    Normally one would use ensure_repository which also ensures the metadata
    is cached. However repositories that are not version controlled in the
    normal way (e.g. spinnaker.github.io) dont use the metadata so the
    assumptions in ensure_repository are not applicable.

    Returns: The build number to use
    """
    raise NotImplementedError(self.__class__.__name__)

  def ensure_local_repository(self, repository, commit=None):
    """Make sure local repository directory exists, and make it so if not."""
    git_dir = repository.git_dir
    have_git_dir = os.path.exists(git_dir)

    if have_git_dir:
      self.check_repository_is_current(repository)
    else:
      self.ensure_git_path(repository)

  def refresh_source_info(self, repository, build_number):
    """Extract the source info from repository and cache with build number.

    We associate the build number because the different builds
    (debian, container, etc) need to have the same builder number so that the
    eventual BOM is consitent. Since we dont build everything at once, we'll
    need to remember it.

    We extract out the repository summary info, particularly the commit it is
    at, to ensure that future operations are consistent and operating on the
    same commit.
    """
    summary = self.__git.collect_repository_summary(repository.git_dir)
    expect_build_number = (self.__options.build_number
                           if hasattr(self.__options, 'build_number')
                           else build_number)
    info = SourceInfo(expect_build_number, summary)

    filename = repository.name + '-meta.yml'
    dir_path = os.path.join(self.__options.output_dir, 'source_info')
    cache_path = os.path.join(dir_path, filename)
    logging.debug(
        'Refreshing source info for %s and caching to %s for buildnum=%s',
        repository.name, cache_path, build_number)
    write_to_path(info.summary.to_yaml(), cache_path)
    return info

  def lookup_source_info(self, repository):
    """Return the SourceInfo for the given repository."""
    filename = repository.name + '-meta.yml'
    dir_path = os.path.join(self.__options.output_dir, 'source_info')
    build_number = self.determine_build_number(repository)
    with open(os.path.join(dir_path, filename), 'r') as stream:
      return SourceInfo(
          build_number,
          RepositorySummary.from_dict(yaml.safe_load(stream.read())))

  def check_source_info(self, repository):
    """Ensure cached source info is consistent with current repository."""
    logging.debug('Checking that cached commit is consistent with %s',
                  repository.git_dir)
    info = self.lookup_source_info(repository)
    commit = self.__git.query_local_repository_commit_id(repository.git_dir)
    cached_commit = info.summary.commit_id
    if cached_commit != commit:
      raise_and_log_error(
          UnexpectedError(
              'Cached commit {cache} != current commit {id} in {dir}'.format(
                  cache=cached_commit, id=commit, dir=repository.git_dir)))
    return info

  def foreach_source_repository(
      self, all_repos, call_function, *posargs, **kwargs):
    """Call the function on each of the SourceRepository instances."""
    worker = RepositoryWorker(call_function, *posargs, **kwargs)
    num_threads = min(self.__max_threads, len(all_repos))
    if num_threads > 1:
      pool = ThreadPool(num_threads)
      logging.info('Mapping %d/%s',
                   len(all_repos), [repo.name for repo in all_repos])
      try:
        raw_list = pool.map(worker, all_repos)
        result = {name: value for name, value in raw_list}
      except Exception:
        logging.error('Map caught exception')
        pool.close()
        pool.join()
        raise
      logging.info('Finished mapping')
      pool.close()
      pool.join()
    else:
      # If we have only one thread, skip the pool
      # this is primarily to make debugging easier.
      result = {
          repository.name: worker(repository)[1]
          for repository in all_repos
      }
    return result

  def push_to_origin_if_not_upstream(self, repository, branch):
    """Push the local repository back to the origin, but not upstream."""
    git_dir = repository.git_dir
    origin = repository.origin
    upstream = repository.upstream_or_none()
    if upstream is None:
      logging.warning('Skipping push origin %s because upstream is None.',
                      repository.name)
      return
    if origin == upstream:
      logging.warning('Skipping push origin %s because origin is upstream.',
                      repository.name)
      return
    self.__git.push_branch_to_origin(git_dir, branch)

  def determine_source_repositories(self):
    """Determine which repositories are available to this SCM."""
    raise_and_log_error(
        UnexpectedError(self.__class__.__name__
                        + ': Should only be applicable to BomSCM',
                        cause='NotReachable'))
