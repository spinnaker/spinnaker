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
import sys
import tempfile
import unittest

from spinnaker.configurator import Configurator
from spinnaker.configurator import InstallationParameters
from spinnaker.yaml_util import YamlBindings



class ConfiguratorTest(unittest.TestCase):
    def test_configuration(self):
        temp_homedir = tempfile.mkdtemp()
        home_spinnaker_dir = os.path.join(temp_homedir, '.spinnaker')
        os.mkdir(home_spinnaker_dir)
        with open(os.path.join(home_spinnaker_dir,
                               'spinnaker-local.yml'), 'w') as f:
          f.write('empty:\n')

        # We arent using HOME in a test, but want to verify that it is
        # being used.
        old_home = os.environ['HOME']
        os.environ['HOME'] = temp_homedir
        try:
            configurator = Configurator()
        finally:
            os.environ['HOME'] = old_home

        self.assertEquals(home_spinnaker_dir, configurator.user_config_dir)
        self.assertEquals(
            os.path.abspath(
                  os.path.join(os.path.dirname(sys.argv[0]), '../config')),
            configurator.installation_config_dir)

    def test_export_environment_variables(self):
        content = """
# comment
FIRST=one
# WRONG=another
SECOND=two
"""
        Configurator.export_environment_variables(content)
        self.assertEqual('one', os.environ.get('FIRST', None))
        self.assertEqual('two', os.environ.get('SECOND', None))
        self.assertIsNone(os.environ.get('WRONG', None))

    def test_update_deck_settings(self):
        temp_sourcedir = tempfile.mkdtemp()
        temp_targetdir = tempfile.mkdtemp()
        template = """
preamble
// BEGIN reconfigure_spinnaker
// var gateUrl = ${{services.gate.baseUrl}};
{gate_url_value}
// var bakeryBaseUrl = ${{services.bakery.baseUrl}};
{bakery_url_value}
// END reconfigure_spinnaker
// var gateUrl = ${{services.gate.baseUrl}};
stuff here is left along.
"""
        # This was originally just a comment, which was preserved.
        bakery_url_assignment = ("var bakeryBaseUrl = 'BAKERY_BASE_URL';"
                                 "\n# comment")

        # This was originally a different let statement that was removed.
        gate_url_assignment = "var gateUrl = 'GATE_BASE_URL';"
        bindings = YamlBindings()
        bindings.import_dict({
            'services': {
                'gate': { 'baseUrl': 'GATE_BASE_URL' },
                'bakery': { 'baseUrl': 'BAKERY_BASE_URL' },
             }
        })

        installation = InstallationParameters
        installation.INSTALLED_CONFIG_DIR = temp_sourcedir
        installation.DECK_INSTALL_DIR = temp_targetdir
        configurator = Configurator(installation_parameters=installation,
                                    bindings=bindings)
        try:
            source_settings_path = os.path.join(temp_sourcedir, 'settings.js')
            target_settings_path = os.path.join(temp_targetdir, 'settings.js')
            with open(source_settings_path, 'w') as f:
                f.write(template.format(gate_url_value="var gateUrl='old';",
                                        bakery_url_value='# comment'))

            configurator.update_deck_settings()
            with open(target_settings_path, 'r') as f:
                got = f.read()
            expect = template.format(gate_url_value=gate_url_assignment,
                                     bakery_url_value=bakery_url_assignment)
            self.assertEqual(expect, got)
        finally:
            shutil.rmtree(temp_sourcedir)
            shutil.rmtree(temp_targetdir)

    @classmethod
    def setUpClass(cls):
        # The configurator requires we be run in the build directory
        # containing deck. So let's pretend this was the case.
        cls.__orig_pwd = os.environ['PWD']
        cls.__temp_pwd = tempfile.mkdtemp()
        os.environ['PWD'] = cls.__temp_pwd
        os.mkdir(os.path.join(cls.__temp_pwd, 'deck'))

    @classmethod
    def tearDownClass(cls):
        os.environ['PWD'] = cls.__orig_pwd
        shutil.rmtree(cls.__temp_pwd)


if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(ConfiguratorTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
