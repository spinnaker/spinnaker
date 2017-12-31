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

"""Metrics support via stackdriver.

To clear metric descriptors and start over:

from google.cloud.monitoring import Client
  client = Client.from_service_account_json(<json path>)
  l = client.list_metric_descriptors(
       type_prefix='custom.googleapis.com/buildtool/')
  for d in l:
     d.delete()


NOTE(ewiseblatt): 20180102
This is not practical in practice because the custom metrics produced
are difficult to consume out of stackdriver using the normal UIs
available.

I'm leaving this around for the time being in case inspiration hits for
how to work around this or I come across some other tool/technique.
Otherwise, use the file based metrics then upload them into some other
database or metrics system via post-processing.
"""

import logging
import urllib2
from buildtool.base_metrics import (
    BaseMetricsRegistry,
    Counter,
    Gauge,
    Timer,
    MetricFamily)

from buildtool.util import add_parser_argument

from google.cloud.monitoring import (
    MetricKind,
    ValueType,
    LabelDescriptor,
    LabelValueType)
import google.cloud.monitoring as gcp_monitoring


def name_to_stackdriver_type(name):
  """Stackdriver custom metric type name for the given metric name."""
  return 'custom.googleapis.com/buildtool/{name}'.format(name=name)


class StackdriverCounter(Counter):
  """Specializes for stackdriver."""
  # pylint: disable=too-few-public-methods
  def __init__(self, family, labels):
    super(StackdriverCounter, self).__init__(family, labels)

    # Treat all labels as strings for now for simplicity of specifying the
    # metric descriptor label descriptors.
    self.__stackdriver_metric = family.registry.client.metric(
        type_=name_to_stackdriver_type(family.name),
        labels={key: str(value) for key, value in labels.items()})

  def append_to_timeseries(self, timeseries, client, resource):
    """Add this counter to the given timeseries list."""
    with self.mutex:
      timeseries.append(
          client.time_series(
              self.__stackdriver_metric, resource,
              self.count, end_time=self.last_modified,
              start_time=self.family.start_time))


class StackdriverTimer(Timer):
  """Specializes for stackdriver."""
  # pylint: disable=too-few-public-methods
  def __init__(self, family, labels):
    super(StackdriverTimer, self).__init__(family, labels)
    name = family.name
    # pylint: disable=protected-access
    # This is our registry class -- we're encapsulated.
    count_family = family.registry._do_make_counter_family(
        name + '_count', family.description, labels.keys(), int)
    total_family = family.registry._do_make_counter_family(
        name + '_totalSecs', family.description, labels.keys(), float)

    # Treat all labels as strings for now for simplicity of specifying the
    # metric descriptor label descriptors.
    str_labels = {key: str(value) for key, value in labels.items()}
    self.__stackdriver_count_metric = count_family.registry.client.metric(
        type_=name_to_stackdriver_type(count_family.name),
        labels=str_labels)
    self.__stackdriver_total_metric = total_family.registry.client.metric(
        type_=name_to_stackdriver_type(total_family.name),
        labels=str_labels)

  def append_to_timeseries(self, timeseries, client, resource):
    """Add this counter to the given timeseries list."""
    with self.mutex:
      timeseries.append(
          client.time_series(
              self.__stackdriver_count_metric, resource,
              self.count, end_time=self.last_modified,
              start_time=self.family.start_time))
      timeseries.append(
          client.time_series(
              self.__stackdriver_total_metric, resource,
              self.total_seconds, end_time=self.last_modified,
              start_time=self.family.start_time))


class StackdriverGauge(Gauge):
  """Specializes for stackdriver."""
  # pylint: disable=too-few-public-methods
  def __init__(self, family, labels):
    super(StackdriverGauge, self).__init__(family, labels)

    # Treat all labels as strings for now for simplicity of specifying the
    # metric descriptor label descriptors.
    self.__stackdriver_metric = family.registry.client.metric(
        type_=name_to_stackdriver_type(family.name),
        labels={key: str(value) for key, value in labels.items()})

  def append_to_timeseries(self, timeseries, client, resource):
    """Add this counter to the given timeseries list."""
    with self.mutex:
      timeseries.append(
          client.time_series(
              self.__stackdriver_metric, resource,
              self.value, end_time=self.last_modified))


def get_google_metadata(attribute):
  """Query google metadata server for attribute value."""
  url = 'http://169.254.169.254/computeMetadata/v1/' + attribute
  request = urllib2.Request(url)
  request.add_header('Metadata-Flavor', 'Google')
  try:
    response = urllib2.urlopen(request)
  except IOError as ioex:
    logging.info('Cannot read google metadata,'
                 ' probably not on Google Cloud Platform.'
                 ' url=%s: %s', url, ioex)
    raise ioex

  return response.read()


class StackdriverMetricsRegistry(BaseMetricsRegistry):
  """Implements MetricsRegistry using Stackdriver."""
  # pylint: disable=too-few-public-methods

  @property
  def client(self):
    """Get stackdriver client instance."""
    return self.__client

  def __init__(self, options):
    super(StackdriverMetricsRegistry, self).__init__(options)
    self.__known_descriptors = None
    self.__known_descriptor_types = {}
    self.__client = gcp_monitoring.Client(
        project=options.stackdriver_project,
        credentials=options.stackdriver_credentials)

    if not options.monitoring_enabled:
      return

    # pylint: disable=broad-except
    try:
      full_zone = get_google_metadata('instance/zone')
      zone = full_zone[full_zone.rfind('/') + 1:]
      self.__monitored_resource = self.__client.resource(
          'gce_instance',
          {
              'project_id': get_google_metadata('project/project-id'),
              'zone': zone,
              'instance_id': get_google_metadata('instance/id')
          })
    except Exception as ex:
      logging.error('Caught %s', ex)
      logging.warning('Disabling stackdriver monitoring.')
      options.monitoring_enabled = False
      return

    self.__known_descriptors = self.__client.list_metric_descriptors(
        type_prefix='custom.googleapis.com/buildtool/')
    self.__known_descriptor_types = {
        descriptor.type: descriptor for descriptor in self.__known_descriptors
    }

  def __type_to_stackdriver_type(self, value_type):
    if value_type == float:
      return ValueType.DOUBLE
    elif value_type == long:
      return ValueType.INT64
    elif value_type == int:
      return ValueType.INT64
    else:
      raise TypeError('Unsupported value_type {0}'.format(value_type))

  def _do_make_counter_family(self, name, description, label_names, value_type):
    """Implements interface."""
    stackdriver_value_type = self.__type_to_stackdriver_type(value_type)
    return self.__new_family(name, description, label_names,
                             MetricKind.CUMULATIVE, stackdriver_value_type)

  def _do_make_gauge_family(self, name, description, label_names, value_type):
    """Implements interface."""
    stackdriver_value_type = self.__type_to_stackdriver_type(value_type)
    return self.__new_family(name, description, label_names,
                             MetricKind.GAUGE, stackdriver_value_type)

  def _do_make_timer_family(self, name, description, label_names):
    """Implements interface."""
    # Component families will be created by StackdriverTimer instances.
    return MetricFamily(
        self, name, description, StackdriverTimer, MetricFamily.TIMER)

  def _do_flush_updated_metrics(self, updated_metrics):
    """Pushes metrics to stackdriver."""
    timeseries = []
    for metric in updated_metrics:
      metric.append_to_timeseries(
          timeseries, self.__client, self.__monitored_resource)

    if timeseries:
      logging.debug('Updating %d metrics', len(timeseries))
      self.__client.write_time_series(timeseries)
    else:
      logging.debug('No metrics need updating.')

  def __new_family(self, name, description, label_names,
                   metric_kind, value_type):
    """Defines a new metric descriptor and metric family instance."""

    metric_type = name_to_stackdriver_type(name)
    descriptor = self.__known_descriptor_types.get(metric_type)
    if not descriptor:
      label_descriptors = []
      for label in label_names:
        label_descriptors.append(LabelDescriptor(
            label, LabelValueType.STRING))
      descriptor = self.__client.metric_descriptor(
          metric_type, metric_kind=metric_kind, value_type=value_type,
          description=description, labels=label_descriptors)
      self.__known_descriptor_types[metric_type] = descriptor
      logging.info('Creating new metric descriptor for %s', metric_type)
      if self.options.monitoring_enabled:
        descriptor.create()

    if descriptor.value_type != value_type:
      raise ValueError('Existing {name} value types {old} vs {new}'
                       .format(name=name,
                               old=descriptor.value_type, new=value_type))
    if descriptor.metric_kind != metric_kind:
      raise ValueError('Existing {name} metric kind {old} vs {new}'
                       .format(name=name,
                               old=descriptor.metric_kind, new=metric_kind))
    return (
        MetricFamily(self, name, description,
                     StackdriverCounter, MetricFamily.COUNTER)
        if metric_kind == MetricKind.CUMULATIVE
        else MetricFamily(self, name, description,
                          StackdriverGauge, MetricFamily.GAUGE))


def init_argument_parser(parser, defaults):
  """Initialize argument parser with stackdriver parameters."""
  add_parser_argument(
      parser, 'stackdriver_project', defaults, None,
      help='stackdriver project to manage metrics in')
  add_parser_argument(
      parser, 'stackdriver_credentials', defaults, None,
      help='stackdriver project to manage metrics in')
