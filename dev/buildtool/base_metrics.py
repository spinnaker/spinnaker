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
  """A metric with unique combination of name and bindings."""

  @property
  def family(self):
    """The metric family this instance belongs to.

    Members of a family share the same name but different label bindings.
    """
    return self.__family

  @property
  def name(self):
    return self.__family.name

  @property
  def labels(self):
    return self.__labels

  @property
  def last_modified(self):
    """In real seconds."""
    return self.__last_modified

  @property
  def mutex(self):
    return self.__mutex

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

  @property
  def count(self):
    """Returns the current [local] counter value."""
    return self.__count

  def __init__(self, family, labels):
    super(Counter, self).__init__(family, labels)
    self.__count = 0

  def inc(self, amount=1, utc=None):
    with self.mutex:
      self.__count += amount
      self.touch(utc=utc)


class Gauge(Metric):
  @property
  def value(self):
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
    """Set the gauge to an absolute value."""
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
    """The name for this family will be the name of its Metric instances."""
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
    """Return all the label binding metric variations within this family."""
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


class BaseMetricsRegistry(object):
  """Provides base class interface for metrics management.

  Specific metric stores would subclass this to specialize to push
  into their own systems.

  While having this registry be abstract is overkill, it is for what feels
  like practical reasons where there is no easy to use system for our use
  case of short lived batch jobs so there's going to be a lot of maintainence
  here and trials of different systems making this investment more appealing.
  """
  # pylint: disable=too-many-public-methods

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
    self.__pusher_thread = None
    self.__pusher_thread_event = threading.Event()
    self.__metric_families = {}
    self.__family_mutex = threading.Lock()
    self.__updated_metrics = set([])
    self.__update_mutex = threading.Lock()

  def _do_make_family(
      self, family_type, name, description, label_names):
    """Creates new metric-system specific gauge family.

    Args:
      family_type: MetricFamily.COUNTER, GUAGE, or TIMER
      name: [string] Metric name.
      description: [string] Metric help description.
      label_names: [list of string] The labels used to distinguish instances.

    Returns:
      specialized MetricFamily for the given type and registry implementation.
    """
    raise NotImplementedError()

  def queue_update(self, metric):
    """Add metric to list of metrics to push out."""
    with self.__update_mutex:
      self.__updated_metrics.add(metric)

  def inc_counter(self, name, labels, description, **kwargs):
    """Track number of completed calls to the given function."""
    family = self.__ensure_family(
        MetricFamily.COUNTER, name, labels, description)
    counter = family.get(labels)
    counter.inc(**kwargs)
    return counter

  def count_call(self, name, labels, description,
                 func, *pos_args, **kwargs):
    """Track number of completed calls to the given function."""
    labels = dict(labels)
    success = False
    try:
      result = func(*pos_args, **kwargs)
      success = True
      return result
    finally:
      labels['success'] = success
      self.inc_counter(name, labels, description, **kwargs)

  def set(self, name, labels, description, value):
    """Sets the implied gauge with the specified value."""
    family = self.__ensure_family(
        MetricFamily.GAUGE, name, labels, description)
    gauge = family.get(labels)
    gauge.set(value)
    return gauge

  def track_call(self, name, labels, description, func, *pos_args, **kwargs):
    """Track number of active calls to the given function."""
    family = self.__ensure_family(
        MetricFamily.GAUGE, name, labels, description)
    gauge = family.get(labels)
    return gauge.track(func, *pos_args, **kwargs)

  def observe_timer(self, name, labels, description, seconds):
    """Add an observation to the specified timer."""
    family = self.__ensure_family(
        MetricFamily.TIMER, name, labels, description)
    timer = family.get(labels)
    timer.observe(seconds)
    return timer

  def time_call(self, name, labels, description,
                func, *pos_args, **kwargs):
    """Track number of completed calls to the given function."""
    family = self.__ensure_family(
        MetricFamily.TIMER, name, labels, description)
    timer = family.get(labels)

    try:
      start_time = time.time()
      return func(*pos_args, **kwargs)
    finally:
      timer.observe(time.time() - start_time)

  def lookup_family_or_none(self, name):
    return self.__metric_families.get(name)

  def __ensure_family(
      self, family_type, name, labels, description, *pos_args):
    """Find family with given name if it exists already, otherwise make one."""
    family = self.__metric_families.get(name)
    if family:
      if family.family_type != family_type:
        raise TypeError('{have} is not a {want}'.format(
            have=family, want=family_type))
      return family

    family = self._do_make_family(
        family_type, name, description, labels.keys(), *pos_args)
    with self.__family_mutex:
      if name not in self.__metric_families:
        self.__metric_families[name] = family
    return family

  def instrument_track_and_outcome(
      self, name, description, track_labels, outcome_labels_func,
      result_func, *pos_args, **kwargs):
    """Call the function with the given arguments while instrumenting it.

    This will instrument both tracking of call counts in progress
    as well as the final outcomes in terms of performance and outcome.
    """
    start = time.time()
    try:
      tracking_name = name + '_InProgress'
      result = self.track_call(tracking_name, track_labels, description,
                               result_func, *pos_args, **kwargs)
      outcome_labels = outcome_labels_func()
      return result
    except:
      outcome_labels = outcome_labels_func()
      raise
    finally:
      seconds = max(0, time.time() - start)
      outcome_name = name + '_Outcome'
      self.observe_timer(outcome_name, outcome_labels, description, seconds)

  def start_pusher_thread(self):
    """Starts thread for pushing metrics."""
    def delay_func():
      """Helper function for push thread"""
      # pylint: disable=broad-except
      try:
        if self.__pusher_thread:
          self.__pusher_thread_event.wait(
              self.options.monitoring_flush_frequency)
          return self.__pusher_thread is not None
      except Exception as ex:
        logging.error('Pusher thread delay func caught %s', ex)
        return False

    self.__pusher_thread = threading.Thread(
        name='MetricsManager', target=self.flush_every_loop, args=[delay_func])
    self.__pusher_thread.start()

  def stop_pusher_thread(self):
    """Stop thread for pushing metrics."""
    logging.debug('Signaling pusher thread %s', self.__pusher_thread)
    pusher_thread = self.__pusher_thread
    self.__pusher_thread = None
    self.__pusher_thread_event.set()

    # Give a chance for the thread to self-terminate before we continue.
    # It's ok if this times out, but logging is cleaner to give it a chance.
    pusher_thread.join(2)

  def flush_every_loop(self, ready_func):
    """Start a loop that pushes while the ready_func is true."""
    logging.debug('Starting loop to push metrics...')
    while ready_func():
      self.flush_updated_metrics()
    logging.debug('Ending loop to push metrics...')

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

    self._do_flush_final_metrics()

  def flush_updated_metrics(self):
    """Push incremental metrics to the metrics server."""
    if not self.options.monitoring_enabled:
      logging.warning('Monitoring disabled -- dont push incremental metrics.')
      return

    with self.__update_mutex:
      updated_metrics = self.__updated_metrics
      self.__updated_metrics = set([])
    self._do_flush_updated_metrics(updated_metrics)
