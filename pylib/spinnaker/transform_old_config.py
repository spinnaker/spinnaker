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
import sys
import yaml_util
from run import check_run_quick


class Processor(object):
  def __init__(self, config, environ_path, yml_path, aws_path):
      with open(environ_path, 'r') as f:
          self.__environ_content = f.read()
          if not self.__environ_content.endswith('\n'):
              self.__environ_content += '\n'

      with open(yml_path, 'r') as f:
          self.__output = f.read()

      self.__bindings = yaml_util.YamlBindings()
      self.__bindings.import_string(config)
      self.__write_yml_path = yml_path
      self.__write_aws_path = aws_path
      self.__write_environ_path = environ_path
      self.__environ_keys = set()

  def update_environ(self, key, name):
      value = self.lookup(key)
      if value is None:
          return

      self.__environ_keys.add(key)
      assignment = '{name}={value}'.format(name=name, value=value)
      match = re.search('^{name}=.*'.format(name=name),
                        self.__environ_content,
                        re.MULTILINE)
      if match:
        self.__environ_content = ''.join([
            self.__environ_content[0:match.start(0)],
            assignment,
            self.__environ_content[match.end(0):]
        ])
      else:
        self.__environ_content += assignment + '\n'

  def update_in_place(self, key):
      self.__output = self.__bindings.transform_yaml_source(self.__output, key)

  def lookup(self, key):
      try:
          return self.__bindings.get(key)
      except KeyError:
          return None

  def update_remaining_keys(self):
    stack = [('', self.__bindings.map)]
    while stack:
        prefix, root = stack.pop()
        for name, value in root.items():
          key = '{prefix}{child}'.format(prefix=prefix, child=name)
          if isinstance(value, dict):
              stack.append((key + '.', value))
          elif not key in self.__environ_keys:
              try:
                self.update_in_place(key)
              except ValueError:
                pass


  def process(self):
      self.update_environ('providers.aws.enabled', 'SPINNAKER_AWS_ENABLED')
      self.update_environ('providers.aws.defaultRegion',
                          'SPINNAKER_AWS_DEFAULT_REGION')
      self.update_environ('providers.google.enabled',
                          'SPINNAKER_GOOGLE_ENABLED')
      self.update_environ('providers.google.primaryCredentials.project',
                          'SPINNAKER_GOOGLE_PROJECT_ID')
      self.update_environ('providers.google.defaultRegion',
                          'SPINNAKER_GOOGLE_DEFAULT_REGION')
      self.update_environ('providers.google.defaultZone',
                          'SPINNAKER_GOOGLE_DEFAULT_ZONE')

      self.update_in_place('providers.aws.primaryCredentials.name')
      aws_name = self.lookup('providers.aws.primaryCredentials.name')
      aws_key = self.lookup('providers.aws.primaryCredentials.access_key_id')
      aws_secret = self.lookup('providers.aws.primaryCredentials.secret_key')
      if aws_key and aws_secret:
          with open(self.__write_aws_path, 'w') as f:
              f.write("""
[default]
aws_secret_access_key = {secret}
aws_access_key_id = {key}
""".format(name=aws_name, secret=aws_secret, key=aws_key))

      self.update_remaining_keys()

      with open(self.__write_environ_path, 'w') as f:
          f.write(self.__environ_content)

      with open(self.__write_yml_path, 'w') as f:
          f.write(self.__output)


if __name__ == '__main__':
  if len(sys.argv) != 5:
      sys.stderr.write('Usage: <content> <environ-path> <local-yml-path> <aws-cred-path>\n')
      sys.exit(-1)

  content = sys.argv[1]
  environ_path = sys.argv[2]
  local_yml_path = sys.argv[3]
  aws_credentials_path = sys.argv[4]
  processor = Processor(content,
                        environ_path, local_yml_path, aws_credentials_path)
  processor.process()
  sys.exit(0)
