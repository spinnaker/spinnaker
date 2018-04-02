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

"""Implements spinnaker support commands for buildtool."""

import copy
import logging
import os
import urllib2
import yaml

from buildtool import (
    SPINNAKER_BOM_REPOSITORY_NAMES,
    SPINNAKER_GITHUB_IO_REPOSITORY_NAME,
    BomSourceCodeManager,
    BranchSourceCodeManager,
    CommandProcessor,
    CommandFactory,
    RepositoryCommandFactory,
    RepositoryCommandProcessor,
    GitRunner,
    HalRunner,

    check_options_set,
    write_to_path,
    raise_and_log_error,
    ConfigError)

from buildtool.changelog_commands import PublishChangelogFactory


class InitiateReleaseBranchFactory(RepositoryCommandFactory):
  def __init__(self, **kwargs):
    repo_names = list(SPINNAKER_BOM_REPOSITORY_NAMES)
    repo_names.append(SPINNAKER_GITHUB_IO_REPOSITORY_NAME)
    super(InitiateReleaseBranchFactory, self).__init__(
        'new_release_branch', InitiateReleaseBranchCommand,
        'Create a new spinnaker release branch in each of the repos.',
        BranchSourceCodeManager,
        source_repository_names=repo_names,
        **kwargs)

  def init_argparser(self, parser, defaults):
    GitRunner.add_parser_args(parser, defaults)
    GitRunner.add_publishing_parser_args(parser, defaults)
    super(InitiateReleaseBranchFactory, self).init_argparser(parser, defaults)
    self.add_argument(
        parser, 'skip_existing', defaults, False, type=bool,
        help='Leave the existing tag if found in a repository.')
    self.add_argument(
        parser, 'delete_existing', defaults, False, type=bool,
        help='Delete the existing tag if found in a repository.')
    self.add_argument(
        parser, 'spinnaker_version', defaults, None,
        help='The version branch name should be "release-<num>.<num>.x"')


class InitiateReleaseBranchCommand(RepositoryCommandProcessor):
  def __init__(self, factory, options, **kwargs):
    super(InitiateReleaseBranchCommand, self).__init__(
        factory, options, **kwargs)
    check_options_set(options, ['spinnaker_version'])
    self.__git = GitRunner(options)

  def _do_repository(self, repository):
    git_dir = repository.git_dir
    branch = self.options.spinnaker_version

    logging.debug('Checking for branch="%s" in "%s"', branch, git_dir)
    remote_branches = [
        line.strip()
        for line in self.__git.check_run(git_dir, 'branch -r').split('\n')]

    if 'origin/' + branch in remote_branches:
      if self.options.skip_existing:
        logging.info('Branch "%s" already exists in "%s" -- skip',
                     branch, repository.origin)
        return
      elif self.options.delete_existing:
        logging.warning('Branch "%s" already exists in "%s" -- delete',
                        branch, repository.origin)
        self.__git.delete_branch_on_origin(git_dir, branch)
      else:
        raise_and_log_error(
            ConfigError(
                'Branch "{branch}" already exists in "{repo}"'.format(
                    branch=branch, repo=repository.name),
                cause='branch_exists'))

    logging.info('Creating and pushing branch "%s" to "%s"',
                 branch, repository.origin)
    self.__git.check_run(git_dir, 'checkout -b ' + branch)
    self.__git.push_branch_to_origin(git_dir, branch)


class PublishSpinnakerFactory(CommandFactory):
  """"Implements the publish_spinnaker command."""
  def __init__(self):
    super(PublishSpinnakerFactory, self).__init__(
        'publish_spinnaker', PublishSpinnakerCommand,
        'Publish a spinnaker release')

  def init_argparser(self, parser, defaults):
    super(PublishSpinnakerFactory, self).init_argparser(parser, defaults)
    HalRunner.add_parser_args(parser, defaults)
    GitRunner.add_parser_args(parser, defaults)
    GitRunner.add_publishing_parser_args(parser, defaults)
    PublishChangelogFactory().init_argparser(parser, defaults)

    self.add_argument(
        parser, 'spinnaker_release_alias', defaults, None,
        help='The spinnaker version alias to publish as.')
    self.add_argument(
        parser, 'halyard_bom_bucket', defaults, 'halconfig',
        help='The bucket manaing halyard BOMs and config profiles.')
    self.add_argument(
        parser, 'bom_version', defaults, None,
        help='The existing bom version usef for this release.')
    self.add_argument(
        parser, 'min_halyard_version', defaults, None,
        help='The minimum halyard version required.')


class PublishSpinnakerCommand(CommandProcessor):
  """"Implements the publish_spinnaker command."""
  # pylint: disable=too-few-public-methods

  def __init__(self, factory, options, **kwargs):
    super(PublishSpinnakerCommand, self).__init__(factory, options, **kwargs)
    check_options_set(options, [
        'spinnaker_version',
        'spinnaker_release_alias',
        'bom_version',
        'changelog_gist_url',
        'github_owner',
        'min_halyard_version'
    ])

    options_copy = copy.copy(options)
    self.__scm = BomSourceCodeManager(options_copy, self.get_input_dir())
    self.__hal = HalRunner(options)
    self.__git = GitRunner(options)
    self.__hal.check_property(
        'spinnaker.config.input.bucket', options.halyard_bom_bucket)
    if options.only_repositories:
      self.__only_repositories = options.only_repositories.split(',')
    else:
      self.__only_repositories = []

  def push_branches_and_tags(self, bom):
    """Update the release branches and tags in each of the BOM repositires."""
    major, minor, _ = self.options.spinnaker_version.split('.')
    branch = 'release-{major}.{minor}.x'.format(major=major, minor=minor)
    logging.info('Tagging each of the BOM service repos')

    # Run in two passes so we dont push anything if we hit a problem
    # in the tagging pass. Since we are spread against multiple repositiories,
    # we cannot do this atomically. The two passes gives us more protection
    # from a partial push due to errors in a repo.
    for which in ['tag', 'push']:
      for name, spec in bom['services'].items():
        if name in ['monitoring-third-party', 'defaultArtifact']:
          # Ignore this, it is redundant to monitoring-daemon
          continue
        if name == 'monitoring-daemon':
          name = 'spinnaker-monitoring'
        if self.__only_repositories and name not in self.__only_repositories:
          logging.debug('Skipping %s because of --only_repositories', name)
          continue
        if spec is None:
          logging.warning('HAVE bom.services.%s = None', name)
          continue

        repository = self.__scm.make_repository_spec(name)
        self.__scm.ensure_local_repository(repository)
        if which == 'tag':
          self.__branch_and_tag_repository(repository, branch)
        else:
          self.__push_branch_and_tag_repository(repository, branch)

  def __branch_and_tag_repository(self, repository, branch):
    """Create a branch and/or verison tag in the repository, if needed."""
    version = self.__scm.determine_repository_version(repository)
    tag = 'version-' + version
    self.__git.check_run(repository.git_dir, 'tag ' + tag)

  def __push_branch_and_tag_repository(self, repository, branch):
    """Push the branch and verison tag to the origin."""
    source_info = self.__scm.lookup_source_info(repository)
    tag = 'version-' + source_info.summary.version
    self.__git.push_branch_to_origin(repository.git_dir, branch)
    self.__git.push_tag_to_origin(repository.git_dir, tag)

  def _do_command(self):
    """Implements CommandProcessor interface."""
    options = self.options
    spinnaker_version = options.spinnaker_version
    publish_changelog_command = PublishChangelogFactory().make_command(options)
    changelog_gist_url = options.changelog_gist_url

    # Make sure changelog exists already.
    # If it does not then fail.
    try:
      logging.debug('Verifying changelog ready at %s', changelog_gist_url)
      urllib2.urlopen(changelog_gist_url)
    except urllib2.HTTPError as error:
      logging.error(error.message)
      raise_and_log_error(
          ConfigError(
              'Changelog gist "{url}" must exist before publising a release.'
              .format(url=changelog_gist_url),
              cause='ChangelogMissing'))

    bom = self.__hal.retrieve_bom_version(self.options.bom_version)
    bom['version'] = spinnaker_version
    bom_path = os.path.join(self.get_output_dir(), spinnaker_version + '.yml')
    write_to_path(yaml.dump(bom, default_flow_style=False), bom_path)
    self.push_branches_and_tags(bom)

    self.__hal.publish_spinnaker_release(
        spinnaker_version, options.spinnaker_release_alias, changelog_gist_url,
        options.min_halyard_version)

    logging.info('Publishing changelog')
    publish_changelog_command()


def register_commands(registry, subparsers, defaults):
  InitiateReleaseBranchFactory().register(registry, subparsers, defaults)
  PublishSpinnakerFactory().register(registry, subparsers, defaults)
