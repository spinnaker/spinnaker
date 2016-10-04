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

"""Helper module for converting YAML into flat dictionaries."""


import yaml


def __flatten_into(root, prefix, target):
  """Helper function that flattens a dictionary into the target dictionary.

  Args:
    root: [dict] The dictionary to flatten from.
    prefix: [string] The key prefix to use when adding entries into the target.
    target: [dict] The dictinoary to update into.
  """
  for name, value in root.items():
    key = prefix + name
    if isinstance(value, dict):
      __flatten_into(value, key + '.', target)
    else:
      target[key] = value


def flatten(root):
  """Flatten a hierarchical YAML dictionary into one with composite keys.

  Args:
    root: [dict] A hierarchical dictionary.

  Returns:
    Equivalent dictionary with '.'-delimited keys.
  """
  result = {}
  __flatten_into(root, '', result)
  return result


def load_string(source, target):
  """Load dictionary implied by YAML text into target dictionary.

  Args:
    source: [string] YAML document text.
    target: [dict] To update from YAML.
  """
  target.update(flatten(yaml.load(source, Loader=yaml.Loader)))


def load_path(path, target):
  """Load dictionary implied by YAML file into target dictionary.

  Args:
    path: [string] Path to file containing YAML document text.
    target: [dict] To update from YAML.
  """
  with open(path, 'r') as f:
    target.update(flatten(yaml.load(f, Loader=yaml.Loader)))

