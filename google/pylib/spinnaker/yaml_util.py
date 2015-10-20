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

  def import_string(self, str):
    self.import_dict(yaml.load(str, Loader=yaml.Loader))

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

    result = ''
    offset = 0
    if field in saw:
      raise ValueError('Cycle looking up variable ' + original)
    saw.append(field)

    re_variable = re.compile('\${([a-zA-Z_][a-zA-Z0-0_\.]+)(:.+)?}')
    for match in re_variable.finditer(value) or []:
        result += value[offset:match.pos]
        offset = match.endpos
        match_value = match.group(1)
        try:
          result += self.__get_value(match_value, saw, original)
        except KeyError:
          def_value = match.group(2)
          if def_value:
            result += def_value[1:]  # stream leading ':'
          else:
            result += value
    result += value[offset:]
    return result
