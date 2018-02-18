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
import yaml

from buildtool import (
    SPINNAKER_HALYARD_REPOSITORY_NAME,
    BomSourceCodeManager,
    BranchSourceCodeManager,
    GradleCommandFactory,
    GradleCommandProcessor,

    check_subprocess,
    check_subprocesses_to_logfile,
    write_to_path,
    raise_and_log_error,
    ConfigError)


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
    if self.options.container_builder == 'gcb':
      build_version = self.scm.get_repository_service_build_version(repository)
      return self.__check_gcb_image(repository, build_version)
    return False

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    options = self.options

    builder_methods = {
        'docker': self.__build_with_docker,
        'gcb': self.__build_with_gcb
    }
    builder_methods[options.container_builder](repository)

  def __build_with_docker(self, repository):
    logging.warning('DOCKER builds are still under development')
    name = repository.name
    source_info = self.source_code_manager.check_source_info(repository)
    docker_tag = '{reg}/{name}:{build_version}'.format(
        reg=self.options.docker_registry,
        name=name,
        build_version=source_info.to_build_version())

    cmds = [
        'docker build -f Dockerfile -t %s .' % docker_tag,
        'docker push %s' % docker_tag
    ]

    gradle_dir = repository.gradle_dir
    logfile = self.get_logfile_path(name + '-docker-build')
    check_subprocesses_to_logfile(
        name + ' docker build', logfile, cmds, cwd=gradle_dir)

  def __check_gcb_image(self, repository, version):
    """Determine if gcb image already exists."""
    options = self.options
    image_name = self.scm.repository_name_to_service_name(repository.name)
    command = ['gcloud', '--account', options.gcb_service_account,
               'container', 'images', 'list-tags',
               options.docker_registry + '/' + image_name,
               '--filter="%s"' % version,
               '--format=json']
    got = check_subprocess(' '.join(command))
    if got.strip() != '[]':
      labels = {'repository': repository.name, 'artifact': 'gcr-container'}
      if self.options.skip_existing:
        logging.info('Already have %s -- skipping build', image_name)
        self.metrics.inc_counter('ReuseArtifact', labels,
                                 'Kept existing container image.')
        return True
      if self.options.delete_existing:
        self.__delete_gcb_image(repository, image_name, version)
      else:
        raise_and_log_error(
            ConfigError('Already have {name} version {version}'.format(
                name=image_name, version=version)))
    return False

  def __delete_gcb_image(self, repository, image_name, version):
    """Delete the gcb image if it already exists."""
    options = self.options
    command = ['gcloud', '--account', options.gcb_service_account,
               'container', 'images', 'delete',
               '%s/%s:%s' % (options.docker_registry, image_name, version),
               '--quiet']
    labels = {'repository': repository.name, 'artifact': 'gcr-container'}
    self.metrics.count_call(
        'DeleteArtifact', labels,
        'Attempts to delete existing GCR container images.',
        check_subprocess, ' '.join(command))

  def __build_with_gcb(self, repository):
    name = repository.name
    source_info = self.source_code_manager.check_source_info(repository)
    gcb_config = self.__derive_gcb_config(repository, source_info)
    if gcb_config is None:
      logging.info('Skipping GCB for %s because there is config for it',
                   name)
      return

    options = self.options

    # Use an absolute path here because we're going to
    # pass this to the gcloud command, which will be running
    # in a different directory so relative paths wont hold.
    config_dir = os.path.abspath(self.get_output_dir())
    config_path = os.path.join(config_dir, name + '-gcb.yml')
    write_to_path(gcb_config, config_path)

    # Local .gradle dir stomps on GCB's .gradle directory when the gradle
    # wrapper is installed, so we need to delete the local one.
    # The .gradle dir is transient and will be recreated on the next gradle
    # build, so this is OK.
    #
    # This can still be shared among components as long as the
    # output directory remains around.
    git_dir = repository.git_dir
    if options.force_clean_gradle_cache:
      # If we're going to delete existing ones, then keep each component
      # separate so they dont stomp on one another
      gradle_cache = os.path.abspath(os.path.join(git_dir, '.gradle'))
    else:
      # Otherwise allow all the components to share a common gradle directory
      gradle_cache = os.path.abspath(
          os.path.join(options.output_dir, '.gradle'))

    if options.force_clean_gradle_cache and os.path.isdir(gradle_cache):
      shutil.rmtree(gradle_cache)

    # Note this command assumes a cwd of git_dir
    command = ('gcloud container builds submit '
               ' --account={account} --project={project}'
               ' --config="{config_path}" .'
               .format(account=options.gcb_service_account,
                       project=options.gcb_project,
                       config_path=config_path))

    logfile = self.get_logfile_path(name + '-gcb-build')
    labels = {'repository': repository.name}
    self.metrics.time_call(
        'GcrBuild', labels,
        'Attempts to build GCR container images.',
        check_subprocesses_to_logfile,
        name + ' container build', logfile, [command], cwd=git_dir)

  def __make_gradle_gcb_step(self, name, env_vars_list):
    command_sequence = ['git rev-parse HEAD | xargs git checkout']
    if name == 'deck':
      command_sequence.append('./gradlew build -PskipTests')
    else:
      command_sequence.append('./gradlew %s-web:installDist -x test' % name)

    return {
        'args': ['bash', '-c', ';'.join(command_sequence)],
        'env': env_vars_list,
        'name': self.options.container_base_image
    }

  def __derive_gcb_config(self, repository, source_info):
    """Helper function for repository_main."""
    options = self.options
    name = repository.name
    if name == 'spinnaker-monitoring':
      has_gradle_step = False
      name = 'monitoring-daemon'
      dirname = 'spinnaker-monitoring-daemon'
      dockerfile = 'Dockerfile'
    else:
      has_gradle_step = True
      dirname = '.'
      dockerfile = ('Dockerfile.slim'
                    if repository.name not in ['echo']
                    else 'Dockerfile')

    dockerfile_path = os.path.join(repository.git_dir, dirname, dockerfile)
    if not os.path.exists(dockerfile_path):
      logging.warning('No GCB config for %s because there is no %s',
                      repository.name, dockerfile)
      return None

    env_vars_list = [env
                     for env in options.container_env_vars.split(',')
                     if env]
    versioned_image = '{reg}/{repo}:{build_version}'.format(
        reg=options.docker_registry, repo=name,
        build_version=source_info.to_build_version())
    steps = ([self.__make_gradle_gcb_step(name, env_vars_list)]
             if has_gradle_step
             else [])
    build_step = {
        'args': ['build', '-t', versioned_image, '-f', dockerfile, '.'],
        'env': env_vars_list,
        'name': 'gcr.io/cloud-builders/docker'
    }
    if dirname and dirname != '.':
      build_step['dir'] = dirname

    steps.append(build_step)

    config = {
        'images': [versioned_image],
        'timeout': '3600s',
        'steps': steps
    }

    return yaml.dump(config, default_flow_style=True)


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
        parser, 'container_env_vars', defaults,
        'GRADLE_USER_HOME=/gradle_cache/.gradle',
        help='Comma-separated list of environment variable bindings'
        ' to set when performing container builds.')
    self.add_argument(
        parser, 'container_builder', defaults, 'gcb',
        choices=['docker', 'gcb', 'gcb-trigger'],
        help='Type of builder to use.')
    self.add_argument(
        parser, 'gcb_project', defaults, None,
        help='The GCP project ID to publish containers to when'
        ' using Google Container Builder.')
    self.add_argument(
        parser, 'gcb_service_account', defaults, None,
        help='Google Service Account when using the GCP Container Builder.')

    self.add_argument(
        parser, 'container_base_image', defaults, None,
        help='Base image to start from in the container builds.')
    self.add_argument(
        parser, 'force_clean_gradle_cache', defaults, True,
        help='Force a fresh new component-specific gradle cache for'
        ' each component build')


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
