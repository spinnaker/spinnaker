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

from publish_bom import BomPublisher
from publish_changelog import ChangelogPublisher
from reconstruct_source import SourceReconstructor


def init_argument_parser(parser):
  # ChangelogPublisher subclasses BomPublisher, only need to initialize
  # the subclass's flags.
  ChangelogPublisher.init_argument_parser(parser)

def main():
  """Publish a validated Spinnaker release.
  """
  parser = argparse.ArgumentParser()
  init_argument_parser(parser)
  options = parser.parse_args()

  reconstructor = SourceReconstructor(options, bom_version=options.rc_version)
  reconstructor.reconstruct_source_from_bom()
  bom_publisher = BomPublisher(options)
  bom_publisher.unpack_bom()
  gist_uri = bom_publisher.publish_changelog_gist()

  changelog_publisher = ChangelogPublisher(options, changelog_gist_uri=gist_uri)
  changelog_publisher.publish_changelog()

  bom_publisher.push_branch_and_tags()
  bom_publisher.publish_release_bom()


if __name__ == '__main__':
  sys.exit(main())
