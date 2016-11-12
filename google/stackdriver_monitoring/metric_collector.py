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
import collections
import logging
import logging.config
import socket
import threading
import time

from http_server import (HttpServer, StdoutRequestHandler)

import metric_collector_handlers as handlers
import stackdriver_handlers as stackdriver_handlers

import spectator_client
import stackdriver_client


HandlerDefinition = collections.namedtuple(
  'HandlersDefinition', ['handler', 'url_path', 'command_name', 'description'])


class DummyMetricService(object):
  def store(self, service_metrics):
    spectator_client.foreach_metric_in_service_map(service_metrics, self.dump)

  def dump(self, service, name, metadata, instance):
    name, tags = spectator_client.normalize_name_and_tags(
        name, metadata, instance)
    if tags is None:
      print 'IGNORE {0}.{1}'.format(service, name)
    else:
      print '{0}.{1} {2}'.format(service, name,
                                 [(tag['key'], tag['value']) for tag in tags])


def __determine_host(host):
  if host in ['localhost', '127.0.0.1', None, '']:
    host = socket.getfqdn()
  return host


class Monitor(object):
  def __init__(self, spectator, metric_service, options):
    self.__period = options.period
    self.__spectator = spectator
    self.__metric_service = metric_service
    self.__services = options.services
    self.__params = {}

  def __data_map_to_service_metrics(self, data_map):
    result = {}
    count = 0
    for service, metrics in data_map.items():
      actual_metrics = metrics.get('metrics', None)
      if actual_metrics is None:
        logging.error('Unexpected response from "%s"', service)
      else:
        count += len(actual_metrics)
        result[service] = actual_metrics
    return result, count

  def __call__(self):
    logging.info('Starting Monitor')
    while True:
      start = time.time()
      data_map = self.__spectator.scan_by_service(
        self.__services, params=self.__params)
      collected = time.time()
      service_metrics, count = self.__data_map_to_service_metrics(data_map)
      self.__metric_service.store(service_metrics)
      done = time.time()
      logging.info('Wrote %d metrics in %d ms + %d ms',
                   count, collected - start, done - collected)
      time.sleep(self.__period)


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
  parser.add_argument('--period', default=60, type=int)
  parser.add_argument('--prototype_path', default='')
  parser.add_argument('--command', default='')
  parser.add_argument('--monitor', default=False, action='store_true')
  parser.add_argument('--nomonitor', dest='monitor', action='store_false')

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

  spectator = spectator_client.SpectatorClient(options)
  try:
    stackdriver = stackdriver_client.StackdriverClient.make_client(options)
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

  if options.monitor:
    logging.info('Starting Monitor every %d s', options.period)

    # TODO: Replace this with a real service.
    metric_service = DummyMetricService()

    monitor = Monitor(spectator, metric_service, options)
    threading.Thread(target=monitor, name='monitor').start()

  logging.info('Starting HTTP server on port %d', options.port)
  url_path_to_handler = {entry.url_path: entry.handler for entry in registry}
  httpd = HttpServer(options.port, url_path_to_handler)
  httpd.serve_forever()


if __name__ == '__main__':
  main()

