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
import sys
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

    def host_test_helper(self, tests, valid, required=False):
        bindings = YamlBindings()
        bindings.import_dict(tests)
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        for key, value in tests.items():
            msg = '"{key}" was {valid}'.format(
                key=key, valid='invalid' if valid else 'valid')
                                               
            self.assertEqual(valid, validator.verify_host(key, required), msg)
        return validator

    def test_verify_host_good(self):
        tests = {
             'short': 'localhost',
             'numeric': '0.0.0.0',
             'ipv6-standard': '2607:f8b0:4001:0c20:0000:0000:0000:0066',
             'ipv6-short-zero': '2607:f8b0:4001:c20:0:0:0:66',
             'dot1': 'my.host',
             'dot2': 'my.host.name',
             'hyphen': 'this.is-a.host1234',
        }
        self.host_test_helper(tests, True)
        
    def test_verify_host_bad(self):
        tests = {
             'upper': 'LOCALHOST',
             'under': 'local_host',
             'space': 'local host',
             'slash': 'localhost/foo',
             'colon': 'localhost:80',
             'illegal': '-invalid-'
        }
        validator = self.host_test_helper(tests, False)
        self.assertTrue(validator.errors[0].startswith('name="LOCALHOST"'))

    def test_verify_host_missing(self):
        tests = {
            'unresolved': '${whatever}'
        }

        validator = self.host_test_helper(tests, False, required=True)
        self.assertEquals('Missing "unresolved".', validator.errors[0])

        self.assertFalse(validator.verify_host('missing', True))
        self.assertEquals('No host provided for "missing".',
                          validator.errors[len(tests)])

    def test_verify_host_optional_ok(self):
        tests = {
             'ok': 'localhost',
             'unresolved': '${whatever}',
        }
        self.host_test_helper(tests, True, required=False)

    def baseUrl_test_helper(self, tests, valid, scheme_optional):
        bindings = YamlBindings()
        bindings.import_dict(tests)
        validator = ValidateConfig(
              configurator=Configurator(bindings=bindings))
        for key, value in tests.items():
            msg = '"{key}" was {valid}'.format(
                key=key, valid='invalid' if valid else 'valid')
                                               
            self.assertEqual(
                valid,
                validator.verify_baseUrl(key, True,
                                         scheme_optional=scheme_optional),
                msg)

    def test_verify_baseUrl_only_host_ok(self):
        tests = {
            'localhost': 'localhost',
            'ip4': '10.20.30.40',
            'ip4-short': '1.2.3.4',
            'ip4-long': '255.255.255.255',
            'ipv6-standard': '2607:f8b0:4001:0c20:0000:0000:0000:0066',
            'ipv6-short-zero': '2607:f8b0:4001:c20:0:0:0:66',
            'ipv6-abbrev-mid': '2607:f8b0:4001:c20::66',
            'ipv6-abbrev-end': '2607:f8b0:4001:c20::',
            'too-generous': '256.300.500.999', # invalid, but accept anyway
            'domain': 'foo.bar',
            'fulldomain': 'foo.bar.baz.test',
            'mixed': 'myhost32.sub-domain23',
            'reference': '${ip4}',

            # These aren't valid, but are accepted as valid
            # to keep the implementation simple.
            # They are here for documentation, but are not necessarily
            # guaranteed to pass in the future.
            'ipv6-badabbrev3': '2607:f8b0:4001:c20:::66',
            'ipv6-multi-colon': '2607:f8b0::c20::',
        }
        self.baseUrl_test_helper(tests, True, scheme_optional=True)

    def test_verify_baseUrl_only_host_bad(self):
        tests = {
            'leading_int': '32my',
            'too-few': '10.20.30',
            'too-many': '10.20.30.40.50',
            'trailing-dot': '10.20.30.40.',
            'undef': '${unknown}',
            'capital': 'myHost',
            'trailing-dot-again': 'myhost.'
        }
        self.baseUrl_test_helper(tests, False, scheme_optional=True)

    def test_verify_baseUrl_only_host_port_ok(self):
        tests = {
            'localhost': 'localhost:123',
            'ip4': '10.20.30.40:456',
            'domain': 'foo.bar:789',
            'fulldomain': 'foo.bar.baz.test:980',
            'mixed': 'myhost32-test.sub-domain23:32'
        }
        self.baseUrl_test_helper(tests, True, scheme_optional=True)

    def test_verify_baseUrl_only_host_port_bad(self):
        tests = {
            'letters': 'test:abc',
            'mixed': 'test:123a',
            'empty': 'test:'
        }
        self.baseUrl_test_helper(tests, False, scheme_optional=True)

    def test_verify_baseUrl_only_host_port_path_ok(self):
        tests = {
            'simple': 'localhost:123/simple',
            'noport': 'localhost/simple',
            'ip4': '10.20.30.40:456/simple',
            'deep': 'localhost/parent/child/leaf',
            'dir': 'foo.bar.baz.test:980/dir/',
            'numeric': 'host/012345',
            'root': 'host/',
            'mixed': 'myhost32-test.sub-domain23:123/root-path',
            'escaped': 'host/spaced%32path',
            'escapedhex': 'host/spaced%afpath',
            'escapednumeric': 'host/spaced%321',
            'jumple': 'host/%32path+-._',
            'ref': '${root}${root}'
        }
        self.baseUrl_test_helper(tests, True, scheme_optional=True)

    def test_verify_baseUrl_only_host_port_path_bad(self):
        tests = {
            'onlypath': '/bad',
            'undef': 'localhost/${unknown}',
            'badescape0': 'host/bad%',
            'badescape1': 'host/bad%1',
            'badescapeX': 'host/bad%gg',
            'space': 'host/bad space',
            'query': 'host/path?name',
            'frag': 'host/path#frag',
        }
        self.baseUrl_test_helper(tests, False, scheme_optional=True)

    def test_verify_baseUrl_scheme_ok(self):
        tests = {
            'host': 'http://localhost',
            'port': 'https://localhost:123',
            'path': 'http://localhost/path',
        }
        self.baseUrl_test_helper(tests, True, scheme_optional=True)

    def test_verify_baseUrl_scheme_bad(self):
        tests = {
            'nocolon': 'https//localhost:123',
            'nonetloc': 'http:///path',
        }
        self.baseUrl_test_helper(tests, False, scheme_optional=True)

    def test_verify_baseUrl_scheme_required_ok(self):
        tests = {
            'host': 'http://host',
            'host_port': 'http://host:80',
            'host_path': 'http://host/path',
            'host_port_path': 'http://host:80/path'
        }
        self.baseUrl_test_helper(tests, True, scheme_optional=False)

    def test_verify_baseUrl_scheme_required_bad(self):
        tests = {
            'scheme': 'http',
            'scheme_colon': 'http://',
            'host': 'localhost',
            'host_port': 'host:80',
            'host_port_path': 'host:80/path',
            'nohost': 'http://'
        }
        self.baseUrl_test_helper(tests, False, scheme_optional=False)

    def test_verify_user_access_only_good(self):
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

    def test_verify_user_access_only_bad(self):
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

    def test_verify_at_least_one_provider_enabled_good(self):
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

    def test_verify_at_least_one_provider_enabled_bad(self):
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
  got = unittest.TextTestRunner(verbosity=2).run(suite)
  sys.exit(len(got.errors) + len(got.failures))

