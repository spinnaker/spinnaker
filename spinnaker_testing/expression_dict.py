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

"""A dictionary that can resolve values from other fields."""

import re


class ExpressionDict(dict):
  @property
  def default_value_interpreter(self):
    return self.__default_value_interpreter

  @default_value_interpreter.setter
  def default_value_interpreter(self, f):
    self.__default_value_interpreter = f

  def __init__(self, *args, **kwargs):
      super(ExpressionDict, self).__init__(*args, **kwargs)
      self.__default_value_interpreter = lambda x: x

  def get(self, key, default_value=None):
      if not key in self:
          return default_value
      return self.__resolve_value(key, saw=[], original=key)

  def __getitem__(self, key):
      if not key in self:
          raise KeyError(key)
      return self.__resolve_value(key, saw=[], original=key)

  def __resolve_value(self, field, saw, original):
    value = super(ExpressionDict, self).get(field, None)
    if value is None and not field in self:
      raise KeyError(field)

    if not isinstance(value, basestring):
      return value

    if field in saw:
      raise ValueError('Cycle looking up variable ' + original)
    saw = saw + [field]

    expression_re = re.compile('\${([\._a-zA-Z0-9]+)(:.+?)?}')
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

