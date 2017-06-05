#!/usr/bin/python
#
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

# pylint: disable=import-error
# pylint: disable=star-args
"""
Implements program for deleting Google API resources by their name and/or age.

Example usage:
  # Delete all images in my project more than 30 days old.
  python delete_resources.py \
      compute.images \
      --project=myproject \
      --age=30 \
      --dry_run

  # Delete all the versions of all the objects in gs://mybucket
  python delete_resources.py \
      storage.objects \
      --delete_kwargs=bucket=mybucket \
      --list_kwargs=versions=true \
      --dry_run

  # Delete all the instances from this project that have 'xyz' in the name.
  # Use the list_aggregated method, which considers all zones.
  python delete_resources.py \
      compute.instances \
      --project= \
      --aggregated \
      --name=".*xyz.*" \
      --dry_run

  # Delete all the instances from this project in the 'us-central1-f' zone
  # that have 'xyz' in the name.
  python delete_resources.py \
      compute.instances \
      --project= \
      --delete_kwargs=zone=us-central1-f \
      --name=".*xyz.*" \
      --dry_run
"""


import argparse
import datetime
import httplib2
import re
import sys
import urllib2
import urlparse

import apiclient

from oauth2client.client import GoogleCredentials
from oauth2client.service_account import ServiceAccountCredentials


PLATFORM_FULL_SCOPE = 'https://www.googleapis.com/auth/cloud-platform'


def get_metadata(relative_url):
  """Return metadata value.

  Args:
    relative_url: [string] Metadata url to fetch relative to base metadata URL.
  """
  base_url = 'http://metadata/computeMetadata/v1/'
  url = urlparse.urljoin(base_url, relative_url)
  headers = {'Metadata-Flavor': 'Google'}
  return urllib2.urlopen(urllib2.Request(url, headers=headers)).read()


def make_service(api, version, credentials_path=None):
  """Create Google Service stub.

  Args:
    api: [string] Google API name
    version: [string] Google API version
    credentials_path: [string] Path to credentials file, or none for default.
  """
  if credentials_path:
    credentials = ServiceAccountCredentials.from_json_keyfile_name(
        credentials_path, scopes=PLATFORM_FULL_SCOPE)
  else:
    credentials = GoogleCredentials.get_application_default()
  http = credentials.authorize(httplib2.Http())
  return apiclient.discovery.build(api, version, http=http)


def determine_version(api):
  """Determine current version of Google API

  Args:
    api: [string] Google API name
  """
  discovery = make_service('discovery', 'v1')
  response = discovery.apis().list(name=api, preferred=True).execute()
  if not response.get('items'):
    raise ValueError('Unknown API "{0}".'.format(api))
  return response['items'][0]['version']


def determine_timestamp(item):
  """Determine the timestamp of the given item

  Args:
  item: [dict] Specifies an resource instance created by an API
  """
  # There is no standard for this.
  # The following are common to some APIs.
  for key in ['creationTimestamp', 'timeCreated']:
    if key in item:
      return item[key]
  raise ValueError('Could not determine timestamp key for {0}'
                   .format(item.get('kind', item)))


def __determine_resource_obj(service, resource):
  """Find the desired resource object method container from the service.

  Args:
    service: [stub] Google API stub object.
    resource: [string]  '.' delimited resource name in service API.
  """
  path = resource.split('.')
  node = service
  for elem in path:
    try:
      node = getattr(node, elem)()
    except AttributeError:
      raise AttributeError('"{0}" has no attribute "{1}"'.format(
          '.'.join(path[0:path.index(elem)]), elem))
  return node


def __filter_items(items, name_filter, before_str):
  """
  Args:
    items: [list of dict] List of item candidates.
    name_filter: [re] Regex for matching resource instance names.
    before_str: [string] Specifies newest time to consider (non-inclusive).
  """
  result = []
  for item in items:
    if not name_filter.match(item.get('name')):
      continue

    # Compare strings to keep this simple since there
    # isnt easy support to parse dates. This is only approximate
    # before we arent interpreting the time zone correctly. But it
    # is good enough.
    if before_str is None or determine_timestamp(item) < before_str:
      result.append(item)
  return result


def collect(resource_obj, list_kwargs, name_filter, before_str=None):
  """"Helper function that actually collects and filters the desired items.

  Args:
    resource_obj: [obj] The API container object for the resource.
    list_kwargs: [dict] Parameters for the API list method.
    name_filter: [re] Regex for matching resource instance names.
    before_str: [string] Specifies newest time to consider (non-inclusive).
  """
  result = []
  request = resource_obj.list(**list_kwargs)
  while request:
    response = request.execute()
    result.extend(__filter_items(response.get('items', []),
                                 name_filter, before_str))
    try:
      request = resource_obj.list_next(request, response)
    except AttributeError:
      request = None
  return result


def collect_aggregated(resource_obj, items_list_key,
                       list_kwargs, name_filter, before_str=None):
  """"Helper function that actually collects and filters the desired items.

  Args:
    resource_obj: [obj] The API container object for the resource.
    list_kwargs: [dict] Parameters for the API list method.
    name_filter: [re] Regex for matching resource instance names.
    before_str: [string] Specifies newest time to consider (non-inclusive).
  """
  result = []
  request = resource_obj.aggregatedList(**list_kwargs)
  while request:
    response = request.execute()
    items = response.get('items', [])
    for key, key_item in items.items():
      filtered_items = __filter_items(key_item.get(items_list_key, []),
                                      name_filter, before_str)
      result.extend([(key, value) for value in filtered_items])
    try:
      request = resource_obj.list_next(request, response)
    except AttributeError:
      request = None
  return result


def make_resource_object(resource_type, credentials_path):
  """Creates and configures the service object for operating on resources.

  Args:
    resource_type: [string] The Google API resource type to operate on.
    credentials_path: [string] Path to credentials file, or none for default.
  """
  try:
    api_name, resource = resource_type.split('.', 1)
  except ValueError:
    raise ValueError('resource_type "{0}" is not in form <api>.<resource>'
                     .format(resource_type))
  version = determine_version(api_name)
  service = make_service(api_name, version, credentials_path)

  path = resource.split('.')
  node = service
  for elem in path:
    try:
      node = getattr(node, elem)()
    except AttributeError:
      path_str = '.'.join(path[0:path.index(elem)])
      raise AttributeError('"{0}{1}" has no attribute "{2}"'.format(
          api_name, '.' + path_str if path_str else '', elem))
  return node


def get_options():
  """Process commandline arguments."""
  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--project', default=None,
      help='The project owning the resources to consider.'
      ' This should only be specified if the resource API requires a project'
      ' (e.g. compute.images, but not storage.objects).'
      ' An empty string means the local GCP project.'
      '\nThis is a shortcut for adding the project into --delete_kwargs.')

  parser.add_argument('resource', nargs=1,
                      help='The resource to delete.')
  parser.add_argument('--age', default=None, type=int,
                      help='The number of recent days to keep.'
                           ' The default is none.')
  parser.add_argument('--name', default='.*',
                      help='The regular expression for names to remove.'
                           ' The default is ".*" (everything)')
  parser.add_argument('--credentials', default=None,
                      help='Path to JSON credentials to use.'
                           ' The default is application default credentials')
  parser.add_argument('--aggregated', default=False, action='store_true',
                      help='Use aggregated_list() method.')
  parser.add_argument('--dry_run', default=False, action='store_true',
                      help='Show proposed delete, dont actually do them.')
  parser.add_argument(
      '--delete_kwargs', default=None,
      help='The extra arguments to pass to delete.'
           ' This is a comma-delimted list of name=value.'
           ' For more info, see https://developers.google.com/apis-explorer/#p/')
  parser.add_argument(
      '--list_kwargs', default=None,
      help='The extra arguments to pass to list,'
           ' beyond those in --delete_kwargs (which are added automatically).'
           ' This is a comma-delimted list of name=value.')

  return parser.parse_args()


def delete(resource_obj, resource_instance, params, dry_run):
  """Delete the resource_instance."""
  decorator = '[dry run] ' if dry_run else ''
  print '{decorator}DELETE {name} [{time}] FROM {params}'.format(
      decorator=decorator, name=resource_instance.get('name'),
      time=determine_timestamp(resource_instance), params=params)
  if dry_run:
    return

  resource_obj.delete(**params).execute()


def __kwargs_option_to_dict(raw_value):
  """Convert comma-delimted kwargs argument into a normalized dictionary.

  Args:
    raw_value: [string] Comma-delimited key=value bindings.
  """
  kwargs = {}
  if raw_value:
    for binding in raw_value.split(','):
      name, value = binding.split('=')
      if value.lower() == 'true':
        value = True
      elif value.lower() == 'false':
        value = False
      elif value.isdigit():
        value = int(value)
      kwargs[name] = value
  return kwargs


def __determine_delete_kwargs(options):
  """Determine the standard keyword arguments to pass to delete() method."""
  delete_kwargs = __kwargs_option_to_dict(options.delete_kwargs)
  project = delete_kwargs.get('project', options.project)
  if project == '':
    try:
      project = get_metadata('project/project-id')
    except:
      raise ValueError('project is required when not running on GCP')

  delete_kwargs['project'] = project
  return delete_kwargs


def __determine_list_kwargs(options):
  """Determine the standard keyword arguments to pass to list() method."""
  list_kwargs = __determine_delete_kwargs(options)
  list_kwargs.update(__kwargs_option_to_dict(options.list_kwargs))
  return list_kwargs


def __determine_before_str(options):
  """Determine the date string for the newest timestamp to filter by."""
  now = datetime.datetime.utcnow()
  today = datetime.datetime(now.year, now.month, now.day)
  day_offset = options.age
  before_str = ((today - datetime.timedelta(day_offset)).isoformat()
                if day_offset is not None
                else None)
  return before_str


def main():
  """The main program."""
  options = get_options()
  options.resource = options.resource[0]  # Treat as singluar string for now
  before_str = __determine_before_str(options)

  resource_basename = options.resource[options.resource.rfind('.') + 1:]
  resource_id_key = (resource_basename[:-1]
                     if resource_basename[-1] == 's'
                     else resource_basename)

  delete_kwargs = __determine_delete_kwargs(options)
  list_kwargs = __determine_list_kwargs(options)

  resource_obj = make_resource_object(options.resource, options.credentials)
  num_errors = 0
  if options.aggregated:
    elems = collect_aggregated(
        resource_obj, resource_basename,
        list_kwargs, name_filter=re.compile(options.name),
        before_str=before_str)

    for keyvalue, resource_instance in elems:
      key, value = keyvalue.split('/')
      delete_kwargs[key[:-1]] = value
      delete_kwargs[resource_id_key] = resource_instance['name']
      try:
        delete(resource_obj, resource_instance, delete_kwargs, options.dry_run)
      except IOError as error:
        sys.stderr.write(str(error) + '\n')
        num_errors += 1

  else:
    elems = collect(
        resource_obj, list_kwargs, name_filter=re.compile(options.name),
        before_str=before_str)
    for resource_instance in elems:
      delete_kwargs[resource_id_key] = resource_instance['name']
      try:
        delete(resource_obj, resource_instance, delete_kwargs, options.dry_run)
      except IOError as error:
        sys.stderr.write(str(error) + '\n')
        num_errors += 1

  return 0 if num_errors == 0 else -1


if __name__ == '__main__':
  # pylint: disable=broad-except
  try:
    exit(main())
  except Exception as error:
    sys.stderr.write(error.message + '\n')
    exit(-1)
