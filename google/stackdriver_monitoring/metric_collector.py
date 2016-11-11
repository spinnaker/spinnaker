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


# pylint: disable=missing-docstring
import argparse
import logging
import logging.config
import collections

from http_server import (HttpServer, StdoutRequestHandler)

import metric_collector_handlers as handlers
import stackdriver_handlers as stackdriver_handlers

from spectator_client import SpectatorClient
from stackdriver_client import StackdriverClient


HandlerDefinition = collections.namedtuple(
  'HandlersDefinition', ['handler', 'url_path', 'command_name', 'description'])


def init_logging(log_file):
  log_config = {
    'version':1,
    'disable_existing_loggers':True,
    'formatters': {
      'timestamped':{
        'format':'%(asctime)s %(message)s',
        'datefmt':'%H:%M:%S'
      }
    },
    'handlers':{
      'console':{
        'level':'WARNING',
        'class':'logging.StreamHandler',
        'formatter':'timestamped'
      },
      'file':{
        'level':'DEBUG',
        'class':'logging.FileHandler',
        'formatter':'timestamped',
        'filename': log_file,
        'mode':'w'
      },
    },
    'loggers':{
       '': {
         'level':'DEBUG',
         'handlers':['console', 'file']
       },
    }
  }
  logging.config.dictConfig(log_config)


def get_options():
  parser = argparse.ArgumentParser()

  parser.add_argument('--port', default=8008, type=int)
  parser.add_argument('--project', default='')
  parser.add_argument('--credentials_path', default='')
  parser.add_argument('--host', default='localhost')
  parser.add_argument('--period', default=30)
  parser.add_argument('--prototype_path', default='')
  parser.add_argument('--command', default='')

  # Either space or ',' delimited
  parser.add_argument('services', nargs='*', default=['all'])

  return parser.parse_args()


def process_command(command, registry):
  """Process the given command.

  Args:
    command: [string] The name of the command to run.
    registry: [list of HandlerDefinition]: The inventory of known commands.
  """
  request = StdoutRequestHandler()
  params = {}

  for entry in registry:
    if command == entry.command_name:
      entry.handler(request, entry.url_path, params, None)
      return

  raise ValueError('Unknown command "{0}".'.format(command))


def main():
  init_logging('metric_collector.log')
  options = get_options()

  spectator = SpectatorClient(options)
  try:
    stackdriver = StackdriverClient.make_client(options)
  except IOError as ioerror:
    logging.error('Could not create stackdriver client'
                  ' -- Stackdriver will be unavailable\n%s',
                  ioerror)
    stackdriver = None

  registry = []
  registry.extend([
      HandlerDefinition(
          handlers.BaseHandler(options, registry),
          '/', 'Home', 'Home page for Spinnaker metric administration.'),
      HandlerDefinition(
          stackdriver_handlers.ClearCustomDescriptorsHandler(
              options, stackdriver),
          '/clear',
          'clear',
          'Clear all the Stackdriver Custom Metrics'),
      HandlerDefinition(
          stackdriver_handlers.ListCustomDescriptorsHandler(
              options, stackdriver),
          '/list',
          'list',
          'List all the Stackdriver Custom Metric Descriptors.'
          ),

      HandlerDefinition(
          handlers.DumpMetricsHandler(options, spectator),
          '/dump',
          'dump',
          'Show current raw metric JSON from all the servers.'),
      HandlerDefinition(
          handlers.ExploreCustomDescriptorsHandler(options, spectator),
          '/explore',
          'explore',
          'Explore metric type usage across Spinnaker microservices.',
          ),
      HandlerDefinition(
          handlers.ShowCurrentMetricsHandler(options, spectator),
          '/show',
          'show',
          'Show current metric JSON for all Spinnaker.'
          ),
      ])

  if options.command:
    process_command(options.command, registry)
    return

  logging.info('Starting HTTP server on port %d', options.port)
  url_path_to_handler = {entry.url_path: entry.handler for entry in registry}
  httpd = HttpServer(options.port, url_path_to_handler)
  httpd.serve_forever()


if __name__ == '__main__':
  main()

