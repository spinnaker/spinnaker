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

import buildtool.command

COMMAND = 'test_command_example'
CUSTOM_ARG_NAME = 'custom_test_arg'
CUSTOM_ARG_DEFAULT_VALUE = 'Custom Default Value'


class TestCommand(buildtool.command.CommandProcessor):
  @property
  def calls(self):
    return self.__calls

  def __init__(self, factory, options):
    super(TestCommand, self).__init__(factory, options)
    self.__calls = 0

  def _do_command(self):
    self.__calls += 1


class TestCommandFactory(buildtool.command.CommandFactory):
  def __init__(self):
    super(TestCommandFactory, self).__init__(
        COMMAND, TestCommand, 'My Test Command')

  def init_argparser(self, parser, defaults):
    super(TestCommandFactory, self).init_argparser(parser, defaults)
    TestCommandFactory.add_argument(
        parser, CUSTOM_ARG_NAME, defaults, CUSTOM_ARG_DEFAULT_VALUE)

def register_commands(registry, subparsers, defaults):
  TestCommandFactory().register(registry, subparsers, defaults)
