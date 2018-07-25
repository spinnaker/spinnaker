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

"""Implements SpinCLI support commands for buildtool."""

import logging
import os

from collections import namedtuple

from buildtool import (
    BomSourceCodeManager,
    RepositoryCommandFactory,
    RepositoryCommandProcessor,
    check_subprocess)

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

  def _do_can_skip_repository(self, repository):
    build_version = self.scm.get_repository_service_build_version(repository)

    version_exists = [
      self.__gcs_uploader.check_file_exists(
        'spin/{}/{}/{}/spin'.format(build_version, d.dist, d.arch))
      for d in DIST_ARCH_LIST
    ]
    return all(version_exists)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    name = repository.name
    build_version = self.scm.get_repository_service_build_version(repository)

    # TODO(jacobkiefer): go version && go env?
    self.source_code_manager.ensure_local_repository(repository)
    config_root = repository.git_dir

    for dist_arch in DIST_ARCH_LIST:
      # Sub-directory the binaries are stored in are specified by
      # ${build_version}/${dist}.
      version_bin_path = ('spin/{}/{}/{}/spin'
                          .format(build_version, dist_arch.dist, dist_arch.arch))
      nightly_bin_path = ('spin/nightly/{}/{}/spin'
                          .format(dist_arch.dist, dist_arch.arch))

      logging.info('Building spin binary for %s', dist_arch)
      check_subprocess('go get -d -v', cwd=config_root)
      check_subprocess('env CGO_ENABLED=0 GOOS={} GOARCH={} go build .'
                       .format(dist_arch.dist, dist_arch.arch),
                       cwd=config_root)

      spin_path = '{}/spin'.format(config_root)
      self.__gcs_uploader.upload_from_filename(
        version_bin_path, spin_path)
      self.__gcs_uploader.upload_from_filename(
        nightly_bin_path, spin_path)
      os.remove(spin_path)

    output_dir = self.get_output_dir()
    latest_path = os.path.join(output_dir, 'latest')
    with open(latest_path, 'w') as latest_file:
      latest_file.write(build_version)
    self.__gcs_uploader.upload_from_filename(
      'spin/latest', latest_path)


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


class BuildSpinCommandFactory(RepositoryCommandFactory):
  """Implements the build_spin command."""
  # pylint: disable=too-few-public-methods

  def __init__(self):
    super(BuildSpinCommandFactory, self).__init__(
      'build_spin', BuildSpinCommand,
      'Build spin cli from the local git repository.',
      BomSourceCodeManager)

  def init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildSpinCommandFactory, self).init_argparser(parser, defaults)

    self.add_argument(
        parser, 'spin_build_bucket', defaults, None,
        help='The bucket to publish spin binaries to.')
    self.add_argument(
        parser, 'spin_build_credentials_path', defaults, None,
        help='The credentials to use to authenticate with the bucket.')


def register_commands(registry, subparsers, defaults):
  BuildSpinCommandFactory().register(registry, subparsers, defaults)
