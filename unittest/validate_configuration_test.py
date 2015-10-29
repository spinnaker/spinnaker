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
import tempfile
import unittest

from spinnaker.configurator import Configurator
from spinnaker.validate_configuration import ValidateConfig
from spinnaker.yaml_util import YamlBindings


class ValidateConfigurationTest(unittest.TestCase):
    def test_is_reference_good(self):
        bindings = YamlBindings()
        validator = ValidateConfig(
            configurator=Configurator(bindings=bindings))
        self.assertTrue(validator.is_reference('${a}'))
        self.assertTrue(validator.is_reference('${a:value'))

    def test_is_reference_bad(self):
        bindings = YamlBindings()
        validator = ValidateConfig(
            configurator=Configurator(bindings=bindings))
        self.assertFalse(validator.is_reference('str'))
        self.assertFalse(validator.is_reference('true'))
        self.assertFalse(validator.is_reference('0'))
        self.assertFalse(validator.is_reference('not ${a}'))
        
    def test_true_false_good(self):
        bindings = YamlBindings()
        bindings.import_dict(
            {'t': True, 'f':False, 'indirect':'${t}', 'default': '${x:true}'})
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertTrue(validator.verify_true_false('t'))
        self.assertTrue(validator.verify_true_false('f'))
        self.assertTrue(validator.verify_true_false('indirect'))
        self.assertTrue(validator.verify_true_false('default'))

    def test_true_false_bad(self):
        bindings = YamlBindings()
        bindings.import_dict(
            {'t': 'true', 'f':'false', 'indirect':'${t}', 'default': '${x:0}'})
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertFalse(validator.verify_true_false('t'))
        self.assertFalse(validator.verify_true_false('f'))
        self.assertFalse(validator.verify_true_false('indirect'))
        self.assertFalse(validator.verify_true_false('default'))
        self.assertEqual(4, len(validator.errors))
        self.assertEqual(0, len(validator.warnings))
        self.assertEqual(
            ["t='true' is not valid. Must be boolean true or false.",
             "f='false' is not valid. Must be boolean true or false.",
             "indirect='true' is not valid. Must be boolean true or false.",
             "default=0 is not valid. Must be boolean true or false."],
            validator.errors)

    def test_true_false_not_resolved(self):
        bindings = YamlBindings()
        bindings.import_dict({'indirect': '${t}'})
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertFalse(validator.verify_true_false('indirect'))
        self.assertEqual('Missing "indirect".', validator.errors[0])

    def test_verify_host_good(self):
        bindings = YamlBindings()
        bindings.import_dict({
             'short': 'localhost',
             'numeric': '0.0.0.0',
             'dot1': 'my.host',
             'dot2': 'my.host.name',
             'hyphen': 'this.is-a.host1234',

             # Note we accept this because the validation is loose.
             'illegal': '-acceptable-even-though-invalid-'
        })
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertTrue(validator.verify_host('short', True))
        self.assertTrue(validator.verify_host('numeric', True))
        self.assertTrue(validator.verify_host('dot1', True))
        self.assertTrue(validator.verify_host('dot2', True))
        self.assertTrue(validator.verify_host('hyphen', True))
        self.assertTrue(validator.verify_host('illegal', True))
        
    def test_verify_host_bad(self):
        bindings = YamlBindings()
        bindings.import_dict({
             'upper': 'LOCALHOST',
             'under': 'local_host',
             'space': 'local host',
             'slash': 'localhost/foo',
             'colon': 'localhost:80',
        })
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertFalse(validator.verify_host('upper', True))
        self.assertFalse(validator.verify_host('under', True))
        self.assertFalse(validator.verify_host('space', True))
        self.assertFalse(validator.verify_host('slash', True))
        self.assertFalse(validator.verify_host('colon', True))
        self.assertTrue(validator.errors[0].startswith('name="LOCALHOST"'))

    def test_verify_host_missing(self):
        bindings = YamlBindings()
        bindings = {'unresolved': '${whatever}'}

        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertFalse(validator.verify_host('missing', True))
        self.assertFalse(validator.verify_host('unresolved', True))
        self.assertEquals('No host provided for "missing".',
                          validator.errors[0])
        self.assertEquals('Missing "unresolved".', validator.errors[1])

    def test_verify_host_optional_ok(self):
        bindings = YamlBindings()
        bindings.import_dict({
             'ok': 'localhost',
             'unresolved': '${whatever}',
        })
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertTrue(validator.verify_host('ok', False))
        self.assertTrue(validator.verify_host('missing', False))
        self.assertTrue(validator.verify_host('unresolved', False))

    def test_verify_protection_good(self):
        bindings = YamlBindings()
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))

        fd, temp = tempfile.mkstemp()
        os.close(fd)
        try:
            os.chmod(temp, 0400)
            self.assertTrue(validator.verify_user_access_only(temp))
            os.chmod(temp, 0600)
            self.assertTrue(validator.verify_user_access_only(temp))
        finally:
            os.remove(temp)

    def test_verify_protection_bad(self):
        bindings = YamlBindings()
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))

        fd, temp = tempfile.mkstemp()
        os.close(fd)
        try:
            os.chmod(temp, 0410)
            self.assertFalse(validator.verify_user_access_only(temp))
            self.assertEqual(
                '"{temp}" should not have non-owner access. Mode is 410.'
                .format(temp=temp),
                validator.errors[0])
            os.chmod(temp, 0420)
            self.assertFalse(validator.verify_user_access_only(temp))
            os.chmod(temp, 0440)
            self.assertFalse(validator.verify_user_access_only(temp))
            os.chmod(temp, 0401)
            self.assertFalse(validator.verify_user_access_only(temp))
            os.chmod(temp, 0402)
            self.assertFalse(validator.verify_user_access_only(temp))
            os.chmod(temp, 0404)
            self.assertFalse(validator.verify_user_access_only(temp))
        finally:
            os.remove(temp)

    def test_verify_protection_good(self):
        bindings = YamlBindings()
        bindings.import_dict({
            'providers': {
                'aws': { 'enabled': False },
                'google': {'enabled': False },
                'another': {'enabled': True }
            },
        })
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertTrue(validator.verify_at_least_one_provider_enabled())

    def test_verify_protection_bad(self):
        bindings = YamlBindings()
        bindings.import_dict({
            'providers': {
                'aws': { 'enabled': False },
                'google': {'enabled': False }
            },
            'services': {'test': { 'enabled': True }}
        })
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        self.assertFalse(validator.verify_at_least_one_provider_enabled())
        self.assertEqual('None of the providers are enabled.',
                         validator.errors[0])


if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(ValidateConfigurationTest)
  unittest.TextTestRunner(verbosity=2).run(suite)


