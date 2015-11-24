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


import unittest

from spinnaker_testing.expression_dict import ExpressionDict

class ExpressionDictTest(unittest.TestCase):
  def test_constructor(self):
    d = {'a': 'A'}
    x = ExpressionDict({'a': 'A'})
    self.assertEquals('A', x['a'])
    self.assertEquals(d.items(), x.items())
    self.assertEquals(d, x)
    
  def test_load_composite_value(self):
    x = ExpressionDict({'a':'A', 'b':'B', 'test':'${a}/${b}'})
    self.assertEqual('A/B', x.get('test'))

  def test_load_default(self):
    x = ExpressionDict({'field': '${injected.value}'})
    self.assertEqual('MISSING', x.get('missing', 'MISSING'))

  def test_load_not_found(self):
    x = ExpressionDict({'field': '${injected.value}'})
    self.assertEqual('${injected.value}', x.get('field', None))
    self.assertEqual('${injected.value}', x['field'])

  def test_load_tail_not_found(self):
    x = ExpressionDict({'field': '${injected.value}', 'injected': {}})
    self.assertEqual('${injected.value}', x.get('field'))

  def test_load_default(self):
    x = ExpressionDict({'field': '${injected.value:HELLO}'})
    self.assertEqual('HELLO', x.get('field'))

  def test_load_transitive(self):
    x = ExpressionDict({'field': '${injected.value}',
                        'injected.value': 'HELLO'})
    self.assertEqual('HELLO', x.get('field'))

  def test_load_transitive_indirect(self):
    x = ExpressionDict({'field': '${injected.value}', 'found': 'FOUND',
                        'injected.value': '${found}'})
    self.assertEqual('FOUND', x.get('field'))

  def test_load_key_not_found(self):
    x = ExpressionDict({'field': '${injected.value}', 'injected': {}})
    self.assertIsNone(x.get('unknown'))
    with self.assertRaises(KeyError):
      x['unknown']

  def test_cyclic_reference(self):
    x = ExpressionDict({'field': '${injected.value}',
                        'injected.value': '${field}'})
    with self.assertRaises(ValueError):
      x['field']
    with self.assertRaises(ValueError):
      x.get('field')

  def test_def_value(self):
    x = ExpressionDict({'t': 'true', 'f': 'false',
                        'def': '${unknown:true}', 'indirect': '${f}'})
    x.default_value_interpreter = lambda x: True if x == 'true' else False if x == 'false' else x
    self.assertEqual('true', x.get('t'))
    self.assertEqual('false', x.get('f'))
    self.assertEqual(True, x.get('def'))
    self.assertEqual('false', x.get('indirect'))

if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(ExpressionDictTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
