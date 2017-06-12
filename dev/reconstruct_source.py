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
import sys
import yaml

from spinnaker.run import check_run_quick

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
  'spinnaker-monitoring',
  'spinnaker'
]

class SourceReconstructor(object):

  def __init__(self, options, bom_version=None):
    self.__bom_dict = {}
    self.__bom_version = bom_version or options.bom_version

  def reconstruct_source_from_bom(self):
    """Reconstruct the Spinnaker source repositories from a BOM.
    """
    self.__load_bom()
    self.__checkout_components()

  def __load_bom(self):
    """Load the release candidate BOM into memory.
    """
    bom_yaml_string = check_run_quick('hal version bom {0} --color false --quiet'
                                      .format(self.__bom_version), echo=False).stdout.strip()
    print 'bom yaml string pulled by hal: \n\n{0}\n\n'.format(bom_yaml_string)
    self.__bom_dict = yaml.load(bom_yaml_string)

  def __checkout_components(self):
    git_prefix = self.__bom_dict['artifactSources']['gitPrefix']
    for comp in COMPONENTS:
      # We assume spinnaker/spinnaker is cloned since we're running this script.
      if comp != 'spinnaker':
        component_uri = '{prefix}/{component}.git'.format(prefix=git_prefix,
                                                          component=comp)
        check_run_quick('git clone {0}'.format(component_uri))

      entry_key = ''
      if comp == 'spinnaker-monitoring':
        entry_key = 'monitoring-daemon'
      else:
        entry_key = comp
      component_bom_entry = self.__bom_dict['services'][entry_key]
      commit = component_bom_entry['commit']
      version = component_bom_entry['version']
      dash_idx = version.index('-')
      tag = 'version-{}'.format(version[:dash_idx])
      check_run_quick('git -C {0} checkout {1}'.format(comp, commit))
      check_run_quick('git -C {0} tag {1} HEAD || true'.format(comp, tag))

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments.
    """
    parser.add_argument('--bom_version', default='', required=True,
                        help="The BOM version to reconstruct the source from.")

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    source_reconstructor = cls(options)
    source_reconstructor.reconstruct_source_from_bom()

if __name__ == '__main__':
  sys.exit(SourceReconstructor.main())
