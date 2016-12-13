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

from datetime import datetime
import httplib2
import json
import logging
import os
import re
import traceback
import urllib2

import apiclient
from googleapiclient.errors import HttpError
from oauth2client.client import GoogleCredentials
from oauth2client.service_account import ServiceAccountCredentials

import spectator_client
# This doesnt belong here, but this library insists on logging
# ImportError: file_cache is unavailable when using oauth2client >= 4.0.0
# The maintainer wont fix or remove the warning so we'll force it to be disabled
logging.getLogger('googleapiclient.discovery_cache').setLevel(logging.ERROR)


def get_aws_identity_document():
  url = 'http://169.254.169.254/latest/dynamic/instance-identity/document'
  request = urllib2.Request(url)
  try:
    response = urllib2.urlopen(request)
  except IOError as ioex:
    logging.info('Cannot read AWS Identity Document,'
                 ' probably not on Amazon Web Services.'
                 ' url=%s: %s', url, ioex)
    raise ioex
  return json.JSONDecoder().decode(response.read())


def get_google_metadata(attribute):
  url = os.path.join('http://169.254.169.254/computeMetadata/v1', attribute)
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


class StackdriverMetricsService(object):
  """Helper class for interacting with Stackdriver."""
  WRITE_SCOPE = 'https://www.googleapis.com/auth/monitoring'
  CUSTOM_PREFIX = 'custom.googleapis.com/spinnaker/'
  MAX_BATCH = 200

  @staticmethod
  def millis_to_time(millis):
    return datetime.fromtimestamp(millis / 1000).isoformat('T') + 'Z'

  @property
  def stub(self):
    """Returns the stackdriver client stub."""
    if self.__stub is None:
      self.__stub = self.__stub_factory()
    return self.__stub

  @property
  def monitored_resource(self):
    if self.__monitored_resource is not None:
      return self.__monitored_resource

    if self.__monitored_resource is None:
      self.__monitored_resource = self.__google_monitored_resource_or_none()

    if self.__monitored_resource is None:
      self.__monitored_resource = self.__ec2_monitored_resource_or_none()

    if self.__monitored_resource is None:
      self.__add_source_tag = True
      self.__monitored_resource = {
        'type': 'global',
        'project_id': self.__project
      }

    logging.info('Monitoring {0}'.format(self.__monitored_resource))
    return self.__monitored_resource

  def __ec2_monitored_resource_or_none(self):
    """If deployed on EC2, return the monitored resource, else None."""
    try:
      doc = get_aws_identity_document()

      return {
        'instance_id': doc['instanceId'],
        'region': doc['region'],
        'aws_account': doc['accountId'],
        'project_id': self.__project
       }
    except (IOError, ValueError, KeyError):
      return None

  def __google_monitored_resource_or_none(self):
    """If deployed on GCE, return the monitored resource, else None."""
    project = self.__project
    zone = self.__options.get('zone', None)
    instance_id = self.__options.get('instance_id', None)

    try:
      if not project:
        project = get_google_metadata('project/project-id')
      if not zone:
        zone = os.path.basename(get_google_metadata('instance/zone'))
      if not instance_id:
        instance_id = int(get_google_metadata('instance/id'))

      return {
        'type': 'gce_instance',
        'labels': {
            'zone': zone,
            'instance_id': str(instance_id)
        }
      }
    except IOError:
      return None

  def __init__(self, stub_factory, options):
    """Constructor.

    Args:
      stub_factory: [callable that creates stub for stackdriver]
          This is passed as a callable to defer initialization because
          we create the handlers before we process commandline args.
    """
    self.logger = logging.getLogger(__name__)
    self.__options = options

    self.__stub_factory = stub_factory
    self.__stub = None
    self.__project = options.get('project', None)
    if not self.__project:
      # Set default to our instance if we are on GCE.
      # Otherwise ignore since we might not actually need the project.
      try:
        self.__project = get_google_metadata('project/project-id')
      except IOError:
        pass

    self.__fix_stackdriver_labels_unsafe = options.get(
        'fix_stackdriver_labels_unsafe', False)
    self.__monitored_resource = None
    self.__add_source_tag = False

  @staticmethod
  def add_parser_arguments(parser):
    """Add arguments for coniguring stackdriver."""
    parser.add_argument('--project', default='')
    parser.add_argument('--zone', default='us-central1-f')
    parser.add_argument('--instance_id', default=0, type=int)
    parser.add_argument('--credentials_path', default='')

  def project_to_resource(self, project):
    if not project:
      raise ValueError('No project specified')
    return 'projects/' + project

  def metric_type(self, service, name):
    """Determine the basic metric name."""
    return self.name_to_type('{0}/{1}'.format(service, name))

  def name_to_type(self, name):
    """Determine Custom Descriptor type name for the given metric type name."""
    return self.CUSTOM_PREFIX + name

  def fetch_all_custom_descriptors(self, project):
    """Get all the custom spinnaker descriptors already known in Stackdriver."""
    project_name = 'projects/' + (project or self.__project)
    found = {}

    def partition(descriptor):
      descriptor_type = descriptor['type']
      if descriptor_type.startswith(self.CUSTOM_PREFIX):
        found[descriptor_type] = descriptor

    self.foreach_custom_descriptor(partition, name=project_name)
    return found

  def foreach_custom_descriptor(self, func, **args):
    """Apply a function to each metric descriptor known to Stackdriver."""
    request = self.stub.projects().metricDescriptors().list(**args)

    count = 0
    while request:
      self.logger.info('Fetching metricDescriptors')
      response = request.execute()
      for elem in response.get('metricDescriptors', []):
        count += 1
        func(elem)
      request = self.stub.projects().metricDescriptors().list_next(
          request, response)
    return count

  def publish_metrics(self, service_metrics):
    time_series = []
    spectator_client.foreach_metric_in_service_map(
        service_metrics, self.add_metric_to_timeseries, time_series)
    offset = 0
    method = self.stub.projects().timeSeries().create

    while offset < len(time_series):
      last = min(offset + self.MAX_BATCH, len(time_series))
      chunk = time_series[offset:last]
      try:
        (method(name=self.project_to_resource(self.__project),
                body={'timeSeries': chunk})
         .execute())
      except HttpError as err:
        self.handle_time_series_http_error(err, chunk)
      offset = last
    return len(time_series)

  def find_problematic_elements(self, error, batch):
    try:
      content = json.JSONDecoder().decode(error.content)
      message = content['error']['message']
    except KeyError:
      return []

    pattern = (r'timeSeries\[(\d+?)\]\.metric\.labels\[\d+?\]'
               r' had an invalid value of "(\w+?)"')
    found = []
    for match in re.finditer(pattern, message):
      ts_index = int(match.group(1))
      label = match.group(2)
      metric = batch[ts_index]['metric']
      metric_type = metric['type']
      found.append((self.add_label_and_retry,
                    label, metric_type, batch[ts_index]))
    return found

  def add_label_and_retry(self, label, metric_type, ts_request):
    if self.add_label_to_metric(label, metric_type):
      # Try again to write time series data.
      logging.info('Retrying create timeseries {0}'.format(ts_request))
      (self.stub.projects().timeSeries().create(
          name=self.project_to_resource(self.__project),
               body={'timeSeries': ts_request})
       .execute())

  def add_label_to_metric(self, label, metric_type):
    metric_name_param = os.path.join(
        self.project_to_resource(self.__project),
        'metricDescriptors', metric_type)
    logging.info('Attempting to add label "%s" to %s', label, metric_type)
    api = self.stub.projects().metricDescriptors()

    try:
      descriptor = api.get(name=metric_name_param).execute()
    except HttpError as err:
      # Maybe another process is deleting it
      logging.error('Could not get descriptor: %s', err)
      return False

    labels = descriptor.get('labels', [])
    if [elem for elem in labels if elem['key'] == label]:
      logging.info('Label was already added: %s', descriptor)
      return True

    logging.info('Starting with metricDescriptors.get %s:', descriptor)
    labels.append({'key': label, 'valueType': 'STRING'})
    descriptor['labels'] = labels
    try:
      logging.info('Deleting existing descriptor %s', metric_name_param)
      response = api.delete(name=metric_name_param).execute()
      logging.info('Delete response: %s', repr(response))
    except HttpError as err:
      logging.error('Could not delete descriptor %s', err)
      if err.resp.status != 404:
        return False
      else:
        logging.info("Ignore error.")

    logging.info('Updating descriptor as %s', descriptor)
    try:
      response = api.create(
          name=self.project_to_resource(self.__project),
          body=descriptor).execute()
      logging.info('Response from create: %s', response)

      response = api.get(name=metric_name_param).execute()
      logging.info('Now metricDescriptors.get returns %s:', response)
      return True
    except HttpError as err:
      logging.error('Failed: %s', err)
      return False

  def handle_time_series_http_error(self, error, batch):
    logging.error('Caught {0}'.format(error))
    if error.resp.status == 400:
      problems = self.find_problematic_elements(error, batch)
      logging.info('PROBLEMS {0!r}'.format(problems))
      if problems and not self.__fix_stackdriver_labels_unsafe:
        logging.info(
            'Fixing this problem would wipe stackdriver data.'
            ' Doing so was not enabled with --fix_stackdriver_lebals_unsafe')
      elif problems:
        logging.info('Attempting to fix these problems. This may lose'
                     ' stackdriver data for these metrics. To disable this,'
                     ' invoke with --nofix_stackdriver_labels_unsafe.')
        for elem in problems:
          try:
            elem[0](*elem[1:])
          except BaseException as bex:
            traceback.print_exc()
            logging.error('Failed {0}({1}): {2}'.format(
                elem[0], elem[1:], bex))

  def add_metric_to_timeseries(self, service, name, instance,
                               metric_metadata, service_metadata, result):
    name, tags = spectator_client.normalize_name_and_tags(
        name, instance, metric_metadata)
    if tags is None:
      return

    metric = {
      'type': self.metric_type(service, name),
      'labels': {tag['key']: tag['value'] for tag in tags}
    }
    if self.__add_source_tag:
      metric['labels']['InstanceSrc'] = '{host}:{port}'.format(
          host=service_metadata['__host'], port=service_metadata['__port'])

    points = [{'interval': {'endTime': self.millis_to_time(e['t'])},
               'value': {'doubleValue': e['v']}}
              for e in instance['values']]

    if metric_metadata['kind'] in ['Counter', 'Timer']:
      # TODO(ewiseblatt): 20161115
      # startTime is in a newer spectator version that is not yet
      # available all over.
      start_time = self.millis_to_time(service_metadata.get('startTime', 0))
      metric_kind = 'CUMULATIVE'
      for elem in points:
        elem['interval']['startTime'] = start_time
    else:
      metric_kind = 'GAUGE'

    result.append({
        'metric': metric,
        'resource': self.monitored_resource,
        'metricKind': metric_kind,
        'valueType': 'DOUBLE',
        'points': points
        })

def make_service(options):
  """Factory method for creating a new StackdriverMetricsService."""
  def make_stub():
    """Helper function for making a stub to talk to service."""
    credentials_path = options.get('credentials_path', None)
    http = httplib2.Http()
    http = apiclient.http.set_user_agent(
        http, 'SpinnakerStackdriverAgent/0.001')
    if credentials_path:
      logging.info('Using Stackdriver Credentials from "%s"', credentials_path)
      credentials = ServiceAccountCredentials.from_json_keyfile_name(
            credentials_path, scopes=StackdriverMetricsService.WRITE_SCOPE)
    else:
      logging.info('Using Stackdriver Credentials from application default.')
      credentials = GoogleCredentials.get_application_default()

    http = credentials.authorize(http)
    return apiclient.discovery.build('monitoring', 'v3', http=http)

  return StackdriverMetricsService(make_stub, options)

