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

import threading
import time
import unittest
import buildtool.command
import buildtool.git
import custom_test_command

TEST_ROOT_PATH = '/test/root/path/for/local/repos'
TEST_REPOSITORIES = {
    # These repos are never actually used, just the objects.
    'repo-one': buildtool.git.RemoteGitRepository(
          'repo-one', 'test-user', None),
    'repo-two': buildtool.git.RemoteGitRepository(
          'repo-two', 'test-user', None)
}

COMMAND = custom_test_command.COMMAND

class SimpleNamespace(object):
  # for options
  pass

class TestRepositoryCommand(buildtool.command.RepositoryCommandProcessor):
  def __init__(self, factory, options, pos_arg, **kwargs):
     super(TestRepositoryCommand, self).__init__(factory, options)
     self.test_init_args = (factory, options, pos_arg, kwargs)
     self.test_call_sequence = []
     self.test_repos = []
     self.test_repo_threadid = []

  def _do_determine_source_repositories(self):
    return TEST_REPOSITORIES

  def _do_preprocess(self):
    self.test_call_sequence.append('PREPROCESS')

  def _do_postprocess(self, result_dict):
    self.test_call_sequence.append(('POSTPROCESS', result_dict))
    return {'foo': 'bar'}

  def _do_repository(self, repository):
    self.test_call_sequence.append(
        ('REPOSITORY', list(self.test_call_sequence)))
    time.sleep(0.1)  # Encourage threads to be different
    self.test_repo_threadid.append(threading.current_thread().ident)
    self.test_repos.append(repository)
    return 'TEST {0}'.format(repository.name)


class TestRepositoryCommandProcessor(unittest.TestCase):
  def do_test_command(self, options):
    options.root_path = TEST_ROOT_PATH
    options.only_repositories = None

    init_dict = {'a': 'A', 'b': 'B'}
    factory = buildtool.command.RepositoryCommandFactory(
        'TestRepositoryCommand', TestRepositoryCommand, 'A test command.',
        123, **init_dict)

    # Test construction
    command = factory.make_command(options)
    self.assertEquals((factory, options, 123, init_dict),
                      command.test_init_args)
    self.assertEquals([], command.test_call_sequence)
    self.assertEquals(factory, command.factory)
    self.assertEquals(factory.name, command.name)
    self.assertEquals(TEST_REPOSITORIES, command.source_repositories)
    self.assertEquals(TEST_ROOT_PATH, command.source_code_manager.root_path)


    # Test invocation
    self.assertEquals({'foo': 'bar'}, command())

    expect_sequence = [
        'PREPROCESS',
        ('REPOSITORY', ['PREPROCESS']),
        ('REPOSITORY', ['PREPROCESS', ('REPOSITORY', ['PREPROCESS'])]),
        ('POSTPROCESS', {name: 'TEST ' + name
                         for name in TEST_REPOSITORIES.keys()})]
    self.assertEquals(expect_sequence, command.test_call_sequence)
    return command

  def test_concurrent_command(self):
    options = SimpleNamespace()
    options.one_at_a_time = False
    command = self.do_test_command(options)
    self.assertNotEquals(command.test_repo_threadid[0],
                         command.test_repo_threadid[1])

  def test_serialized_command(self):
    options = SimpleNamespace()
    options.one_at_a_time = True
    command = self.do_test_command(options)
    self.assertEquals(command.test_repo_threadid[0],
                      command.test_repo_threadid[1])


if __name__ == '__main__':
  import logging
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  unittest.main(verbosity=2)
