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
import urllib2


def __foreach_metric_tag_binding(
    service, metric_name, metric_data, service_data,
    visitor, visitor_pos_args, visitor_kwargs):
  for metric_instance in metric_data['values']:
    visitor(service, metric_name, metric_instance, metric_data, service_data,
            *visitor_pos_args, **visitor_kwargs)


def foreach_metric_in_service_map(
    service_map, visitor, *visitor_pos_args, **visitor_kwargs):
  for service, service_metrics in service_map.items():
    if service_metrics is None:
      continue
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


class SpectatorClient(object):
  """Helper class for pulling data from Spectator servers."""

  SERVICE_PORT_MAP = {
    'clouddriver': 7002,
    'echo': 8089,
    'fiat': 7003,
    'front50': 8080,
    'gate': 8084,
    'igor': 8088,
    'orca': 8083,
    'rosco': 8087,
  }

  def __init__(self, options):
    self.__host = options['host']
    self.__prototype = None
    self.__default_scan_params = {}

    if options['prototype_path']:
      with open(options['prototype_path']) as fd:
        self.__prototype = json.JSONDecoder().decode(fd.read())

  def collect_metrics(self, host, port, params=None):
    """Return JSON metrics from the given server."""
    sep = '?'
    query = ''
    query_params = dict(self.__default_scan_params)
    query_params.update(params or {})
    for key, value in query_params.items():
      query += sep + key + "=" + urllib2.quote(value)
      sep = "&"
    url = 'http://{host}:{port}/spectator/metrics{query}'.format(
        host=host, port=port, query=query)
    response = urllib2.urlopen(url)
    all_metrics = json.JSONDecoder(encoding='utf-8').decode(response.read())
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

  def scan_by_service(self, service_list, params=None):
    result = {}
    if service_list == ['all']:
      service_list = self.SERVICE_PORT_MAP.keys()

    start = time.time()
    service_time = {service: 0 for service in service_list}
    result = {service: None for service in service_list}
    threads = {}

    def timed_collect(self, service): #, params, service_time, result):
      now = time.time()
      try:
        port = self.SERVICE_PORT_MAP[service]
        result[service] = self.collect_metrics(self.__host, port, params=params)
      except IOError as ioex:
        logging.getLogger(__name__).error('%s failed: %s', service, ioex)
      service_time[service] = int((time.time() - now) * 1000)

    for service in service_list:
      threads[service] = threading.Thread(
          target=timed_collect,
          args=(self, service))#, params, service_time, result))
      threads[service].start()
    for service in service_list:
      threads[service].join()

    logging.info('Collection times %d (ms): %s',
                 (time.time() - start) * 1000, service_time)
    return result

  def scan_by_type(self, service_list, params=None):
    service_map = self.scan_by_service(service_list, params=params)
    return self.service_map_to_type_map(service_map)

  @staticmethod
  def ingest_metrics(service, service_response, type_map):
    """Add JSON metrics |response| from |service| name and add to |type_map|"""
    for key, value in service_response['metrics'].items():
      if key in type_map:
        type_map[key][service] = value
      else:
        type_map[key] = {service: value}

  @staticmethod
  def service_map_to_type_map(service_map):
    type_map = {}
    for service, got in service_map.items():
      SpectatorClient.ingest_metrics(service, got, type_map)
    return type_map
