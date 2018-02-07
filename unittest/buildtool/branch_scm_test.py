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

# pylint: disable=missing-docstring
# pylint: disable=invalid-name

import argparse
import logging
import os
import shutil
import tempfile
import unittest

from buildtool import (
    GitRunner,
    SemanticVersion,
    BranchSourceCodeManager,
    SpinnakerSourceCodeManager,

    check_subprocess_sequence)

from test_util import init_runtime


SCM_USER = 'scm_user'
TEST_USER = 'test_user'

BASE_VERSION = 'version-7.8.9'
UNTAGGED_BRANCH = 'untagged-branch'


def make_default_options():
  """Helper function for creating default options for runner."""
  parser = argparse.ArgumentParser()
  parser.add_argument('--output_dir',
                      default=os.path.join('/tmp', 'scmtest.%d' % os.getpid()))
  GitRunner.add_parser_args(parser, {'github_owner': 'test_github_owner'})
  options = parser.parse_args([])
  options.command = 'test-command'
  options.git_branch = 'testing'
  return options


class TestSourceCodeManager(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    cls.base_temp_dir = tempfile.mkdtemp(prefix='scm_test')
    origin_root = os.path.join(cls.base_temp_dir, 'origin_repos')

    cls.ORIGIN_URLS = {
        'RepoOne': os.path.join(origin_root, SCM_USER, 'RepoOne'),
        'RepoTwo': os.path.join(origin_root, SCM_USER, 'RepoTwo'),
        'RepoTest': os.path.join(origin_root, TEST_USER, 'RepoTest'),
    }

    for repo_name, repo_origin in cls.ORIGIN_URLS.items():
      os.makedirs(repo_origin)
      base_file = os.path.join(
          repo_origin, '{name}-base.txt'.format(name=repo_name))
      unique_file = os.path.join(
          repo_origin, '{name}-unique.txt'.format(name=repo_name))
      untagged_file = os.path.join(
          repo_origin, '{name}-untagged.txt'.format(name=repo_name))

      logging.debug('Initializing repository %s', repo_origin)
      git_prefix = 'git -C "{dir}" '.format(dir=repo_origin)
      run_git = lambda cmd: git_prefix + cmd

      check_subprocess_sequence([
          # BASE_VERSION
          'touch "{file}"'.format(file=base_file),
          run_git(' init'),
          run_git('add "{file}"'.format(
              file=os.path.basename(base_file))),
          run_git('commit -a -m "feat(first): first commit"'),
          run_git('tag {base_version} HEAD'.format(
              base_version=BASE_VERSION)),

          # Add Unique branch name per repo
          run_git('checkout -b {name}-branch'.format(name=repo_name)),
          'touch "{file}"'.format(file=unique_file),
          run_git('add "{file}"'.format(file=os.path.basename(unique_file))),
          run_git('commit -a -m "chore(uniq): unique commit"'),

          # Add a common branch name, but without a tag on HEAD
          run_git('checkout master'),
          run_git('checkout -b {branch}'
                  .format(branch=UNTAGGED_BRANCH)),
          'touch "{file}"'.format(file=untagged_file),
          run_git('add "{file}"'.format(
              file=os.path.basename(untagged_file))),
          run_git('commit -a -m "chore(uniq): untagged commit"'),
          run_git('checkout master')
          ])

  @classmethod
  def tearDownClass(cls):
    shutil.rmtree(cls.base_temp_dir)

  def setUp(self):
    self.options = make_default_options()

  def test_get_local_repository_path(self):
    test_root = os.path.join(self.base_temp_dir, 'unused_source')
    scm = BranchSourceCodeManager(self.options, test_root)

    tests = ['RepoOne', 'RepoTwo', 'RepoTest', 'DoesNotExist']
    for repo_name in tests:
      repository = scm.make_repository_spec(repo_name)
      expect = os.path.join(test_root, repo_name)
      self.assertEquals(expect, repository.git_dir)
      self.assertFalse(os.path.exists(expect))

  def test_maybe_pull_repository_branch(self):
    test_root = os.path.join(self.base_temp_dir, 'pulled_test')
    options = make_default_options()
    options.git_branch = UNTAGGED_BRANCH
    options.build_number = 'maybe_pull_branch_buildnum'
    scm = BranchSourceCodeManager(options, test_root)

    for repository_name, origin in self.ORIGIN_URLS.items():
      repository = scm.make_repository_spec(repository_name, origin=origin,
                                            upstream=None)
      scm.ensure_local_repository(repository)

      git_dir = repository.git_dir
      spec = scm.git.determine_git_repository_spec(git_dir)
      self.assertEquals(repository, spec)

      in_branch = scm.git.query_local_repository_branch(git_dir)
      self.assertEquals(UNTAGGED_BRANCH, in_branch)

      summary = scm.git.collect_repository_summary(git_dir)
      semver = SemanticVersion.make(BASE_VERSION)
      expect_version = semver.next(
          SemanticVersion.MINOR_INDEX).to_version()

      self.assertEquals(expect_version, summary.version)

  def test_pull_repository_fallback_branch(self):
    test_root = os.path.join(self.base_temp_dir, 'fallback_test')
    unique_branch = 'RepoTwo-branch'
    options = make_default_options()
    options.git_branch = unique_branch
    options.git_fallback_branch = 'master'
    options.build_number = 'pull_repository_fallback_buildnumber'
    scm = BranchSourceCodeManager(options, test_root)

    for repo_name, origin in self.ORIGIN_URLS.items():
      repository = scm.make_repository_spec(repo_name, origin=origin)
      scm.ensure_local_repository(repository)
      git_dir = repository.git_dir
      want_branch = (unique_branch
                     if repository.name == 'RepoTwo'
                     else 'master')
      in_branch = scm.git.query_local_repository_branch(git_dir)
      self.assertEquals(want_branch, in_branch)

  def test_foreach_repo(self):
    test_root = os.path.join(self.base_temp_dir, 'foreach_test')
    pos_args = [1, 2, 3]
    kwargs = {'a': 'A', 'b': 'B'}

    scm = SpinnakerSourceCodeManager(self.options, test_root)
    all_repos = [scm.make_repository_spec(repo_name, origin=origin)
                 for repo_name, origin in self.ORIGIN_URLS.items()]
    expect = {
        repository.name: (repository, pos_args, kwargs)
        for repository in all_repos
    }

    def _foreach_func(repository, *pos_args, **kwargs):
      return (repository, list(pos_args), dict(kwargs))
    got = scm.foreach_source_repository(
        all_repos, _foreach_func, *pos_args, **kwargs)
    self.assertEquals(expect, got)


if __name__ == '__main__':
  init_runtime()
  unittest.main(verbosity=2)
