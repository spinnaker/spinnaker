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

"""Implements metric service for interacting with Datadog."""


# pip install datadog
import logging
import os
import socket
import datadog

import spectator_client


class DatadogMetricsService(object):
  """A metrics service for interacting with Datadog."""

  @staticmethod
  def add_standard_parser_arguments(parser):
    """Adds commandline arguments common to datadog commands."""
    parser.add_argument('--host', default='localhost')


  @property
  def api(self):
    """The Datadog API stub for interacting with Datadog."""
    if self.__api is None:
      datadog.initialize(api_key=self.__api_key, app_key=self.__app_key,
                         host_name=self.__host)
      self.__api = datadog.api
    return self.__api


  def __init__(self, api_key, app_key, host=None):
    """Constructs the object."""
    self.__api = None
    self.__host = host
    self.__api_key = api_key
    self.__app_key = app_key

  def __append_timeseries_point(
        self, service, name,
        instance, metric_metadata, service_metadata, result):
    """Creates a post payload for a DataDog time series data point.

       See http://docs.datadoghq.com/api/?lang=python#metrics-post.

    Args:
      service: [string] The name of the service that the metric is from.
      name: [string] The name of the metric coming from the service.
      instance: [dict] The spectator entry for a specific metric value
         for a specific tag binding instance that we're going to append.
      metric_metadata: [dict] The spectator JSON object for the metric
         is used to get the kind and possibly other metadata.
      result: [list] The result list to append all the time series messages to.
    """
    # In practice this converts a Spinnaker Timer into either
    # <name>__count or <name>__totalTime and removes the "statistic" tag.
    name, tags = spectator_client.normalize_name_and_tags(
        name, instance, metric_metadata)
    if tags is None:
      return  # ignore metrics that had no tags because these are bogus.

    result.append({
        'metric': '{service}.{name}'.format(service=service, name=name),
        'host': service_metadata['__host'],
        'points': [(elem['t'] / 1000, elem['v'])
                   for elem in instance['values']],
        'tags': ['{0}:{1}'.format(tag['key'], tag['value']) for tag in tags]
    })

  def publish_metrics(self, service_metrics):
    """Writes time series data to Datadog for a metric snapshot."""
    points = []
    spectator_client.foreach_metric_in_service_map(
        service_metrics, self.__append_timeseries_point, points)

    try:
      self.api.Metric.send(points)
    except IOError as ioerr:
      logging.error('Error sending to datadog: %s', ioerr)
      raise

    return len(points)


def make_datadog_service(options):
  """Create a datadog service instance for interacting with Datadog."""
  app_key = os.environ['DATADOG_APP_KEY']
  api_key = os.environ['DATADOG_API_KEY']
  host = socket.getfqdn(options['host'] or '')
  return DatadogMetricsService(api_key=api_key, app_key=app_key, host=host)
