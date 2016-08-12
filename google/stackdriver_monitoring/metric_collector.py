#!/usr/bin/env python
# pylint: disable=missing-docstring
import argparse
import logging
import logging.config

from http_server import (HttpServer, StdoutRequestHandler)

import metric_collector_handlers as handlers

from spectator_client import SpectatorClient
from stackdriver_client import StackdriverClient


# Treat everything as a gauge because StackDriver counters
# require managing a start time.
COUNTER_KIND = 'GAUGE'
KIND_MAP = {'Gauge': 'GAUGE', 'Counter': COUNTER_KIND, 'Timer': COUNTER_KIND}


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
  parser.add_argument('--credential_path', default='')
  parser.add_argument('--host', default='localhost')
  parser.add_argument('--period', default=30)
  parser.add_argument('--prototype_path', default='')
  parser.add_argument('--command', default='')

  # Either space or ',' delimited
  parser.add_argument('services', nargs='*', default=['all'])

  return parser.parse_args()


def process_command(command, spectator, stackdriver, options):
  request = StdoutRequestHandler()
  params = {}

  if command == 'clear':
    handlers.ClearCustomDescriptorsHandler(
        options, stackdriver)(request, '/clear', params, None)
  elif command == 'dump':
    handlers.DumpMetricsHandler(
        options, spectator)(request, '/dump', params, None)
  elif command == 'list':
    handlers.ListCustomDescriptorsHandler(
        options, stackdriver)(request, '/list', params, None)
  elif command == 'explore':
    handlers.ExploreCustomDescriptorsHandler(
        options, spectator)(request, '/explore', params, None)
  elif command == 'show':
    handlers.ShowCurrentMetricsHandler(
        options, spectator)(request, '/show', params, None)
  else:
    raise ValueError('Unknown command "{0}".'.format(command))

def main():
  init_logging('metric_collector.log')
  options = get_options()

  spectator = SpectatorClient(options)
  try:
    stackdriver = StackdriverClient.make_client(options)
  except IOError as ioerror:
    logging.error('Could not create stackdriver client -- Stackdriver will be unavailable\n%s',
                  ioerror)
    stackdriver = None

  if options.command:
    process_command(options.command, spectator, stackdriver, options)
    return

  path_handlers = {
    '/': handlers.BaseHandler(options),
    '/clear': handlers.ClearCustomDescriptorsHandler(options, stackdriver),
    '/dump': handlers.DumpMetricsHandler(options, spectator),
    '/list': handlers.ListCustomDescriptorsHandler(options, stackdriver),
    '/explore': handlers.ExploreCustomDescriptorsHandler(options, spectator),
    '/show': handlers.ShowCurrentMetricsHandler(options, spectator)
   }

  logging.info('Starting HTTP server on port %d', options.port)
  httpd = HttpServer(options.port, path_handlers)
  httpd.serve_forever()


if __name__ == '__main__':
  main()

