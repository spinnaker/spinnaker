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

import logging
import os
import shutil
import tempfile
import unittest
import yaml
from mock import patch

from buildtool import (
    check_subprocess_sequence,
    check_subprocess,
    MetricsManager)


def init_runtime(options=None):
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  if not options:
    class Options(object):
      pass
    options = Options()
    options.metric_name_scope = 'unittest'
    options.monitoring_flush_frequency = -1
    options.monitoring_system = 'file'
    options.monitoring_enabled = False

  MetricsManager.startup_metrics(options)


#  These are used to define the standard test repositories
NORMAL_SERVICE = 'gate'
NORMAL_REPO = NORMAL_SERVICE
OUTLIER_SERVICE = 'monitoring-daemon'
OUTLIER_REPO = 'spinnaker-monitoring'
EXTRA_REPO = 'spinnaker.github.io'
EXTRA_SERVICE = EXTRA_REPO

STANDARD_GIT_HOST = 'test-gitserver'
OUTLIER_GIT_HOST = STANDARD_GIT_HOST
STANDARD_GIT_OWNER = 'spinnaker'
OUTLIER_GIT_OWNER = 'spinnaker'

BASE_VERSION_TAG = 'version-7.8.9'
PATCH_VERSION_TAG = 'version-7.8.10'
PATCH_VERSION_NUMBER = '7.8.10'
PATCH_BRANCH = 'patch'
UNTAGGED_BRANCH = 'untagged-branch'


def make_standard_git_repo(git_dir):
  """Initialize local standard test repos.

  These are used by tests that interact with a git repository.
  """
  branch_commits = {'ORIGIN': git_dir}
  repo_name = os.path.basename(git_dir)

  run_git = lambda cmd: 'git %s' % cmd
  os.makedirs(git_dir)
  logging.debug('Initializing git repository in "%s"', git_dir)

  check_subprocess_sequence(
      [
          'touch  %s-basefile.txt' % repo_name,
          run_git('init'),
          run_git('add %s-basefile.txt' % repo_name),
          run_git('commit -a -m "feat(first): first commit"'),
          run_git('tag %s HEAD' % BASE_VERSION_TAG),
      ],
      cwd=git_dir)
  branch_commits['master'] = check_subprocess('git rev-parse HEAD', cwd=git_dir)

  check_subprocess_sequence(
      [
          run_git('checkout -b ' + PATCH_BRANCH),
          'touch %s-patchfile.txt' % repo_name,
          run_git('add %s-patchfile.txt' % repo_name),
          run_git('commit -a -m "fix(patch): added patch change"')
      ],
      cwd=git_dir)
  branch_commits[PATCH_BRANCH] = check_subprocess(
      'git rev-parse HEAD', cwd=git_dir)

  check_subprocess_sequence(
      [
          run_git('checkout master'),
          run_git('checkout -b %s-branch' % repo_name),
          'touch %s-unique.txt' % repo_name,
          run_git('add %s-unique.txt' % repo_name),
          run_git('commit -a -m "chore(uniq): unique commit"')
      ],
      cwd=git_dir)
  branch_commits['%s-branch' % repo_name] = check_subprocess(
      'git rev-parse HEAD', cwd=git_dir)

  check_subprocess_sequence(
      [
          run_git('checkout master'),
          run_git('checkout -b %s' % UNTAGGED_BRANCH),
          'touch %s-untagged.txt' % repo_name,
          run_git('add %s-untagged.txt' % repo_name),
          run_git('commit -a -m "chore(uniq): untagged commit"'),
      ],
      cwd=git_dir)
  branch_commits[UNTAGGED_BRANCH] = check_subprocess(
      'git rev-parse HEAD', cwd=git_dir)

  return branch_commits

ALL_STANDARD_TEST_REPO_NAMES = [NORMAL_REPO, OUTLIER_REPO, EXTRA_REPO]
ALL_STANDARD_TEST_BOM_REPO_NAMES = [NORMAL_REPO, OUTLIER_REPO]

def make_all_standard_git_repos(base_dir):
  """Creates git repositories for each of the standard test repos."""

  result = {}

  path = os.path.join(base_dir, STANDARD_GIT_HOST, STANDARD_GIT_OWNER,
                      NORMAL_REPO)
  result[NORMAL_REPO] = make_standard_git_repo(path)

  path = os.path.join(base_dir, STANDARD_GIT_HOST, STANDARD_GIT_OWNER,
                      EXTRA_REPO)
  result[EXTRA_REPO] = make_standard_git_repo(path)

  path = os.path.join(base_dir, OUTLIER_GIT_HOST, OUTLIER_GIT_OWNER,
                      OUTLIER_REPO)
  result[OUTLIER_REPO] = make_standard_git_repo(path)

  return result


class BaseGitRepoTestFixture(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    logging.debug('BEGIN setUpClass %s', cls.__name__)
    cls.base_temp_dir = tempfile.mkdtemp(prefix='bom_scm_test')
    cls.repo_commit_map = make_all_standard_git_repos(cls.base_temp_dir)
    source_path = os.path.join(os.path.dirname(__file__),
                               'standard_test_bom.yml')

    # Adjust the golden bom so it references the details of
    # the test instance specific origin repo we just created in test_util.
    with open(source_path, 'r') as stream:
      cls.golden_bom = yaml.load(stream.read())

      #  Change the bom's default gitPrefix to our origin root
      cls.golden_bom['artifactSources']['gitPrefix'] = (
          os.path.dirname(cls.repo_commit_map[NORMAL_REPO]['ORIGIN']))

      # Update the service commit id's in the BOM to the actual id's
      # so we can check them out later.
      services = cls.golden_bom['services']
      for name, entry in services.items():
        repo_name = name
        if name in ['monitoring-third-party', 'monitoring-daemon']:
          repo_name = name = 'spinnaker-monitoring'
        if name == OUTLIER_SERVICE:
          repo_name = OUTLIER_REPO
        entry['commit'] = cls.repo_commit_map[repo_name][PATCH_BRANCH]
    logging.debug('FINISH setUpClass %s', cls.__name__)

  @classmethod
  def tearDownClass(cls):
    shutil.rmtree(cls.base_temp_dir)

  @classmethod
  def to_origin(cls, repo_name):
    return cls.repo_commit_map[repo_name]['ORIGIN']

  def patch_function(self, name):
    patcher = patch(name)
    hook = patcher.start()
    self.addCleanup(patcher.stop)
    return hook

  def patch_method(self, klas, method):
    patcher = patch.object(klas, method)
    hook = patcher.start()
    self.addCleanup(patcher.stop)
    return hook

  def make_test_options(self):
    class Options(object):
      pass
    options = Options()
    options.command = self._testMethodName
    options.input_dir = os.path.join(self.test_root, 'input_dir')
    options.output_dir = os.path.join(self.test_root, 'output_dir')
    options.github_hostname = STANDARD_GIT_HOST
    return options

  def setUp(self):
    self.test_root = os.path.join(self.base_temp_dir, self._testMethodName)
    self.options = self.make_test_options()
    self.options.github_filesystem_root = self.base_temp_dir
