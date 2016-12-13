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

"""Implements Command Processor for defining and executing commands.

Commands can be invoked from the command-line and/or web server.
Some commands may only be one or the other. The URL path indicates the
URL for web invocation and command name is the command for CLI invocation.
"""


import collections
from http_server import StdoutRequestHandler


# Global options are a hack so that the web commands can get them
# when then need to pass them to object factories.
_global_options = {}


def set_global_options(options):
  """Sets the global options used as defaults for web server execution."""
  # pylint: disable=global-statement
  global _global_options
  _global_options = dict(options)


def get_global_options():
  """Returns the global options used as defaults for web server execution."""
  return _global_options


CommandDefinition = collections.namedtuple(
  'CommandDefinition',
  ['handler', 'url_path', 'command_name', 'command_request', 'description'])


class CommandRequest(StdoutRequestHandler):
  # pylint: disable=missing-docstring
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
      # pylint: disable=invalid-name
      with open(self.__output_path, 'w') as f:
        f.write(body)
      print 'Wrote {0}'.format(self.__output_path)


# pylint: disable=abstract-class-not-used
class CommandHandler(object):
  """The base class for command handlers.

  A handler can have a url_path and/or command_name to plug into the
  Web and CLI execution processors.

  The handler is a stateless controller. The same instance is reused each time
  for a given command. Each distinct command has a different handler.
  """
  @property
  def url_path(self):
    """The url_path that invokes the command in the HTTP server."""
    return self.__url_path

  @property
  def command_name(self):
    """The name (first argument) that invokes the command from the CLI."""
    return self.__command_name

  @property
  def description(self):
    """For help text."""
    return self.__description

  def __init__(self, url_path, command_name, description):
    """Constructs the command."""
    self.__url_path = url_path
    self.__command_name = command_name
    self.__description = description

  def params_to_query(self, params):
    """Convert a parameter dictionary to URL query parameter suffix."""
    query_list = ['{0}={1}'.format(key, value) for key, value in params.items()]
    return '?{0}'.format('&'.join(query_list)) if query_list else ''

  def process_commandline_request(self, options, **kwargs):
    """Processes the command from a commandline request.

    Output (as opposed to logging) should be emitted using output()
    so that it can be captured to a file or supressed.

    Args:
      options: [dict] The commandline arguments passed.
    """
    request = CommandRequest(options=options)
    self.process_web_request(request, None, options, None)

  def process_web_request(self, request, path, params, fragment):
    """Processes the command from an HTTP request.

    Args:
      request: [HttpRequest] The request.
      path: [string] The path from the request URL.
      params: [dict] The query parameters from the request URL.
      fragment: [string] The fragment from the request URL.
    """
    raise NotImplementedError()

  def add_argparser(self, subparsers):
    """Add specialized argparser for this command.

    Args:
      subparsers: The subparsers from the argparser parser.

    Returns:
      A new parser for to add additional custom parameters for this command.
    """
    parser = subparsers.add_parser(self.command_name, help=self.description)
    parser.add_argument('--quiet', default=False, action='store_true',
                        help='Dont print output to stdout.')
    parser.add_argument('--output_path', default='',
                        help='If provided, write command output to this path.')
    return parser

  def output(self, options, content):
    """Output content from the command.

    This will write to stdout as well as a file depending
    on the options.

    Args:
      options: [dict] The command line parameters.
      content: [string] The output.
    """
    do_print = not options.get('quiet', False)
    if do_print:
      print content
    output_path = options.get('output_path', None)

    if output_path:
      # pylint: disable=invalid-name
      with open(options['output_path'], 'w') as f:
        f.write(content)
      if do_print:
        print 'Wrote {0}'.format(output_path)


def process_command(command, options, command_registry):
  """Process the given command.

  Args:
    command: [string] The name of the command to run.
    options: [dict] Options for the command.
    command_registry: [list of CommandDefinition]: Inventory of known commands.
  """
  for entry in command_registry:
    if command == entry.command_name:
      entry.process_commandline_request(options)
      return

  raise ValueError('Unknown command "{0}".'.format(command))
