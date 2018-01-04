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

"""Implements generate_bom command for buildtool."""

import datetime
import logging
import os
import urllib2
import yaml

import buildtool.build_commands
import buildtool.source_commands

from buildtool.command import (
    CommandFactory,
    CommandProcessor,
    RepositoryCommandFactory,
    RepositoryCommandProcessor)

from buildtool.source_code_manager import SPINNAKER_BOM_REPOSITORIES
from buildtool.util import (
    check_subprocess,
    write_to_path)


# pylint: disable=fixme
# TODO(ewiseblatt):
# Need a way to maintain this automatically and collect it.
DEFAULT_BOM_DEPENDENCIES = {
    name: {'version': version}
    for name, version in [
        ('consul', '0.7.5'),
        ('redis', '2:2.8.4-2'),
        ('vault', '0.7.0')]
}


def _url_prefix(url):
  """Determine url up to the terminal path component."""
  # We're assuming no query parameter/fragment since these are git URLs.
  # otherwise we need to parse the url and extract the path
  return url[:url.rfind('/')]


def _determine_bom_path(options):
  if options.bom_path:
    return options.bom_path

  filename = 'bom-{branch}-{buildnumber}.yml'.format(
      branch=options.git_branch or 'NOBRANCH', buildnumber=options.build_number)
  return os.path.join(options.scratch_dir, filename)


class GenerateBomCommand(RepositoryCommandProcessor):
  """Implements the generate_bom."""

  def _do_determine_source_repositories(self):
    """Implements RepositoryCommand interface."""
    local_repository = {name: os.path.join(self.options.root_path, name)
                        for name in SPINNAKER_BOM_REPOSITORIES.keys()}
    return {name: self.git.determine_remote_git_repository(git_dir)
            for name, git_dir in local_repository.items()}

  def _do_repository(self, repository):
    """Collect the summary for the given repository."""
    scm = self.source_code_manager
    git_dir = scm.get_local_repository_path(repository.name)
    return scm.git.collect_repository_summary(git_dir)

  def _do_postprocess(self, result_dict):
    """Construct the bom from the collected summary, then write it out."""
    path = _determine_bom_path(self.options)
    summary_table = result_dict

    bom = self.construct_bom(summary_table, DEFAULT_BOM_DEPENDENCIES)
    bom_text = yaml.dump(bom, default_flow_style=False)
    write_to_path(bom_text, path)
    logging.info('Wrote bom to %s', path)

  def to_build_version(self, version):
    """Return version decorated with build number."""
    build_number = self.options.build_number
    return '{version}-{build}'.format(version=version, build=build_number)

  def make_bom_services_spec(
      self, repositories, summary_table, default_source_prefix):
    """Create the 'services' block for a BOM.

    Args:
      repositories: [dict] The {<name>: <RemoteGitRepository>}.
         Only those referenced in the summary_table are considered.
      summary_table: [dict] The {<name>: <RepositorySummary>} to count.
      default_source_prefix: [string] The repository.url prefix assumed.
    """
    services = {}
    for name, summary in summary_table.items():
      repository = repositories[name]
      source_prefix = _url_prefix(repository.url)

      version_info = {
          'commit': summary.commit_id,
          'version': self.to_build_version(summary.version)
      }
      if source_prefix != default_source_prefix:
        version_info['source'] = repository.url

      if name == ' spinnaker-monitoring':
        services['monitoring-third-party'] = version_info
        services['monitoring-daemon'] = version_info
        continue
      services[name] = version_info
    return services

  def determine_most_common_prefix(self, repositories, summary_table):
    """Determine which of repositories url's is most commonly used.

    Args:
      repositories: [dict] The {<name>: <RemoteGitRepository>}.
         Only those referenced in the summary_table are considered.
      summary_table: [dict] The {<name>: <RepositorySummary>} to count.
    """
    prefix_count = {}
    for name in summary_table.keys():
      repository = repositories[name]
      url_prefix = _url_prefix(repository.url)
      prefix_count[url_prefix] = prefix_count.get(url_prefix, 0) + 1
    default_prefix = None
    max_count = 0
    for prefix, count in prefix_count.items():
      if count > max_count:
        default_prefix, max_count = prefix, count
    return default_prefix

  def construct_bom(self, summary_table, dependencies):
    """Create a BOM specification.

    Args:
      summary_table: [dict] The {<name>: <RepositorySummary>} to include.
      dependencies: [dict] Inject as the 'Dependencies' section of the BOM.
    """
    repositories = self.source_code_manager.source_repositories
    default_source_prefix = self.determine_most_common_prefix(
        repositories, summary_table)

    services = self.make_bom_services_spec(
        repositories, summary_table, default_source_prefix)

    options = self.options
    branch = options.git_branch or 'master'
    artifact_sources = {
        'gitPrefix': default_source_prefix,
        'gitBranch': branch
    }
    build_debian_repository = (
        None
        if options.build_bintray_repository is None
        else 'https://dl.bintray.com/{repo}'.format(
            repo=options.build_bintray_repository))

    artifact_sources.update({
        name: source
        for name, source in [
            ('debianRepository', build_debian_repository),
            ('dockerRegistry', options.build_docker_registry),
            ('googleImageProject', options.publish_gce_image_project)
        ]
        if source
    })

    now = datetime.datetime.utcnow()
    timestamp_decorator = '{:%Y-%m-%d}'.format(now)
    branch_version = '{branch}-{timestamp}'.format(
        branch=branch, timestamp=timestamp_decorator)

    return {
        'artifactSources': artifact_sources,
        'dependencies': dependencies,
        'services': services,
        'version': self.to_build_version(branch_version),
        'timestamp': '{:%Y-%m-%d %H:%M:%S}'.format(now)
    }


class GenerateBomCommandFactory(RepositoryCommandFactory):
  """Generates bom files."""

  def __init__(self, **kwargs):
    super(GenerateBomCommandFactory, self).__init__(
        'generate_bom', GenerateBomCommand, 'Generate a BOM file.',
        **kwargs)

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(GenerateBomCommandFactory, self)._do_init_argparser(parser, defaults)
    buildtool.build_commands.add_bom_parser_args(parser, defaults)
    buildtool.source_commands.FetchSourceCommandFactory.add_fetch_parser_args(
        parser, defaults)

    self.add_argument(
        parser, 'bom_path', defaults, None,
        help='Generate the BOM and write it to the given path.')


class PublishBomCommand(CommandProcessor):
  """Implements publish_bom"""

  def _check_property(self, config, name, want):
    """Check a configuration property meets our needs."""
    have = config[name]
    if have == want:
      logging.debug('Confirmed Halyard server is configured with %s="%s"',
                    name, have)
    else:
      raise ValueError(
          'Halyard server is not configured to support this request.\n'
          'It is using {name}={have!r} rather than {want!r}.\n'
          'You will need to modify /opt/spinnaker/config/halyard-local.yml'
          ' and restart the halyard server.'
          .format(name=name, have=have, want=want))

  def _verify_config(self):
    """Verify halyard is configured the way we think it is."""
    # Halyard is configured with fixed endpoints, however when we
    # pubish we want to be explicit about where we are publishing to.
    # There isnt a way to control this in halyard on a per-request basis
    # so make sure halyard was configured consistent with where we want
    # these BOMs to go.

    logging.debug('Verifying halyard server is consistent')
    url = 'http://localhost:8064/resolvedEnv'
    response = urllib2.urlopen(url)
    if response.getcode() >= 300:
      raise ValueError('{url}: {code}\n{body}'.format(
          url=url, code=response.getcode(), body=response.read()))
    config = yaml.load(response)

    self._check_property(
        config, 'spinnaker.config.input.writerEnabled', 'true')
    self._check_property(
        config, 'spinnaker.config.input.bucket',
        self.options.halyard_bom_bucket)

  def _do_command(self):
    """Implements CommandProcessor interface."""
    options = self.options
    self._verify_config()
    bom_path = _determine_bom_path(options)
    logging.info('Publishing bom from %s', bom_path)
    self._publish_path(bom_path)

    if options.bom_alias:
      logging.info('Publishing bom alias %s = %s',
                   options.bom_alias, os.path.basename(bom_path))
      with open(bom_path, 'r') as stream:
        bom = yaml.load(stream)

      alias_path = os.path.join(options.scratch_dir, options.bom_alias)
      with open(alias_path, 'w') as stream:
        bom['version'] = options.bom_alias
        yaml.dump(bom, stream, default_flow_style=False)
      self._publish_path(alias_path)

  def _publish_path(self, path):
    """Publish a bom path via halyard."""

    cmd = '{hal} admin publish bom --color false --bom-path {path}'.format(
        hal=self.options.hal_path, path=os.path.abspath(path))
    check_subprocess(cmd)


class PublishBomCommandFactory(CommandFactory):
  """Publishes local bom file to halyard."""

  def __init__(self, **kwargs):
    super(PublishBomCommandFactory, self).__init__(
        'publish_bom', PublishBomCommand, 'Publish a BOM file to Halyard.',
        **kwargs)

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(PublishBomCommandFactory, self)._do_init_argparser(parser, defaults)
    self.add_argument(
        parser, 'bom_path', defaults, None,
        help='Publish the BOM from the given path.')
    self.add_argument(
        parser, 'bom_alias', defaults, None,
        help='Also publish the BOM using this alias name.')
    self.add_argument(
        parser, 'halyard_bom_bucket', defaults, 'halconfig',
        help='The bucket manaing halyard BOMs and config profiles.')
    self.add_argument(
        parser, 'hal_path', defaults, '/usr/local/bin/hal',
        help='Path to local Halyard "hal" CLI.')


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  GenerateBomCommandFactory().register(registry, subparsers, defaults)
  PublishBomCommandFactory().register(registry, subparsers, defaults)
