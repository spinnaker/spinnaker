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

"""Implements build_bom command for buildtool."""

import datetime
import logging
import os
import shutil
import yaml

import buildtool.container_commands
import buildtool.debian_commands
import buildtool.image_commands

from buildtool import (
    DEFAULT_BUILD_NUMBER,

    SPINNAKER_BOM_REPOSITORY_NAMES,

    BomSourceCodeManager,
    BranchSourceCodeManager,
    RepositoryCommandFactory,
    RepositoryCommandProcessor,

    HalRunner,
    check_subprocess,

    check_path_exists,
    ensure_dir_exists,
    raise_and_log_error,
    write_to_path,
    ConfigError)


def _determine_bom_path(command_processor):
  if command_processor.options.bom_path:
    return command_processor.options.bom_path

  options = command_processor.options
  filename = '{branch}-{buildnum}.yml'.format(
      branch=options.git_branch or 'NOBRANCH', buildnum=options.build_number)
  return os.path.join(command_processor.get_output_dir(command='build_bom'),
                      filename)


def now():
  """Hook for easier mocking."""
  return datetime.datetime.utcnow()


class BomBuilder(object):
  """Helper class for BuildBomCommand that constructs the bom specification."""

  @staticmethod
  def new_from_bom(options, scm, bom):
    return BomBuilder(options, scm, base_bom=bom)

  @property
  def base_bom(self):
    return self.__base_bom

  def __init__(self, options, scm, base_bom=None):
    """Construct new builder.

    Args:
      base_bom[dict]: If defined, this is a bom to start with.
                      It is intended to support a "refresh" use case where
                      only a subset of entires are updated within it.
    """
    self.__options = options
    self.__scm = scm
    self.__services = {}
    self.__repositories = {}
    self.__base_bom = base_bom or {}
    if not base_bom and not options.bom_dependencies_path:
      self.__bom_dependencies_path = os.path.join(
          os.path.dirname(__file__), 'bom_dependencies.yml')
    else:
      self.__bom_dependencies_path = options.bom_dependencies_path

    if self.__bom_dependencies_path:
      check_path_exists(self.__bom_dependencies_path, "bom_dependencies_path")

  def to_url_prefix(self, url):
    """Determine url up to the terminal path component."""
    # We're assuming no query parameter/fragment since these are git URLs.
    # otherwise we need to parse the url and extract the path
    return url[:url.rfind('/')]

  def add_repository(self, repository, source_info):
    """Helper function for determining the repository's BOM entry."""
    version_info = {
        'commit': source_info.summary.commit_id,
        'version': source_info.to_build_version()
    }

    service_name = self.__scm.repository_name_to_service_name(repository.name)
    self.__services[service_name] = version_info
    self.__repositories[service_name] = repository
    if service_name == 'monitoring-daemon':
      # Dont use the same actual object because having the repeated
      # value reference causes the generated yaml to be invalid.
      self.__services['monitoring-third-party'] = dict(version_info)
      self.__repositories['monitoring-third-party'] = repository

  def determine_most_common_prefix(self):
    """Determine which of repositories url's is most commonly used."""
    prefix_count = {}
    for repository in self.__repositories.values():
      url_prefix = self.to_url_prefix(repository.origin)
      prefix_count[url_prefix] = prefix_count.get(url_prefix, 0) + 1
    default_prefix = None
    max_count = 0
    for prefix, count in prefix_count.items():
      if count > max_count:
        default_prefix, max_count = prefix, count
    return default_prefix

  def build(self):
    options = self.__options

    if self.__bom_dependencies_path:
      logging.debug('Loading bom dependencies from %s',
                    self.__bom_dependencies_path)
      with open(self.__bom_dependencies_path, 'r') as stream:
        dependencies = yaml.load(stream.read())
        logging.debug('Loaded %s', dependencies)
    else:
      dependencies = None
    if not dependencies:
      dependencies = self.__base_bom.get('dependencies')

    if not dependencies:
      raise_and_log_error(ConfigError('No BOM dependencies found'))

    base_sources = self.__base_bom.get('artifactSources', {})
    default_source_prefix = (base_sources.get('gitPrefix', None)
                             or self.determine_most_common_prefix())
    for name, version_info in self.__services.items():
      repository = self.__repositories[name]
      origin = repository.origin
      source_prefix = self.to_url_prefix(origin)
      if source_prefix != default_source_prefix:
        version_info['gitPrefix'] = source_prefix

    branch = options.git_branch or 'master'
    artifact_sources = {
        'gitPrefix': default_source_prefix,
    }
    debian_repository = (
        None
        if options.bintray_debian_repository is None
        else 'https://dl.bintray.com/{org}/{repo}'.format(
            org=options.bintray_org,
            repo=options.bintray_debian_repository))

    artifact_sources.update({
        name: source
        for name, source in [
            ('debianRepository', debian_repository),
            ('dockerRegistry', options.docker_registry),
            ('googleImageProject', options.publish_gce_image_project)
        ]
        if source
    })

    services = dict(self.__base_bom.get('services', {}))
    changed = False
    for name, info in self.__services.items():
      if info['commit'] == services.get(name, {}).get('commit', None):
        logging.debug('%s commit hasnt changed -- keeping existing %s',
                      name, info)
        continue
      changed = True
      services[name] = info

    if (self.__base_bom.get('artifactSources') != artifact_sources
        or self.__base_bom.get('dependencies') != dependencies):
      changed = True

    if not changed:
      return self.__base_bom

    return {
        'artifactSources': artifact_sources,
        'dependencies': dependencies,
        'services': services,
        'version': '%s-%s' % (branch, options.build_number),
        'timestamp': '{:%Y-%m-%d %H:%M:%S}'.format(now())
    }


class BuildBomCommand(RepositoryCommandProcessor):
  """Implements build_bom."""

  def __init__(self, factory, options, *pos_args, **kwargs):
    super(BuildBomCommand, self).__init__(factory, options, *pos_args, **kwargs)

    if options.refresh_from_bom_path and options.refresh_from_bom_version:
      raise_and_log_error(
          ConfigError('Cannot specify both --refresh_from_bom_path="{0}"'
                      ' and --refresh_from_bom_version="{1}"'
                      .format(options.refresh_from_bom_path,
                              options.refresh_from_bom_version)))
    if options.refresh_from_bom_path:
      logging.debug('Using base bom from path "%s"',
                    options.refresh_from_bom_path)
      check_path_exists(options.refresh_from_bom_path,
                        "refresh_from_bom_path")
      with open(options.refresh_from_bom_path, 'r') as stream:
        base_bom = yaml.load(stream.read())
    elif options.refresh_from_bom_version:
      logging.debug('Using base bom version "%s"',
                    options.refresh_from_bom_version)
      base_bom = HalRunner(options).retrieve_bom_version(
          options.refresh_from_bom_version)
    else:
      base_bom = None
    if base_bom:
      logging.info('Creating new bom based on version "%s"',
                   base_bom.get('version', 'UNKNOWN'))
    self.__builder = BomBuilder(self.options, self.scm, base_bom=base_bom)

  def _do_can_skip_repository(self, repository):
    name = repository.name
    service_name = self.scm.repository_name_to_service_name(name)
    origin = repository.origin
    branch = self.options.git_branch
    services = self.__builder.base_bom.get('services', {})
    existing_bom_entry = services.get(service_name, {})
    existing_commit = existing_bom_entry.get('commit')
    logging.debug('Fetching current commit for %s %s', origin, branch)
    current_commit = self.git.query_remote_repository_commit_id(origin, branch)

    if current_commit == existing_commit:
      result = True
      reason = 'same commit'
      logging.debug('%s %s is unchanged - skip.', origin, branch)
    else:
      result = False
      reason = 'different commit' if existing_bom_entry else 'fresh bom'

    labels = {'repository': name, 'branch': branch,
              'reason': reason, 'updated': result}
    self.metrics.inc_counter('UpdateBomEntry', labels,
                             'Attempts to update bom entries.')
    return result

  def _do_repository(self, repository):
    source_info = self.scm.lookup_source_info(repository)
    self.__builder.add_repository(repository, source_info)

  def _do_postprocess(self, _):
    """Construct BOM and write it to the configured path."""
    bom = self.__builder.build()
    if bom == self.__builder.base_bom:
      logging.info('Bom has not changed from version %s @ %s',
                   bom['version'], bom['timestamp'])

    bom_text = yaml.dump(bom, default_flow_style=False)

    path = _determine_bom_path(self)
    write_to_path(bom_text, path)
    logging.info('Wrote bom to %s', path)


class BuildBomCommandFactory(RepositoryCommandFactory):
  def __init__(self, **kwargs):
    super(BuildBomCommandFactory, self).__init__(
        'build_bom', BuildBomCommand, 'Build a BOM file.',
        BranchSourceCodeManager,
        source_repository_names=SPINNAKER_BOM_REPOSITORY_NAMES,
        **kwargs)

  def init_argparser(self, parser, defaults):
    super(BuildBomCommandFactory, self).init_argparser(parser, defaults)
    HalRunner.add_parser_args(parser, defaults)
    buildtool.container_commands.add_bom_parser_args(parser, defaults)
    buildtool.debian_commands.add_bom_parser_args(parser, defaults)
    buildtool.image_commands.add_bom_parser_args(parser, defaults)

    self.add_argument(
        parser, 'build_number', defaults, DEFAULT_BUILD_NUMBER,
        help='The build number for this specific bom.')
    self.add_argument(
        parser, 'bom_path', defaults, None,
        help='The path to the local BOM file copy to write out.')
    self.add_argument(
        parser, 'bom_dependencies_path', defaults, None,
        help='The path to YAML file specifying the BOM dependencies section'
             ' if overriding.')
    self.add_argument(
        parser, 'refresh_from_bom_path', defaults, None,
        help='If specified then use the existing bom_path as a prototype'
             ' to refresh. Use with --only_repositories to create a new BOM.'
             ' using only the new versions and build numbers for select repos'
             ' while keeping the existing versions and build numbers for'
             ' others.')
    self.add_argument(
        parser, 'refresh_from_bom_version', defaults, None,
        help='Similar to refresh_from_bom_path but using a version obtained.'
             ' from halyard.')
    self.add_argument(
        parser, 'git_fallback_branch', defaults, None,
        help='The branch to pull for the BOM if --git_branch isnt found.'
             ' This is intended only for speculative development where'
             ' some repositories are being modified and the remaing are'
             ' to come from a release branch.')


class PublishBomCommand(RepositoryCommandProcessor):
  """Implements publish_bom"""

  def __init__(self, factory, options, **kwargs):
    options.github_disable_upstream_push = True
    super(PublishBomCommand, self).__init__(factory, options, **kwargs)
    self.__hal_runner = HalRunner(options)
    logging.debug('Verifying halyard server is consistent')

    # Halyard is configured with fixed endpoints, however when we
    # pubish we want to be explicit about where we are publishing to.
    # There isnt a way to control this in halyard on a per-request basis
    # so make sure halyard was configured consistent with where we want
    # these BOMs to go.
    self.__hal_runner.check_property(
        'spinnaker.config.input.bucket', options.halyard_bom_bucket)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    self.source_code_manager.ensure_local_repository(repository)
    self.__collect_halconfig_files(repository)

  def _do_postprocess(self, _):
    """Implements RepositoryCommandProcessor interface."""
    options = self.options
    bom_path = _determine_bom_path(self)
    self.__hal_runner.publish_bom_path(bom_path)
    self.__publish_configs(bom_path)

    if options.bom_alias:
      alias = os.path.splitext(options.bom_alias)[0]
      logging.info('Publishing bom alias %s = %s',
                   alias, os.path.basename(bom_path))
      with open(bom_path, 'r') as stream:
        bom = yaml.load(stream)

      alias_path = os.path.join(os.path.dirname(bom_path), alias + '.yml')
      with open(alias_path, 'w') as stream:
        bom['version'] = options.bom_alias
        yaml.dump(bom, stream, default_flow_style=False)
      self.__hal_runner.publish_bom_path(alias_path)

  def __publish_configs(self, bom_path):
    """Publish each of the halconfigs for the bom at the given path."""
    def publish_repo_config(repository):
      """Helper function to publish individual repository."""
      name = self.scm.repository_name_to_service_name(repository.name)
      config_dir = os.path.join(self.get_output_dir(), 'halconfig', name)
      if not os.path.exists(config_dir):
        logging.warning('No profiles for %s', name)
        return

      logging.debug('Publishing profiles for %s', name)
      for profile in os.listdir(config_dir):
        profile_path = os.path.join(config_dir, profile)
        self.__hal_runner.publish_profile(name, profile_path, bom_path)

    logging.info('Publishing halyard configs...')
    self.source_code_manager.foreach_source_repository(
        self.source_repositories, publish_repo_config)

  def __collect_halconfig_files(self, repository):
    """Gets the component config files and writes them into the output_dir."""
    name = repository.name
    if (name not in SPINNAKER_BOM_REPOSITORY_NAMES
        or name in ['spinnaker']):
      logging.debug('%s does not use config files -- skipping', name)
      return

    if name == 'spinnaker-monitoring':
      config_root = os.path.join(
          repository.git_dir, 'spinnaker-monitoring-daemon')
    else:
      config_root = repository.git_dir

    service_name = self.scm.repository_name_to_service_name(repository.name)
    target_dir = os.path.join(self.get_output_dir(), 'halconfig', service_name)
    ensure_dir_exists(target_dir)

    config_path = os.path.join(config_root, 'halconfig')
    logging.info('Copying configs from %s...', config_path)
    for profile in os.listdir(config_path):
      profile_path = os.path.join(config_path, profile)
      if os.path.isfile(profile_path):
        shutil.copyfile(profile_path, os.path.join(target_dir, profile))
        logging.debug('Copied profile to %s', profile_path)
      elif not os.path.isdir(profile_path):
        logging.warning('%s is neither file nor directory -- ignoring',
                        profile_path)
        continue
      else:
        tar_path = os.path.join(
            target_dir, '{profile}.tar.gz'.format(profile=profile))
        file_list = ' '.join(os.listdir(profile_path))

        # NOTE: For historic reasons this is not actually compressed
        # even though the tar_path says ".tar.gz"
        check_subprocess(
            'tar cf {path} -C {profile} {file_list}'.format(
                path=tar_path, profile=profile_path, file_list=file_list))
        logging.debug('Copied profile to %s', tar_path)


class PublishBomCommandFactory(RepositoryCommandFactory):
  def __init__(self, **kwargs):
    super(PublishBomCommandFactory, self).__init__(
        'publish_bom', PublishBomCommand, 'Publish a BOM file to Halyard.',
        BomSourceCodeManager,
        source_repository_names=SPINNAKER_BOM_REPOSITORY_NAMES,
        **kwargs)

  def init_argparser(self, parser, defaults):
    super(PublishBomCommandFactory, self).init_argparser(parser, defaults)
    HalRunner.add_parser_args(parser, defaults)

    self.add_argument(
        parser, 'halyard_bom_bucket', defaults, 'halconfig',
        help='The bucket manaing halyard BOMs and config profiles.')
    self.add_argument(
        parser, 'bom_alias', defaults, None,
        help='Also publish the BOM using this alias name.')


def register_commands(registry, subparsers, defaults):
  BuildBomCommandFactory().register(registry, subparsers, defaults)
  PublishBomCommandFactory().register(registry, subparsers, defaults)
