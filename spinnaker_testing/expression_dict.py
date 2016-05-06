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

"""A dictionary that can resolve values from other keys."""

import re


class ExpressionDict(dict):
  """A specialization of dict where key values can reference other entries.

  The dictionary is a flat name/value dictionary. If a value is in the form
  ${KEY} then the value is assumed to be the value of a transitive lookup on
  KEY. If the value is in the form ${KEY:DEFAULT} then the value will be
  DEFAULT if the KEY is not present.
  """

  @property
  def default_value_interpreter(self):
    """A function mapping a default value into its actual value.

    When an expression specifies a default value, the default value is
    specified as a string. However if the dictionary contains typed values
    then it may wish to use this function to interpet the specified string
    into a typed value.
    """
    return self.__default_value_interpreter

  @default_value_interpreter.setter
  def default_value_interpreter(self, func):
    """Binds the default_value_interpreter function.

    Args:
      func: [object (string)]: Function turning a string into an object.
    """
    self.__default_value_interpreter = func

  def __init__(self, *args, **kwargs):
    """Overrides standard dictionary constructor."""
    super(ExpressionDict, self).__init__(*args, **kwargs)
    self.__default_value_interpreter = lambda x: x

  def get(self, key, default_value=None):
    """Implements dict interface.

    This will track down values that reference other keys in the dictionary.
    """
    if not key in self:
      return default_value
    return self.__resolve_value(key, saw=[], original=key)

  def __getitem__(self, key):
    """Implements dict interface.

    This will track down values that reference other keys in the dictionary.
    """
    if not key in self:
      raise KeyError(key)
    return self.__resolve_value(key, saw=[], original=key)

  def __resolve_value(self, key, saw, original):
    """Looks up specified key and returns its final value.

    Args:
      key: [string] Specification of the key to retrieve.
      saw: [list of string] The path of keys we're chasing down for the value.
      original: [string] The original starting point of the |saw| path.

    Raises:
      KeyError if the |key| is not in the dictionary.
      ValueError if a cycle is encountered.
    """
    value = super(ExpressionDict, self).get(key, None)
    if value is None and not key in self:
      raise KeyError(key)

    if not isinstance(value, basestring):
      return value

    if key in saw:
      raise ValueError('Cycle looking up variable ' + original)
    saw = saw + [key]

    expression_re = re.compile(r'\${([\._a-zA-Z0-9]+)(:.*?)?}')
    exact_match = expression_re.match(value)
    if exact_match and exact_match.group(0) == value:
      try:
        got = self.__resolve_value(exact_match.group(1), saw, original)
        return got
      except KeyError:
        if exact_match.group(2):
          return self.__default_value_interpreter(exact_match.group(2)[1:])
        else:
          return value

    result = []
    offset = 0

    # Look for fragments of ${key} or ${key:default} then resolve them.
    text = value
    for match in expression_re.finditer(text):
      result.append(text[offset:match.start()])
      try:
        got = self.__resolve_value(match.group(1), saw, original)
        result.append(str(got))
      except KeyError:
        if match.group(2):
          result.append(str(match.group(2)[1:]))
        else:
          result.append(match.group(0))
      offset = match.end()  # skip trailing '}'
    result.append(text[offset:])

    return ''.join(result)

