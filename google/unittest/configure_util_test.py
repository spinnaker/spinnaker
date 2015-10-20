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

from spinnaker import configure_util
from spinnaker import yaml_util
from spinnaker.fetch import get_google_project
from spinnaker.fetch import is_google_instance

# This is a config file defining VARIABLE_A and VARIABLE_B.
# The 'B1' denotes that B has the value 1 here (other files will redefine it)
A_B1_CONFIG_DATA="""
VARIABLE_A=value for a
VARIABLE_B=1
"""

# This is a config file defining VARIABLE_B and VARIABLE_C.
# The 'B2' denotes that B has the value 2 here (other files will redefine it).
B2_C_CONFIG_DATA="""
VARIABLE_B=2
VARIABLE_C=value for c
"""

TRANSITIVE_CONFIG_DATA="""
LEVEL_2 = $LEVEL_1
LEVEL_1 = $VARIABLE_B
"""

# This is a config file defining credentials assuming we are deployed on Google
# where we are implicitly specifying the project that deploeyd Spinnaker.
GOOGLE_PROVIDER_CREDENTIALS_CONFIG_DATA="""
@GOOGLE_CREDENTIALS=test-default-account::
@GOOGLE_CREDENTIALS=test-full-account:test-project:/test/path/credentials
"""

# This is a config file defining google credentials.
GOOGLE_GENERIC_CREDENTIALS_CONFIG_DATA="""
@GOOGLE_CREDENTIALS=test-default-account:default-project:/default/path/credentials
@GOOGLE_CREDENTIALS=test-full-account:test-project:/test/path/credentials
"""


# The YAML produced from the above config.
# Note there are two projects here. The first just specifies a name and project.
# The second also includes a path.
# TODO(ewiseblatt): 2015930
# This is brittle/sensitive to the order that the yaml library
# is sorting into (it uses a dictionary so fields are not ordered).
# The string here is used for both input to load YAML and output to dump it.
# This might be fixable configuring the yaml dumper, but it isnt clear.
# So this string reflects what the yaml.dump will produce for the dicts we want.
if is_google_instance():
  # We're explicitly naming the current project where additional credentials
  # are not required. We can only do this when running on Google Cloud Platform.
  GOOGLE_PROVIDER_CREDENTIALS_YAML = """- name: test-default-account
  project: {my_project}
- jsonPath: /test/path/credentials
  name: test-full-account
  project: test-project
""".format(
    my_project=get_google_project())

if is_google_instance():
  # The credentials expected from GOOGLE_PROVIDER_CREDENTIALS_YAML.
  GOOGLE_PROVIDER_CREDENTIALS_LIST = [
    {'name': 'test-default-account',
     'project': get_google_project()},

    {'name': 'test-full-account',
     'project': 'test-project',
     'jsonPath': '/test/path/credentials'}]


GOOGLE_GENERIC_CREDENTIALS_YAML = """- jsonPath: /default/path/credentials
  name: test-default-account
  project: default-project
- jsonPath: /test/path/credentials
  name: test-full-account
  project: test-project
"""

GOOGLE_GENERIC_CREDENTIALS_LIST = [
    {'name': 'test-default-account',
     'project': 'default-project',
     'jsonPath': '/default/path/credentials'},

    {'name': 'test-full-account',
     'project': 'test-project',
     'jsonPath': '/test/path/credentials'}]


CONSTRAINED_CREDENTIALS_CONFIG_DATA="""
@GOOGLE_CREDENTIALS=test-nopath-account:google.com:test-project:
@GOOGLE_CREDENTIALS=test-full-account:google.com:test-project:/test/path/credentials
"""

CONSTRAINED_CREDENTIALS_LIST = [
    {'name': 'test-nopath-account',
     'project': 'google.com:test-project'},
    {'name': 'test-full-account',
     'project': 'google.com:test-project',
     'jsonPath': '/test/path/credentials'}]


PARAMETERIZED_CREDENTIALS_CONFIG_DATA="""
ACCOUNT=parameterized-account
PROJECT=parameterized-project
PATH=/parameterized/path
@GOOGLE_CREDENTIALS=$ACCOUNT:$PROJECT:$PATH
"""


# The YAML produced from the above config
PARAMETERIZED_CREDENTIALS_YAML = """- jsonPath: /parameterized/path
  name: parameterized-account
  project: parameterized/project

"""


PARAMETERIZED_CREDENTIALS_LIST = [
    {'name': 'parameterized-account',
    'project': 'parameterized-project',
    'jsonPath': '/parameterized/path'}]


class TestInstallationParameters(configure_util.InstallationParameters):
    pass

class ConfigureUtilTest(unittest.TestCase):
  # Show we can construct the binding variables from a file.
  def test_construct_variable_bindings(self):
    typical_file="""
# Comments and blank lines are ignored.
VALUE_ABC_DEF = abc def
VALUE_EXTRA_SPACES =   abc  xyz

VALUE_1 = 1
VALUE_XYZ=xyz
VALUE_TRUE = true

EMPTY=
# The previous was empty.

VALUE_QUOTES = "abc"
VALUE_HASH = # Keep
"""
    fd,path = tempfile.mkstemp()
    os.write(fd, typical_file)
    os.close(fd)
    bindings = configure_util.Bindings()
    bindings.update_from_config(path)
    os.remove(path)
    self.assertEqual(8, len(bindings.variables))
    self.assertEqual('abc def', bindings.get_variable('VALUE_ABC_DEF', None))
    self.assertEqual('abc  xyz',
                     bindings.get_variable('VALUE_EXTRA_SPACES', None))
    self.assertEqual('1', bindings.get_variable('VALUE_1', None))
    self.assertEqual('xyz', bindings.get_variable('VALUE_XYZ', None))
    self.assertEqual('true', bindings.get_variable('VALUE_TRUE', None))
    self.assertEqual('', bindings.get_variable('EMPTY', 'testing'))
    self.assertEqual('"abc"', bindings.get_variable('VALUE_QUOTES', None))
    self.assertEqual('# Keep', bindings.get_variable('VALUE_HASH', None))

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

  @unittest.skipUnless(is_google_instance(),
                       'This test only runs on Google')
  def test_parse_google_provider_credentials(self):
    bindings = configure_util.Bindings()
    fd,path = tempfile.mkstemp()
    os.write(fd, GOOGLE_PROVIDER_CREDENTIALS_CONFIG_DATA)
    os.close(fd)
    bindings.update_from_config(path)
    os.remove(path)
    self.assertEqual(
        GOOGLE_PROVIDER_CREDENTIALS_LIST,
        bindings.get_yaml('GOOGLE_CREDENTIALS_DECLARATION', None))

  def test_parse_google_credentials(self):
    bindings = configure_util.Bindings()
    fd,path = tempfile.mkstemp()
    os.write(fd, GOOGLE_GENERIC_CREDENTIALS_CONFIG_DATA)
    os.close(fd)
    bindings.update_from_config(path)
    os.remove(path)
    self.assertEqual(
        GOOGLE_GENERIC_CREDENTIALS_LIST,
        bindings.get_yaml('GOOGLE_CREDENTIALS_DECLARATION', None))

  def test_parse_constrained_credentials(self):
    bindings = configure_util.Bindings()
    fd,path = tempfile.mkstemp()
    os.write(fd, CONSTRAINED_CREDENTIALS_CONFIG_DATA)
    os.close(fd)
    bindings.update_from_config(path)
    os.remove(path)
    self.assertEqual(
        CONSTRAINED_CREDENTIALS_LIST,
        bindings.get_yaml('GOOGLE_CREDENTIALS_DECLARATION', None))

  @unittest.skipUnless(is_google_instance(),
                       'This test only runs on Google')
  def test_render_google_provider_credentials(self):
    bindings = configure_util.Bindings()
    fd,path = tempfile.mkstemp()
    os.write(fd, GOOGLE_PROVIDER_CREDENTIALS_CONFIG_DATA)
    os.close(fd)
    bindings.update_from_config(path)
    os.remove(path)

    template_data="""
  # before
  credentials:
      {credentials}
  # after
"""

    original = template_data.format(
        credentials='$GOOGLE_CREDENTIALS_DECLARATION')
    expect = template_data.format(
        credentials=GOOGLE_PROVIDER_CREDENTIALS_YAML.replace('\n', '\n      '))
    got = bindings.replace_variables(original)
    self.assertEqual(expect, got)


  def test_render_google_generic_credentials(self):
    bindings = configure_util.Bindings()
    fd,path = tempfile.mkstemp()
    os.write(fd, GOOGLE_GENERIC_CREDENTIALS_CONFIG_DATA)
    os.close(fd)
    bindings.update_from_config(path)
    os.remove(path)

    template_data="""
  # before
  credentials:
      {credentials}
  # after
"""

    original = template_data.format(
        credentials='$GOOGLE_CREDENTIALS_DECLARATION')
    expect = template_data.format(
        credentials=GOOGLE_GENERIC_CREDENTIALS_YAML.replace('\n', '\n      '))
    got = bindings.replace_variables(original)
    self.assertEqual(expect, got)


   # Show we can replace variables the different ways they occur.
  def test_replace_variable_content(self):
    template_data="""
declaration_a={a}
declaration_b={b}
declaration_c={c}
# Another reference to {a} {b} and {c}.
"""
    bindings = configure_util.Bindings()
    bindings.set_variable('VARIABLE_A', 'A')
    bindings.set_variable('VARIABLE_B', 'B')
    bindings.set_variable('VARIABLE_C', 'C')

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
    bindings.set_variable('VARIABLE_A', 'A')

    configure_util.ConfigureUtil.replace_variables_in_file(
        source_path, target_path, bindings)

    with open(target_path) as f:
        got = f.read()
    os.remove(source_path)
    os.remove(target_path)

    self.assertEqual(template_data.format(a='A'), got)

  def test_load_old_bindings(self):
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
          f.write(TRANSITIVE_CONFIG_DATA)
      with open(os.path.join(config_template_dir,
                             'default_spinnaker_config.cfg'), 'w') as f:
          f.write(B2_C_CONFIG_DATA)

      util = configure_util.ConfigureUtil(TestInstallationParameters)
      bindings = util.load_bindings()
    finally:
      shutil.rmtree(root)

    default_google_enabled = 'false'
    platform_variables = []
    if is_google_instance():
      my_project = get_google_project()
      platform_variables = [('GOOGLE_PRIMARY_MANAGED_PROJECT_ID', my_project)]
      default_google_enabled = 'true'

    # LEVEL_[1|1]
    # + VARIABLE_[A|B|C]
    # + 3 dynamically injected bindings
    self.assertEqual(2 + 3 + 3 + len(platform_variables),
                     len(bindings.variables))
    self.assertEqual('1', bindings.get_variable('VARIABLE_B', ''))
    self.assertEqual('false', bindings.get_variable('IGOR_ENABLED', ''))
    self.assertEqual('false', bindings.get_variable('AWS_ENABLED', ''))
    self.assertEqual('1', bindings.get_variable('LEVEL_1', ''))
    self.assertEqual('1', bindings.get_variable('LEVEL_2', ''))
    self.assertEqual(default_google_enabled,
                     bindings.get_variable('GOOGLE_ENABLED', ''))
    for platform in platform_variables:
      self.assertEqual(platform[1], bindings.get_variable(platform[0], ''))

  def process_deck_settings(self):
    bindings = yaml_util.YamlBindings()
    bindings.import_dict({'parent': {'a': 'A', 'b': 'B'}})
    original = """  
let initial = 'Initial';
# BEGIN reconfigure_spinnaker
let v1 = 'discard'
let superflous = 'removed'
# let v1 = '${parent.a}'
# let v2 = 'both ${parent.b} and ${parent.a}';

# let v2 = '${unknown:C}'
# END reconfigure_spinnaker
let final = 'Final';
"""

    expect = """
let initial = 'Initial';
# BEGIN reconfigure_spinnaker
let v1 = 'A';
let v2 = 'both B and A';
let v3 = 'C';
# END reconfigure_spinnaker
let final = 'Final';
"""

    util = configure_util.ConfigureUtil()
    self.assertEqual(expect,
                     util.process_deck_settings, original, yaml_bindings())


if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(ConfigureUtilTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
