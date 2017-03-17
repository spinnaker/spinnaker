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
import os
import sys
import yaml

from github import Github
from github.Gist import Gist
from github.InputFileContent import InputFileContent

from generate_bom import BomGenerator
from spinnaker.run import run_quick

VERSION = 'version'

class BomPromoter(BomGenerator):

  def __init__(self, options):
    self.__rc_version = options.rc_version
    self.__bom_dict = {}
    self.__release_version = options.release_version
    self.__changelog_file = options.changelog_file
    self.__github_token = options.github_token
    self.__github_user = options.github_user
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
    new_bom_file = '{0}.yml'.format(self.__release_version)
    self.__bom_dict[VERSION] = self.__release_version
    self.write_bom_file(new_bom_file, self.__bom_dict)
    self.publish_bom(new_bom_file)
    # Re-write the 'latest' Spinnaker version.
    # TODO(jacobkiefer): Update 'available versions' with Halyard when that feature is ready.
    self.write_bom_file('latest.yml', self.__bom_dict)
    self.publish_bom('latest.yml')

  def publish_changelog_gist(self):
    """Publish the changelog as a github gist.
    """
    g = Github(self.__github_user, self.__github_token)
    description = 'Changelog for Spinnaker {0}'.format(self.__release_version)
    with open(self.__changelog_file, 'r') as clog:
      raw_content = clog.read()
      content = InputFileContent(raw_content)
      filename = os.path.basename(self.__changelog_file)
      gist = g.get_user().create_gist(True, {filename: content}, description=description)
      print ('Wrote changelog to Gist at https://gist.github.com/{user}/{id}'
             .format(user=self.__github_user, id=gist.id))

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    bom_promoter = cls(options)
    bom_promoter.promote_bom()
    bom_promoter.publish_changelog_gist()

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments."""
    parser.add_argument('--changelog_file', default='', required=True,
                        help='The changelog to publish during this promotion.')
    parser.add_argument('--github_token', default='', required=True,
                        help="The GitHub user token with scope='gists' to write gists.")
    parser.add_argument('--rc_version', default='', required=True,
                        help='The version of the Spinnaker release candidate we are promoting.')
    parser.add_argument('--release_version', default='', required=True,
                        help='The version for the new Spinnaker release.')
    super(BomPromoter, cls).init_argument_parser(parser)

if __name__ == '__main__':
  sys.exit(BomPromoter.main())
