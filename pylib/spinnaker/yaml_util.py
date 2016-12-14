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

def yml_or_yaml_path(basedir, basename):
  """Return a path to the requested YAML file.

  Args:
    basedir [string]: The directory path containing the file.
    basename [string]: The base filename for the file

  Returns:
    Path to the YAML file with either a .yml or .yaml extension,
    depending on which if any exists.
    If neither exists, return the .yml. If both, then raise an exception.
  """
  basepath = os.path.join(basedir, basename)
  yml_path = basepath + ".yml"
  yaml_path = basepath + ".yaml"
  if os.path.exists(yaml_path):
    if os.path.exists(yml_path):
      raise ValueError('Both {0} and {1} exist.'.format(yml_path, yaml_path))
    return yaml_path
  return yml_path


class YamlBindings(object):
  """Implements a map from yaml using variable references similar to spring."""

  @property
  def map(self):
    return self.__map

  def __init__(self):
    self.__map = {}

  def __getitem__(self, field):
    return self.__get_field_value(field, [], original=field)

  def get(self, field, default=None):
    try:
      return self.__get_field_value(field, [], original=field)
    except KeyError:
      return default

  def import_dict(self, d):
    if dict is not None:
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

  def __typed_value(self, value_text):
    """Convert the text of a value into the YAML value.

    This is used for type conversion for default values.
    Not particularly efficient, but there doesnt seem to be a direct API.
    """
    return yaml.load('x: {0}'.format(value_text), Loader=yaml.Loader)['x']

  def __get_field_value(self, field, saw, original):
    value = os.environ.get(field, None)
    if value is None:
      value = self.__get_node(field)
    else:
      value = self.__typed_value(value)
    if not isinstance(value, basestring) or value.find('$') < 0:
      return value

    if field in saw:
      raise ValueError('Cycle looking up variable ' + original)
    saw = saw + [field]

    return self.__resolve_value(value, saw, original)

  def __resolve_value(self, value, saw, original):
    expression_re = re.compile('\${([\._a-zA-Z0-9]+)(:.+?)?}')
    exact_match = expression_re.match(value)

    if exact_match and exact_match.group(0) == value:
      try:
        got = self.__get_field_value(exact_match.group(1), saw, original)
        return got
      except KeyError:
        if exact_match.group(2):
          return self.__typed_value(exact_match.group(2)[1:])
        else:
          return value

    result = []
    offset = 0

    # Look for fragments of ${key} or ${key:default} then resolve them.
    text = value
    for match in expression_re.finditer(text):
        result.append(text[offset:match.start()])
        try:
          got = self.__get_field_value(str(match.group(1)), saw, original)
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
    return self.__resolve_value(text, [], text)


  def __get_flat_keys(self, container):
    flat_keys = []
    for key,value in container.items():
      if isinstance(value, dict):
        flat_keys.extend([key + '.' + subkey for subkey in self.__get_flat_keys(value)])
      else:
        flat_keys.append(key)
    return flat_keys


  @staticmethod
  def update_yml_source(path, update_dict):
    """Update the yaml source at the path according to the update dict.

    All the previous bindings not in the update dict remain unchanged.
    The yaml file at the path is re-written with the new bindings.

    Args:
      path [string]: Path to a yaml source file.
      update_dict [dict]: Nested dictionary corresponding to
          nested yaml properties, keyed by strings.
    """
    bindings = YamlBindings()
    bindings.import_dict(update_dict)
    updated_keys = bindings.__get_flat_keys(bindings.__map)
    source = '' # declare so this is in scope for both 'with' blocks
    with open(path, 'r') as source_file:
      source = source_file.read()
      for prop in updated_keys:
        source = bindings.transform_yaml_source(source, prop)

    with open(path, 'w') as source_file:
      source_file.write(source)


  def transform_yaml_source(self, source, key):
    """Transform the given yaml source so its value of key matches the binding.

    Has no effect if key is not among the bindings.
    But raises a KeyError if it is in the bindings but not in the source.

    Args:
      source [string]: A YAML document
      key [string]: A key into the bindings.

    Returns:
      Transformed source with value of key replaced to match the bindings.
    """
    try:
      value = self[key]
    except KeyError:
      return source

    parts = key.split('.')
    offset = 0
    s = source
    for attr in parts:
        match = re.search('^ *{attr}:(.*)'.format(attr=attr), s, re.MULTILINE)
        if not match:
            raise ValueError(
                'Could not find {key}. Failed on {attr} at {offset}'
                .format(key=key, attr=attr, offset=offset))
        offset += match.start(0)
        s = source[offset:]

    offset -= match.start(0)
    value_start = match.start(1) + offset
    value_end = match.end(0) + offset

    if isinstance(value, basestring) and re.search('{[^}]*{', value):
      # Quote strings with nested {} yaml flows
      value = '"{0}"'.format(value)

    # yaml doesn't understand capital letter boolean values.
    if isinstance(value, bool):
      value = str(value).lower()

    return ''.join([
        source[0:value_start],
        ' {value}'.format(value=value),
        source[value_end:]
    ])


def load_bindings(installed_config_dir, user_config_dir, only_if_local=False):
    user_local_yml_path = yml_or_yaml_path(user_config_dir, 'spinnaker-local')
    install_local_yml_path = yml_or_yaml_path(installed_config_dir,
                                              'spinnaker-local')

    have_user_local = os.path.exists(user_local_yml_path)
    have_install_local = os.path.exists(install_local_yml_path)

    have_local = have_user_local or have_install_local
    if only_if_local and not have_local:
      return None

    bindings = YamlBindings()
    bindings.import_path(yml_or_yaml_path(installed_config_dir, 'spinnaker'))
    if have_install_local:
      bindings.import_path(install_local_yml_path)
    if have_user_local:
      bindings.import_path(user_local_yml_path)
    return bindings
