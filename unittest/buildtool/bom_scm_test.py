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
import yaml

from buildtool import (
    GitRunner,
    BomSourceCodeManager,
    check_subprocess_sequence)

from test_util import init_runtime


SCM_USER = 'scm_user'
TEST_USER = 'test_user'

BASE_VERSION = 'version-7.8.9'
UNTAGGED_BRANCH = 'untagged-branch'


def _foreach_func(repository, *pos_args, **kwargs):
  return (repository, list(pos_args), dict(kwargs))


def make_default_options():
  """Helper function for creating default options for runner."""
  parser = argparse.ArgumentParser()
  parser.add_argument('--output_dir',
                      default=os.path.join('/tmp', 'scmtest.%d' % os.getpid()))
  GitRunner.add_parser_args(parser, {'github_owner': 'test_github_owner'})
  return parser.parse_args([])


class TestBomSourceCodeManager(unittest.TestCase):
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

  def test_pull_bom(self):
    # Boms contain more stuff, but this test doesnt need it.
    input_dir = os.path.join(self.base_temp_dir, 'bom_test')
    origin_root = os.path.join(self.base_temp_dir, 'origin_repos')
    bom = {
        'artifactSources': {
            'gitBranch': 'master',
            'gitPrefix': os.path.join(self.base_temp_dir,
                                      'origin_repos', SCM_USER)
        },
        'services': {}
    }

    git = GitRunner(self.options)
    git_dir = os.path.join(origin_root, SCM_USER, 'RepoOne')
    git.check_run(git_dir, 'checkout ' + BASE_VERSION)
    bom['services']['RepoOne'] = {
        'version': 'BogusVersion',
        'commit': git.query_local_repository_commit_id(git_dir)
    }

    git_dir = os.path.join(origin_root, TEST_USER, 'RepoTest')
    git.check_run(git_dir, 'checkout ' + UNTAGGED_BRANCH)
    bom['services']['RepoTest'] = {
        'version': 'BogusVersion',
        'commit': git.query_local_repository_commit_id(git_dir),
        'gitPrefix': os.path.join(origin_root, TEST_USER),
        'gitBranch': UNTAGGED_BRANCH
    }

    bom_path = os.path.join(self.base_temp_dir, 'test_bom.yml')
    with open(bom_path, 'w') as stream:
      yaml.dump(bom)
    options = make_default_options()
    options.bom_path = bom_path
    options.github_owner = None

    scm = BomSourceCodeManager(options, input_dir, bom=bom)
    repository = scm.make_repository_spec('RepoOne')
    self.assertEquals(repository.origin,
                      os.path.join(origin_root, SCM_USER, 'RepoOne'))
    self.assertEquals(repository.git_dir,
                      os.path.join(input_dir, 'RepoOne'))
    self.assertFalse(os.path.exists(repository.git_dir))
    scm.ensure_local_repository(repository)
    self.assertTrue(os.path.exists(repository.git_dir))
    self.assertEquals(bom['services']['RepoOne']['commit'],
                      git.query_local_repository_commit_id(repository.git_dir))

    repository = scm.make_repository_spec('RepoTest')
    self.assertEquals(repository.origin,
                      os.path.join(origin_root, TEST_USER, 'RepoTest'))
    self.assertEquals(repository.git_dir,
                      os.path.join(input_dir, 'RepoTest'))
    self.assertFalse(os.path.exists(repository.git_dir))
    scm.ensure_local_repository(repository)
    self.assertTrue(os.path.exists(repository.git_dir))
    self.assertEquals(bom['services']['RepoTest']['commit'],
                      git.query_local_repository_commit_id(repository.git_dir))


if __name__ == '__main__':
  init_runtime()
  unittest.main(verbosity=2)
