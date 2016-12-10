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

import spinnaker_testing.yaml_accumulator as yaml_accumulator


class YamlAccumulatorTest(unittest.TestCase):
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
              'd.child.grandchild': 'x',
              'e': None}
    got = {}
    yaml_accumulator.load_string(yaml, got)
    self.assertEqual(expect, got)

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
              'd.child.grandchild': 'x',
              'e': None}

    fd, temp_path = tempfile.mkstemp()
    os.write(fd, yaml)
    os.close(fd)

    got = {}
    yaml_accumulator.load_path(temp_path, got)
    self.assertEqual(expect, got)


if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(YamlAccumulatorTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
