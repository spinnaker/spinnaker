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
# python create_google_image.py --release_path $RELEASE_PATH

import argparse
import os
import re
import sys

from spinnaker.run import check_run_quick
from spinnaker.run import run_quick
from abstract_packer_builder import AbstractPackerBuilder


def get_default_project():
  """Determine the default project name.

  The default project name is the gcloud configured default project.
  """
  result = check_run_quick('gcloud config list', echo=False)
  return re.search('project = (.*)\n', result.stdout).group(1)


class GooglePackerBuilder(AbstractPackerBuilder):
  PACKER_TEMPLATE = os.path.dirname(__file__) + '/build_google_image.packer'

  def _do_prepare(self):
    if not self.options.image_project:
      self.options.image_project = get_default_project()

    # Set the default target_image name to the release name.
    # If --target_image was on the commandline it will override this
    # later when the commandline vars are added.
    self.add_packer_variable(
        'target_image',
        os.path.basename(self.options.release_path).replace('_', '-'))

    # image_project isn't passed through to packer.
    self.remove_raw_arg('image_project')

    # The default project_id may be overriden by a commandline argument later.
    self.add_packer_variable('project_id', self.options.image_project)

  def _do_get_next_steps(self):
      match = re.search('googlecompute: A disk image was created: (.+)',
                        self.packer_output)
      image_name = match.group(1) if match else '$IMAGE_NAME'

      return """
To deploy this image, use a command like:
    gcloud compute instances create {image} \\
        --project $GOOGLE_SPINNAKER_PROJECT \\
        --image {image} \\
        --image-project {image_project} \\
        --machine-type n1-standard-8 \\
        --zone $GOOGLE_ZONE \\
        --scopes=compute-rw \\
        --metadata=startup-script=/opt/spinnaker/install/first_google_boot.sh \\
        --metadata-from-file=\\
spinnaker_local=$SPINNAKER_YML_PATH,\\
managed_project_credentials=$GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH

  You can leave off the managed_project_credentials metadata if
  $SPINNAKER_PROJECT is the same as the GOOGLE_PRIMARY_MANAGED_PROJECT_ID
  in the spinnaker-local.yml.
""".format(
    image=image_name,
    image_project=self.options.image_project)

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize the command-line parameters."""

    super(GooglePackerBuilder, cls).init_argument_parser(parser)

    parser.add_argument(
        '--image_project', default='',
        help='Google Cloud Platform project to add the image to.')


if __name__ == '__main__':
    GooglePackerBuilder.main()
