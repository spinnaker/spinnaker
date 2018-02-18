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

import logging
import os
import re

from buildtool import (
    SPINNAKER_RUNNABLE_REPOSITORY_NAMES,

    BomSourceCodeManager,
    RepositoryCommandFactory,
    RepositoryCommandProcessor,

    GitRepositorySpec,
    GitRunner,
    HalRunner,

    add_parser_argument,
    check_subprocess,
    check_subprocesses_to_logfile,
    check_options_set,
    raise_and_log_error,
    ConfigError,
    UnexpectedError)


# TODO(ewiseblatt): 20180203
# Really these should come from "bom_dependencies_path" file
# so that it is extendable
EXTRA_REPO_NAMES = ['consul', 'redis', 'vault']

class BuildGceComponentImages(RepositoryCommandProcessor):
  """Builds GCE VM images for each of the runtime components.

  Although we are calling this a RepositoryCommandProcessor, it
  isnt really processing repositories. Some of the images we build
  are not associated with github or repos at all (e.g. redis). However
  the RepositoryCommandProcessor is a useful abstraction for its ability
  to parallelize across the different subsystems so we're going to overload
  it and pretend we have repositories even though we'll never do anything
  with their urls.
  """

  def _do_determine_source_repositories(self):
    """Implements RepositoryCommandProcessor interface."""
    # These arent actually used, just the names.
    repositories = [self.source_code_manager.make_repository_spec(name)
                    for name in SPINNAKER_RUNNABLE_REPOSITORY_NAMES]
    repositories.extend([
        GitRepositorySpec(name) for name in EXTRA_REPO_NAMES])
    return repositories

  def __init__(self, factory, options, **kwargs):
    check_options_set(
        options,
        ['build_gce_service_account',
         'build_gce_project',
         'publish_gce_image_project'])

    options.github_disable_upstream_push = True
    super(BuildGceComponentImages, self).__init__(factory, options, **kwargs)

  def __determine_repo_install_args(self, repository):
    """Determine --spinnaker_dev-github_[owner|user] args for install script."""
    options = self.options
    branch = options.git_branch
    owner = ('spinnaker'
             if options.github_owner in ('default', 'upstream')
             else options.github_owner)
    git_dir = os.path.dirname(__file__)
    if not branch:
      branch = GitRunner(options).query_local_repository_branch(git_dir)
    if not owner:
      url = repository.origin
      match = re.search('github.com/([^/]+)/', url)
      if not match:
        raise_and_log_error(
            UnexpectedError('Cannot determine owner from url=%s' % url,
                            cause='BadUrl'))
      owner = match.group(1)
    return [
        '--spinnaker_dev_github_owner', owner,
        '--spinnaker_dev_github_branch', branch
    ]

  def have_image(self, repository):
    """Determine if we already have an image for the repository or not."""
    bom = self.source_code_manager.bom
    dependencies = bom['dependencies']
    services = bom['services']
    service_name = self.scm.repository_name_to_service_name(repository.name)
    if service_name in dependencies:
      build_version = dependencies[service_name]['version']
    else:
      build_version = services[service_name]['version']

    options = self.options
    image_name = 'spinnaker-{repo}-{version}'.format(
        repo=repository.name,
        version=build_version.replace('.', '-').replace(':', '-'))
    lookup_command = ['gcloud', '--account', options.build_gce_service_account,
                      'compute', 'images', 'list', '--filter', image_name,
                      '--project', options.build_gce_project,
                      '--quiet', '--format=json']
    logging.debug('Checking for existing image for "%s"', repository.name)
    got = check_subprocess(' '.join(lookup_command))
    if got.strip() == '[]':
      return False
    labels = {'repository': repository.name, 'artifact': 'gce-image'}
    if self.options.skip_existing:
      logging.info('Already have %s -- skipping build', image_name)
      self.metrics.inc_counter('ReuseArtifact', labels,
                               'Kept existing GCE image.')
      return True
    if not self.options.delete_existing:
      raise_and_log_error(
          ConfigError('Already have image "{name}"'.format(name=image_name)))

    delete_command = ['gcloud', '--account', options.gcb_service_account,
                      'compute', 'images', 'delete', image_name,
                      '--project', options.build_gce_project,
                      '--quiet']
    logging.debug('Deleting existing image %s', image_name)
    self.metrics.count_call(
        'DeleteArtifact', labels,
        'Attempts to delete existing GCE images.',
        check_subprocess, ' '.join(delete_command))

  def ensure_local_repository(self, repository):
    """Local repositories are used to get version information."""
    if repository.name in EXTRA_REPO_NAMES:
      return None
    return super(BuildGceComponentImages, self).ensure_local_repository(
        repository)

  def _do_can_skip_repository(self, repository):
    if not repository.name in SPINNAKER_RUNNABLE_REPOSITORY_NAMES:
      logging.debug('%s does not build a GCE component image -- skip',
                    repository.name)
      return True

    if self.have_image(repository):
      return

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""

    name = repository.name
    build_component_image_sh = os.path.join(
        os.path.dirname(__file__), '..', 'build_google_component_image.sh')

    options = self.options
    bom_version = self.source_code_manager.determine_bom_version()
    command_line = [
        build_component_image_sh,
        '--artifact ', name,
        '--account', options.build_gce_service_account,
        '--build_project', options.build_gce_project,
        '--install_script', options.install_image_script,
        '--publish_project', options.publish_gce_image_project,
        '--publish_script', options.publish_gce_image_script,
        '--version', bom_version,
        '--zone', options.build_gce_zone]
    command_line.extend(self.__determine_repo_install_args(repository))

    extra_install_args = []
    if options.halyard_bom_bucket:
      extra_install_args.extend(
          ['--halyard_config_bucket', options.halyard_bom_bucket])

    if options.bintray_debian_repository:
      bintray_url = 'https://dl.bintray.com/{org}/{repo}'.format(
          org=options.bintray_org,
          repo=options.bintray_debian_repository)
      extra_install_args.extend([
          '--release_track', options.halyard_release_track,
          '--halyard_repository', bintray_url,
          '--spinnaker_repository', bintray_url])

    if extra_install_args:
      command_line.extend(['--extra_install_script_args',
                           '"{0}"'.format(' '.join(extra_install_args))])

    command = ' '.join(command_line)
    logfile = self.get_logfile_path(name + '-gce-image')

    what = '{name} component image'.format(name=name)
    check_subprocesses_to_logfile(what, logfile, [command])
    return what


class BuildGceComponentImagesFactory(RepositoryCommandFactory):
  """Builds GCE VM images for each of the runtime components."""

  def __init__(self):
    super(BuildGceComponentImagesFactory, self).__init__(
        'build_gce_component_images', BuildGceComponentImages,
        'Build Google Compute Engine VM Images For Each Service.',
        BomSourceCodeManager)

  @staticmethod
  def add_bom_parser_args(parser, defaults):
    """Adds arguments shared with creating boms."""
    if hasattr(parser, 'added_gce_image_project'):
      return
    parser.added_gce_image_project = True

    add_parser_argument(
        parser, 'publish_gce_image_project', defaults, None,
        help='Project to publish images to.')

  def init_argparser(self, parser, defaults):
    super(BuildGceComponentImagesFactory, self).init_argparser(
        parser, defaults)
    HalRunner.add_parser_args(parser, defaults)
    self.add_bom_parser_args(parser, defaults)

    self.add_argument(
        parser, 'halyard_release_track', defaults, 'stable',
        choices=['nightly', 'stable'],
        help='Which halyard release track to use when installing images.')
    self.add_argument(
        parser, 'skip_existing', defaults, False, type=bool,
        help='Skip builds if the desired image already exists in GCE.')
    self.add_argument(
        parser, 'delete_existing', defaults, None, type=bool,
        help='Delete pre-existing desired images from GCE.')

    self.add_argument(
        parser, 'build_gce_service_account', defaults, None,
        help='Service account for building images.')
    self.add_argument(
        parser, 'build_gce_project', defaults, None,
        help='Project to build image in.')
    self.add_argument(
        parser, 'build_gce_zone', defaults, 'us-central1-f',
        help='Zone to build image in.')

    halyard_install_sh = 'dev/halyard_install_component.sh'
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
        parser, 'git_branch', defaults, None,
        help='Github branch to get install scripts from.'
             ' If none, then use the source repo branch that this script'
             ' is running from.')
    self.add_argument(
        parser, 'bintray_org', defaults, None,
        help='The bintray organization for the bintray_*_repositories.')
    self.add_argument(
        parser, 'bintray_debian_repository', defaults, None,
        help='Repository where built debians were placed.')
    self.add_argument(
        parser, 'halyard_bom_bucket', defaults, 'halconfig',
        help='The bucket manaing halyard BOMs and config profiles.')


def add_bom_parser_args(parser, defaults):
  """Adds parser arguments pertaining to publishing boms."""
  BuildGceComponentImagesFactory.add_bom_parser_args(parser, defaults)


def register_commands(registry, subparsers, defaults):
  BuildGceComponentImagesFactory().register(registry, subparsers, defaults)
