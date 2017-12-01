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

"""Orchestrates building component GCP VM images for a Spinnaker release.
"""

import argparse
import multiprocessing
import os
import sys


from build_release import run_shell_and_log, BuildFailure


SUBSYSTEM_LIST = ['clouddriver', 'deck', 'echo', 'fiat', 'front50', 'gate',
                  'igor', 'orca', 'rosco', 'consul', 'redis', 'vault']


class ComponentVmBuilder(object):
  def __init__(self, options):
    self.__account = options.account
    self.__build_failures = []
    self.__build_project = options.build_project
    self.__install_script = options.install_script
    self.__publish_project = options.publish_project
    self.__publish_script = options.publish_script
    self.__version = options.version
    self.__zone = options.zone

  def __do_build(self, artifact):
    cmds = [
      './build_google_component_image.sh --artifact {artifact} --account {account} '
      '--build_project {build_project} --install_script {install_script} '
      '--publish_project {publish_project} --publish_script {publish_script} '
      '--version {version} --zone {zone}'.format(
        artifact=artifact,
        account=self.__account,
        build_project=self.__build_project,
        install_script=self.__install_script,
        publish_project=self.__publish_project,
        publish_script=self.__publish_script,
        version=self.__version,
        zone=self.__zone
      )
    ]
    try:
      logfile = '{artifact}-vm-build.log'.format(artifact=artifact)
      run_shell_and_log(cmds, logfile, cwd=os.path.abspath(os.path.dirname(__file__)))
    except Exception as ex:
      self.__build_failures.append(BuildFailure(artifact, ex))

  def __check_build_failures(self):
    if self.__build_failures:
      msg_lines = ['Builds failed:\n']
      should_exit = False
      for failure in self.__build_failures:
        if failure.component in SUBSYSTEM_LIST:
          should_exit = True
          msg_lines.append('Building component {} failed with exception:'
                           '\n{}\n'.format(failure.component, failure.exception))
      if should_exit:
        msg = '\n'.join(msg_lines)
        raise RuntimeError(msg)

  def build_component_images(self):
    pool = multiprocessing.pool.ThreadPool(processes=5) # Probably should be an argument.
    pool.map(self.__do_build, SUBSYSTEM_LIST)
    self.__check_build_failures()

  @classmethod
  def init_argument_parser(cls, parser):
    parser.add_argument('--account',
                        help='GCP service account to use.')
    parser.add_argument('--build_project',
                        help='GCP project to build the component images in.')
    parser.add_argument('--install_script',
                        help='Path to the install script to run when building'
                        'the component image.')
    parser.add_argument('--publish_project',
                        help='GCP project to publish the component images to.')
    parser.add_argument('--publish_script',
                        help='Script to use to publish the component images.')
    parser.add_argument('--version',
                        help='Spinnaker release version to use.')
    parser.add_argument('--zone',
                        help='GCP zone to use when creating the images.')

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()
    vm_image_builder = cls(options)
    vm_image_builder.build_component_images()


if __name__ == '__main__':
  sys.exit(ComponentVmBuilder.main())
