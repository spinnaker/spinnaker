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

from collections import namedtuple

from buildtool import (
    DEFAULT_BUILD_NUMBER,

    BomSourceCodeManager,
    BranchSourceCodeManager,
    CommandProcessor,
    CommandFactory,
    RepositoryCommandFactory,
    RepositoryCommandProcessor,
    check_subprocess,
    check_subprocesses_to_logfile)

from google.cloud import storage


class DistArch(namedtuple('DistArch', ['dist', 'arch'])):
  """Describes an distribution/architecture pair.
  """
  def __repl__(self):
    return '{},{}'.format(self.dist, self.arch)


DIST_ARCH_LIST = [
    DistArch('darwin', 'amd64'),
    DistArch('linux', 'amd64')
]


class BuildSpinCommand(RepositoryCommandProcessor):
  def __init__(self, factory, options, **kwargs):
    super(BuildSpinCommand, self).__init__(
      factory, options, source_repository_names=['spin'], **kwargs)
    self.__gcs_uploader = SpinGcsUploader(options)
    self.__build_version = None  # recorded after build

  def _do_can_skip_repository(self, repository):
    self.source_code_manager.ensure_local_repository(repository)
    source_info = self.source_code_manager.refresh_source_info(
      repository, self.options.build_number)
    self.__build_version = source_info.to_build_version()

    version_exists = [
      self.__gcs_uploader.check_file_exists(
        'spin/{}/{}/{}/spin'.format(self.__build_version, d.dist, d.arch))
      for d in DIST_ARCH_LIST
    ]
    return all(version_exists)

  def build_all_distributions(self, repository):
    name = repository.name
    source_info = self.source_code_manager.refresh_source_info(
      repository, self.options.build_number)
    self.__build_version = source_info.to_build_version()
    config_root = repository.git_dir

    check_subprocess('go get -d -v', cwd=config_root)
    for dist_arch in DIST_ARCH_LIST:
      # Sub-directory the binaries are stored in are specified by
      # ${build_version}/${dist}.
      version_bin_path = ('spin/{}/{}/{}/spin'
                          .format(self.__build_version, dist_arch.dist, dist_arch.arch))

      context = '%s-%s' % (dist_arch.dist, dist_arch.arch)
      logfile = self.get_logfile_path(
          repository.name + '-build-' + context)
      logging.info('Building spin binary for %s', dist_arch)
      labels = {'repository': repository.name,
                'dist': dist_arch.dist,
                'arch': dist_arch.arch}
      env = dict(os.environ)
      env.update({'CGO_ENABLED': '0',
                  'GOOS': dist_arch.dist,
                  'GOARCH': dist_arch.arch})
      self.metrics.time_call(
          'GoBuild', labels, self.metrics.default_determine_outcome_labels,
          check_subprocesses_to_logfile, 'Building spin ' + context, logfile,
          ['go build .'], cwd=config_root, env=env)

      spin_path = '{}/spin'.format(config_root)
      self.__gcs_uploader.upload_from_filename(
        version_bin_path, spin_path)
      os.remove(spin_path)

    output_dir = self.get_output_dir()
    latest_path = os.path.join(output_dir, 'latest')
    with open(latest_path, 'w') as latest_file:
      latest_file.write(self.__build_version)
    self.__gcs_uploader.upload_from_filename(
      'spin/latest', latest_path)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    # TODO(jacobkiefer): Docker container publish
    self.source_code_manager.ensure_local_repository(repository)
    self.build_all_distributions(repository)


class SpinGcsUploader(object):
  """Utility to upload spin binaries to a credential-protected GCS bucket.
  """
  def __init__(self, options):
    if options.spin_build_credentials_path:
      self.__client = storage.Client.from_service_account_json(
        options.spin_build_credentials_path)
    else:
      self.__client = storage.Client()

    self.__bucket = options.spin_build_bucket or None

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
    return blob.read_as_string()

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
    options_copy.bom_path = None
    options_copy.bom_version = None
    options_copy.git_branch = 'master'
    options_copy.github_hostname = 'github.com'
    super(PublishSpinCommand, self).__init__(factory, options_copy, **kwargs)

    check_options_set(options, ['spin_version']) # Ensure we have a version to promote.
    bom_contents = BomSourceCodeManager.load_bom(options_copy)
    gate_entry = bom_contents.get('services', {}).get('gate', {})
    if not gate_entry:
      raise_and_log_error(
          ConfigError('No gate service entry found in bom {}'.format(bom_contents)))

    spinnaker_version = bom_contents['version']
    gate_version = gate_entry['version']
    match = re.match(r'(\d+)\.(\d+)\.(\d+)-\d+', gate_version)
    if match is None:
      raise_and_log_error(
          ConfigError('gate version {version} is not X.Y.Z-<buildnum>'
                      .format(version=gate_version)))
    self.__stable_version = '{major}.{minor}.{patch}'.format(
        major=match.group(1), minor=match.group(2), patch=match.group(3))
    self.__scm = BranchSourceCodeManager(options_copy, self.get_input_dir())
    # TODO(jacobkiefer): Add spin CLI autogenerated docs.

    dash = spinnaker_version.find('-')
    semver_str = spinnaker_version[0:dash]
    semver_parts = semver_str.split('.')
    if len(semver_parts) != 3:
      raise_and_log_error(
          ConfigError('Expected spinnaker version in the form X.Y.Z-N'))
    self.__release_branch = 'release-{maj}.{min}.x'.format(
        maj=semver_parts[0], min=semver_parts[1])
    self.__release_tag = 'version-' + semver_str

    self.__release_version = semver_str
    self.__gcs_uploader = SpinGcsUploader(options)

  def push_tag_and_branch(self, repository):
    """Pushes a stable branch and git version tag to the origin repository."""
    git_dir = repository.git_dir
    git = self.__scm.git

    release_url = repository.origin
    logging.info('Pushing branch=%s and tag=%s to %s',
                 self.__release_branch, self.__release_tag, release_url)

    existing_commit = git.query_commit_at_tag(git_dir, self.__release_tag)
    if existing_commit:
      want_commit = git.query_local_repository_commit_id(git_dir)
      if want_commit == existing_commit:
        logging.debug('Already have "%s" at %s',
                      self.__release_tag, want_commit)
        return

    git.check_run_sequence(
        git_dir,
        [
            'checkout -b ' + self.__release_branch,
            'remote add release ' + release_url,
            'push release ' + self.__release_branch,
            'tag ' + self.__release_tag,
            'push release ' + self.__release_tag
        ])

  def _promote_spin(self, repository):
    """Promote an existing build to become the spin CLI stable version."""
    options = self.options
    candidate = options.spin_version
    stable = self.__stable_version
    for d in DIST_ARCH_LIST:
      source = 'spin/{}/{}/{}/spin'.format(candidate, d.dist, d.arch)
      dest = 'spin/{}/{}/{}/spin'.format(stable, d.dist, d.arch)
      self.__gcs_uploader.copy_file(source, dest)

  def _do_command(self):
    """Implements CommandProcessor interface."""
    repository = self.__scm.make_repository_spec('spin')
    self._promote_spin(repository)
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
        parser, 'spin_build_bucket', defaults, None,
        help='The bucket to publish spin binaries to.')
    self.add_argument(
        parser, 'spin_build_credentials_path', defaults, None,
        help='The credentials to use to authenticate with the bucket.')


class PublishSpinCommandFactory(CommandFactory):
  def __init__(self):
    super(PublishSpinCommandFactory, self).__init__(
        'publish_spin', PublishSpinCommand,
        'Publish a new spin CLI release.')

  def init_argparser(self, parser, defaults):
    super(PublishSpinCommandFactory, self).init_argparser(
        parser, defaults)
    self.add_argument(
        parser, 'spin_version', defaults, None,
        help='The semantic version of the release to publish.')
    # BomSourceCodeManager adds bom_version and bom_path arguments to fetch BOMs.
    BomSourceCodeManager.add_parser_args(parser, defaults)


def register_commands(registry, subparsers, defaults):
  BuildSpinCommandFactory().register(registry, subparsers, defaults)
  PublishSpinCommandFactory().register(registry, subparsers, defaults)
