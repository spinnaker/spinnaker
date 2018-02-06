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

"""Implements debian support commands for buildtool."""

import os

from buildtool import (
    BomSourceCodeManager,
    GradleCommandProcessor,
    GradleCommandFactory,

    check_options_set,
    raise_and_log_error,
    ConfigError)


class BuildDebianCommand(GradleCommandProcessor):
  def __init__(self, factory, options, **kwargs):
    options.github_disable_upstream_push = True
    super(BuildDebianCommand, self).__init__(
        factory, options, max_threads=options.max_local_builds, **kwargs)

    if not os.environ.get('BINTRAY_KEY'):
      raise_and_log_error(ConfigError('Expected BINTRAY_KEY set.'))
    if not os.environ.get('BINTRAY_USER'):
      raise_and_log_error(ConfigError('Expected BINTRAY_USER set.'))
    check_options_set(
        options, ['bintray_org', 'bintray_jar_repository',
                  'bintray_debian_repository'])

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    if self.gradle.consider_debian_on_bintray(repository):
      return

    options = self.options
    name = repository.name
    args = self.gradle.get_common_args()
    if options.gradle_cache_path:
      args.append('--gradle-user-home=' + options.gradle_cache_path)

    if (not options.run_unit_tests
        or (name == 'deck' and not 'CHROME_BIN' in os.environ)):
      args.append('-x test')

    if not options.run_unit_tests and name == 'orca':
      args.append('-x junitPlatformTest')
      # This second one is only for 1.5.x
      # args.append('-x generateHtmlTestReports')
    args.extend(self.gradle.get_debian_args('trusty,xenial'))

    self.gradle.check_run(args, self, repository, 'candidate', 'debian-build')


def add_bom_parser_args(parser, defaults):
  """Adds parser arguments pertaining to publishing boms."""
  # These are implemented by the gradle factory, but conceptually
  # for debians, so are exported this way.
  GradleCommandFactory.add_bom_parser_args(parser, defaults)


def register_commands(registry, subparsers, defaults):
  build_debian_factory = GradleCommandFactory(
      'build_debians', BuildDebianCommand,
      'Build one or more debian packages from the local git repository.',
      BomSourceCodeManager)

  build_debian_factory.register(registry, subparsers, defaults)
