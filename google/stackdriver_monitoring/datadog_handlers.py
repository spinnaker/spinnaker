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

"""Implements CommandHandlers that interact with Datadog."""

import json

import datadog_service
from command_processor import CommandHandler


class BaseDatadogCommandHandler(CommandHandler):
  """Base class for Datadog command handlers."""
  def add_argparser(self, subparsers):
    parser = super(BaseDatadogCommandHandler, self).add_argparser(subparsers)
    datadog_service.DatadogMetricsService.add_standard_parser_arguments(parser)
    return parser

class ListArtifactsHandler(BaseDatadogCommandHandler):
  """List all the datadog artifacts.

  Currently these are either Dashboards or Screenboards.
  """

  def __init__(self, type_name, url_path, command_name, description):
    """Constructor.

    Args:
      type_name: [string] The type of artifact.
    """
    super(ListArtifactsHandler, self).__init__(
        url_path, command_name, description)
    self.__type_name = type_name

  def process_commandline_request(self, options):
    """Implements CommandHandler."""
    datadog = datadog_service.make_datadog_service(options)
    if self.__type_name == 'screenboard':
      method = datadog.api.Screenboard.get_all
    elif self.__type_name == 'timeboard':
      method = datadog.api.Timeboard.get_all
    else:
      raise ValueError('Unknown datadog artifact "{0}". '
                       ' Either "screenboard" or "timeboard"'
                       .format(self.__type_name))

    json_text = json.JSONEncoder(indent=2).encode(method())
    self.output(options, json_text)


class GetArtifactHandler(BaseDatadogCommandHandler):
  """Gets a specific datadog artifact instance .

  Currently these are either Dashboards or Screenboards.
  """
  def __init__(self, type_name, url_path, command_name, description):
    """Constructor.

    Args:
      type_name: [string] The type of artifact.
    """
    super(GetArtifactHandler, self).__init__(
        url_path, command_name, description)
    self.__type_name = type_name

  def add_argparser(self, subparsers):
    """Implements CommandHandler."""
    parser = super(GetArtifactHandler, self).add_argparser(subparsers)
    parser.add_argument(
        '--name', required=True,
      help='The name of the {0} to get.'.format(self.__type_name))
    return parser

  def process_commandline_request(self, options):
    """Implements CommandHandler."""
    title = options.get('name', None)
    if not title:
      raise ValueError('No name provided.')

    api = datadog_service.make_datadog_service(options).api
    if self.__type_name == 'screenboard':
      list_method = api.Screenboard.get_all
      get_method = api.Screenboard.get
      result_key = 'screenboards'
    elif self.__type_name == 'timeboard':
      list_method = api.Timeboard.get_all
      get_method = api.Timeboard.get
      result_key = 'dashes'
    else:
      raise ValueError('Unknown datadog artifact "{0}". '
                       ' Either "screenboard" or "timeboard"'
                       .format(self.__type_name))

    all_list = list_method()
    artifact_id = None
    for artifact in all_list[result_key]:
      if artifact['title'] == title:
        artifact_id = artifact['id']
        break

    if artifact_id is None:
      raise ValueError('Could not find title "{0}"'.format(title))

    json_text = json.JSONEncoder(indent=2).encode(get_method(artifact_id))
    self.output(options, json_text)


def add_handlers(handler_list, subparsers):
  """Registers all the CommandHandlers for interacting with Datadog."""
  command_handlers = [
      ListArtifactsHandler('screenboard',
                           None, 'list_datadog_screenboards',
                           'Get the list of Screenboards from Datadog.'),
      ListArtifactsHandler('timeboard',
                           None, 'list_datadog_timeboards',
                           'Get the list of Timeboards from Datadog.'),
      GetArtifactHandler('screenboard',
                         None, 'get_datadog_screenboard',
                         'Get the a Datadog Screenboard.'),
      GetArtifactHandler('timeboard',
                         None, 'get_datadog_timeboard',
                         'Get the a Datadog Timeboard.'),
  ]
  for handler in command_handlers:
    handler.add_argparser(subparsers)
    handler_list.append(handler)
