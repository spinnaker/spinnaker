# Copyright 2018 Google Inc. All Rights Reserved.
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

import argparse
import datetime
import os
import tempfile
import textwrap
import unittest

import yaml

import buildtool.__main__ as bomtool_main
import buildtool.spinnaker_commands
from buildtool import (
    GitRunner)

from test_util import (
    EXTRA_REPO,
    init_runtime,
    BaseGitRepoTestFixture)


class TestSpinnakerCommandFixture(BaseGitRepoTestFixture):
  def setUp(self):
    super(TestSpinnakerCommandFixture, self).setUp()
    self.parser = argparse.ArgumentParser()
    self.subparsers = self.parser.add_subparsers(title='command', dest='command')

  def test_new_release_branch_command(self):
    defaults = {
        'input_dir': self.options.input_dir,
        'output_dir': self.options.output_dir,

        'only_repositories': EXTRA_REPO,
        'github_owner': 'default',
        'git_branch': EXTRA_REPO + '-branch',

        'spinnaker_version': 'NewSpinnakerVersion',
        'github_filesystem_root': self.options.github_filesystem_root,
        'github_hostname': self.options.github_hostname
    }

    registry = {}
    bomtool_main.add_standard_parser_args(self.parser, defaults)
    buildtool.spinnaker_commands.register_commands(
        registry, self.subparsers, defaults)

    factory = registry['new_release_branch']
    factory.init_argparser(self.parser, defaults)

    options = self.parser.parse_args(['new_release_branch'])

    mock_push_tag = self.patch_method(GitRunner, 'push_tag_to_origin')
    mock_push_branch = self.patch_method(GitRunner, 'push_branch_to_origin')

    command = factory.make_command(options)
    command()

    base_git_dir = os.path.join(options.input_dir, 'new_release_branch')
    self.assertEquals(os.listdir(base_git_dir), [EXTRA_REPO])
    git_dir = os.path.join(base_git_dir, EXTRA_REPO)
    self.assertEquals(
        GitRunner(options).query_local_repository_commit_id(git_dir),
        self.repo_commit_map[EXTRA_REPO][EXTRA_REPO + '-branch'])

    mock_push_branch.assert_called_once_with(git_dir, 'NewSpinnakerVersion')
    self.assertEquals(0, mock_push_tag.call_count)


if __name__ == '__main__':
  init_runtime()
  unittest.main(verbosity=2)
