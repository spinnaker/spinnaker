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

"""Derives a spinnaker subsystem spring configuration."""

import os
import re
import sys
import urllib2
import yaml
from json import JSONDecoder

from .expression_dict import ExpressionDict


def infer(json):
  applicationConfig = {}
  for name,value in json.items():
      match = re.match('applicationConfig: \[(.+)\](.*)', name)
      if not match:
        continue
      key = match.group(1)
      decorator = match.group(2) or ''
      applicationConfig[key + decorator] = value

  expr_dict = ExpressionDict()
  expr_dict.update(json.get('defaultProperties', {}))
  expr_dict.update(json.get('systemProperties', {}))
  expr_dict.update(json.get('systemEnvironment', {}))

  names = expr_dict['spring.config.name'].split(',')
  profiles = expr_dict['spring.profiles.active'].split(',')
  locations = expr_dict['spring.config.location'].split(',')

  for location in locations:
      location_names = names if location.endswith('/') else ['']
      for name in location_names:
          root_filename = 'file:{location}{name}'.format(
              location=location, name=name)
          expr_dict.update(
              applicationConfig.get(root_filename + '.yml', {}))
          for profile in profiles:
              key = root_filename + '-' + profile + '.yml'
              expr_dict.update(applicationConfig.get(key, {}))

  return expr_dict


def scrape_spring_config(url):
    """Construct a config binding dictionary from a running instance's baseUrl.

    Args:
      url: The url to construct from.

    Raises:
      urlib2.URLError if url is bad.
    """
    request = urllib2.Request(url=url)
    response = urllib2.urlopen(request)
    http_code = response.getcode()
    content = response.read()
    if http_code < 200 or http_code >= 300:
        raise ValueError('Invalid HTTP={code} from {url}:\n{msg}'.format(
            code=http_code, url=url, msg=content))
    json = JSONDecoder().decode(content)
    return infer(json)
