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
import os
import unittest

from buildtool import (
    GitRunner,
    BomSourceCodeManager,
    SemanticVersion)

from test_util import (
    ALL_STANDARD_TEST_BOM_REPO_NAMES,
    BASE_VERSION_TAG,
    PATCH_BRANCH,
    BaseGitRepoTestFixture,
    init_runtime)


def _foreach_func(repository, *pos_args, **kwargs):
  return (repository, list(pos_args), dict(kwargs))


class TestBomSourceCodeManager(BaseGitRepoTestFixture):
  def make_test_options(self):
    options = super(TestBomSourceCodeManager, self).make_test_options()
    parser = argparse.ArgumentParser()
    parser.add_argument('--output_dir', default=options.output_dir)
    GitRunner.add_parser_args(parser, {'github_owner': 'test_github_owner'})
    return parser.parse_args()

  def test_pull_bom(self):
    input_dir = self.test_root
    scm = BomSourceCodeManager(self.options, input_dir, bom=self.golden_bom)
    for repo_name in ALL_STANDARD_TEST_BOM_REPO_NAMES:
      repository = scm.make_repository_spec(repo_name)
      self.assertFalse(os.path.exists(repository.git_dir))
      scm.ensure_local_repository(repository)
      self.assertTrue(os.path.exists(repository.git_dir))

      git_dir = repository.git_dir
      spec = scm.git.determine_git_repository_spec(git_dir)
      self.assertEquals(repository.name, spec.name)
      self.assertEquals(repository.git_dir, spec.git_dir)
      self.assertEquals(repository.origin, spec.origin)
      self.assertIsNone(spec.upstream_or_none())

      repo_name = repository.name
      at_commit = scm.git.query_local_repository_commit_id(git_dir)
      self.assertEquals(
          self.repo_commit_map[repo_name]['ORIGIN'], repository.origin)
      self.assertEquals(
          self.repo_commit_map[repo_name][PATCH_BRANCH], at_commit)

      summary = scm.git.collect_repository_summary(git_dir)
      semver = SemanticVersion.make(BASE_VERSION_TAG)
      expect_version = semver.next(
          SemanticVersion.PATCH_INDEX).to_version()
      self.assertEquals(expect_version, summary.version)


if __name__ == '__main__':
  init_runtime()
  unittest.main(verbosity=2)
