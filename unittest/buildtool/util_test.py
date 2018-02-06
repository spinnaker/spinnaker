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

from buildtool import (
    ensure_dir_exists,
    timedelta_string,
    write_to_path)


class TestRunner(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    cls.base_temp_dir = tempfile.mkdtemp(prefix='buildtool.util_test')

  @classmethod
  def tearDownClass(cls):
    shutil.rmtree(cls.base_temp_dir)

  def test_ensure_dir(self):
    want = os.path.join(self.base_temp_dir, 'ensure', 'a', 'b', 'c')
    self.assertFalse(os.path.exists(want))
    ensure_dir_exists(want)
    self.assertTrue(os.path.exists(want))

    # Ok if already exists
    ensure_dir_exists(want)
    self.assertTrue(os.path.exists(want))

  def test_write_to_path(self):
    path = os.path.join(self.base_temp_dir, 'test_write', 'file')
    content = 'First Line\nSecond Line'
    write_to_path(content, path)
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
      self.assertEquals(test[1], timedelta_string(test[0]))


if __name__ == '__main__':
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  unittest.main(verbosity=2)
