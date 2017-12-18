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

import datetime
import logging
import os
import shutil
import tempfile
import unittest

import buildtool.util


class TestRunner(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    cls.base_temp_dir = tempfile.mkdtemp(prefix='buildtool.util_test')

  @classmethod
  def tearDownClass(cls):
    shutil.rmtree(cls.base_temp_dir)

  def do_run_subprocess_ok(self, check):
    if os.path.exists('/bin/true'):
      true_path = '/bin/true'
    elif os.path.exists('/usr/bin/true'):
      true_path = '/usr/bin/true'
    else:
      raise NotImplementedError('Unsupported test platform.')

    tests = [(true_path, ''),
             ('/bin/echo Hello', 'Hello'),
             ('/bin/echo "Hello"', 'Hello'),
             ('/bin/echo "Hello World"', 'Hello World'),
             ('/bin/echo "Hello\nWorld"', 'Hello\nWorld'),
             ('/bin/echo \'"Hello World"\'', '"Hello World"')]
    for cmd, expect in tests:
      if check:
        output = buildtool.util.check_subprocess(cmd)
      else:
        code, output = buildtool.util.run_subprocess(cmd)
        self.assertEquals(0, code)

      self.assertEquals(expect, output)

  def test_run_subprocess_ok(self):
    self.do_run_subprocess_ok(False)

  def test_check_subprocess_ok(self):
    self.do_run_subprocess_ok(False)

  def test_run_subprocess_fail(self):
    if os.path.exists('/bin/false'):
      false_path = '/bin/false'
    elif os.path.exists('/usr/bin/false'):
      false_path = '/usr/bin/false'
    else:
      raise NotImplementedError('Unsupported test platform.')

    got, output = buildtool.util.run_subprocess(false_path)
    self.assertEquals((1, ''), (got, output))

    got, output = buildtool.util.run_subprocess('/bin/ls /abc/def')
    self.assertNotEquals(0, got)
    self.assertTrue(output.find('No such file or directory') >= 0)

  def test_check_subprocess_fail(self):
    if os.path.exists('/bin/false'):
      false_path = '/bin/false'
    elif os.path.exists('/usr/bin/false'):
      false_path = '/usr/bin/false'
    else:
      raise NotImplementedError('Unsupported test platform.')

    tests = [false_path, '/bin/ls /abc/def']
    for test in tests:
      with self.assertRaises(Exception) as ex:
        buildtool.util.check_subprocess(test)
      self.assertTrue(hasattr(ex.exception, 'logged'))

  def test_run_subprocess_get_pid(self):
    # See if we can run a job by looking up our job
    # This is also testing parsing command lines.
    code, output = buildtool.util.run_subprocess('/bin/ps -f')
    self.assertEquals(0, code)
    my_pid = ' %d ' % os.getpid()
    candidates = [line for line in output.split('\n') if line.find(my_pid) > 0]
    if len(candidates) != 1:
      logging.error('Unexpected output\n%s', output)
    self.assertEquals(1, len(candidates))
    self.assertTrue(candidates[0].find(' python ') > 0)

  def test_ensure_dir(self):
    want = os.path.join(self.base_temp_dir, 'ensure', 'a', 'b', 'c')
    self.assertFalse(os.path.exists(want))
    buildtool.util.ensure_dir_exists(want)
    self.assertTrue(os.path.exists(want))

    # Ok if already exists
    buildtool.util.ensure_dir_exists(want)
    self.assertTrue(os.path.exists(want))

  def test_write_to_path(self):
    path = os.path.join(self.base_temp_dir, 'test_write', 'file')
    content = 'First Line\nSecond Line'
    buildtool.util.write_to_path(content, path)
    with open(path, 'r') as f:
      self.assertEquals(content, f.read())

  def test_deltatime_string(self):
    timedelta = datetime.timedelta
    tests = [
        (timedelta(1, 60 * 60 * 4 + 60 * 5 + 2, 123456), 'days=1 + 04:05:02'),
        (timedelta(1, 60 * 5 + 2, 123456), 'days=1 + 00:05:02'),
        (timedelta(1, 2, 123456), 'days=1 + 00:00:02'),
        (timedelta(0, 60 * 60 * 4 + 60 * 5 + 2, 123456), '04:05:02'),
        (timedelta(0, 60 * 5 + 2, 123456), '05:02'),
        (timedelta(0, 2, 123456), '2.123 secs')
    ]
    for test in tests:
      self.assertEquals(test[1], buildtool.util.timedelta_string(test[0]))


if __name__ == '__main__':
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  unittest.main(verbosity=2)
