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

import json
import logging
import threading
import time
import socket
import urllib2


def __foreach_metric_tag_binding(
    service, metric_name, metric_data, service_data,
    visitor, visitor_pos_args, visitor_kwargs):
  for metric_instance in metric_data['values']:
    visitor(service, metric_name, metric_instance, metric_data, service_data,
            *visitor_pos_args, **visitor_kwargs)


def foreach_metric_in_service_map(
    service_map, visitor, *visitor_pos_args, **visitor_kwargs):
  for service, service_metric_list in service_map.items():
    if not service_metric_list:
      continue
    for service_metrics in service_metric_list:
      for metric_name, metric_data in service_metrics['metrics'].items():
        __foreach_metric_tag_binding(
            service, metric_name, metric_data, service_metrics,
            visitor, visitor_pos_args, visitor_kwargs)


def normalize_name_and_tags(name, metric_instance, metric_metadata):
  tags = metric_instance.get('tags', None)
  if not tags:
    return name, None   # signal this metric had no tags so we can ignore it.

  is_timer = metric_metadata['kind'] == 'Timer'
  if is_timer:
    for index, tag in enumerate(tags):
      if tag['key'] == 'statistic':
        name = name + '__{0}'.format(tag['value'])
        del tags[index]
        break
  return name, tags


def _collect_endpoints(service, options, default_host_list):
  result = []
  all_netlocs = options.get(service, '').split(',')
  for netloc in all_netlocs:
    host_port = netloc.split(':')
    if not host_port or not host_port[0]:
      continue
    if len(host_port) > 2:
      raise ValueError('Invalid network location for "{0}": {1}'
                       .format(service, netloc))
    if len(host_port) == 1:
      host_port.append(SpectatorClient.DEFAULT_SERVICE_PORT_MAP[service])
    else:
      try:
        host_port[1] = int(host_port[1])
      except ValueError:
        pass  # allow named ports

    if host_port[0] == SpectatorClient.ALL_HOSTS:
      if default_host_list:
        result.extend([(host, host_port[1])
                       for host in default_host_list])
    else:
      result.append((host_port[0], host_port[1]))

  return result


def determine_service_endpoints(options):
  """Determine the list of spectator endpoints to poll.

  Args:
    options: [dict] The configuration options dictionary.
    See SpectatorClient.add_standard_parser_arguments

  Returns:
    A dictionary of endpoint lists keyed by service of interest.
  """
  result = {}
  default_host_list = [name
                       for name in options.get('service_hosts', '').split(',')
                       if name]
  all_services = SpectatorClient.DEFAULT_SERVICE_PORT_MAP.keys()
  for service in all_services:
    endpoints = _collect_endpoints(service, options, default_host_list)
    if endpoints:
      result[service] = endpoints
  return result


class SpectatorClient(object):
  """Helper class for pulling data from Spectator servers."""

  ALL_HOSTS = '*'

  DEFAULT_SERVICE_PORT_MAP = {
    'clouddriver': 7002,
    'echo': 8089,
    'fiat': 7003,
    'front50': 8080,
    'gate': 8084,
    'igor': 8088,
    'orca': 8083,
    'rosco': 8087,
  }

  @staticmethod
  def add_standard_parser_arguments(parser):
    def add_microservice(parser, service):
      parser.add_argument(
          '--' + service, default=SpectatorClient.ALL_HOSTS,
          help=('A comma-delimited list of {service} endpoints.'
                ' Each endpoint is in the form <host>[:port].'
                ' The default is "{all}" indicating all --service_hosts'
                .format(service=service, all=SpectatorClient.ALL_HOSTS)))

    parser.add_argument('--host', default='localhost',
                        help='The hostname of the spectator services '
                        ' containing the services.')
    parser.add_argument('--prototype_path', default='',
                        help='Optional filter to restrict metrics of interest.')
    for service in SpectatorClient.DEFAULT_SERVICE_PORT_MAP.keys():
      add_microservice(parser, service)

    parser.add_argument(
        '--service_hosts', default='localhost',
        help=('A comma delimited list of hostnames to poll.'
              ' An empty list indicates do not poll any by default;'
              ' each service will explicitly declare its sources, if any.'))

  def __init__(self, options):
    self.__host = options['host']
    self.__prototype = None
    self.__default_scan_params = {'tagNameRegex': '.+'}

    if options['prototype_path']:
      # pylint: disable=invalid-name
      with open(options['prototype_path']) as fd:
        self.__prototype = json.JSONDecoder().decode(fd.read())

  def collect_metrics(self, host, port, params=None):
    """Return JSON metrics from the given server."""
    sep = '?'
    query = ''
    query_params = dict(self.__default_scan_params)
    if params is None:
      params = {}
    keys_to_copy = [key
                    for key in ['tagNameRegex', 'tagValueRegex',
                                'meterNameRegex']
                    if key in params]
    for key in keys_to_copy:
      query_params[key] = params[key]

    for key, value in query_params.items():
      query += sep + key + "=" + urllib2.quote(value)
      sep = "&"

    url = 'http://{host}:{port}/spectator/metrics{query}'.format(
        host=host, port=port, query=query)
    response = urllib2.urlopen(url)
    all_metrics = json.JSONDecoder(encoding='utf-8').decode(response.read())
    all_metrics['__port'] = port
    all_metrics['__host'] = (socket.getfqdn()
                             if host in ['localhost', '127.0.0.1', None, '']
                             else host)

    return (self.filter_metrics(all_metrics, self.__prototype)
            if self.__prototype else all_metrics)

  def filter_metrics(self, instance, prototype):
    """Filter metrics entries in |instance| to those that match |prototype|.

    Only the names and tags are checked. The instance must contain a
    tag binding found in the prototype, but may also contain additional tags.
    The prototype is the same format as the json of the metrics returned.
    """
    filtered = {}

    metrics = instance.get('metrics') or {}
    for key, expect in prototype.get('metrics', {}).items():
      got = metrics.get(key)
      if not got:
        continue
      expect_values = expect.get('values')
      if not expect_values:
        filtered[key] = got
        continue

      expect_tags = [elem.get('tags') for elem in expect_values]

      # Clone the dict because we are going to modify it to remove values
      # we dont care about
      keep_values = []
      def have_tags(expect_tags, got_tags):
        for wanted_set in expect_tags:
          # pylint: disable=invalid-name
          ok = True
          for want in wanted_set:
            if want not in got_tags:
              ok = False
              break
          if ok:
            return True

        return expect_tags == []

      for got_value in got.get('values', []):
        got_tags = got_value.get('tags')
        if have_tags(expect_tags, got_tags):
          keep_values.append(got_value)
      if not keep_values:
        continue

      keep = dict(got)
      keep['values'] = keep_values
      filtered[key] = keep

    result = dict(instance)
    result['metrics'] = filtered
    return result

  def scan_by_service(self, service_endpoints, params=None):
    result = {}

    start = time.time()
    service_time = {service: 0 for service in service_endpoints}
    result = {service: None for service in service_endpoints.keys()}
    threads = {}

    def timed_collect(self, service, endpoints):
      now = time.time()
      endpoint_data_list = []
      for service_host, service_port in endpoints:
        try:
          endpoint_data_list.append(self.collect_metrics(
              service_host, service_port, params=params))
        except IOError as ioex:
          logging.getLogger(__name__).error(
              '%s failed %s:%s with %s',
              service, service_host, service_port, ioex)

      result[service] = endpoint_data_list
      service_time[service] = int((time.time() - now) * 1000)

    for service, endpoints in service_endpoints.items():
      threads[service] = threading.Thread(
          target=timed_collect,
          args=(self, service, endpoints))
      threads[service].start()
    for service in service_endpoints.keys():
      threads[service].join()

    logging.info('Collection times %d (ms): %s',
                 (time.time() - start) * 1000, service_time)
    return result

  def scan_by_type(self, service_endpoints, params=None):
    service_map = self.scan_by_service(service_endpoints, params=params)
    return self.service_map_to_type_map(service_map)

  @staticmethod
  def ingest_metrics(service, response_data, type_map):
    """Add JSON |metric_data| from |service| name and add to |type_map|"""
    metric_data = response_data.get('metrics', {})
    for key, value in metric_data.items():
      if key in type_map:
        have = type_map[key].get(service, [])
        have.append(value)
        type_map[key][service] = have
      else:
        type_map[key] = {service: [value]}

  @staticmethod
  def service_map_to_type_map(service_map):
    type_map = {}
    for service, got_from_each_endpoint in service_map.items():
      for got in got_from_each_endpoint or []:
        SpectatorClient.ingest_metrics(service, got, type_map)
    return type_map
