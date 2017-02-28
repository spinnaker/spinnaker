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

import argparse
import datetime
import os
import socket
import sys
import yaml

from annotate_source import Annotator
from spinnaker.run import run_quick

SERVICES = 'services'
VERSION = 'version'

class BomGenerator(Annotator):
  """Provides facilities for generating the Bill of Materials file for the
  Spinnaker product release.
  """

  COMPONENTS = [
    'clouddriver',
    'deck',
    'echo',
    'front50',
    'gate',
    'igor',
    'orca',
    'rosco',
    'fiat',
  ]

  def __init__(self, options):
    self.__base_dir = options.base_dir
    self.__component_versions = {}
    super(BomGenerator, self).__init__(options)

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments."""
    parser.add_argument('--base_dir', default='.',
                        help="Base directory containing the component's git repositories as subdirectories.")
    super(BomGenerator, cls).init_argument_parser(parser)

  def write_bom(self):
    output_yaml = {SERVICES: {}}

    breaking_change = False
    feature = False
    for comp in self.__component_versions:
      version_bump = self.__component_versions[comp]

      if version_bump.major == True:
        breaking_change = True
      elif version_bump.minor == True:
        feature = True

      next_tag_with_build = '{0}-{1}'.format(version_bump.version_str,
                                             self.build_number)
      first_dash_idx = next_tag_with_build.index('-')
      gradle_version = next_tag_with_build[first_dash_idx + 1:]
      version_entry = {VERSION: gradle_version}
      output_yaml[SERVICES][comp] = version_entry

    # Current publicly released version of Spinnaker product.
    curr_version = run_quick('hal versions latest --color false', echo=False).stdout.strip()
    print curr_version
    major, minor, patch = curr_version.split('.')
    toplevel_version = ''
    if breaking_change == True:
      toplevel_version = str(int(major) + 1) + '.0.0'
    elif feature == True:
      toplevel_version =  major + '.' + str(int(minor) + 1) + '.0'
    else:
      toplevel_version =  major + '.' + minor + '.' + str(int(patch) + 1)

    toplevel_with_build = '{0}-{1}'.format(toplevel_version, self.build_number)
    output_yaml[VERSION] = toplevel_with_build
    bom_file = '{0}.yml'.format(toplevel_with_build)
    with open(os.path.join(self.__base_dir, bom_file), 'w') as output_file:
      timestamp = '{:%Y-%m-%d %H:%M:%S}'.format(datetime.datetime.now())
      tracking = '# Generated on host {0} on {1}'.format(socket.gethostname(),
                                                         timestamp)
      output_file.write(tracking + '\n')
      yaml.dump(output_yaml, output_file, default_flow_style=False)
      print 'Wrote BOM to {0}.'.format(bom_file)

  def determine_and_tag_versions(self):
    for comp in self.COMPONENTS:
      self.path = os.path.join(self.__base_dir, comp)
      self.parse_git_tree()
      version_bump = self.tag_head()
      self.__component_versions[comp] = version_bump
      self.delete_unwanted_tags()

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    cls.init_extra_argument_parser(parser)
    options = parser.parse_args()

    bom_generator = cls(options)
    bom_generator.determine_and_tag_versions()
    bom_generator.write_bom()

if __name__ == '__main__':
  sys.exit(BomGenerator.main())
