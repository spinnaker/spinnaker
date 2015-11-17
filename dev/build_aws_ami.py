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

# Creates an image in the default project named with the release name.
# python buidl_aws_ami.py --release_uri $RELEASE_URI

import os
import re
import sys

from abstract_packer_builder import AbstractPackerBuilder


class AmiPackerBuilder(AbstractPackerBuilder):
  PACKER_TEMPLATE = os.path.dirname(sys.argv[0]) + '/build_aws_ami.packer'

  def _do_prepare(self):
    self.add_packer_variable(
        'target_ami', os.path.basename(self.options.release_path))

  def _do_get_next_steps(self):
      last_line = self.packer_output.split()[-1]
      match = re.match('^(-a-z0-9]+): (.+)$', last_line)
      region_name = match.group(1) if match else None
      ami_name = match.group(2) if match else None

      match = re.match('amazon-ebs: Creating the AMI: (.+)\n',
                       self.packer_output)
      image_name = match.group(1) if match else None

      return 'Region={region}\nAMI={ami}\nImage={image}'.format(
          region=region_name, ami=ami_name, image=image_name)

if __name__ == '__main__':
    AmiPackerBuilder.main()
