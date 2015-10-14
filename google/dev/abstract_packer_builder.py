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

# Creates an image in the default project named with the release name.
# python create_google_image.py --release_path $RELEASE_PATH

import argparse
import os
import sys
import tempfile

from pylib.run import check_run_and_monitor
from pylib.run import check_run_quick
from pylib.run import run_quick

class AbstractPackerBuilder(object):
  PACKER_TEMPLATE = "Undefined"   # specializations should override

  @property
  def packer_output(self):
    """The output from the packer process once it has completed."""
    return self.__output

  @property
  def options(self):
    """The options parsed from the commandline."""
    return self.__options

  def __init__(self, options):
    self.__options = options
    self.__installer_path = None
    self.__packer_vars = []
    self.__raw_args = sys.argv[1:]
    self.__var_map = {}
    self.__output = None

    # This is used to clean up error reporting
    self.__in_subprocess = False

  def remove_raw_arg(self, name):
    """Remove the raw argument data for the given name."""
    result = []
    args = self.__raw_args
    flag = '--' + name
    for i in range(len(args)):
        if not args[i].startswith(flag):
          result.append(args[i])
          continue
        if i + 1 < len(args) and not args[i + 1].startswith('--'):
          # argument was in form --flag value
          # so skip the value part as well.
          i += 1

    self.__raw_args = result

  def add_packer_variable(self, name, value):
    """Adds variable to pass to packer.

    Args:
      name [string]: The name of the variable.
      value [string]: The value to pass.
    """
    self.__var_map[name] = value

  def _do_prepare(self):
    """Hook for specialized builders to prepare arguments if needed."""
    pass

  def _do_cleanup(self):
    """Hook for specialized builders to cleanup if needed."""
    pass

  def _do_next_steps(self):
    """Hook for specialized builders to add followup instructions."""
    pass

  def create_image(self):
    """Runs the process for creating an image.

    Prepare, Build, Cleanup.
    """
    self.__prepare()
    try:
      self.__in_subprocess = True
      result = check_run_and_monitor(
          'packer build {vars} {packer}'
          .format(vars=' '.join(self.__packer_vars),
                  packer=self.PACKER_TEMPLATE),
          echo=True)
      self.__in_subprocess = False

      self.__output = result.stdout
    finally:
      self.__cleanup()

  def __prepare(self):
    """Internal helper function implementing the Prepare step.

    Calls _do_prepare to allow specialized classes to hook in.
    """
    fd,self.__installer_path = tempfile.mkstemp()
    os.close(fd)
    self.__var_map['installer_path'] = self.__installer_path

    if self.options.release_path.startswith('gs://'):
      program = 'gsutil'
    elif self.options.release_path.startswith('s3://'):
      program = 'aws s3'
    else:
      raise ValueError('--release_path must be either GCS or S3, not "{path}".'
                       .format(path=self.options.release_path))

    self.__in_subprocess = True
    check_run_quick(
        '{program} cp {release}/install/install_spinnaker.py.zip {path}'
        .format(program=program,
                release=self.options.release_path,
                path=self.__installer_path))
    self.__in_subprocess = False

    self._do_prepare()
    self.__add_args_to_map()
    self.__packer_vars = ['-var "{name}={value}"'
                          .format(name=name, value=value)
                          for name,value in self.__var_map.items()]

  def __cleanup(self):
    """Internal helper function implementing the Cleanup step.

    Calls _do_cleanup to allow specialized classes to hook in.
    """
    if self.__installer_path:
      try:
        os.remove(self.__installer_path)
      except:
        pass
    self._do_cleanup()

  def __add_args_to_map(self):
    """Add remaining raw args to the packer variable map.

    This is a helper method for internal prepare()
    """
    for i in range(len(self.__raw_args)):
      arg = self.__raw_args[i]
      if not arg.startswith('--'):
        raise ValueError('Unexpected argument "{arg}"'.format(arg))
      arg = arg[2:]
      eq = arg.find('=')
      if eq > 0:
         self.__var_map[arg[0:eq]] = arg[eq + 1:]
      else:
         self.__var_map[arg] = a[i + 1]
         i += 1

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize the command-line parameters."""

    parser.add_argument(
          '--release_path', required=True,
          help='URI to the release to install on a storage service.')

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    parser.description = (
        'Additional command line variables are passed through to {packer}'
        '\nSee the "variables" section in the template for more options.'
        .format(packer=cls.PACKER_TEMPLATE))

    cls.init_argument_parser(parser)

    options, unknown = parser.parse_known_args()
    builder = cls(options)

    try:
      builder.create_image()
      print builder._do_get_next_steps()
    except BaseException as ex:
      if builder.__in_subprocess:
        # If we failed in packer, just exit
        sys.exit(-1)

      # This was our programming error, so get a stack trace.
      raise
