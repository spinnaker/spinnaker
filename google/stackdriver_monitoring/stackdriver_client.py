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
import logging

import apiclient
from googleapiclient.errors import HttpError
from oauth2client.client import GoogleCredentials
from oauth2client.service_account import ServiceAccountCredentials


# This doesnt belong here, but this library insists on logging
# ImportError: file_cache is unavailable when using oauth2client >= 4.0.0
# The maintainer wont fix or remove the warning so we'll force it to be disabled
logging.getLogger('googleapiclient.discovery_cache').setLevel(logging.ERROR)


class StackdriverClient(object):
  """Helper class for interacting with Stackdriver."""
  WRITE_SCOPE = 'https://www.googleapis.com/auth/monitoring'
  CUSTOM_PREFIX = 'custom.googleapis.com/spinnaker/'

  @staticmethod
  def millis_to_time(millis):
    return datetime.fromtimestamp(millis / 1000).isoformat('T') + 'Z'

  @property
  def service(self):
    """Returns the stackdriver client stub."""
    return self.__stackdriver

  def __init__(self, service, options):
    self.logger = logging.getLogger(__name__)
    self.__stackdriver = service
    self.__project = options.project
    self.__project_name = 'projects/{0}'.format(options.project)
    self.__cache = {}

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

  @staticmethod
  def make_client(options):
    credentials_path = options.credentials_path
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
