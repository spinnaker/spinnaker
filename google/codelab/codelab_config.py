# Copyright 2016 Google Inc. All Rights Reserved.
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

from spinnaker.yaml_util import YamlBindings

def configure_codelab_igor_jenkins(password):
  """Configures Igor to be enabled and to point to the codelab jenkins instance.

  """
  YamlBindings.update_yml_source(
    '/opt/spinnaker/config/spinnaker-local.yml',
    {
      'services': {
        'jenkins': {
          'defaultMaster': {
            'name': 'CodelabJenkins',
            'baseUrl': 'http://localhost:5656',
            'username': 'admin',
            'password': password
          }
        },
        'igor': {
          'enabled': 'true'
        }
      }
    }
  )


if __name__ == '__main__':
  configure_codelab_igor_jenkins('admin')
