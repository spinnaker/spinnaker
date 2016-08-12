# pylint: disable=missing-docstring

import httplib2
import logging

import apiclient
from googleapiclient.errors import HttpError
from oauth2client.client import GoogleCredentials
from oauth2client.service_account import ServiceAccountCredentials


class StackdriverClient(object):
  """Helper class for interacting with Stackdriver."""
  WRITE_SCOPE = 'https://www.googleapis.com/auth/monitoring'
  CUSTOM_PREFIX = 'custom.googleapis.com/spinnaker/'

  @property
  def service(self):
    """Returns the stackdriver client stub."""
    return self.__stackdriver

  def __init__(self, service, options):
    self.logger = logging.getLogger(__name__)
    self.__stackdriver = service
    self.__project_name = 'projects/{0}'.format(options.project)
    self.__custom_descriptors = self.fetch_custom_descriptors(options.project)

  def name_to_type(self, name):
    """Determine stackdriver descriptor type name for the given metric name."""
    return self.CUSTOM_PREFIX + name

  def fetch_custom_descriptors(self, project):
    """Get all the custom spinnaker descriptors already known in Stackdriver."""
    project_name = 'projects/' + project if project else self.__project_name
    found = {}

    def partition(descriptor):
      descriptor_type = descriptor['type']
      if descriptor_type.startswith(self.CUSTOM_PREFIX):
        found[descriptor_type] = descriptor

    self.foreach_descriptor(partition, name=project_name)
    return found

  def foreach_descriptor(self, func, **args):
    """Apply a function to each metric descriptor known to Stackdriver."""
    request = self.__stackdriver.projects().metricDescriptors().list(**args)
    count = 0
    while request:
      self.logger.info('Fetching metricDescriptors')
      response = request.execute()
      for elem in response.get('metricDescriptors', []):
        count += 1
        func(elem)
        request = self.__stackdriver.projects().metricDescriptors().list_next(
            request, response)
    return count

  def hack_maybe_add_label(self, key, label_list):
    """Add label with |key| to |label_list| if not already present."""
    for tag in label_list:
      if tag['key'] == key:
        return
    label_list.append({'key': key, 'valueType': 'STRING'})

  def get_descriptor(self, name, record, kind_map, default_kind):
    """Return the stackdriver metric descriptor for the spectator metric."""
    custom_type = self.name_to_type(name)
    descriptor = self.__custom_descriptors.get(custom_type, None)
    if descriptor is not None:
      return descriptor

    label_list = [{'key': 'MicroserviceSrc', 'valueType': 'STRING'},
                  {'key': 'InstanceSrc', 'valueType': 'STRING'}],
    label_list.add_all([{'key': tag['key'], 'valueType': 'STRING'}
                       for tag in record['values'][0]['tags']])
    if name == 'controller.invocations':
      self.hack_maybe_add_label('account', label_list)

    custom = {
      'name': name,
      'type': custom_type,
      'labels': label_list,
      'metricKind':  kind_map.get(record['kind'], default_kind),
      'valueType': 'DOUBLE',
    }

    self.logger.info('Creating %s', name)
    try:
      descriptor = self.__stackdriver.projects().metricDescriptors().create(
          name=self.__project_name, body=custom).execute()
      self.logger.info('Added %s', name)
    except HttpError as err:
      self.logger.error('CAUGHT: %s', err)
      descriptor = None

    self.__custom_descriptors['type'] = descriptor
    return descriptor

  @staticmethod
  def make_client(options):
    credentials_path = options.credential_path
    http = httplib2.Http()
    http = apiclient.http.set_user_agent(
        http, 'SpinnakerStackdriverAgent/0.001')
    if credentials_path:
      credentials = ServiceAccountCredentials.from_json_keyfile_name(
            credentials_path, scopes=StackdriverClient.WRITE_SCOPE)
    else:
      credentials = GoogleCredentials.get_application_default()

    http = credentials.authorize(http)
    service = apiclient.discovery.build('monitoring', 'v3', http=http)
    return StackdriverClient(service, options)
