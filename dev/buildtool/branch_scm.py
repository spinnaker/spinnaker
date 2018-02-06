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

"""Source code manager that uses git branches."""

import logging
import os

from buildtool import (
    DEFAULT_BUILD_NUMBER,
    GitRunner,
    SpinnakerSourceCodeManager,

    add_parser_argument,
    check_kwargs_empty,
    check_options_set,
    raise_and_log_error,
    UnexpectedError)


class BranchSourceCodeManager(SpinnakerSourceCodeManager):
  """Sources are retrieved from github using branches."""

  @staticmethod
  def add_parser_args(parser, defaults):
    """Add standard parser arguments used by SourceCodeManager."""
    if hasattr(parser, 'added_branch_scm'):
      return
    parser.added_branch_scm = True

    GitRunner.add_parser_args(parser, defaults)
    add_parser_argument(parser, 'git_branch', defaults, None,
                        help='The git branch to operate on.')

  def __init__(self, *pos_args, **kwargs):
    super(BranchSourceCodeManager, self).__init__(*pos_args, **kwargs)
    check_options_set(self.options, ['git_branch', 'github_owner'])

  def determine_origin(self, name):
    origin = self.determine_owner_origin_or_none(name)
    if origin is None:
      raise_and_log_error(
          UnexpectedError('Not reachable', cause='NotReachable'))
    return origin

  def ensure_git_path(self, repository, **kwargs):
    branch = kwargs.pop('branch', None)
    check_kwargs_empty(kwargs)
    options = self.options

    git_dir = repository.git_dir
    have_git_dir = os.path.exists(git_dir)
    if not branch:
      if hasattr(options, 'git_branch'):
        branch = options.git_branch
      else:
        branch = 'master'
        logging.debug('No git_branch option available.'
                      ' Assuming "%s" branch is "master"', repository.name)

    fallback_branch = (options.git_fallback_branch
                       if hasattr(options, 'git_fallback_branch')
                       else None)
    if not have_git_dir:
      self.git.clone_repository_to_path(
          repository, branch=branch, default_branch=fallback_branch)

  def determine_build_number(self, repository):
    if hasattr(self.options, 'build_number'):
      build_number = self.options.build_number
    else:
      build_number = DEFAULT_BUILD_NUMBER
      logging.debug('Using default build number "%s" for "%s"',
                    build_number, repository.name)
    return build_number

  def check_repository_is_current(self, repository):
    branch = self.options.git_branch or 'master'
    have_branch = self.git.query_local_repository_branch(repository.git_dir)
    if have_branch == branch:
      return True
    logging.warning('"%s" was in the wrong branch -- checkout "%s"',
                    repository.git_dir, branch)
    self.git.check_run(repository.git_dir, 'checkout ' + branch)
    return False
