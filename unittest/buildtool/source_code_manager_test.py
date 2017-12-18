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

import os
import shutil
import tempfile
import unittest

from buildtool.git import (
    GitRunner,
    RemoteGitRepository,
    SemanticVersion)
from buildtool.source_code_manager import SpinnakerSourceCodeManager
from buildtool.util import check_subprocess_sequence


SCM_USER = 'scm_user'
TEST_USER = 'test_user'

BASE_VERSION = 'version-7.8.9'
UNTAGGED_BRANCH = 'untagged-branch'


def _foreach_func(repository, *pos_args, **kwargs):
  return (repository, list(pos_args), dict(kwargs))


class TestSourceCodeManager(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    cls.git = GitRunner()
    cls.base_temp_dir = tempfile.mkdtemp(prefix='scm_test')
    origin_root = os.path.join(cls.base_temp_dir, 'origin_repos')

    repository_list = [
        RemoteGitRepository.make_from_url(url)
        for url in [
            os.path.join(origin_root, SCM_USER, 'RepoOne'),
            os.path.join(origin_root, SCM_USER, 'RepoTwo'),
            os.path.join(origin_root, TEST_USER, 'RepoTest')]
    ]

    cls.TEST_SOURCE_REPOSITORIES = {
        repo.name: repo
        for repo in repository_list
    }

    for repo in repository_list:
      os.makedirs(repo.url)
      base_file = os.path.join(
          repo.url, '{name}-base.txt'.format(name=repo.name))
      unique_file = os.path.join(
          repo.url, '{name}-unique.txt'.format(name=repo.name))
      untagged_file = os.path.join(
          repo.url, '{name}-untagged.txt'.format(name=repo.name))

      logging.debug('Initializing repository %s', repo.url)
      git_prefix = 'git -C "{dir}" '.format(dir=repo.url)
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
          run_git('checkout -b {name}-branch'.format(name=repo.name)),
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

  def test_get_local_repository_path(self):
    test_root = os.path.join(self.base_temp_dir, 'unused_source')
    scm = SpinnakerSourceCodeManager(
        self.git, test_root, self.TEST_SOURCE_REPOSITORIES)

    tests = ['RepoOne', 'RepoTwo', 'RepoTest', 'DoesNotExist']
    for repo_name in tests:
      expect = os.path.join(test_root, repo_name)
      self.assertEquals(expect,
                        scm.get_local_repository_path(repo_name))
      self.assertFalse(os.path.exists(expect))

  def test_maybe_pull_unknown_branch(self):
    test_root = os.path.join(self.base_temp_dir, 'unknown_branch')
    git = self.git
    scm = SpinnakerSourceCodeManager(
        git, test_root, self.TEST_SOURCE_REPOSITORIES)

    repository = self.TEST_SOURCE_REPOSITORIES['RepoOne']
    branch = 'XYZ'
    regexp = r"Branches \['{branch}'\] do not exist in {url}\.".format(
        branch=branch, url=repository.url)

    with self.assertRaisesRegexp(Exception, regexp):
      scm.maybe_pull_repository_source(repository, git_branch=branch)

  def test_maybe_pull_repository_branch(self):
    test_root = os.path.join(self.base_temp_dir, 'pulled_test')
    git = self.git
    scm = SpinnakerSourceCodeManager(
        git, test_root, self.TEST_SOURCE_REPOSITORIES)

    for repository in self.TEST_SOURCE_REPOSITORIES.values():
      scm.maybe_pull_repository_source(
          repository, git_branch=UNTAGGED_BRANCH)
      git_dir = scm.get_local_repository_path(repository.name)

      remote_git = git.determine_remote_git_repository(
          git_dir)
      self.assertEquals(repository, remote_git)

      in_branch = git.query_local_repository_branch(git_dir)
      self.assertEquals(UNTAGGED_BRANCH, in_branch)

      summary = git.collect_repository_summary(git_dir)
      semver = SemanticVersion.make(BASE_VERSION)
      expect_version = semver.next(
          SemanticVersion.MINOR_INDEX).to_version()

      self.assertEquals(expect_version, summary.version)

  def test_pull_repository_fallback_branch(self):
    test_root = os.path.join(self.base_temp_dir, 'fallback_test')
    git = self.git
    scm = SpinnakerSourceCodeManager(
        git, test_root, self.TEST_SOURCE_REPOSITORIES)

    unique_branch = 'RepoTwo-branch'
    for repository in self.TEST_SOURCE_REPOSITORIES.values():
      scm.maybe_pull_repository_source(
          repository,
          git_branch=unique_branch,
          default_branch='master')
      git_dir = scm.get_local_repository_path(repository.name)
      want_branch = (unique_branch
                     if repository.name == 'RepoTwo'
                     else 'master')
      in_branch = git.query_local_repository_branch(git_dir)
      self.assertEquals(want_branch, in_branch)

  def test_foreach_repo(self):
    test_root = os.path.join(self.base_temp_dir, 'foreach_test')
    git = self.git
    pos_args = [1, 2, 3]
    kwargs = {'a': 'A', 'b': 'B'}

    expect = {
        repository.name: (repository, pos_args, kwargs)
        for repository in self.TEST_SOURCE_REPOSITORIES.values()
    }

    scm = SpinnakerSourceCodeManager(
        git, test_root, self.TEST_SOURCE_REPOSITORIES)
    got = scm.foreach_source_repository(
        _foreach_func, *pos_args, **kwargs)
    self.assertEquals(expect, got)


if __name__ == '__main__':
  import logging
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.INFO)

  unittest.main(verbosity=2)
