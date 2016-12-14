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

from spinnaker.yaml_util import YamlBindings
from spinnaker.yaml_util import yml_or_yaml_path

class YamlUtilTest(unittest.TestCase):
  def test_load_dict(self):
    expect = {'a': 'A',
              'b': 0,
              'c': ['A','B'],
              'd': {'child': {'grandchild': 'x'}},
              'e': None}

    bindings = YamlBindings()
    bindings.import_dict(expect)
    self.assertEqual(expect, bindings.map)

  def test_load_string(self):
    yaml = """
a: A
b: 0
c:
  - A
  - B
d:
  child:
    grandchild: x
e:
"""
    expect = {'a': 'A',
              'b': 0,
              'c': ['A','B'],
              'd': {'child': {'grandchild': 'x'}},
              'e': None}

    bindings = YamlBindings()
    bindings.import_string(yaml)
    self.assertEqual(expect, bindings.map)

  def test_load_path(self):
    yaml = """
a: A
b: 0
c:
  - A
  - B
d:
  child:
    grandchild: x
e:
"""
    expect = {'a': 'A',
              'b': 0,
              'c': ['A','B'],
              'd': {'child': {'grandchild': 'x'}},
              'e': None}

    fd, temp_path = tempfile.mkstemp()
    os.write(fd, yaml)
    os.close(fd)

    bindings = YamlBindings()
    bindings.import_path(temp_path)
    self.assertEqual(expect, bindings.map)

  def test_yml_or_yaml_path(self):
    temp_dir = tempfile.gettempdir()

    fd, temp_path = tempfile.mkstemp(suffix='.yml')
    os.close(fd)
    rootname = os.path.splitext(os.path.basename(temp_path))[0]
    self.assertEqual(temp_path, yml_or_yaml_path(temp_dir, rootname))
    os.remove(temp_path)

    fd, temp_path = tempfile.mkstemp(suffix='.yaml')
    os.close(fd)
    rootname = os.path.splitext(os.path.basename(temp_path))[0]
    self.assertEqual(temp_path, yml_or_yaml_path(temp_dir, rootname))
    os.remove(temp_path)

  def test_yml_or_yaml_neither(self):
    the_dir = '/no/such/dir'
    self.assertEqual(os.path.join(the_dir, 'bogus') + '.yml',
                     yml_or_yaml_path(the_dir, 'bogus'))

  def test_yml_or_yaml_both(self):
    temp_dir = tempfile.gettempdir()
    fd, yml_path = tempfile.mkstemp(suffix='.yml')
    os.close(fd)
    rootname = os.path.splitext(os.path.basename(yml_path))[0]

    yaml_path = os.path.join(temp_dir, rootname) + '.yaml'
    with open(yaml_path, 'w') as f:
      pass

    # Both cases raise exception
    with self.assertRaises(ValueError):
      yml_or_yaml_path(temp_dir, rootname)
    os.remove(yml_path)
    os.remove(yaml_path)

  def test_load_composite_value(self):
    bindings = YamlBindings()
    bindings.import_dict({'a': 'A', 'b':'B'})
    bindings.import_string('test: ${a}/${b}')
    print str(bindings.map)
    self.assertEqual('A/B', bindings.get('test'))

  def test_update_field_union(self):
    bindings = YamlBindings()
    bindings.import_dict({'a': 'A'})
    bindings.import_dict({'b': 'B'})
    self.assertEqual({'a': 'A', 'b': 'B'}, bindings.map)

  def test_update_field_union_child(self):
    bindings = YamlBindings()
    bindings.import_dict({'parent1': {'a': 'A'}, 'parent2': {'x': 'X'}})
    bindings.import_dict({'parent1': {'b': 'B'}})
    self.assertEqual({'parent1': {'a': 'A', 'b': 'B'},
                      'parent2': {'x': 'X'}},
                     bindings.map)

  def test_update_field_replace_child(self):
    bindings = YamlBindings()
    bindings.import_dict({'parent': {'a': 'A', 'b': 'B', 'c': 'C'}})
    bindings.import_dict({'parent': {'a': 'X', 'b': 'Y', 'z': 'Z'}})
    self.assertEqual({'parent': {'a': 'X', 'b': 'Y', 'z': 'Z', 'c': 'C'}},
                     bindings.map)

  def test_load_not_found(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${injected.value}'})
    self.assertEqual('${injected.value}', bindings.get('field'))

  def test_load_tail_not_found(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${injected.value}', 'injected': {}})
    self.assertEqual('${injected.value}', bindings.get('field'))

  def test_load_default(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${injected.value:HELLO}'})
    self.assertEqual('HELLO', bindings.get('field'))

  def test_load_default_int(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${undefined:123}'})
    self.assertEqual(123, bindings.get('field'))

  def test_load_default_bool(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${undefined:True}'})
    self.assertEqual(True, bindings.get('field'))
    bindings.import_dict({'field': '${undefined:true}'})
    self.assertEqual(True, bindings.get('field'))
    bindings.import_dict({'field': '${undefined:false}'})
    self.assertEqual(False, bindings.get('field'))
    bindings.import_dict({'field': '${undefined:False}'})
    self.assertEqual(False, bindings.get('field'))

  def test_environ(self):
    os.environ['TEST_VARIABLE'] = 'TEST_VALUE'
    bindings = YamlBindings()
    bindings.import_dict({'field': '${TEST_VARIABLE}'})
    self.assertEqual('TEST_VALUE', bindings.get('field'))

  def test_environ_bool(self):
    os.environ['TEST_BOOL'] = 'TRUE'
    bindings = YamlBindings()
    bindings.import_dict({'field': '${TEST_BOOL}'})
    self.assertEqual(True, bindings.get('field'))

  def test_environ_int(self):
    os.environ['TEST_INT'] = '123'
    bindings = YamlBindings()
    bindings.import_dict({'field': '${TEST_INT}'})
    self.assertEqual(123, bindings.get('field'))

  def test_load_transitive(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${injected.value}'})
    bindings.import_dict({'injected': {'value': 'HELLO'}})
    self.assertEqual('HELLO', bindings.get('field'))

  def test_load_transitive_indirect(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${injected.value}', 'found': 'FOUND'})
    bindings.import_dict({'injected': {'value': '${found}'}})
    self.assertEqual('FOUND', bindings.get('field'))
    self.assertEqual('FOUND', bindings['field'])
    self.assertEqual('FOUND', bindings.get('field', None))

  def test_load_key_not_found(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${injected.value}', 'injected': {}})

    with self.assertRaises(KeyError):
      bindings['unknown']
    self.assertEqual(None, bindings.get('unknown', None))

  def test_cyclic_reference(self):
    bindings = YamlBindings()
    bindings.import_dict({'field': '${injected.value}',
                          'injected': {'value': '${field}'}})
    with self.assertRaises(ValueError):
      bindings.get('field')

  def test_replace(self):
    bindings = YamlBindings()
    bindings.import_dict({'a': 'A', 'container': {'b': 'B'}})
    self.assertEqual('This is A B or C',
                     bindings.replace('This is ${a} ${container.b} or ${c:C}'))

  def test_replace_int(self):
    bindings = YamlBindings()
    bindings.import_dict({'a': 1, 'container': {'b': 2}})
    self.assertEqual('This is 1 2 or 3',
                     bindings.replace('This is ${a} ${container.b} or ${c:3}'))

  def test_replace_bool(self):
    bindings = YamlBindings()
    bindings.import_dict({'a': True, 'container': {'b': False}})
    self.assertEqual('This is True False or false',
                     bindings.replace('This is ${a} ${container.b} or ${c:false}'))

  def test_boolean(self):
     bindings = YamlBindings()
     bindings.import_string(
        "t: true\nf: false\ndef: ${unkown:true}\nindirect: ${f}")
     self.assertEqual(True, bindings.get('t'))
     self.assertEqual(False, bindings.get('f'))
     self.assertEqual(True, bindings.get('def'))
     self.assertEqual(False, bindings.get('indirect'))

  def test_number(self):
     bindings = YamlBindings()
     bindings.import_string(
        "scalar: 123\nneg: -321\ndef: ${unkown:234}\nindirect: ${scalar}")
     self.assertEqual(123, bindings.get('scalar'))
     self.assertEqual(-321, bindings.get('neg'))
     self.assertEqual(234, bindings.get('def'))
     self.assertEqual(123, bindings.get('indirect'))

  def test_concat(self):
     bindings = YamlBindings()
     bindings.import_string(
        "s: 'TEST'\nmix: a.${s}")
     self.assertEqual('a.TEST', bindings.get('mix'))

  def test_concat_default(self):
     bindings = YamlBindings()
     bindings.import_string(
        "mix: a.${s:TEST}")
     self.assertEqual('a.TEST', bindings.get('mix'))

  def test_transform_ok(self):
     bindings = YamlBindings()
     bindings.import_dict({'a': {'b': { 'space': 'WithSpace',
                                        'nospace': 'WithoutSpace',
                                        'empty': 'Empty'}},
                           'x' : {'unique': True}})
     template = """
a:
  b:
    space: {space}
    nospace:{nospace}
    empty:{empty}
unique:
  b:
     space: A
     nospace:B
     empty:
"""
     source = template.format(space='SPACE', nospace='NOSPACE', empty='')
     expect = template.format(space='WithSpace',
                              nospace=' WithoutSpace',
                              empty=' Empty')
     got = source
     for key in [ 'a.b.space', 'a.b.nospace', 'a.b.empty' ]:
       got = bindings.transform_yaml_source(got, key)

     self.assertEqual(expect, bindings.transform_yaml_source(expect, 'bogus'))
     self.assertEqual(expect, got)


  def test_transform_fail(self):
     bindings = YamlBindings()
     bindings.import_dict({'a': {'b': { 'child': 'Hello, World!'}},
                           'x' : {'unique': True}})
     yaml = """
a:
  b:
     child: Hello
"""
     with self.assertRaises(ValueError):
       bindings.transform_yaml_source(yaml, 'x.unique')

  def test_list(self):
     bindings = YamlBindings()
     bindings.import_string(
        "root:\n - elem: 'first'\n - elem: 2\n - elem: true\ncopy: ${root}")
     self.assertEqual([{'elem': 'first'}, {'elem': 2}, {'elem': True}],
                      bindings.get('root'))
     self.assertEqual(bindings.get('root'), bindings.get('copy'))

  def test_bool(self):
     bindings = YamlBindings()
     bindings.import_string(
        "root:\n - elem: true\n - elem: True\n - elem: false\n - elem: False\ncopy: ${root}")
     self.assertEqual([{'elem': True}, {'elem': True}, {'elem': False}, {'elem': False}],
                      bindings.get('root'))
     self.assertEqual(bindings.get('root'), bindings.get('copy'))

  def test_write_bool(self):
    yaml = 'a: false'

    update_dict = {
      'a': True
    }
    expected = 'a: true'

    fd, temp_path = tempfile.mkstemp()
    os.write(fd, yaml)
    os.close(fd)
    YamlBindings.update_yml_source(temp_path, update_dict)

    with open(temp_path, 'r') as f:
      self.assertEqual(expected, f.read())

    os.remove(temp_path)

  def test_update_yml_source(self):
    yaml = """
a: A
b: 0
c:
  - A
  - B
d:
  child:
    grandchild: x
e:
"""
    fd, temp_path = tempfile.mkstemp()
    os.write(fd, yaml)
    os.close(fd)

    update_dict = {
      'b': 'Z',
      'd': {
        'child': {
          'grandchild': 'xy'
        }
      },
      'e': 'AA'
    }

    expect = {'a': 'A',
              'b': 'Z',
              'c': ['A','B'],
              'd': {
                'child': {
                  'grandchild': 'xy'
                }
              },
              'e': 'AA'}

    YamlBindings.update_yml_source(temp_path, update_dict)

    comparison_bindings = YamlBindings()
    comparison_bindings.import_path(temp_path)
    self.assertEqual(expect, comparison_bindings.map)
    os.remove(temp_path)

if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(YamlUtilTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
