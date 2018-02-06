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

"""Implements rpm support commands for buildtool."""

from buildtool import (
    BomSourceCodeManager,
    GradleCommandProcessor,
    GradleCommandFactory)


class BuildRpmCommand(GradleCommandProcessor):
  def __init__(self, factory, options, **kwargs):
    super(BuildRpmCommand, self).__init__(
        factory, options, max_threads=options.max_local_builds, **kwargs)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    args = self.gradle.get_common_args()
    if self.options.gradle_cache_path:
      args.append('--gradle-user-home=' + self.options.gradle_cache_path)

    self.gradle.check_run(args, self, repository, 'buildRpm', 'rpm-build')


def register_commands(registry, subparsers, defaults):
  build_rpm_factory = GradleCommandFactory(
      'build_rpms', BuildRpmCommand,
      'Build one or more rpm packages from the local git repository.',
      BomSourceCodeManager)

  build_rpm_factory.register(registry, subparsers, defaults)
