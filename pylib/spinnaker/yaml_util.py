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
    if d is not None:
      for name,value in d.items():
        self.__update_field(name, value, self.__map)

  def import_string(self, s):
    self.import_dict(yaml.load(s, Loader=yaml.Loader))

  def import_path(self, path):
    with open(path, 'r') as f:
      self.import_dict(yaml.load(f, Loader=yaml.Loader))

  def __update_field(self, name, value, container):
    if not isinstance(value, dict) or not name in container:
      if not (value is None and isinstance(container.get(name, None), dict)):
        # Set the value, but if it is an empty dictionary, keep the old one
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
  def update_yml_source(path, update_dict, add_new_nodes=True):
    """Update the yaml source at the path according to the update dict.

    All the previous bindings not in the update dict remain unchanged.
    The yaml file at the path is re-written with the new bindings.

    Args:
      path [string]: Path to a yaml source file.
      update_dict [dict]: Nested dictionary corresponding to
          nested yaml properties, keyed by strings.
      add_new_nodes [boolean]: If true, add nodes in update_dict that
          were not in the original. Otherwise raise a KeyError.
    """
    bindings = YamlBindings()
    bindings.import_dict(update_dict)
    updated_keys = bindings.__get_flat_keys(bindings.__map)
    source = '' # declare so this is in scope for both 'with' blocks
    with open(path, 'r') as source_file:
      source = source_file.read()
      for prop in updated_keys:
        source = bindings.transform_yaml_source(source, prop,
                                                add_new_nodes=add_new_nodes)

    with open(path, 'w') as source_file:
      source_file.write(source)

  def find_yaml_context(self, source, root_node, full_key, raise_if_not_found):
    """Given a yaml file and full key, find the span of the value for the key.

    If the key does not exist, then return how we should modify the file
    around the insertion point so that the context is there.

    Args:
      source: [string] The YAML source text.
      root_node: [yaml.nodes.MappingNode]  The composed yaml tree
      full_key: [string] Dot-delimited path whose value we're looking for
      raise_if_not_found: [boolean] Whether to raise a KeyError or return
         additional context if key isnt found.

    Returns:
      context, span

      where:
          context:[string, string)]: Text to append before and after the cut.
          span: [(int, int)]: The start/end range of source with existing value
                              to cut.
    """
    parts = full_key.split('.')
    if not isinstance(root_node, yaml.nodes.MappingNode):
      if not root_node and not raise_if_not_found:
        return (self._make_missing_key_text('', parts), '\n'), (0, 0)
      else:
        raise ValueError(root_node.__class__.__name__ + ' is not a yaml node.')

    closest_node = None
    for key_index, key in enumerate(parts):
      found = False
      for node in root_node.value:
        if node[0].value == key:
          closest_node = node
          found = True
          break
      if not found:
        break
      root_node = closest_node[1]

    if closest_node is None:
      if raise_if_not_found:
        raise KeyError(full_key)
      # Nothing matches, so stick this at the start of the file.
      return (self._make_missing_key_text('', parts), '\n'), (0, 0)

    span = (closest_node[1].start_mark.index,
            closest_node[1].end_mark.index)
    span_is_empty = span[0] == span[1]

    if found:
      # value of closest_node is what we are going to replace.
      # There is still a space between the token and value we write.
      return (' ' if span_is_empty else '', ''), span

    if raise_if_not_found:
      raise KeyError('.'.join(parts[0:key_index + 1]))

    # We are going to add a new child. This is going to be indented equal
    # to the current line if the value isnt empty, otherwise one more level.
    line_start = closest_node[0].start_mark.index - 1
    while line_start >= 0 and source[line_start] == ' ':
      line_start -= 1

    indent = ' ' * (closest_node[0].start_mark.index - line_start + 1)
    key_text = self._make_missing_key_text(indent, parts[key_index:])
    if span_is_empty:
      key_text = '  ' + prefix
    return (key_text, '\n' + indent), (span[0], span[0])

  def _make_missing_key_text(self, indent, keys):
    key_context = []
    sep = ''
    for depth, key in enumerate(keys):
      key_context.append('{sep}{extra_indent}{key}:'
                         .format(sep=sep,
                                 extra_indent='  ' * depth,
                                 key=key,
                                 base_indent=indent))
      sep = '\n' + indent
    key_context.append(' ')
    return ''.join(key_context)

  def transform_yaml_source(self, source, key, add_new_nodes=True):
    """Transform the given yaml source so its value of key matches the binding.

    Has no effect if key is not among the bindings.
    But raises a KeyError if it is in the bindings but not in the source.

    Args:
      source [string]: A YAML document
      key [string]: A key into the bindings.
      add_new_nodes [boolean]: If true, add node for key if not already present.
           Otherwise raise a KeyError.

    Returns:
      Transformed source with value of key replaced to match the bindings.
    """
    try:
      value = self[key]
    except KeyError:
      return source

    if isinstance(value, basestring) and re.search('{[^}]*{', value):
      # Quote strings with nested {} yaml flows
      value = '"{0}"'.format(value)

    # yaml doesn't understand capital letter boolean values.
    if isinstance(value, bool):
      value = str(value).lower()

    yaml_root = yaml.compose(source)
    context, span = self.find_yaml_context(
        source, yaml_root, key, not add_new_nodes)

    text_before = context[0]
    text_after = context[1]
    start_cut = span[0]
    end_cut = span[1]
    return ''.join([
        source[0:start_cut],
        text_before,
        '{value}'.format(value=value),
        text_after,
        source[end_cut:]
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
