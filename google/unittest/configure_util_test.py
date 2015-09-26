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

import os
import shutil
import tempfile
import unittest

from pylib import configure_util


A_B1_CONFIG_DATA="""
# Comment
VARIABLE_A=value for a

VARIABLE_B=1
"""

B2_C_CONFIG_DATA="""
VARIABLE_B=2
VARIABLE_C=value for c
"""

class TestInstallationParameters(configure_util.InstallationParameters):
    pass

class ConfigureUtilTest(unittest.TestCase):
  # Show we can construct the binding variables from a file.
  def test_construct_variable_bindings(self):
    fd,path = tempfile.mkstemp()
    os.write(fd, A_B1_CONFIG_DATA)
    os.close(fd)
    bindings = configure_util.Bindings()
    bindings.update_from_config(path)
    os.remove(path)
    self.assertEqual(2, len(bindings.variables))
    self.assertEqual('value for a', bindings.get_variable('VARIABLE_A', None))
    self.assertEqual('1', bindings.get_variable('VARIABLE_B', None))

  # Show we can inherit data across files with correct precedence.
  def test_update_variable_bindings(self):
    bindings = configure_util.Bindings()

    for data in [A_B1_CONFIG_DATA, B2_C_CONFIG_DATA]:
      fd,path = tempfile.mkstemp()
      os.write(fd, data)
      os.close(fd)
      bindings.update_from_config(path)
      os.remove(path)

    self.assertEqual(3, len(bindings.variables))
    self.assertEqual('value for a', bindings.get_variable('VARIABLE_A', None))
    self.assertEqual('2', bindings.get_variable('VARIABLE_B', None))
    self.assertEqual('value for c', bindings.get_variable('VARIABLE_C', None))

   # Show we can replace variables the different ways the occur.
  def test_replace_content(self):
    template_data="""
declaration_a={a}
declaration_b={b}
declaration_c={c}
# Another reference to {a} {b} and {c}.
"""
    bindings = configure_util.Bindings()
    bindings.add_variable('VARIABLE_A', 'A')
    bindings.add_variable('VARIABLE_B', 'B')
    bindings.add_variable('VARIABLE_C', 'C')

    original = template_data.format(
        a='$VARIABLE_A',
        b='${VARIABLE_B: undefined}',
        c='${VARIABLE_C}')
    expect = template_data.format(
        a='A',
        b='B',
        c='C')

    got = bindings.replace_variables(original)
    self.assertEqual(expect, got)

  # Show we can replace variables in a file.
  def test_replace_variables_in_file(self):
    template_data = 'declaration_a={a}'

    fd,source_path = tempfile.mkstemp()
    os.write(fd, template_data.format(a='$VARIABLE_A'))
    os.close(fd)

    fd,target_path = tempfile.mkstemp()
    os.close(fd)

    bindings = configure_util.Bindings()
    bindings.add_variable('VARIABLE_A', 'A')

    configure_util.ConfigureUtil.replace_variables_in_file(
        source_path, target_path, bindings)

    with open(target_path) as f:
        got = f.read()
    os.remove(source_path)
    os.remove(target_path)

    self.assertEqual(template_data.format(a='A'), got)

  def test_load_bindings(self):
    root = tempfile.mkdtemp()
    config_dir = os.path.join(root, 'config')
    config_template_dir = os.path.join(root, 'template')
    try:
      TestInstallationParameters.CONFIG_DIR = config_dir
      TestInstallationParameters.CONFIG_TEMPLATE_DIR = config_template_dir
      os.mkdir(config_dir)
      os.mkdir(config_template_dir)
      with open(os.path.join(config_dir, 'spinnaker_config.cfg'), 'w') as f:
          f.write(A_B1_CONFIG_DATA)
      with open(os.path.join(config_template_dir,
                             'default_spinnaker_config.cfg'), 'w') as f:
          f.write(B2_C_CONFIG_DATA)

      util = configure_util.ConfigureUtil(TestInstallationParameters)
      bindings = util.load_bindings()
    finally:
      shutil.rmtree(root)

    self.assertEqual(4, len(bindings.variables))
    self.assertEqual('1', bindings.get_variable('VARIABLE_B', ''))
    self.assertNotEqual('',
                        bindings.get_variable('GOOGLE_MANAGED_PROJECT_ID', ''))


if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(ConfigureUtilTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
