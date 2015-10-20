#!/usr/bin/python
#
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
import sys
import configure_util
import yaml_util

if __name__ == '__main__':
  try:
    util = configure_util.ConfigureUtil()

    root_dir = os.environ.get('SPINNAKER_HOME', '/opt/spinnaker')
    config_dir = os.path.join(root_dir, 'config')
    yaml_bindings = yaml_util.load_new_bindings(config_dir, only_if_local=True)
    if yaml_bindings:
      util.update_deck_settings(yaml_bindings)
    else:   
      util.validate_or_die()
      bindings = util.load_bindings()
      util.update_all_config_files(bindings)
  except SystemExit:
    sys.exit(-1)
