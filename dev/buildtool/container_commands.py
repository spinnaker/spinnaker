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

"""Implements container support commands for buildtool."""

import copy
import logging
import os
import shutil

from buildtool import (
  SPINNAKER_HALYARD_REPOSITORY_NAME,
  BomSourceCodeManager,
  BranchSourceCodeManager,
  GradleCommandFactory,
  GradleCommandProcessor,

  check_subprocess,
  check_subprocesses_to_logfile
)


class BuildContainerCommand(GradleCommandProcessor):
  def __init__(self, factory, options, source_repository_names=None, **kwargs):
    # Use own repository to avoid race conditions when commands are
    # running concurrently.
    options_copy = copy.copy(options)
    options_copy.github_disable_upstream_push = True
    super(BuildContainerCommand, self).__init__(
        factory, options_copy,
        source_repository_names=source_repository_names, **kwargs)

  def _do_can_skip_repository(self, repository):
    image_name = self.scm.repository_name_to_service_name(repository.name)
    version = self.scm.get_repository_service_build_version(repository)

    for variant in ('slim', 'ubuntu'):
      tag = f"{version}-{variant}"
      if not self.__gcb_image_exists(image_name, tag):
        return False

    labels = {'repository': repository.name, 'artifact': 'gcr-container'}
    logging.info('Already have %s -- skipping build', image_name)
    self.metrics.inc_counter('ReuseArtifact', labels)
    return True

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    scm = self.source_code_manager
    build_version = scm.get_repository_service_build_version(repository)
    self.__build_with_gcb(repository, build_version)

  def __gcb_image_exists(self, image_name, version):
    """Determine if gcb image already exists."""
    options = self.options
    command = ['gcloud', '--account', options.gcb_service_account,
               'container', 'images', 'list-tags',
               options.docker_registry + '/' + image_name,
               '--filter="%s"' % version,
               '--format=json']
    got = check_subprocess(' '.join(command))
    if got.strip() != '[]':
      return True
    return False

  def __build_with_gcb(self, repository, build_version):
    name = repository.name
    options = self.options

    # Local .gradle dir stomps on GCB's .gradle directory when the gradle
    # wrapper is installed, so we need to delete the local one.
    # The .gradle dir is transient and will be recreated on the next gradle
    # build, so this is OK.
    #
    # This can still be shared among components as long as the
    # output directory remains around.
    git_dir = repository.git_dir
    # If we're going to delete existing ones, then keep each component
    # separate so they dont stomp on one another
    gradle_cache = os.path.abspath(os.path.join(git_dir, '.gradle'))

    if os.path.isdir(gradle_cache):
      shutil.rmtree(gradle_cache)

    cloudbuild_config = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cloudbuild.yml')
    service_name = self.scm.repository_name_to_service_name(repository.name)
    # Note this command assumes a cwd of git_dir
    command = ('gcloud builds submit '
               ' --account={account} --project={project}'
               ' --substitutions=TAG_NAME={tag_name},_IMAGE_NAME={image_name}'
               ' --config={cloudbuild_config} .'
               .format(account=options.gcb_service_account,
                       project=options.gcb_project,
                       tag_name=build_version,
                       image_name=service_name,
                       cloudbuild_config=cloudbuild_config))

    logfile = self.get_logfile_path(name + '-gcb-build')
    labels = {'repository': repository.name}
    self.metrics.time_call(
        'GcrBuild', labels, self.metrics.default_determine_outcome_labels,
        check_subprocesses_to_logfile,
        name + ' container build', logfile, [command], cwd=git_dir)

class BuildContainerFactory(GradleCommandFactory):
  @staticmethod
  def add_bom_parser_args(parser, defaults):
    """Adds publishing arguments of interest to the BOM commands as well."""
    if hasattr(parser, 'added_container'):
      return
    parser.added_container = True
    GradleCommandFactory.add_bom_parser_args(parser, defaults)

    BuildContainerFactory.add_argument(
        parser, 'docker_registry', defaults, None,
        help='Docker registry to push the container images to.')

  def init_argparser(self, parser, defaults):
    super(BuildContainerFactory, self).init_argparser(parser, defaults)

    self.add_bom_parser_args(parser, defaults)
    self.add_argument(
        parser, 'gcb_project', defaults, None,
        help='The GCP project ID to publish containers to when'
        ' using Google Container Builder.')
    self.add_argument(
        parser, 'gcb_service_account', defaults, None,
        help='Google Service Account when using the GCP Container Builder.')


def add_bom_parser_args(parser, defaults):
  """Adds parser arguments pertaining to publishing boms."""
  BuildContainerFactory.add_bom_parser_args(parser, defaults)


def register_commands(registry, subparsers, defaults):
  build_bom_containers_factory = BuildContainerFactory(
      'build_bom_containers', BuildContainerCommand,
      'Build one or more service containers from the local git repository.',
      BomSourceCodeManager)

  build_hal_containers_factory = BuildContainerFactory(
      'build_halyard_containers', BuildContainerCommand,
      'Build one or more service containers from the local git repository.',
      BranchSourceCodeManager,
      source_repository_names=[SPINNAKER_HALYARD_REPOSITORY_NAME])

  build_bom_containers_factory.register(registry, subparsers, defaults)
  build_hal_containers_factory.register(registry, subparsers, defaults)
