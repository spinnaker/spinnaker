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

from spinnaker.run import RunResult
from abstract_packer_builder import AbstractPackerBuilder


last_test_builder = None
class TestPackerBuilder(AbstractPackerBuilder):
  def __init__(self, options=None, argv=None):
    self.captured_vars = None
    self.captured_installer_path = None

    # Temporarily swap out system args to pretend we had argv instead.
    if argv:
        old_argv = sys.argv
        sys.argv = argv
    super(TestPackerBuilder, self).__init__(options or {})
    if argv:
        sys.argv = old_argv

    # Remember this instance for indirect tests
    global last_test_builder
    last_test_builder = self

  def _do_run_packer(self):
    self.captured_vars = list(self._packer_vars)
    return RunResult(0, "ok", "bad")

  def _do_prepare(self):
    self.remove_raw_arg('my_stripped_arg')

  def _do_prepare_installer(self, installer_path):
    self.captured_installer_path = installer_path
    with open(installer_path, 'w') as f:
        f.write('Hello, World!')

  @classmethod
  def init_argument_parser(cls, parser):
    super(TestPackerBuilder, cls).init_argument_parser(parser)
    parser.add_argument('--my_test_arg', default='TESTING')


class PackerBuilderTest(unittest.TestCase):
  def test_constructor(self):
      my_argv = ['program', '--first', 'value', '--second', '--third']
      builder = TestPackerBuilder(argv=my_argv)
      self.assertEqual(my_argv[1:], builder._raw_args)

  def test_remove_raw_arg_good(self):
      my_argv = ['program', '--first', 'value', '--second', '--third']
      builder = TestPackerBuilder(argv=my_argv)

      builder.remove_raw_arg('second')
      self.assertEqual(['--first', 'value', '--third'], builder._raw_args)
      builder.remove_raw_arg('first')
      self.assertEqual(['--third'], builder._raw_args)
      builder.remove_raw_arg('third')
      self.assertEqual([], builder._raw_args)
      
  def test_remove_raw_arg_does_not_exist(self):
      my_argv = ['program', '--first', 'value', '--second']
      builder = TestPackerBuilder(argv=my_argv)

      builder.remove_raw_arg('missing')
      self.assertEqual(['--first', 'value', '--second'], builder._raw_args)

      builder.remove_raw_arg('value')
      self.assertEqual(['--first', 'value', '--second'], builder._raw_args)

  def test_main(self):
      my_argv = ['program',
                 '--release_path', 'MY_RELEASE_PATH',
                 '--my_stripped_arg', 'STRIPPED_VALUE',
                 '--my_test_arg', 'MY_VALUE',
                 '--pass_through_plain',
                 '--pass_through_value', 'ANOTHER_VALUE']
      old_argv = sys.argv
      sys.argv = my_argv
      try:
          TestPackerBuilder.main()
      finally:
          sys.argv = old_argv

      expected_values = [
          '-var "release_path=MY_RELEASE_PATH"',
          '-var "my_test_arg=MY_VALUE"',
          '-var "pass_through_plain="',
          '-var "pass_through_value=ANOTHER_VALUE"',
          '-var "installer_path={path}"'.format(
              path=last_test_builder.captured_installer_path),
      ]
      self.assertEqual(sorted(expected_values),
                       sorted(last_test_builder._packer_vars))
      self.assertEqual('MY_VALUE', last_test_builder.options.my_test_arg)


if __name__ == '__main__':
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(PackerBuilderTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
