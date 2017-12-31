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

"""Metrics support via prometheus.

NOTE(ewiseblatt): 20180102
This does not work in practice because the job is short lived so restarts
frequently with low metric counts. Prometheus wont scrape it so we need
the gateway server, which does not aggregate. Since it has no concept of
job lifetime, it cannot detect restarts. In pratice many of the counters
are 1 per run. The gateway server always sees 1 and thinks nothing has
changed.

I'm leaving this around for the time being in case inspiration hits for
how to work around this. Otherwise, use the file based metrics then
upload them into some other database or metrics system via post-processing.
"""

import logging
from buildtool.base_metrics import (
    BaseMetricsRegistry,
    Counter,
    Gauge,
    Timer,
    MetricFamily)

from buildtool.util import add_parser_argument

from prometheus_client.core import (
    GaugeMetricFamily,
    CounterMetricFamily,
    REGISTRY)

from prometheus_client.exposition import push_to_gateway


class PrometheusMetricFamily(MetricFamily):
  """Handles conversion into prometheus format."""
  def __init__(self, registry, name, description, factory, family_type,
               label_names):
    super(PrometheusMetricFamily, self).__init__(
        registry, name, description, factory, family_type)
    self.__label_names = list(label_names)
    self.__label_index = {key: index for index, key in enumerate(label_names)}
    self.__prometheus_name = 'buildtool:{name}'.format(
        name=name.replace('.', ':'))

  def instance_labels(self, instance):
    """Returns a list of label values in the expected order."""
    result = [''] * len(self.__label_names)
    for key, value in instance.labels.items():
      result[self.__label_index[key]] = str(value)
    return result

  def encode_timer(self):
    """Encodes a timer as a pair of counters."""
    count_member = CounterMetricFamily(
        self.__prometheus_name + '_count',
        self.description, labels=self.__label_names)
    total_member = CounterMetricFamily(
        self.__prometheus_name + '_totalSeconds',
        self.description, labels=self.__label_names)

    for instance in self.instance_list:
      labels = self.instance_labels(instance)
      count_member.add_metric(labels=labels, value=instance.count)
      total_member.add_metric(labels=labels, value=instance.total_seconds)

    return [count_member, total_member]

  def encode(self):
    """Encodes metrics into the prometheus registry."""
    if self.family_type == 'TIMER':
      return self.encode_timer()

    if self.family_type == 'COUNTER':
      prometheus_family = CounterMetricFamily
    elif self.family_type == 'GAUGE':
      prometheus_family = GaugeMetricFamily
    else:
      raise ValueError('Unsupported type {0}'.format(self.family_type))

    member = prometheus_family(
        self.__prometheus_name, '', labels=self.__label_names)

    for instance in self.instance_list:
      labels = self.instance_labels(instance)
      member.add_metric(labels=labels, value=instance.value)

    return [member]


class PrometheusMetricsRegistry(BaseMetricsRegistry):
  """Implements MetricsRegistry using Prometheus."""
  # pylint: disable=too-few-public-methods

  def __init__(self, options):
    super(PrometheusMetricsRegistry, self).__init__(options)
    if not options.monitoring_enabled:
      return

    self.__push_gateway = options.prometheus_gateway_netloc
    REGISTRY.register(self)

  def _do_make_counter_family(self, name, description, label_names, value_type):
    """Implements interface."""
    return PrometheusMetricFamily(
        self, name, description, Counter, MetricFamily.COUNTER, label_names)

  def _do_make_gauge_family(self, name, description, label_names, value_type):
    """Implements interface."""
    return PrometheusMetricFamily(
        self, name, description, Gauge, MetricFamily.GAUGE, label_names)

  def _do_make_timer_family(self, name, description, label_names):
    """Implements interface."""
    return PrometheusMetricFamily(
        self, name, description, Timer, MetricFamily.TIMER, label_names)

  def _do_flush_updated_metrics(self, updated_metrics):
    """Pushes metrics to prometheus."""
    push_to_gateway(self.__push_gateway, "buildtool", REGISTRY)

  def collect(self):
    """Implements prometheus REGISTRY collection interface."""
    all_members = []
    for family in self.metric_family_list:
      all_members.extend(family.encode())

    for member in all_members:
      yield member


def init_argument_parser(parser, defaults):
  """Initialize argument parser with prometheus parameters."""
  add_parser_argument(
      parser, 'prometheus_gateway_netloc', defaults, 'localhost:9091',
      help='Location of the prometheus gateway to push to.')
