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

from generate_bom import BomGenerator
from spinnaker.run import run_quick

VERSION = 'version'

class BomPromoter(BomGenerator):

  def __init__(self, options):
    self.__rc_version = options.rc_version
    self.__bom_dict = {}
    super(BomPromoter, self).__init__(options)

  def __unpack_bom(self):
    """Load the release candidate BOM into memory.
    """
    bom_yaml_string = run_quick('hal versions bom {0} --color false'
                                .format(self.__rc_version), echo=False).stdout.strip()
    print bom_yaml_string
    self.__bom_dict = yaml.load(bom_yaml_string)
    print self.__bom_dict

  def promote_bom(self):
    """Read, update, and promote a release candidate BOM.
    """
    self.__unpack_bom()
    dash_idx = self.__rc_version.index('-')
    if dash_idx == -1:
      raise Exception('Malformed Spinnaker release candidate version: {0}.'
                      .format(self.__rc_version))
    # Chop off build number for BOM promotion.
    new_version = self.__rc_version[:dash_idx]
    print new_version
    new_bom_file = '{0}.yml'.format(new_version)
    self.__bom_dict[VERSION] = new_version
    self.write_bom_file(new_bom_file, self.__bom_dict)
    self.publish_bom(new_bom_file)
    # Re-write the 'latest' Spinnaker version.
    # TODO(jacobkiefer): Update 'available versions' with Halyard when that feature is ready.
    self.write_bom_file('latest.yml', self.__bom_dict)
    self.publish_bom('latest.yml')

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    bom_promoter = cls(options)
    bom_promoter.promote_bom()

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments."""
    parser.add_argument(
      '--rc_version', default='', required=True,
      help='The version of the Spinnaker release candidate we are promoting.'
      'We derive the promoted version from the release candidate version.')
    super(BomPromoter, cls).init_argument_parser(parser)

if __name__ == '__main__':
  sys.exit(BomPromoter.main())
