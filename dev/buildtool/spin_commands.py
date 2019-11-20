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

"""Implements Spin CLI support commands for buildtool."""

import copy
import logging
import os
import re

from collections import namedtuple
from distutils.version import LooseVersion

from buildtool import (
    DEFAULT_BUILD_NUMBER,
    SPIN_REPOSITORY_NAMES,

    BomSourceCodeManager,
    BranchSourceCodeManager,
    CommandProcessor,
    CommandFactory,
    ConfigError,
    GitRunner,
    RepositoryCommandFactory,
    RepositoryCommandProcessor,
    SemanticVersion,
    check_subprocesses_to_logfile,
    check_options_set,
    raise_and_log_error)

from google.cloud import storage


class DistArch(namedtuple('DistArch', ['dist', 'arch'])):
  """Describes an distribution/architecture pair.
  """
  def __repl__(self):
    return '{},{}'.format(self.dist, self.arch)

  @property
  def filename(self):
    return 'spin.exe' if self.dist == 'windows' else 'spin'


DIST_ARCH_LIST = [
    DistArch('darwin', 'amd64'),
    DistArch('linux', 'amd64'),
    DistArch('windows', 'amd64')
]


class BuildSpinCommand(RepositoryCommandProcessor):
  def __init__(self, factory, options, **kwargs):
    super(BuildSpinCommand, self).__init__(
      factory, options, source_repository_names=SPIN_REPOSITORY_NAMES, **kwargs)
    options_copy = copy.copy(options)
    self.__gcs_uploader = SpinGcsUploader(options)
    self.__scm = BranchSourceCodeManager(options_copy, self.get_input_dir())
    self.__build_version = None  # recorded after build
    bom_contents = BomSourceCodeManager.load_bom(options)
    gate_entry = bom_contents.get('services', {}).get('gate', {})
    if not gate_entry:
      raise_and_log_error(
          ConfigError('No gate service entry found in bom {}'.format(bom_contents)))
    self.__gate_version = gate_entry['version']

  def _do_can_skip_repository(self, repository):
    self.source_code_manager.ensure_local_repository(repository)
    source_info = self.source_code_manager.refresh_source_info(
      repository, self.options.build_number)
    self.__build_version = source_info.to_build_version()

    version_exists = [
      self.__gcs_uploader.check_file_exists(
        'spin/{}/{}/{}/{}'.format(self.__build_version, d.dist, d.arch, d.filename))
      for d in DIST_ARCH_LIST
    ]
    all_exist = all(version_exists)
    if all_exist:
      logging.info('Skipping spin CLI build since all versions exist for build version %s', self.__build_version)
    return all_exist

  def build_all_distributions(self, repository):
    spin_package_path = repository.origin
    double_slash = spin_package_path.find('//')
    # Trim spin_package_path to format go package path properly.
    if spin_package_path.find('//') != -1:
      spin_package_path = spin_package_path[double_slash+2:]
    if spin_package_path[:-1] == '/':
      spin_package_path = spin_package_path[:-1]

    source_info = self.source_code_manager.refresh_source_info(
      repository, self.options.build_number)
    self.__build_version = source_info.to_build_version()

    # NOTE: go build is opinionated about where the source repository lives --
    # it expects the source repository to be under $GOPATH/src/<package path>,
    # and will use that the source code in that directory *even if you clone
    # your own copy of the source repo*.
    #
    # To facilitate go's pattern and maintain buildtool's
    # build_input -> build_output pattern, we override go's GOPATH to our
    # filesystem destinations.

    gopath = os.path.abspath(self.get_input_dir())
    config_root = os.path.join(gopath, 'src', spin_package_path)

    env = dict(os.environ)
    env.update({'GOPATH': gopath})

    # spin source + dependency update.
    for dist_arch in DIST_ARCH_LIST:
      env.update({'GOOS': dist_arch.dist,
                  'GOARCH': dist_arch.arch})
      context = '%s-%s' % (dist_arch.dist, dist_arch.arch)
      logfile = self.get_logfile_path(
          repository.name + '-go-get-' + context)
      labels = {'repository': repository.name,
                'dist': dist_arch.dist,
                'arch': dist_arch.arch}
      cmd = 'go get -v'
      self.metrics.time_call(
          'GoGet', labels, self.metrics.default_determine_outcome_labels,
          check_subprocesses_to_logfile, 'Fetching Go packages ' + context,
          logfile, [cmd], cwd=repository.git_dir, env=env)

      # GCS sub-directory the binaries are stored in are specified by
      # ${build_version}/${dist}.
      version_bin_path = ('spin/{}/{}/{}/{}'
                          .format(self.__build_version, dist_arch.dist, dist_arch.arch, dist_arch.filename))

      context = '%s-%s' % (dist_arch.dist, dist_arch.arch)
      logfile = self.get_logfile_path(
          repository.name + '-build-' + context)
      labels = {'repository': repository.name,
                'dist': dist_arch.dist,
                'arch': dist_arch.arch}
      env.update({'CGO_ENABLED': '0',
                  'GOOS': dist_arch.dist,
                  'GOARCH': dist_arch.arch})

      version_package_prefix = os.path.join(spin_package_path, 'version')
      # Unset ReleasePhase tag for proper versions.
      # NOTE: We always set the internal version here with the next logical patch
      # version. We check in publish whether the built commit is different from
      # the commit at the last tag (and we need to publish the new version).
      ldflags = '-ldflags "-X {pref}.Version={internal_version} -X {pref}.ReleasePhase="'.format(pref=version_package_prefix,
                                                                                                 internal_version=self.__determine_internal_version(repository))
      logging.info('Building spin binary for %s with ldflags: %s', dist_arch, ldflags)
      cmd = 'go build {ldflags} .'.format(ldflags=ldflags)
      self.metrics.time_call(
          'GoBuild', labels, self.metrics.default_determine_outcome_labels,
          check_subprocesses_to_logfile, 'Building spin ' + context, logfile,
          [cmd], cwd=repository.git_dir, env=env)

      spin_path = '{}/{}'.format(repository.git_dir, dist_arch.filename)
      self.__gcs_uploader.upload_from_filename(
        version_bin_path, spin_path)
      os.remove(spin_path)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    self.source_code_manager.ensure_local_repository(repository)
    self.build_all_distributions(repository)
    built_version_file = os.path.join(self.get_output_dir(), 'built_spin_version')
    with open(built_version_file, 'w') as output_version_file:
      output_version_file.write(self.__build_version)
      logging.info('Built spin version {} written to output file {}'
                   .format(self.__build_version, built_version_file))

  def __determine_internal_version(self, repository):
    git_dir = repository.git_dir
    git = self.__scm.git
    gate_version = self.__gate_version
    return bump_spin_patch(git, git_dir, gate_version).to_version()


class SpinGcsUploader(object):
  """Utility to upload spin binaries to a credential-protected GCS bucket.
  """
  def __init__(self, options):
    if options.spin_credentials_path:
      self.__client = storage.Client.from_service_account_json(
        options.spin_credentials_path)
    else:
      self.__client = storage.Client()

    self.__bucket = options.spin_bucket or None

  def upload_from_filename(self, path, filename):
    """Upload binary from filename to bucket."""
    logging.info('Uploading local file %s to bucket %s at path %s',
                 filename, self.__bucket, path)
    bucket = self.__client.get_bucket(self.__bucket)
    upload_blob = bucket.blob(path)
    upload_blob.upload_from_filename(filename=filename)

  def check_file_exists(self, path):
    """Checks if the file exists in the bucket."""
    bucket = self.__client.get_bucket(self.__bucket)
    blob = bucket.get_blob(path)
    return bool(blob)

  def read_file(self, path):
    """Reads the contents of a GCS file."""
    bucket = self.__client.get_bucket(self.__bucket)
    blob = bucket.get_blob(path)
    return blob.download_as_string()

  def write_file(self, path, contents):
    """Writes the contents to a GCS file."""
    bucket = self.__client.get_bucket(self.__bucket)
    blob = bucket.get_blob(path)
    return blob.upload_from_string(contents)

  def copy_file(self, source, dest):
    """Copies the blob in GCS from source to dest."""
    bucket = self.__client.get_bucket(self.__bucket)
    blob = bucket.get_blob(source)
    bucket.copy_blob(blob, bucket, new_name=dest)


class PublishSpinCommand(CommandProcessor):
  """Publish Spin CLI version to the public repository."""

  def __init__(self, factory, options, **kwargs):
    options_copy = copy.copy(options)
    options_copy.git_branch = 'master'
    options_copy.github_hostname = 'github.com'
    super(PublishSpinCommand, self).__init__(factory, options_copy, **kwargs)

    check_options_set(options, ['spin_version']) # Ensure we have a version to promote.
    bom_contents = BomSourceCodeManager.load_bom(options_copy)
    gate_entry = bom_contents.get('services', {}).get('gate', {})
    if not gate_entry:
      raise_and_log_error(
          ConfigError('No gate service entry found in bom {}'.format(bom_contents)))

    self.__spinnaker_version = options.bom_version or bom_contents['version']
    self.__gate_version = gate_entry['version']
    self.__stable_version = None # Set after promote_spin.
    self.__no_changes = False # Set after promote_spin.
    self.__scm = BranchSourceCodeManager(options_copy, self.get_input_dir())
    self.__gcs_uploader = SpinGcsUploader(options)


  def push_tag_and_branch(self, repository):
    """Pushes a stable branch and git version tag to the origin repository."""
    if self.__no_changes:
      logging.info('No changes in spin since last tag, skipping branch and tag push.')
      return

    git_dir = repository.git_dir
    git = self.__scm.git

    match = re.match(r'(\d+)\.(\d+)\.(\d+)-\d+', self.__gate_version)
    if match is None:
      raise_and_log_error(
          ConfigError('gate version {version} is not X.Y.Z-<buildnum>'
                      .format(version=self.__gate_version)))
    semver_parts = self.__spinnaker_version.split('.')
    if len(semver_parts) != 3:
      raise_and_log_error(
          ConfigError('Expected spinnaker version in the form X.Y.Z-N, got {}'
                      .format(self.__spinnaker_version)))

    release_branch = 'origin/release-{maj}.{min}.x'.format(
        maj=semver_parts[0], min=semver_parts[1])
    release_tag = 'version-' + self.__stable_version
    logging.info('Pushing branch=%s and tag=%s to %s',
                 release_branch, release_tag, repository.origin)
    git.check_run_sequence(
        git_dir,
        [
            'checkout ' + release_branch,
            'tag ' + release_tag,
        ])
    git.push_tag_to_origin(git_dir, release_tag)

  def promote_spin(self, repository):
    """Promote an existing build to become the spin CLI stable version."""
    git_dir = repository.git_dir
    git = self.__scm.git

    if len(self.__spinnaker_version.split('.')) != 3:
      raise_and_log_error(
          ConfigError('Expected spinnaker version in the form X.Y.Z-N'))

    next_spin_semver = bump_spin_patch(git, git_dir, self.__gate_version)
    gate_major = next_spin_semver.major
    gate_min = next_spin_semver.minor
    last_patch = str(max(next_spin_semver.patch - 1, 0))
    self.__stable_version = next_spin_semver.to_version()
    logging.info('calculated new stable version: {}'
                 .format(self.__stable_version))
    # NOTE: major and minor for spin binaries are dependent on gate versions.
    last_tag = 'version-{maj}.{min}.{patch}'.format(maj=gate_major,
                                                    min=gate_min,
                                                    patch=last_patch)
    self.__no_changes = git.query_local_repository_commit_id(git_dir) == git.query_commit_at_tag(git_dir, last_tag)

    candidate = self.options.spin_version
    if self.__no_changes:
      logging.info('No changes in spin since last tag, skipping publish.')
    else:
      for d in DIST_ARCH_LIST:
        source = 'spin/{}/{}/{}/{}'.format(candidate, d.dist, d.arch, d.filename)
        dest = 'spin/{}/{}/{}/{}'.format(self.__stable_version, d.dist, d.arch, d.filename)
        self.__gcs_uploader.copy_file(source, dest)

      self.__update_release_latest_file(gate_major, gate_min)
      self.__update_global_latest_file()

  def __update_release_latest_file(self, gate_major, gate_minor):
      output_dir = self.get_output_dir()
      release_latest_file = '{}.{}.x-latest'.format(gate_major, gate_minor)
      with open(os.path.join(output_dir, release_latest_file), 'w') as lf:
        lf.write(self.__stable_version)

      release_latest_path = 'spin/{}.{}.x-latest'.format(gate_major, gate_minor)
      self.__gcs_uploader.upload_from_filename(release_latest_path, os.path.join(output_dir, release_latest_file))

  def __update_global_latest_file(self):
    output_dir = self.get_output_dir()
    global_latest_version = self.__gcs_uploader.read_file('spin/latest')

    if LooseVersion(self.__stable_version) > LooseVersion(global_latest_version):
      with open(os.path.join(output_dir, 'latest'), 'w') as lf:
        lf.write(self.__stable_version)
      self.__gcs_uploader.upload_from_filename('spin/latest', os.path.join(output_dir, 'latest'))

  def _do_command(self):
    """Implements CommandProcessor interface."""
    # TODO(jacobkiefer): Add spin CLI docs generation.
    repository = self.__scm.make_repository_spec('spin')
    git = self.__scm.git
    git.clone_repository_to_path(repository)
    self.promote_spin(repository)
    self.push_tag_and_branch(repository)


class BuildSpinCommandFactory(RepositoryCommandFactory):
  """Implements the build_spin command."""
  # pylint: disable=too-few-public-methods

  def __init__(self):
    super(BuildSpinCommandFactory, self).__init__(
      'build_spin', BuildSpinCommand,
      'Build spin cli from the local git repository.',
      BranchSourceCodeManager)

  def init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildSpinCommandFactory, self).init_argparser(parser, defaults)

    self.add_argument(
        parser, 'build_number', defaults, DEFAULT_BUILD_NUMBER,
        help='The the build number to use when building spin.')
    self.add_argument(
        parser, 'spin_bucket', defaults, None,
        help='The bucket to publish spin binaries to.')
    self.add_argument(
        parser, 'spin_credentials_path', defaults, None,
        help='The credentials to use to authenticate with the bucket.')
    # BomSourceCodeManager adds bom_version and bom_path arguments to fetch BOMs.
    BomSourceCodeManager.add_parser_args(parser, defaults)


class PublishSpinCommandFactory(CommandFactory):
  def __init__(self):
    super(PublishSpinCommandFactory, self).__init__(
        'publish_spin', PublishSpinCommand,
        'Publish a new spin CLI release.')

  def init_argparser(self, parser, defaults):
    GitRunner.add_parser_args(parser, defaults)
    GitRunner.add_publishing_parser_args(parser, defaults)
    super(PublishSpinCommandFactory, self).init_argparser(
        parser, defaults)
    self.add_argument(
        parser, 'spin_bucket', defaults, None,
        help='The bucket to publish spin binaries to.')
    self.add_argument(
        parser, 'spin_credentials_path', defaults, None,
        help='The credentials to use to authenticate with the bucket.')
    self.add_argument(
        parser, 'spin_version', defaults, None,
        help='The semantic version of the release to publish.')
    # BomSourceCodeManager adds bom_version and bom_path arguments to fetch BOMs.
    BomSourceCodeManager.add_parser_args(parser, defaults)


def bump_spin_patch(git, git_dir, gate_version):
  '''Calculates the next spin version from the gate version and previous spin tags.

  Spin is coupled to the Gate major and minor version.
  Gate is a routing server, so features and breaking changes in Gate
  must be reflected in spin since it is a client.

  :param git: git support helper class.
  :param git_dir: spin git directory.
  :param gate_version: gate version to align spin version with in <maj>.<min>.<patch>-<buildnum> format.
  :return: (SemanticVersion) Next semver spin version.
  '''
  gate_version_parts = gate_version.split('-')
  if len(gate_version_parts) != 2:
    raise_and_log_error(
        ValueError('Malformed gate version {}'.format(gate_version)))

  # SemanticVersion.make() expects a tag, so formulate the input gate version as a tag.
  gate_semver = SemanticVersion.make('version-{}'.format(gate_version_parts[0]))
  tag_pattern = r'version-{maj}.{min}.(\d+)'.format(maj=gate_semver.major,
                                                    min=gate_semver.minor)
  tag_matcher = re.compile(tag_pattern)
  tags = git.fetch_tags(git_dir)

  logging.info('searching git tags {} for patterns matching {}'.format(tags, tag_pattern))
  matching_semvers = [SemanticVersion.make(t) for t in tags if tag_matcher.match(t)]
  logging.info('found matching semvers: {}'.format(matching_semvers))
  if matching_semvers:
    max_semver = max(matching_semvers)
    next_semver = max_semver.next(SemanticVersion.PATCH_INDEX)
    patch = next_semver.patch
  else:
    patch = '0'

  # SemanticVersion.make() expects a tag, so formulate the input gate version as a tag.
  spin_semver = SemanticVersion.make('version-{major}.{minor}.{patch}'
                                     .format(major=gate_semver.major,
                                             minor=gate_semver.minor,
                                             patch=patch))
  logging.info('calculated next spin patch version: {}'.format(spin_semver))
  return spin_semver


def register_commands(registry, subparsers, defaults):
  BuildSpinCommandFactory().register(registry, subparsers, defaults)
  PublishSpinCommandFactory().register(registry, subparsers, defaults)
