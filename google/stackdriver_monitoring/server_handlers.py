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

"""Implements commands that starts the web-server daemon."""


import logging
import threading
import time
import traceback
import http_server

import command_processor
import spectator_client
import stackdriver_service
import datadog_service


class HomePageHandler(command_processor.CommandHandler):
  """Implements the home page for the server.

  This lists all the commands with links to execute them.
  """
  def __init__(self, all_handlers, url_path, command_name, description):
    """Constructor.

    Args:
      all_handlers: [list of CommandHandler] Determines the page contents.
        The entries that have url_paths will be displayed.
    """
    super(HomePageHandler, self).__init__(url_path, command_name, description)
    self.__all_handlers = all_handlers

  def process_web_request(self, request, path, params, fragment):
    """Implements CommandHandler."""
    query = self.params_to_query(params)
    rows = [(handler.url_path, handler.description)
            for handler in self.__all_handlers]
    row_html = [('<tr>'
                 '<td><A href="{path}{params}">{path}</A></td>'
                 '<td>{info}</td>'
                 '</tr>'.format(path=row[0], params=query, info=row[1]))
                for row in rows if row[0] is not None]

    html_body = '<table>\n{0}\n</table>'.format('\n'.join(row_html))
    html_doc = http_server.build_html_document(
        html_body, title='Spinnaker Metrics Administration')
    request.respond(200, {'ContentType': 'application/html'}, html_doc)


class WebserverCommandHandler(command_processor.CommandHandler):
  """Implements the embedded Web Server."""
  def __init__(self, handler_list, url_path, command_name, description):
    """Constructor.

    Args:
      handler_list: The list of CommandHandlers available to the server.
        The server will lookup and delegate based on the URL path received.
    """
    super(WebserverCommandHandler, self).__init__(
        url_path, command_name, description)
    self.__handler_list = handler_list

  def process_commandline_request(self, options):
    """Implements CommandHandler.

    This starts the server and will run forever.
    """
    command_processor.set_global_options(options)

    logging.info('Starting HTTP server on port %d', options['port'])
    url_path_to_handler = {handler.url_path: handler.process_web_request
                           for handler in self.__handler_list}
    httpd = http_server.HttpServer(options['port'], url_path_to_handler)
    httpd.serve_forever()

  def add_argparser(self, subparsers):
    """Implements CommandHandler."""
    parser = super(WebserverCommandHandler, self).add_argparser(subparsers)
    parser.add_argument('--port', default=8008, type=int)
    spectator_client.SpectatorClient.add_standard_parser_arguments(parser)
    stackdriver_service.StackdriverMetricsService.add_parser_arguments(parser)
    return parser


class MonitorCommandHandler(WebserverCommandHandler):
  """Runs the embedded Web Server with a metric publishing loop."""

  def make_metric_service(self, options):
    """Create the metric service we'll use to publish metrics to a backend.
    """
    if options['stackdriver']:
      return stackdriver_service.make_service(options)
    if options['datadog']:
      return datadog_service.make_datadog_service(options)
    raise ValueError('No metric service specified.')

  def __data_map_to_service_metrics(self, data_map):
    """Extract raw responses into just the metrics.

    Args:
      data_map: [dict of list of response dicts] Keyed by service name,
          whose value is the list of raw response dictionaries.
    Returns:
      dictionary keyed by service ame whose value is the list of 'metrics'
          dictionaries embedded in the original raw response dictionaries.
    """
    result = {}
    for service, metrics in data_map.items():
      actual_metrics = metrics.get('metrics', None)
      if actual_metrics is None:
        logging.error('Unexpected response from "%s"', service)
      else:
        result[service] = actual_metrics
    return result

  def process_commandline_request(self, options, metric_service=None):
    """Impements CommandHandler."""
    if metric_service is None:
      metric_service = self.make_metric_service(options)

    daemon = threading.Thread(target=self, name='monitor',
                     args=(options, metric_service))
    daemon.daemon = True
    daemon.start()
    super(MonitorCommandHandler, self).process_commandline_request(options)

  def __call__(self, options, metric_service):
    """This is the actual method that implements the CommandHandler.

    It is put here in a callable so that we can run this in a separate thread.
    The main thread will be the standard WebServer.
    """
    period = options['period']
    service_endpoints = spectator_client.determine_service_endpoints(options)
    spectator = spectator_client.SpectatorClient(options)

    logging.info('Starting Monitor')
    time_offset = int(time.time())
    while True:
      start = time.time()
      done = start
      service_metric_map = spectator.scan_by_service(service_endpoints)
      collected = time.time()
      try:
        count = metric_service.publish_metrics(service_metric_map)
        if count is None:
          count = 0

        done = time.time()
        logging.info(
            'Wrote %d metrics in %d ms + %d ms',
            count, (collected - start) * 1000, (done - collected) * 1000)
      except BaseException as ex:
        traceback.print_exc(ex)
        logging.error(ex)

      # Try to align time increments so we always collect around the same time
      # so that the measurements we report are in even intervals.
      # There is still going to be jitter on the collection end but we'll at
      # least always start with a steady rhythm.
      delta_time = (period - (int(done) - time_offset)) % period
      if delta_time == 0 and (int(done) == time_offset
                              or (done - start <= 1)):
        delta_time = period
      time.sleep(delta_time)

  def add_argparser(self, subparsers):
    """Implements CommandHandler."""
    parser = super(MonitorCommandHandler, self).add_argparser(subparsers)
    backend = parser.add_mutually_exclusive_group()
    backend.add_argument('--stackdriver', default=False, action='store_true',
                        help='Publish metrics to stackdriver.')
    backend.add_argument('--datadog', default=False, action='store_true',
                         help='Publish metrics to Datadog.')
    parser.add_argument(
        '--fix_stackdriver_labels_unsafe', default=True,
        action='store_true',
        help='Work around Stackdriver design bug. Using this'
        ' option can result in the loss of all historic data for'
        ' a given metric that needs to workaround. Not using this'
        ' options will result in the inability to collect metric'
        ' data for a given metric that needs the workaround.'
        ' When needed the workaround will only be needed once'
        ' and then remembered for the lifetime of the project.')
    parser.add_argument(
        '--nofix_stackdriver_labels_unsafe',
        dest='fix_stackdriver_labels_unsafe',
        action='store_false')

    parser.add_argument('--period', default=60, type=int)
    return parser


def add_handlers(all_handlers, subparsers):
  """Registers the commands that run the embedded web server."""
  all_handlers.append(
      HomePageHandler(all_handlers, '/', None,
                      'Home page for Spinnaker metric administration.'))

  handler_list = [
      MonitorCommandHandler(
          all_handlers, None, 'monitor',
          'Run a daemon that monitors Spinnaker services and publishes metrics'
          ' to a metric service.'),
      WebserverCommandHandler(
          all_handlers, None, 'webserver',
          'Run a daemon that provides a webserver to manually interact with'
          ' spinnaker services publishing metrics.')
  ]
  for handler in handler_list:
    handler.add_argparser(subparsers)
    all_handlers.append(handler)
