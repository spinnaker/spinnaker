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

"""Base metrics support is extended for a concrete monitoring system."""

import datetime
import logging
import threading
import time


class Metric(object):
  """A metric."""

  @property
  def family(self):
    """The metric family this instance belongs to."""
    return self.__family

  @property
  def last_modified(self):
    """Time metric was last modified."""
    return self.__last_modified

  @property
  def mutex(self):
    """Mutex for this instance."""
    return self.__mutex

  @property
  def labels(self):
    """Return bound labels."""
    return self.__labels

  def __init__(self, family, labels):
    self.__mutex = threading.Lock()
    self.__name = family.name
    self.__last_modified = None
    self.__family = family
    self.__labels = labels

  def touch(self, utc=None):
    """Update last modified time"""
    self.__last_modified = utc or datetime.datetime.utcnow()
    self.__family.registry.queue_update(self)


class Counter(Metric):
  """A counter."""

  @property
  def count(self):
    """Returns the current [local] counter value."""
    return self.__count

  def __init__(self, family, labels):
    super(Counter, self).__init__(family, labels)
    self.__count = 0

  def inc(self, amount=1, utc=None):
    """Increment the counter"""
    with self.mutex:
      self.__count += amount
      self.touch(utc=utc)


class Gauge(Metric):
  """A gauge."""

  @property
  def value(self):
    """The current gauge value."""
    return self.__compute()

  def __init__(self, family, labels, compute=None):
    super(Gauge, self).__init__(family, labels)
    func = lambda: self.__value
    self.__value = 0
    self.__compute = compute or func

  def track(self, func, *pos_args, **kwargs):
    """Add to gauge while function call is in progress."""
    try:
      self.inc()
      return func(*pos_args, **kwargs)
    finally:
      self.dec()

  def set(self, value, utc=None):
    """Set the gauge value."""
    with self.mutex:
      self.__value = value
      self.touch(utc=utc)

  def inc(self, amount=1, utc=None):
    """Increment the gauge by an amount."""
    with self.mutex:
      self.__value += amount
      self.touch(utc=utc)

  def dec(self, amount=1, utc=None):
    """Decrement the gauge by an amount."""
    with self.mutex:
      self.__value -= amount
      self.touch(utc=utc)


class MetricFamily(object):
  """A Factory for a counter or Gauge metric with specifically bound labels."""

  GAUGE = 'GAUGE'
  COUNTER = 'COUNTER'
  TIMER = 'TIMER'

  @property
  def start_time(self):
    """The start time values are relative to."""
    return self.__registry.start_time

  @property
  def name(self):
    """The abstract name for this family."""
    return self.__name

  @property
  def description(self):
    """The documentation for this metric."""
    return self.__description

  @property
  def registry(self):
    """The MetricsRegistry containing this family."""
    return self.__registry

  @property
  def family_type(self):
    """Returns the type of metrics in this family (GAUGE, COUNTER, TIMER)."""
    return self.__family_type

  @property
  def mutex(self):
    """Returns lock for this family."""
    return self.__mutex

  @property
  def instance_list(self):
    """Return all the label binding metric variatiosn within this family."""
    return self.__instances.values()

  def __init__(self, registry, name, description, factory, family_type):
    self.__mutex = threading.Lock()
    self.__name = name
    self.__description = description
    self.__factory = factory
    self.__instances = {}
    self.__registry = registry
    self.__family_type = family_type

  def get(self, labels):
    """Returns a metric instance with bound labels."""
    key = ''.join('{0}={1}'.format(key, value) for key, value in labels.items())
    with self.__mutex:
      got = self.__instances.get(key)
      if got is None:
        got = self.__factory(self, labels)
        self.__instances[key] = got
      return got


class Timer(Metric):
  """Observes how long functions take to execute."""

  @property
  def count(self):
    """The number of timings captured."""
    return self.__count

  @property
  def total_seconds(self):
    """The total time across all the captured timings."""
    return self.__total

  def __init__(self, family, labels):
    super(Timer, self).__init__(family, labels)
    self.__count = 0
    self.__total = 0

  def observe(self, seconds, utc=None):
    """Capture a timing observation."""
    with self.mutex:
      self.__count += 1
      self.__total += seconds
      self.touch(utc=utc)


class BaseMetricsRegistry(object):
  """Provides base class interface for metrics management.

  Specific metric stores would subclass this to specialize to push
  into their own systems.

  While having this registry be abstract is overkill, it is for what feels
  like practical reasons where there is no easy to use system for our use
  case of short lived batch jobs so there's going to be a lot of maintainence
  here and trials of different systems making this investment more appealing.
  """

  @property
  def options(self):
    """Configured options."""
    return self.__options

  @property
  def start_time(self):
    """When the registry started -- values are relative to this utc time."""
    return self.__start_time

  @property
  def metric_family_list(self):
    """Return all the metric families."""
    return self.__metric_families.values()

  def __init__(self, options):
    """Constructs registry with options from init_argument_parser."""
    self.__start_time = datetime.datetime.utcnow()
    self.__options = options
    self.__mutex = threading.Lock()
    self.__pusher_thread = None
    self.__pusher_thread_event = threading.Event()
    self.__metric_families = {}
    self.__updated_metrics = set([])

  def queue_update(self, metric):
    """Add metric to list of metrics to push out."""
    with self.__mutex:
      self.__updated_metrics.add(metric)

  def register_counter(self, name, description, label_names, value_type=long):
    """Defines a counter metric."""
    return self.__register_family(name, self._do_make_counter_family,
                                  description, label_names, value_type)
  def _do_make_counter_family(
      self, name, description, label_names, value_type):
    """Creates new metric-system specific counter family.

    Args:
      name: [string] Metric name.
      description: [string] Metric help description.
      label_names: [list of string] The labels used to distinguish instances.
      value_type: [type] int, long, or float

    Returns:
      CounterFamily
    """
    raise NotImplementedError()

  def counter(self, name, labels):
    """Returns the specified counter."""
    family = self.__metric_families[name]
    if family.family_type != family.COUNTER:
      raise TypeError('{have} is not a Counter'.format(have=family))
    return family.get(labels)

  def inc(self, name, labels, amount=1):
    """Increment the implied counter."""
    self.counter(name, labels).inc(amount=amount)

  def register_gauge(self, name, description, label_names, value_type=long):
    """Defines a gauge metric."""
    return self.__register_family(name, self._do_make_gauge_family,
                                  description, label_names, value_type)
  def _do_make_gauge_family(
      self, name, description, label_names, value_type):
    """Creates new metric-system specific gauge family.

    Args:
      name: [string] Metric name.
      description: [string] Metric help description.
      label_names: [list of string] The labels used to distinguish instances.
      value_type: [type] int, long, or float

    Returns:
      GaugeFamily
    """
    raise NotImplementedError()

  def gauge(self, name, labels):
    """Returns the specified gauge."""
    family = self.__metric_families[name]
    if family.family_type != family.GAUGE:
      raise TypeError('{have} is not a Gauge'.format(have=family))
    return family.get(labels)

  def set(self, name, labels, value):
    """Sets the implied gauge with the specified value."""
    self.gauge(name, labels).set(value)

  def track(self, name, labels, func, *pos_args, **kwargs):
    """Track number of active calls to the given function."""
    self.gauge(name, labels).track(func, *pos_args, **kwargs)

  def _do_make_timer_family(self, name, description, label_names):
    """Creates new metric-system specific timer family.

    Args:
      name: [string] Metric name.
      description: [string] Metric help description.
      label_names: [list of string] The labels used to distinguish instances.

    Returns:
      TimerFamily
    """
    raise NotImplementedError()

  def register_timer(self, name, description, label_names):
    """Defines a summary metric."""
    return self.__register_family(name, self._do_make_timer_family,
                                  description, label_names)

  def timer(self, name, labels):
    """Returns the specified counter."""
    family = self.__metric_families[name]
    if family.family_type != family.TIMER:
      raise TypeError('{have} is not a Timer'.format(have=family))
    return family.get(labels)

  def time(self, name, labels, func, *pos_args, **kwargs):
    """Time a call to the given function."""
    timer = self.timer(name, labels)
    with timer.mutex:
      try:
        start_time = time.time()
        return func(*pos_args, **kwargs)
      finally:
        timer.observe(time.time() - start_time)

  def __register_family(
      self, name, family_factory, description, label_names, *pos_args):
    """Find family with given name if it exists already, otherwise make one."""
    family = self.__metric_families.get(name)
    if family:
      return family
    with self.__mutex:
      self.__metric_families[name] = family_factory(
          name, description, label_names, *pos_args)

  def instrument_track_and_outcome(
      self, name, description, base_labels, outcome_labels_func,
      result_func, *pos_args, **kwargs):
    """Call the function with the given arguments while instrumenting it.

    This will instrument both tracking of call counts in progress
    as well as the final outcomes in terms of performance and outcome.
    """
    start = time.time()
    try:
      tracking_name = name + '_InProgress'
      self.register_gauge(tracking_name, description, base_labels.keys())
      tracking_gauge = self.gauge(tracking_name, base_labels)
      return tracking_gauge.track(result_func, *pos_args, **kwargs)
    finally:
      seconds = max(0, time.time() - start)
      outcome_name = name + '_Outcome'
      outcome_labels = outcome_labels_func()
      self.register_timer(outcome_name, description, outcome_labels.keys())
      outcome_timer = self.timer(outcome_name, outcome_labels)
      outcome_timer.observe(seconds)

  def start_pusher_thread(self):
    """Starts thread for pushing metrics."""
    def delay_func():
      """Helper function for push thread"""
      if self.__pusher_thread:
        self.__pusher_thread_event.wait(self.options.monitoring_flush_frequency)
      return self.__pusher_thread is not None
    self.__pusher_thread = threading.Thread(
        target=self.flush_every_loop, args=[delay_func])
    self.__pusher_thread.start()

  def stop_pusher_thread(self):
    """Stop thread for pushing metrics."""
    self.__pusher_thread = None
    self.__pusher_thread_event.set()
    # Thread will remain around until it awakes and sees it should exit the
    # loop, though there is a race condition in which we might restart the
    # loop again before the old one wakes up, but in practice restarts are
    # not an expected use case.

  def flush_every_loop(self, ready_func):
    """Start a loop that pushes while the ready_func is true."""
    logging.info('Starting loop to push metrics...')
    while ready_func():
      self.flush_updated_metrics()
    logging.info('Ending loop to push metrics...')

  def _do_flush_updated_metrics(self, updated_metrics):
    """Writes metrics to the server."""
    raise NotImplementedError()

  def _do_flush_final_metrics(self):
    """Notifies that we're doing updating and it is safe to push final metrics.

    This is only informative for implementations that are not incremental.
    """
    pass

  def flush_final_metrics(self):
    """Push the final metrics to the metrics server."""
    if not self.options.monitoring_enabled:
      logging.warning('Monitoring disabled -- dont push final metrics.')
      return

    with self.__mutex:
      self._do_flush_final_metrics()

  def flush_updated_metrics(self):
    """Push incremental metrics to the metrics server."""
    if not self.options.monitoring_enabled:
      logging.warning('Monitoring disabled -- dont push incremental metrics.')
      return

    with self.__mutex:
      self._do_flush_updated_metrics(self.__updated_metrics)
      self.__updated_metrics = set([])
