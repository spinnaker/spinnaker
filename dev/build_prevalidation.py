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

from annotate_source import Annotator
from build_release import Builder
from generate_bom import BomGenerator
from refresh_source import Refresher


def __annotate_component(annotator, component):
  """Annotate the component's source but don't include it in the BOM.
  """
  annotator.path = component
  annotator.parse_git_tree()
  annotator.tag_head()
  annotator.delete_unwanted_tags()

def init_argument_parser(parser):
  # Don't need to init args for Annotator since BomGenerator extends it.
  BomGenerator.init_argument_parser(parser)
  Builder.init_argument_parser(parser)

def main():
  """Build a Spinnaker release to be validated by Citest.
  """
  parser = argparse.ArgumentParser()
  init_argument_parser(parser)
  options = parser.parse_args()

  annotator = Annotator(options)
  __annotate_component(annotator, 'spinnaker')
  __annotate_component(annotator, 'halyard')
  __annotate_component(annotator, 'spinnaker-monitoring')

  bom_generator = BomGenerator(options)
  bom_generator.determine_and_tag_versions()
  if options.container_builder == 'gcb':
    bom_generator.write_container_builder_gcr_config()
  elif options.container_builder == 'docker':
    bom_generator.write_docker_version_files()
  else:
    raise NotImplementedError('container_builder="{0}"'
                              .format(options.container_builder))
  Builder.do_build(options, options.build_number, options.container_builder)
  # Load version information into memory and write BOM to disk. Don't publish yet.
  bom_generator.write_bom()
  bom_generator.publish_microservice_configs()
  bom_generator.publish_boms()
  bom_generator.generate_changelog()


if __name__ == '__main__':
  sys.exit(main())
