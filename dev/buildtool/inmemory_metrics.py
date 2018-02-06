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

"""Metrics support via in memory storage.

This keeps around all the metrics in memory then flushes them out to
a JSON file on disk where they can be post processed. It is cumbersome,
but reliable given the short-lived nature of these batch jobs and an
interest in longer term retention of the information after the jobs have
completed.

This will write out a single file decorated with the process id.
In practice, multiple jobs are run currently, so the collection directory
will contain multiple files, each with the different process ids.
For what it is worth, these correspond to the process ids in the log
files which could help identify the context of the metrics. In addition
the CLI argv parameters are added into the files to help give context.

All the state changes are recorded and held for the lifetime of the process.
Although some scripts take a long time to execute, they shouldnt be producing
many state changes in metrics so this should not be a big deal. Keeping
all the values is almost as simple as having a single aggregation, but provides
some insight into sequencing and other flow details. Keeping all the data
provides high resolution for timing but is motivated by simplicity.
"""


import collections
import datetime
import json
import logging
import os
import sys
import threading

from buildtool import (
    add_parser_argument,
    write_to_path)

from buildtool.base_metrics import (
    BaseMetricsRegistry,
    Counter,
    Gauge,
    Timer,
    MetricFamily)

SNAPSHOT_CATEGORY = {
    MetricFamily.COUNTER: 'counters',
    MetricFamily.GAUGE: 'gauges',
    MetricFamily.TIMER: 'timers'
}

class DataPoint(collections.namedtuple('DataPoint', ['value', 'utc'])):
  """A time-series data point."""
  pass


class InMemoryCounter(Counter):
  """Specializes for in memory tracking.

  This class also supports systems that want change events
  rather than aggregated counters. To provide this support,
  we have "mark as delta" that returns each of the last events
  since the last mark with the counter values relative to the previous
  event (how much was counted) rather than absolute.

  We'll still accumulate values and write them to files for possible
  convienence if influxDB is not available.
  """
  # pylint: disable=too-few-public-methods

  CATEGORY = SNAPSHOT_CATEGORY[MetricFamily.COUNTER]

  def __init__(self, family, labels):
    super(InMemoryCounter, self).__init__(family, labels)
    self.__timeseries = []
    self.__timeseries_mutex = threading.Lock()
    self.__mark = (0, 0)

  def mark(self):
    """Return the slice of changes since the last mark."""
    with self.__timeseries_mutex:
      count = len(self.__timeseries)
      start = self.__mark[0]
      self.__mark = (count, self.__timeseries[-1][0])
    return self.__timeseries[start:count]

  def mark_as_delta(self):
    """Return the slice of changes since the last mark.

    Return values as delta since previous known value.
    """
    with self.__timeseries_mutex:
      prev_value = self.__mark[1]
    raw_result = self.mark()
    result = []
    for entry in raw_result:
      delta = entry.value - prev_value
      result.append(DataPoint(delta, entry.utc))

    return result

  def touch(self, utc=None):
    super(InMemoryCounter, self).touch(utc=utc)
    with self.__timeseries_mutex:
      self.__timeseries.append(DataPoint(self.count, self.last_modified))

  def append_to_metrics_snapshot(self, snapshot):
    """Add this counter to the given tsnapshot."""
    with self.__timeseries_mutex:
      values = [{'time': point.utc.isoformat(), 'value': point.value}
                for point in self.__timeseries]
    family_timeseries = snapshot[self.CATEGORY][self.name]['collectors']
    family_timeseries.append({
        'labels': self.labels,
        'values': values})
    return len(values)


class InMemoryGauge(Gauge):
  """Specializes for in memory tracking."""
  # pylint: disable=too-few-public-methods

  CATEGORY = SNAPSHOT_CATEGORY[MetricFamily.GAUGE]

  @property
  def timeseries(self):
    return self.__timeseries

  def __init__(self, family, labels):
    super(InMemoryGauge, self).__init__(family, labels)
    self.__timeseries = []
    self.__timeseries_mutex = threading.Lock()
    self.__mark = 0

  def mark(self):
    """Return the slice of changes since the last mark."""
    with self.__timeseries_mutex:
      count = len(self.__timeseries)
      start = self.__mark
      self.__mark = count
    return self.__timeseries[start:count]

  def mark_as_delta(self):
    return self.mark()

  def touch(self, utc=None):
    super(InMemoryGauge, self).touch(utc=utc)
    data_point = DataPoint(self.value, self.last_modified)
    with self.__timeseries_mutex:
      self.__timeseries.append(data_point)

  def append_to_metrics_snapshot(self, snapshot):
    """Add this gauge to the given snapshot."""
    with self.__timeseries_mutex:
      values = [{'time': point.utc.isoformat(), 'value': point.value}
                for point in self.__timeseries]

    family_timeseries = snapshot[self.CATEGORY][self.name]['collectors']
    family_timeseries.append({
        'labels': self.labels,
        'values': values})
    return len(values)


class InMemoryTimer(Timer):
  """Specializes for in memory tracking."""
  # pylint: disable=too-few-public-methods

  CATEGORY = SNAPSHOT_CATEGORY[MetricFamily.TIMER]

  def __init__(self, family, labels):
    super(InMemoryTimer, self).__init__(family, labels)
    self.__timeseries = []
    self.__timeseries_mutex = threading.Lock()
    self.__mark = (0, 0, 0)

  def mark(self):
    """Return the slice of changes since the last mark."""
    with self.__timeseries_mutex:
      count = len(self.__timeseries)
      start = self.__mark[0]
      self.__mark = (count,
                     self.__timeseries[-1][0][0],
                     self.__timeseries[-1][0][1])
    return self.__timeseries[start:count]

  def mark_as_delta(self):
    """Return the slice of changes since the last mark.

    Return values as delta since previous known value.
    """
    with self.__timeseries_mutex:
      prev_count = self.__mark[1]
      prev_total = self.__mark[2]

    raw_result = self.mark()
    result = []
    for entry in raw_result:
      delta_count = entry.value[0] - prev_count
      delta_total = entry.value[1] - prev_total
      result.append(DataPoint((delta_count, delta_total), entry.utc))

    return result

  def touch(self, utc=None):
    super(InMemoryTimer, self).touch(utc=utc)
    with self.__timeseries_mutex:
      self.__timeseries.append(DataPoint((self.count, self.total_seconds),
                                         self.last_modified))

  def append_to_metrics_snapshot(self, snapshot):
    """Add this gauge to the given snapshot."""
    with self.__timeseries_mutex:
      values = [{'time': point.utc.isoformat(),
                 'count': point.value[0],
                 'totalSecs': point.value[1]}
                for point in self.__timeseries]
    family_timeseries = snapshot[self.CATEGORY][self.name]['collectors']
    family_timeseries.append({
        'labels': self.labels,
        'values': values})
    return len(values)


class InMemoryMetricsRegistry(BaseMetricsRegistry):
  """Implements MetricsRegistry using in memroy DataPoints."""
  # pylint: disable=too-few-public-methods

  @staticmethod
  def init_argument_parser(parser, defaults):
    """Initialize argument parser with in-memory parameters."""
    if hasattr(parser, 'added_inmemory'):
      return
    add_parser_argument(
        parser, 'metrics_dir', defaults, None,
        help='Path to file to write metrics into')
    parser.added_inmemory = True

  def __init__(self, options):
    super(InMemoryMetricsRegistry, self).__init__(options)
    self.__metrics_snapshot_prototype = {
        SNAPSHOT_CATEGORY[MetricFamily.COUNTER]: {},
        SNAPSHOT_CATEGORY[MetricFamily.GAUGE]: {},
        SNAPSHOT_CATEGORY[MetricFamily.TIMER]: {},
        'argv': sys.argv,
        'job': 'buildtool',
        'options': vars(self.options),
        'pid': os.getpid(),
        'start_time': datetime.datetime.utcnow().isoformat()
    }

    self.__known_counter_families = {}
    self.__known_gauge_families = {}

    self.__metrics_path = None
    if not options.monitoring_enabled:
      logging.warning('Monitoring is disabled')
      return

    pid = os.getpid()
    dir_path = (options.metrics_dir
                or os.path.join(options.output_dir, 'metrics'))
    self.__metrics_path = os.path.join(
        dir_path,
        'buildtool-metrics__{command}__{pid}.json'.format(
            command=options.command, pid=pid))

  def _do_make_family(
      self, family_type, name, description, label_names):
    """Implements interface."""
    return self.__new_family(name, description, label_names, family_type)

  def make_snapshot(self):
    """Writes metrics to file."""
    snapshot = dict(self.__metrics_snapshot_prototype)
    snapshot['end_time'] = datetime.datetime.utcnow().isoformat()

    total_metric_count = 0
    total_datapoint_count = 0
    for family in self.metric_family_list:
      all_instances = family.instance_list
      total_metric_count += len(all_instances)
      snapshot_category = SNAPSHOT_CATEGORY[family.family_type]
      snapshot[snapshot_category][family.name]['collectors'] = []
      for metric in all_instances:
        total_datapoint_count += metric.append_to_metrics_snapshot(snapshot)
    return snapshot, total_metric_count, total_datapoint_count

  def __flush_snapshot(self, snapshot):
    """Writes metric snapshot to file."""
    text = json.JSONEncoder(indent=2, separators=(',', ': ')).encode(snapshot)

    # Use intermediate temp file to not clobber old snapshot metrics on failure.
    metrics_path = self.__metrics_path
    tmp_path = metrics_path + '.tmp'
    write_to_path(text, tmp_path)
    os.rename(tmp_path, metrics_path)
    logging.debug('Wrote metric snapshot to %s', metrics_path)

  def _do_flush_final_metrics(self):
    """Writes metrics to file."""
    snapshot, metric_count, datapoint_count = self.make_snapshot()
    logging.debug('Flushing final snapshot with %d data points over %d metrics',
                  datapoint_count, metric_count)
    self.__flush_snapshot(snapshot)

  def _do_flush_updated_metrics(self, updated_metrics):
    """Implements interface."""
    snapshot, _, _ = self.make_snapshot()
    self.__flush_snapshot(snapshot)

  def __new_family(self, name, description, label_names, family_type):
    """Defines a new family container instance."""
    # pylint: disable=unused-argument
    # We dont use label_names, but take it because it is part of the interface
    # that the calling methods have.
    self.__metrics_snapshot_prototype[SNAPSHOT_CATEGORY[family_type]][name] = {
        'name': name,
        'type': family_type,
        'description': description,
        'collectors': []
    }
    type_to_factory = {
        MetricFamily.COUNTER: InMemoryCounter,
        MetricFamily.GAUGE: InMemoryGauge,
        MetricFamily.TIMER: InMemoryTimer
    }
    factory = type_to_factory[family_type]
    return MetricFamily(self, name, description, factory, family_type)
