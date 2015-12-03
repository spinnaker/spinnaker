#!/usr/bin/python
#
# Copyright 2015 Google Inc. All Rights Reserved.
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

import collections
import os
import socket
import sys
import urllib2

from run import check_run_quick

GOOGLE_METADATA_URL = 'http://metadata.google.internal/computeMetadata/v1'
GOOGLE_INSTANCE_METADATA_URL = GOOGLE_METADATA_URL + '/instance'
GOOGLE_OAUTH_URL = 'https://www.googleapis.com/auth'
AWS_METADATA_URL = 'http://169.254.169.254/latest/meta-data/'


class FetchResult(collections.namedtuple(
        'FetchResult', ['httpcode', 'content'])):
  """Captures the result of fetching a url.

  Attributes:
    httpcode [int]: The HTTP code returned or -1 if the request raised
        an exception.
    content [string or error]: The HTTP payload result, or the error raised.
  """
  def ok(self):
      return self.httpcode >= 200 and self.httpcode < 300


def fetch(url, google=False, timeout=None):
    request = urllib2.Request(url)
    if google:
      request.add_header('Metadata-Flavor', 'Google')
    try:
      response = urllib2.urlopen(request, timeout=timeout)
      return FetchResult(response.getcode(), response.read())

    except urllib2.HTTPError as e:
      return FetchResult(-1, e)

    except urllib2.URLError as e:
      return FetchResult(-1, e)


def check_fetch(url, google=False, timeout=None):
    response = fetch(url, google=google, timeout=timeout)
    if not response.ok():
        sys.stderr.write('{code}: {url}\n{result}\n'.format(
            code=response.httpcode, url=url, result=response.content))
        raise SystemExit('FAILED')
    return response


__IS_ON_GOOGLE = None
__IS_ON_AWS = None
__ZONE = None


def is_google_instance():
  """Determine if we are running on a Google Cloud Platform instance."""
  global __IS_ON_GOOGLE
  if __IS_ON_GOOGLE is None:
    __IS_ON_GOOGLE = fetch(GOOGLE_METADATA_URL, google=True).ok()
  return __IS_ON_GOOGLE


def is_aws_instance():
  """Determine if we are running on an Amazon Web Services instance."""
  global __IS_ON_AWS
  if __IS_ON_AWS == None:
    __IS_ON_AWS = fetch(AWS_METADATA_URL, timeout=1).ok()
  return __IS_ON_AWS


def check_write_instance_metadata(name, value):
  """Add a name/value pair to our instance metadata.

  Args:
    name [string]: The key name.
    value [string]: The key value.

  Raises
    NotImplementedError if not on a platform with metadata.
  """
  if is_google_instance():
    check_run_quick(
        'gcloud compute instances add-metadata'
        ' {hostname} --zone={zone} --metadata={name}={value}'
        .format(hostname=socket.gethostname(),
                zone=check_get_zone(), name=name, value=value))

  elif is_aws_instance():
    result = check_fetch(os.path.join(AWS_METADATA_URL, 'instance-id'))
    id = result.content.strip()

    result = check_fetch(os.path.join(AWS_METADATA_URL,
                                      'placement/availability-zone'))
    region = result.content.strip()[:-1]

    command = ['aws ec2 create-tags --resources', id,
               '--region', region,
               '--tags Key={key},Value={value}'.format(key=name, value=value)]
    check_run_quick(' '.join(command), echo=False)

  else:
    raise NotImplementedError('This platform does not support metadata.')


def get_google_project():
  """Return the Google project this is running in, or None."""
  result = fetch(GOOGLE_METADATA_URL + '/project/project-id', google=True)
  return result.content if result.ok() else None


def check_get_zone():
  global __ZONE
  if __ZONE is None:
    if is_google_instance():
      result = check_fetch(GOOGLE_INSTANCE_METADATA_URL + '/zone', google=True)
      __ZONE = os.path.basename(result.content)
    elif is_aws_instance():
      result = check_fetch(AWS_METADATA_URL + '/placement/availability-zone')
      __ZONE = result.content
    else:
      raise NotImplementedError('This platform does not support zones.')
  return __ZONE
