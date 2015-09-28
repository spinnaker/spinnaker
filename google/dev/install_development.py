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

from install.install_utils import fetch_or_die
from install.install_utils import run
from install.install_utils import run_or_die

import install.install_runtime_dependencies


NODE_VERSION = '0.12'
NVM_VERSION = 'v0.26.0'

__LOCAL_INVOCATION_NEXT_STEPS = """
To finish the personal developer workspace installation, do the following:
   source /opt/spinnaker/install/bootstrap_vm.sh

This will leave you in a 'build' subdirectory. To run Spinnaker:
   ../spinnaker/google/dev/run_dev.sh
"""

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
                      default=default_values.get('gcloud', True),
                      action='store_true',
                      help='Do not install gcloud')
  parser.add_argument('--nogcloud', dest='gcloud', action='store_false')


def install_gcloud(options):
  if not options.gcloud:
      return

  code, stdout, stderr = run('gcloud --version', echo=False)
  if not code:
    print 'GCloud is already installed:\n    {version_info}'.format(
      version_info=stdout.replace('\n', '\n    '))
    return

  print 'Installing GCloud.'
  run_or_die('curl https://sdk.cloud.google.com | bash', echo=True)


def install_nvm(options):
  print '---------- Installing NVM ---------'
  run_or_die('sudo chmod 775 /usr/local')
  run_or_die('sudo mkdir -m 777 -p /usr/local/node /usr/local/nvm')

  content = fetch_or_die(
    'https://raw.githubusercontent.com/creationix/nvm/{nvm_version}/install.sh'
    .format(nvm_version=NVM_VERSION))

  fd, temp = tempfile.mkstemp()
  os.write(fd, content)
  os.close(fd)

  try:
    run('bash -c "NVM_DIR=/usr/local/nvm source {temp}"'.format(temp=temp))
  finally:
    os.remove(temp)

#  curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.26.0/install.sh | NVM_DIR=/usr/local/nvm bash


  run_or_die('sudo cat > /etc/profile.d/nvm.sh', input=__NVM_SCRIPT)

  print '---------- Installing Node {version} ---------'.format(
    version=NODE_VERSION)

  run('bash -c "source /etc/profile.d/nvm.sh'
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
  run_or_die('sudo tee /etc/hosts', input=modified, echo=False)


def install_build_tools(options):
  run_or_die('sudo apt-get update')
  run_or_die('sudo apt-get install -y git')
  run_or_die('sudo apt-get install -y zip')
  run_or_die('sudo apt-get install -y build-essential')
  install_nvm(options)


if __name__ == '__main__':
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
