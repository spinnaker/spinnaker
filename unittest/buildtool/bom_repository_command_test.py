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

import os
import shutil
import tempfile
import unittest
import yaml

from buildtool import (
    BomSourceCodeManager,
    RepositoryCommandProcessor,
    RepositoryCommandFactory,
    write_to_path)

from test_util import (
    BaseGitRepoTestFixture,
    ALL_STANDARD_TEST_BOM_REPO_NAMES,
    PATCH_BRANCH,
    init_runtime)


class TestBomRepositoryCommand(RepositoryCommandProcessor):
  def __init__(self, *pos_args, **kwargs):
    super(TestBomRepositoryCommand, self).__init__(*pos_args, **kwargs)
    self.summary_info = {}

  def _do_repository(self, repository):
    name = repository.name
    assert(name not in self.summary_info)
    self.summary_info[name] = self.scm.git.collect_repository_summary(
        repository.git_dir)


class TestBomRepositoryCommandProcessor(BaseGitRepoTestFixture):
  def make_test_options(self):
    options = super(TestBomRepositoryCommandProcessor, self).make_test_options()
    options.bom_path = os.path.join(self.test_root, 'bom.yml')
    options.one_at_a_time = False
    options.only_repositories = None
    options.exclude_repositories = None
    options.github_disable_upstream_push = True
    options.git_branch = PATCH_BRANCH
    write_to_path(yaml.dump(self.golden_bom), options.bom_path)
    return options

  def test_repository_command(self):
    options = self.options

    # Create a command referencing our test bom
    # That will learn about our test service through that bom
    factory = RepositoryCommandFactory(
        'TestBomRepositoryCommand', TestBomRepositoryCommand,
        'A test command.', BomSourceCodeManager)
    command = factory.make_command(options)

    for repository in command.source_repositories:
      self.assertEquals(repository.origin,
                        self.repo_commit_map[repository.name]['ORIGIN'])
      self.assertEquals(repository.git_dir,
                        os.path.join(options.input_dir, options.command,
                                     repository.name))
      self.assertFalse(os.path.exists(repository.git_dir))
    self.assertEquals(set(ALL_STANDARD_TEST_BOM_REPO_NAMES),
                      set([repo.name for repo in command.source_repositories]))

    # Now run the command and verify it instantiated the working dir
    # as expected.
    command()

    for repository in command.source_repositories:
      self.assertTrue(os.path.exists(repository.git_dir))
      self.assertEquals(
          command.summary_info[repository.name].commit_id,
          self.repo_commit_map[repository.name][PATCH_BRANCH])


if __name__ == '__main__':
  init_runtime()
  unittest.main(verbosity=2)
