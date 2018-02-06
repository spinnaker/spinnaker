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

"""Implements commands for buildtool."""

import logging
import time
import urllib2

from buildtool import (
    RepositoryCommandProcessor,
    RepositoryCommandFactory,
    BomSourceCodeManager,
    GitRepositorySpec,
    raise_and_log_error,
    ResponseError)


class WaitForDebiansCommand(RepositoryCommandProcessor):
  """Wait until all the repositories are available on bintray.

  This is because bintray is sometimes quite slow between the time a
  repo version is uploaded and can actually be downloaded by a debian
  package manager, thus causes failures.
  """

  def __init__(self, factory, options, **kwargs):
    super(WaitForDebiansCommand, self).__init__(factory, options, **kwargs)
    bom = self.source_code_manager.bom
    self.__bintray_url = bom['artifactSources']['debianRepository']
    self.__bintray_url_prefix = self.__bintray_url + '/pool/main/s'

  def ensure_local_repository(self, repository):
    pass

  def filter_repositories(self, source_repositories):
    repositories = super(WaitForDebiansCommand, self).filter_repositories(
        source_repositories)
    if self.options.halyard_version:
      repositories = list(repositories)
      repositories.append(GitRepositorySpec('halyard'))

    return repositories

  def _do_repository(self, repository):
    decorator = 'spinnaker-' if repository.name != 'spinnaker' else ''
    if repository.name == 'halyard':
      name = 'halyard'
      name_version = 'halyard_' + self.options.halyard_version
    else:
      name = self.scm.repository_name_to_service_name(repository.name)
      entry = self.source_code_manager.bom['services'][name]
      name_version = name + '_' + entry['version']

    url = '{prefix}/{decorator}{name}/{decorator}{version}_all.deb.asc'.format(
        prefix=self.__bintray_url_prefix, decorator=decorator,
        name=name, version=name_version)
    logging.debug('Waiting for %s', url)
    wait_until = time.time() + self.options.timeout
    counted = False
    while True:
      try:
        urllib2.urlopen(url)
        logging.debug('%s available', name_version)
        return name_version
      except urllib2.HTTPError as ex:
        if not counted:
          self.metrics.inc_counter(
              'BintrayNotReady', {'repository': repository.name},
              'Bintray not showing debian package that should be there.')
          logging.warning('%s is not yet available on bintray: %s',
                          name_version, ex)
          counted = True

        remaining = wait_until - time.time()
        if remaining <= 0:
          raise_and_log_error(
              ResponseError(
                  'Bintray not showing %s - giving up' % name_version,
                  server='bintray'))
        time.sleep(min(5, remaining))


class WaitForDebiansFactory(RepositoryCommandFactory):
  def __init__(self, **kwargs):
    super(WaitForDebiansFactory, self).__init__(
        'wait_for_debians', WaitForDebiansCommand,
        'Wait for debian packages to show up on bintray.',
        BomSourceCodeManager, **kwargs)

  def init_argparser(self, parser, defaults):
    super(WaitForDebiansFactory, self).init_argparser(parser, defaults)
    self.add_argument(parser, 'timeout', defaults, 60, type=float,
                      help='Seconds to wait before giving up.')
    self.add_argument(parser, 'halyard_version', defaults, None,
                      help='Also wait on the specified version of halyard.')


def register_commands(registry, subparsers, defaults):
  WaitForDebiansFactory().register(registry, subparsers, defaults)
