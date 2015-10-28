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

import os
import re
import yaml


class YamlBindings(object):
  """Implements a map from yaml using variable references similar to spring."""

  @property
  def map(self):
    return self.__map

  def __init__(self):
    self.__map = {}

  def get(self, field):
    return self.__get_value(field, [], original=field)

  def import_dict(self, d):
    for name,value in d.items():
      self.__update_field(name, value, self.__map)

  def import_string(self, s):
    self.import_dict(yaml.load(s, Loader=yaml.Loader))

  def import_path(self, path):
    with open(path, 'r') as f:
      self.import_dict(yaml.load(f, Loader=yaml.Loader))

  def __update_field(self, name, value, container):
    if not isinstance(value, dict) or not name in container:
      container[name] = value
      return

    container_value = container[name]
    if not isinstance(container_value, dict):
      container[name] = value
      return

    for child_name, child_value in value.items():
      self.__update_field(child_name, child_value, container_value)

  def __get_node(self, field):
    path = field.split('.')
    node = self.__map
    for part in path:
      if not isinstance(node, dict) or not part in node:
        raise KeyError(field)
      if isinstance(node, list):
        node = node[0][part]
      else:
        node = node[part]
    return node

  def __get_value(self, field, saw, original):
    value = self.__get_node(field)
    if not isinstance(value, basestring) or not value.startswith('$'):
      return value

    if field in saw:
      raise ValueError('Cycle looking up variable ' + original)
    saw = saw + [field]

    result = []
    offset = 0

    # Look for fragments of ${key} or ${key:default} then resolve them.
    text = value
    for match in re.finditer('\${([\._a-zA-Z0-9]+)(:.+?)?}', text):
        result.append(text[offset:match.start()])
        try:
          got = self.__get_value(match.group(1), saw, original)
          result.append(str(got))
        except KeyError:
          if match.group(2):
            result.append(str(match.group(2)[1:]))
          else:
            result.append(match.group(0))
        offset = match.end()  # skip trailing '}'
    result.append(text[offset:])

    return ''.join(result)

  def replace(self, text):
    result = []
    offset = 0

    # Look for fragments of ${key} or ${key:default} then resolve them.
    for match in re.finditer('\${([\._a-zA-Z0-9]+)(:.+?)?}', text):
        result.append(text[offset:match.start()])
        try:
          result.append(self.get(match.group(1)))
        except KeyError:
          if match.group(2):
            result.append(str(match.group(2)[1:]))
          else:
            raise
        offset = match.end()  # skip trailing '}'
    result.append(text[offset:])

    return ''.join(result)

def load_bindings(installed_config_dir, user_config_dir, only_if_local=False):
    local_yml_path = os.path.join(user_config_dir, 'spinnaker-local.yml')
    have_local = os.path.exists(local_yml_path)
    if only_if_local and not have_local:
      return None

    bindings = YamlBindings()
    bindings.import_path(os.path.join(installed_config_dir, 'spinnaker.yml'))
    if have_local:
      bindings.import_path(local_yml_path)
    return bindings
