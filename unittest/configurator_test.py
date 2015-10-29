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
        configurator = Configurator()
        self.assertEquals(os.path.join(os.environ['HOME'], '.spinnaker'),
                          configurator.user_config_dir)
        self.assertEquals(
            os.path.abspath(
                  os.path.join(os.path.dirname(sys.argv[0]), '../config')),
            configurator.installation_config_dir)
        with self.assertRaises(RuntimeError):
            # The test isnt run from  a build directory so this raises
            configurator.deck_install_dir

    def test_update_deck_settings(self):
        temp_sourcedir = tempfile.mkdtemp()
        temp_targetdir = tempfile.mkdtemp()
        template = """
preamble
// BEGIN reconfigure_spinnaker
// let gateUrl = ${{services.gate.baseUrl}};
{gate_url_value}
// let bakeryBaseUrl = ${{services.bakery.baseUrl}};
{bakery_url_value}
// END reconfigure_spinnaker
// let gateUrl = ${{services.gate.baseUrl}};
stuff here is left along.
"""
        # This was originally just a comment, which was preserved.
        bakery_url_assignment = ("let bakeryBaseUrl = 'BAKERY_BASE_URL';"
                                 "\n# comment")

        # This was originally a different let statement that was removed.
        gate_url_assignment = "let gateUrl = 'GATE_BASE_URL';"
        bindings = YamlBindings()
        bindings.import_dict({
            'services': {
                'gate': { 'baseUrl': 'GATE_BASE_URL' },
                'bakery': { 'baseUrl': 'BAKERY_BASE_URL' },
             }
        })
        configurator = Configurator(bindings=bindings)
        configurator.installation.INSTALLED_CONFIG_DIR = temp_sourcedir
        configurator.installation.DECK_INSTALL_DIR = temp_targetdir
        try:
            source_settings_path = os.path.join(temp_sourcedir, 'settings.js')
            target_settings_path = os.path.join(temp_targetdir, 'settings.js')
            with open(source_settings_path, 'w') as f:
                f.write(template.format(gate_url_value="let gateUrl='old';",
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
            

if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(ConfiguratorTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
