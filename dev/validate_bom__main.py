# Copyright 2017 Google Inc. All Rights Reserved.
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

"""The main program controller for BomValidate

This program will:
  (1) Deploy Spinnaker as specified via command-line
  (2) Configure Spinnaker as specified via command-line
  (3) Run the test suite or some subset there-of, as specified via command-line
  (4) Collect and report on the results
  (5) Tear down the deployment


Sample usage:
./validate_bom.sh \
  --deploy_platform=gce \
  --deploy_spinnaker_type=localdebian \
  --google_deploy_project=$PROJECT \
  --google_deploy_instance=$INSTANCE \
  --spinnaker_storage=gcs \
  --gcs_storage_bucket=$BUCKET \
  --google_account_credentials=$GOOGLE_CREDENTIAL_PATH \
  --google_account_project=$PROJECT
"""


import argparse
import logging
import sys

import validate_bom__config
import validate_bom__deploy
import validate_bom__test

def build_report(options):
  """Report on the test results."""
  logging.info('SKIP BUILD_REPORT on %s', options.deploy_platform)


def main():
  """The main controller."""
  parser = argparse.ArgumentParser()

  validate_bom__config.init_argument_parser(parser)
  validate_bom__deploy.init_argument_parser(parser)
  validate_bom__test.init_argument_parser(parser)

  options = parser.parse_args()

  validate_bom__config.validate_options(options)
  validate_bom__test.validate_options(options)
  deployer = validate_bom__deploy.make_deployer(options)

  config_script = validate_bom__config.make_script(options)
  file_set = validate_bom__config.get_files_to_upload(options)

  deployer.deploy(config_script, file_set)

  test_controller = validate_bom__test.ValidateBomTestController(deployer)
  test_controller.run_tests()

  deployer.undeploy()

  build_report(options)

  def dump_list(name, entries):
    """Write out all the names from the test results."""
    if not entries:
      return
    print '{0}:'.format(name)
    for entry in entries:
      print '  * {0}'.format(entry[0])

  print '\nSummary:'
  dump_list('SKIPPED', test_controller.skipped)
  dump_list('PASSED', test_controller.passed)
  dump_list('FAILED', test_controller.failed)

  num_skipped = len(test_controller.skipped)
  num_passed = len(test_controller.passed)
  num_failed = len(test_controller.failed)

  print ''
  if num_failed:
    print 'FAILED {0} of {1}, skipped {2}'.format(
        num_failed, (num_failed + num_passed), num_skipped)
    return -1
  else:
    print 'PASSED {0}, skipped {1}'.format(num_passed, num_skipped)
    return 0


if __name__ == '__main__':
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.INFO)

  sys.exit(main())
