#!/usr/bin/python
#
# Copyright 2015 Google Inc. All Rights Reserved.
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
import tempfile

from pylib.fetch import check_fetch
from pylib.run import run_and_monitor
from pylib.run import run_quick
from pylib.run import check_run_quick
from pylib.run import check_run_and_monitor

import install.install_runtime_dependencies


NODE_VERSION = '0.12'
NVM_VERSION = 'v0.26.0'

__LOCAL_INVOCATION_NEXT_STEPS = """
To finish the personal developer workspace installation, do the following:
   source ${dev}/bootstrap_vm.sh

This will leave you in a 'build' subdirectory. To run Spinnaker:
   ../spinnaker/google/dev/run_dev.sh
""".format(dev=os.path.dirname(sys.argv[0]))

__STARTUP_SCRIPT_INVOCATION_NEXT_STEPS = """
To finish the personal developer workspace installation, do the following:
   Log into this vm as your development user.

   source /opt/spinnaker/install/bootstrap_vm.sh

This will leave you in a 'build' subdirectory. To run Spinnaker:
   ../spinnaker/google/dev/run_dev.sh
"""


__NVM_SCRIPT = """#!/bin/bash
export NVM_DIR=/usr/local/nvm
source /usr/local/nvm/nvm.sh

export NPM_CONFIG_PREFIX=/usr/local/node
export PATH="/usr/local/node/bin:$PATH"
"""

def init_argument_parser(parser, default_values={}):
  tmp = {}
  tmp.update(default_values)
  default_values = tmp
  if not 'apache' in default_values:
    default_values['apache'] = False

  install.install_runtime_dependencies.init_argument_parser(
      parser, default_values)
  parser.add_argument('--gcloud',
                      default=default_values.get('gcloud', False),
                      action='store_true',
                      help='Install gcloud')
  parser.add_argument('--nogcloud', dest='gcloud', action='store_false')


def install_gcloud(options):
  if not options.gcloud:
      return

  result = run_quick('gcloud --version', echo=False)
  if not result.returncode:
    print 'GCloud is already installed:\n    {version_info}'.format(
      version_info=result.stdout.replace('\n', '\n    '))
    return

  print 'Installing GCloud.'
  check_run_and_monitor('curl https://sdk.cloud.google.com | bash', echo=True)


def install_nvm(options):
  print '---------- Installing NVM ---------'
  check_run_quick('sudo chmod 775 /usr/local')
  check_run_quick('sudo mkdir -m 777 -p /usr/local/node /usr/local/nvm')

  result = check_fetch(
    'https://raw.githubusercontent.com/creationix/nvm/{nvm_version}/install.sh'
    .format(nvm_version=NVM_VERSION))

  fd, temp = tempfile.mkstemp()
  os.write(fd, result.content)
  os.close(fd)

  try:
    run_and_monitor(
        'bash -c "NVM_DIR=/usr/local/nvm source {temp}"'.format(temp=temp))
  finally:
    os.remove(temp)

#  curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.26.0/install.sh | NVM_DIR=/usr/local/nvm bash


  check_run_and_monitor('sudo bash -c "cat > /etc/profile.d/nvm.sh"',
                        input=__NVM_SCRIPT)

  print '---------- Installing Node {version} ---------'.format(
    version=NODE_VERSION)

  run_and_monitor('bash -c "source /etc/profile.d/nvm.sh'
                  '; nvm install {version}'
                  '; nvm alias default {version}"'
                  .format(version=NODE_VERSION))


def add_gcevm_to_etc_hosts(options):
  """Add gcevm as an alias for localhost to ease working with SOCKS proxy."""
  with open('/etc/hosts', 'r') as f:
      content = f.read()
  modified = content.replace('127.0.0.1 localhost',
                             '127.0.0.1 localhost gcevm')

  # Run tee so we can sudo to write to the file.
  fd, tmp = tempfile.mkstemp()
  os.write(fd, modified)
  os.close(fd)
  try:
    check_run_quick('sudo bash -c "'
                    'chown --reference=/etc/hosts {tmp}'
                    '; chmod --reference=/etc/hosts {tmp}'
                    '; mv {tmp} /etc/hosts'
                    '"'.format(tmp=tmp),
                    echo=False)
  except BaseException:
    os.remove(tmp)


def install_build_tools(options):
  check_run_and_monitor('sudo apt-get update')
  check_run_and_monitor('sudo apt-get install -y git')
  check_run_and_monitor('sudo apt-get install -y zip')
  check_run_and_monitor('sudo apt-get install -y build-essential')
  install_nvm(options)


def main():
  parser = argparse.ArgumentParser()
  init_argument_parser(parser)
  options = parser.parse_args()

  install_build_tools(options)
  install_gcloud(options)
  add_gcevm_to_etc_hosts(options)

  install.install_runtime_dependencies.install_java(options, which='jdk')
  # Force java off since we just installed it.
  options.java = False
  install.install_runtime_dependencies.install_runtime_dependencies(options)

  if os.path.dirname(sys.argv[0]) == 'dev':
    print __LOCAL_INVOCATION_NEXT_STEPS
  else:
    print __STARTUP_SCRIPT_INVOCATION_NEXT_STEPS


if __name__ == '__main__':
  main()
