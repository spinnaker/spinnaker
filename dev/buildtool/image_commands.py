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

"""Implements imaging commands for buildtool.

This uses existing external scripts so the configuration and artifact
handling/generation is not consistent with the rest of the tool.
"""

import os
import re

from buildtool.command import (
    RepositoryCommandFactory,
    RepositoryCommandProcessor)

from buildtool.git import (
    RemoteGitRepository,
    GitRunner)

from buildtool.source_code_manager import (
    SPINNAKER_RUNNABLE_REPOSITORIES)

from buildtool.util import (
    add_parser_argument,
    check_subprocesses_to_logfile,
    determine_logfile_path,
    ensure_options_set)


# See BuildGceComponentImages class comment as to why these are "repositories"
ALL_IMAGE_REPOSITORIES = dict(SPINNAKER_RUNNABLE_REPOSITORIES)
ALL_IMAGE_REPOSITORIES.update({
    name: RemoteGitRepository.make_from_url('https://placeholder/' + name)
    for name in ['consul', 'redis', 'vault']})


class BuildGceComponentImages(RepositoryCommandProcessor):
  """Builds GCE VM images for each of the runtime components.

  Although we are calling this a RepositoryCommandProcessor, it
  isnt really processing repositories. Some of the images we generate
  are not associated with github or repos at all (e.g. redis). However
  the RepositoryCommandProcessor is a useful abstraction for its ability
  to parallelize across the different subsystems so we're going to overload
  it and pretend we have repositories even though we'll never do anything
  with their urls.

  TODO(ewiseblatt): 20180103
  Generalize RepositoryCommandProcessor to some kind of queued processor
  where repository command processor queues repositories but this would queue
  the image specs to build.
  """

  def __init__(self, factory, options, **kwargs):
    ensure_options_set(
        options,
        ['build_gce_service_account',
         'build_gce_project',
         'publish_gce_image_project'])

    # See class note as to why these are "repositories"
    source_repositories = kwargs.pop(
        'source_repositories', ALL_IMAGE_REPOSITORIES)
    super(BuildGceComponentImages, self).__init__(
        factory, options, source_repositories=source_repositories, **kwargs)

  def __determine_repo_install_args(self):
    """Determine --spinnaker_dev-github_[owner|user] args for install script."""
    options = self.options
    branch = options.git_branch
    owner = ('spinnaker'
             if options.github_user in ('default', 'upstream')
             else options.github_user)
    git_dir = os.path.dirname(__file__)
    if not branch:
      branch = GitRunner(options).query_local_repository_branch(git_dir)
    if not owner:
      url = (GitRunner(options)
             .determine_remote_git_repository(git_dir)
             .url)
      owner = re.search('github.com/(.+)/spinnaker', url).group(1)
    return [
        '--spinnaker_dev_github_owner', owner,
        '--spinnaker_dev_github_branch', branch
    ]

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    name = repository.name
    build_component_image_sh = os.path.join(
        os.path.dirname(__file__), '..', 'build_google_component_image.sh')

    options = self.options
    command_line = [
        build_component_image_sh,
        '--artifact ', name,
        '--account', options.build_gce_service_account,
        '--build_project', options.build_gce_project,
        '--install_script', options.install_image_script,
        '--publish_project', options.publish_gce_image_project,
        '--publish_script', options.publish_gce_image_script,
        '--version', options.spinnaker_version,
        '--zone', options.build_gce_zone]
    command_line.extend(self.__determine_repo_install_args())

    if options.build_bintray_repository:
      bintray_url = 'https://dl.bintray.com/' + options.build_bintray_repository
      extra_install_args = [
          '--halyard_repository', bintray_url,
          '--spinnaker_repository', bintray_url]
      command_line.extend(['--extra_install_script_args',
                           '"{0}"'.format(' '.join(extra_install_args))])

    command = ' '.join(command_line)
    logfile = determine_logfile_path(options, name, 'gce-image')

    what = '{name} component image'.format(name=name)
    check_subprocesses_to_logfile(what, logfile, [command])
    return what


class BuildGceComponentImagesFactory(RepositoryCommandFactory):
  """Builds GCE VM images for each of the runtime components."""

  def __init__(self):
    super(BuildGceComponentImagesFactory, self).__init__(
        'build_gce_component_images', BuildGceComponentImages,
        'Build Google Compute Engine VM Images For Each Service.')

  @staticmethod
  def add_bom_parser_args(parser, defaults):
    """Adds arguments shared with creating boms."""
    if hasattr(parser, 'added_gce_image_project'):
      return
    parser.added_gce_image_project = True

    add_parser_argument(
        parser, 'publish_gce_image_project', defaults, None,
        help='Project to publish images to.')

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildGceComponentImagesFactory, self)._do_init_argparser(
        parser, defaults)
    self.add_argument(
        parser, 'build_gce_service_account', defaults, None,
        help='Service account for building images.')
    self.add_argument(
        parser, 'build_gce_project', defaults, None,
        help='Project to build scratch image in.')
    self.add_argument(
        parser, 'build_gce_zone', defaults, 'us-central1-f',
        help='Zone to build scratch image in.')
    self.add_argument(
        parser, 'github_user', defaults, None,
        help='Github user repository to get install scripts from.'
             ' If none, then use the source repo that this script'
             ' is running from.')
    self.add_argument(
        parser, 'git_branch', defaults, None,
        help='Github branch to get install scripts from.'
             ' If none, then use the source repo branch that this script'
             ' is running from.')

    self.add_argument(
        parser, 'build_bintray_repository', defaults, '',
        help='Repository where built debians were placed.')

    halyard_install_sh = os.path.join(
        os.path.dirname(__file__), '..', 'halyard_install_component.sh')
    self.add_argument(
        parser, 'install_image_script', defaults, halyard_install_sh,
        help='Script for installing images.')

    publish_image_sh = os.path.join(
        os.path.dirname(__file__), '..', '..', 'google', 'dev',
        'publish_gce_release.sh')
    self.add_argument(
        parser, 'publish_gce_image_script', defaults, publish_image_sh,
        help='Script for publishing images to a project.')
    self.add_argument(
        parser, 'spinnaker_version', defaults, None, required=True,
        help='The spinnaker version to publish for.')


def add_bom_parser_args(parser, defaults):
  """Adds parser arguments pertaining to publishing boms."""
  BuildGceComponentImagesFactory.add_bom_parser_args(parser, defaults)


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  BuildGceComponentImagesFactory().register(registry, subparsers, defaults)
