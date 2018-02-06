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

# pylint: disable=missing-docstring

import os
import tempfile
import unittest
import yaml

import buildtool.__main__
import custom_test_command


buildtool.__main__.CHECK_HOME_FOR_CONFIG = False

COMMAND = custom_test_command.COMMAND

CUSTOM_DEFAULTS = {
    'input_dir': 'my/input/path',
    'one_at_a_time': True,
    'unused_argument': 'Some Value',
    custom_test_command.CUSTOM_ARG_NAME: 'Overriden Value'
}

OVERRIDE_CUSTOM_DEFAULTS = {
    'default_args_file': 'TBD below in TesTMain.setUpClass()',
    'input_dir': 'OverridenPath'
}


class TestMain(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    # pylint: disable=invalid-name
    fd, cls.defaults_file = tempfile.mkstemp(prefix='main_test_c')
    os.write(fd, yaml.dump(CUSTOM_DEFAULTS))
    os.close(fd)
    OVERRIDE_CUSTOM_DEFAULTS['default_args_file'] = cls.defaults_file

    fd, cls.override_defaults_file = tempfile.mkstemp(prefix='main_test_d')
    os.write(fd, yaml.dump(OVERRIDE_CUSTOM_DEFAULTS))
    os.close(fd)


  @classmethod
  def tearDownClass(cls):
    os.remove(cls.defaults_file)

  def get_options(self, args, modules):
    return buildtool.__main__.init_options_and_registry(args, modules)[0]

  def test_builtin_default_options(self):
    modules = [custom_test_command]
    args = [COMMAND]
    options = self.get_options(args, modules)
    self.assertEquals('source_code', options.input_dir)
    self.assertFalse(options.one_at_a_time)
    self.assertEquals(custom_test_command.CUSTOM_ARG_DEFAULT_VALUE,
                      vars(options)[custom_test_command.CUSTOM_ARG_NAME])

  def test_override_file_options(self):
    modules = [custom_test_command]
    args = ['--default_args_file', self.defaults_file, COMMAND]
    options = self.get_options(args, modules)
    self.assertEquals(CUSTOM_DEFAULTS['input_dir'], options.input_dir)
    self.assertTrue(options.one_at_a_time)
    self.assertEquals(CUSTOM_DEFAULTS[custom_test_command.CUSTOM_ARG_NAME],
                      vars(options)[custom_test_command.CUSTOM_ARG_NAME])

  def test_nested_override_file_options(self):
    modules = [custom_test_command]
    args = ['--default_args_file', self.override_defaults_file, COMMAND]
    options = self.get_options(args, modules)
    self.assertEquals(
        OVERRIDE_CUSTOM_DEFAULTS['input_dir'], options.input_dir)
    self.assertTrue(options.one_at_a_time)
    self.assertEquals(CUSTOM_DEFAULTS[custom_test_command.CUSTOM_ARG_NAME],
                      vars(options)[custom_test_command.CUSTOM_ARG_NAME])

  def test_cli_override_defaults_options(self):
    modules = [custom_test_command]
    override = 'Overriden Value'
    args = ['--default_args_file', self.defaults_file,
            '--input_dir', override,
            COMMAND,
            '--' + custom_test_command.CUSTOM_ARG_NAME,
            'XYZ']
    options = self.get_options(args, modules)
    self.assertEquals(override, options.input_dir)
    self.assertTrue(options.one_at_a_time)
    self.assertEquals('XYZ', vars(options)[custom_test_command.CUSTOM_ARG_NAME])

if __name__ == '__main__':
  import logging
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  unittest.main(verbosity=2)
