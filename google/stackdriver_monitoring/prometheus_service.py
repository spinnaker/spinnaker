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

"""Implements metric service for interacting with Prometheus.

Rather than pushing into prometheus, we'll let prometheus call us
and collect on demand. However the base interface assumes it can call
us so we'll stub that out with a no-op.

To use this service, configure prometheus.yml as follows:

scrape_configs:
  - job_name: 'spinnaker'
    static_configs:
      - targets: ['localhost:8008']
    metrics_path: '/prometheus_metrics'
    honor_labels: true


Where the localhost:8003 is --prometheus_port and localhost is
the hostname this service is running on. The 'honor_labels: true'
is to take the job and service labels injected from this service
(which will be the spinnaker microservices the metrics came from)
rather than the job and instance labels of this service which is
what prometheus is scraping to collect the metrics.
"""

import collections
import logging

import command_processor
import spectator_client

from prometheus_client import (
    CONTENT_TYPE_LATEST,
    generate_latest)

from prometheus_client.core import (
    GaugeMetricFamily,
    CounterMetricFamily,
    REGISTRY)


InstanceRecord = collections.namedtuple(
    'InstanceRecord', ['service', 'netloc', 'data'])
MetricInfo = collections.namedtuple('MetricInfo', ['kind', 'tags', 'records'])


class PrometheusMetricsService(object):
  """Implements monitoring service that implements a Prometheus client.

  This service implements the Prometheus client library interface which
  collects metrics in response to a collect() call.
  """

  @staticmethod
  def add_service_parser_arguments(parser):
    """Adds commandline arguments to configure prometheus client"""
    # Client library has its own http server. Not sure what we need to
    # do to hook it into ours so we'll let the client library use its server
    # for now.
    parser.add_argument(
        '--prometheus_port', default=8003, type=int,
        help='Port for Prometheus HTTP Server')
    parser.add_argument(
        '--prometheus_add_source_metalabels', default=True,
        action='store_true',
        help='Add Spinnaker job/instance labels for prometheus.')

  def __init__(self, options):
    self.__service_endpoints = spectator_client.determine_service_endpoints(
        options)
    self.__spectator = spectator_client.SpectatorClient(options)
    self.__add_metalabels = options.get('prometheus_add_source_metalabels',
                                        True)
    REGISTRY.register(self)

  def __collect_instance_info(
        self, service, name,
        instance, metric_metadata, service_metadata, service_to_name_to_info):
    """Creates a post payload for a DataDog time series data point.

       See http://docs.datadoghq.com/api/?lang=python#metrics-post.

    Args:
      service: [string] The name of the service that the metric is from.
      name: [string] The name of the metric coming from the service.
      instance: [dict] The spectator entry for a specific metric value
         for a specific tag binding instance that we're going to append.
      metric_metadata: [dict] The spectator JSON object for the metric
         is used to get the kind and possibly other metadata.
      service_to_name_to_info: [dict] A dictionary keyed by service to
        A dictionary mapping metric names to MetricInfo being built.
    """
    # In practice this converts a Spinnaker Timer into either
    # <name>__count or <name>__totalTime and removes the "statistic" tag.
    name, tags = spectator_client.normalize_name_and_tags(
        name, instance, metric_metadata)
    if tags is None:
      return  # ignore metrics that had no tags because these are bogus.

    record = InstanceRecord(service,
                            '{0}:{1}'.format(service_metadata['__host'],
                                             service_metadata['__port']),
                            instance)

    name_to_info = service_to_name_to_info.get(service)
    if name_to_info is None:
      name_to_info = {}
      service_to_name_to_info[service] = name_to_info

    tag_names = set([tag['key'] for tag in tags])
    info = name_to_info.get(name)
    if info is None:
      info = MetricInfo(metric_metadata['kind'], tag_names, [record])
      name_to_info[name] = info
      return

    info.records.append(record)
    info.tags.update(tag_names)


  def collect(self):
    """Implements Prometheus Client interface."""
    service_to_name_to_info = {}

    service_metric_map = self.__spectator.scan_by_service(
        self.__service_endpoints)
    spectator_client.foreach_metric_in_service_map(
        service_metric_map, self.__collect_instance_info,
        service_to_name_to_info)

    all_members = []
    for service, name_to_info in service_to_name_to_info.items():
      for name, info in name_to_info.items():
        family = (CounterMetricFamily
                  if info.kind in ('Counter', 'Timer')
                  else GaugeMetricFamily)

        member_name = '{service}:{name}'.format(
            service=service, name=name.replace('.', ':'))

        tags = list(info.tags)
        all_tags = list(tags)
        if self.__add_metalabels:
          all_tags.extend(['job', 'instance'])
        member = family(member_name, '', labels=all_tags)
        all_members.append(member)

        for record in info.records:
          if isinstance(record, dict):
            print '*** RECORD {0}'.format(record)
            print '*** INFO {0}'.format(info)

          instance = record.data
          labels = [''] * len(tags)
          for elem in instance['tags']:
            labels[tags.index(elem['key'])] = elem['value']
          if self.__add_metalabels:
            labels.append(record.service)
            labels.append(record.netloc)

          # Just use the first value. We arent controlling the timestamp
          # so multiple values would be meaningless anyway.
          member.add_metric(labels=labels, value=instance['values'][0]['v'])

    for metric in all_members:
      yield metric


def make_service(options):
  """Create a datadog service instance for interacting with Datadog."""
  return PrometheusMetricsService(options)


class ScrapeHandler(command_processor.CommandHandler):
  """Handles requests from Prometheus Server.

  The server should be configured to hit this URL.
  """
  def __init__(self):
    """Construct handler for Prometheus Server to call."""
    super(ScrapeHandler, self).__init__(
        '/prometheus_metrics',
        'Collect Prometheus Metrics',
        'Forces a server scrape and returns current metrics in'
        ' the current Prometheus format.')

  def process_web_request(self, request, path, params, fragment):
    output = generate_latest()
    request.respond(200, {'ContentType': CONTENT_TYPE_LATEST}, output)
    
