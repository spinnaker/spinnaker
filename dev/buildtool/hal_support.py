#!/usr/bin/python
#
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

"""Helper class for administering halyard.

This module is a helper module for commands that need to interact with the
halyard runtime repositories (build and release info). It has nothing to
do with the static git source repositories.

This module encapsulates knowledge of how to administer halyard, and
more importantly to ensure that the locally running halyard is configured
as intended when publishing artifacts. This is a safety measure to ensure
that custom or test builds do not show up in the production repositories
unless the builds explicitly asked for the production repositories.
"""

import logging
import os
import urllib2
import yaml

from buildtool import (
    add_parser_argument,
    check_subprocess,
    raise_and_log_error,
    ConfigError,
    ResponseError)


class HalRunner(object):
  """Encapsulates knowledge of administering halyard releases."""

  @staticmethod
  def add_parser_args(parser, defaults):
    """Add parser arguments used to administer halyard."""
    if hasattr(parser, 'added_halrunner'):
      return
    parser.added_halrunner = True
    add_parser_argument(
        parser, 'hal_path', defaults, '/usr/local/bin/hal',
        help='Path to local Halyard "hal" CLI.')
    add_parser_argument(
        parser, 'halyard_daemon', defaults, 'localhost:8064',
        help='Network location for halyard server.')

  @property
  def options(self):
    """Returns bound options."""
    return self.__options

  def check_property(self, name, want):
    """Check a configuration property meets our needs."""
    have = self.__halyard_runtime_config[name]
    if have == want:
      logging.debug('Confirmed Halyard server is configured with %s="%s"',
                    name, have)
    else:
      raise_and_log_error(
          ConfigError(
              'Halyard server is not configured to support this request.\n'
              'It is using {name}={have!r} rather than {want!r}.\n'
              'You will need to modify /opt/spinnaker/config/halyard-local.yml'
              ' and restart the halyard server.'.format(
                  name=name, have=have, want=want),
              cause='config/halyard'))

  def __init__(self, options):
    self.__options = options
    self.__hal_path = options.hal_path

    logging.debug('Retrieving halyard runtime configuration.')
    url = 'http://' + options.halyard_daemon + '/resolvedEnv'
    response = urllib2.urlopen(url)
    if response.getcode() >= 300:
      raise_and_log_error(
          ResponseError(
              '{url}: {code}\n{body}'.format(
                  url=url, code=response.getcode(), body=response.read()),
              server='halyard'))
    self.__halyard_runtime_config = yaml.load(response)

  def check_writer_enabled(self):
    """Ensure halyard has writerEnabled true."""
    self.check_property('spinnaker.config.input.writerEnabled', 'true')

  def check_run(self, command_line):
    """Run hal with the supplied command_line."""
    args = ' --color false --daemon-endpoint http://{daemon} '.format(
        daemon=self.__options.halyard_daemon)
    return check_subprocess(self.__hal_path + args + command_line)

  def publish_profile(self, component, profile_path, bom_path):
    """Publish the profile for the given component for the given bom."""
    logging.info('Publishing %s profile=%s for bom=%s',
                 component, profile_path, bom_path)
    self.check_run('admin publish profile ' + component
                   + ' --bom-path ' + bom_path
                   + ' --profile-path ' + profile_path)

  def publish_bom_path(self, path):
    """Publish a bom path via halyard."""
    logging.info('Publishing bom from %s', path)
    self.check_run('admin publish bom --bom-path ' + os.path.abspath(path))

  def retrieve_bom_version(self, version):
    """Retrieve the specified BOM version as a dict."""
    logging.info('Getting bom version %s', version)
    content = self.check_run('version bom ' + version + ' --quiet')
    return yaml.load(content)

  def publish_halyard_release(self, release_version):
    """Make release_version available as the latest version."""
    logging.info('Publishing latest halyard version "%s"', release_version)
    self.check_run('admin publish latest-halyard ' + release_version)

  def publish_spinnaker_release(
      self, release_version, alias_name, changelog_uri, min_halyard_version,
      latest=True):
    """Release spinnaker version to halyard repository."""
    logging.info('Publishing spinnaker version "%s" to halyard',
                 release_version)
    self.check_run('admin publish version --version "{version}"'
                   ' --alias "{alias}" --changelog {changelog}'
                   ' --minimum-halyard-version {halyard_version}'
                   .format(version=release_version, alias=alias_name,
                           changelog=changelog_uri,
                           halyard_version=min_halyard_version))
    if latest:
      logging.info(
          'Publishing spinnaker verison "%s" as latest', release_version)
      self.check_run('admin publish latest "{version}"'.format(
          version=release_version))
