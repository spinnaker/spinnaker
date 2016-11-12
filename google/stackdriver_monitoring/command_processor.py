#!/usr/bin/env python

# Copyright 2016 Google Inc. All Rights Reserved.
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

import collections
from http_server import StdoutRequestHandler


CommandDefinition = collections.namedtuple(
  'CommandDefinition',
  ['handler', 'url_path', 'command_name', 'command_request', 'description'])


class CommandRequest(StdoutRequestHandler):
  @property
  def headers(self):
    return self.__headers

  @property
  def output_path(self):
    return self.__output_path

  def __init__(self, content_type='text/plain', options=None):
    StdoutRequestHandler.__init__(self)
    self.__headers = {'accept': content_type}
    self.__output_path = (options or {}).get('output_path', None)

  def respond(self, code, headers=None, body=None, *pos_args):
    StdoutRequestHandler.respond(self, code, headers, body, *pos_args)
    if self.__output_path and body is not None:
      with open(self.__output_path, 'w') as f:
        f.write(body)
      print 'Wrote {0}'.format(self.__output_path)


def process_command(command, command_registry):
  """Process the given command.

  Args:
    command: [string] The name of the command to run.
    command_registry: [list of CommandDefinition]: Inventory of known commands.
  """
  for entry in command_registry:
    if command == entry.command_name:
      params = {}
      request = entry.command_request
      entry.handler(request, entry.url_path, params, None)
      return

  raise ValueError('Unknown command "{0}".'.format(command))
