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
  --deploy_hal_platform=gce \
  --deploy_spinnaker_type=localdebian \
  --deploy_google_project=$PROJECT \
  --deploy_google_instance=$INSTANCE \
  --spinnaker_storage=gcs \
  --storage_gcs_bucket=$BUCKET \
  --google_account_credentials=$GOOGLE_CREDENTIAL_PATH \
  --google_account_project=$PROJECT

//dev/validate_bom.sh \
  --deploy_google_project=$PROJECT \
  --deploy_google_instance=$INSTANCE \
  --spinnaker_storage=gcs \
  --storage_gcs_bucket=$BUCKET \
  --storage_gcs_credentials=$GOOGLE_CREDENTIAL_PATH  \
  --google_account_credentials=$GOOGLE_CREDENTIAL_PATH \
  --google_account_project=$PROJECT \
  --k8s_account_credentials=$HOME/.kube/config \
  --k8s_account_docker_account=my-docker-account \
  --docker_account_address=https://index.docker.io \
  --docker_account_repositories=library/nginx \
  --deploy_hal_platform=gce \
  --deploy_spinnaker_type=distributed \
  --deploy_k8s_namespace=spinnaker \
  --test_include=(kube|front50) \
  --deploy_undeploy=false \
  --deploy_deploy=false
"""


import argparse
import logging
import os
import sys

import validate_bom__config
import validate_bom__deploy
import validate_bom__test

from spinnaker.run import run_quick


def build_report(options):
  """Report on the test results."""
  citest_log_dir = os.path.join(options.log_dir, 'citest_logs')
  if not os.path.exists(citest_log_dir):
    logging.warning('%s does not exist -- no citest logs.', citest_log_dir)
    return

  response = run_quick(
      'cd {log_dir}'
      '; python -m citest.reporting.generate_html_report --index *.journal'
      .format(log_dir=citest_log_dir))
  if response.returncode != 0:
    logging.error('Error building report: %s', response.stdout)
  logging.info('Logging information is in %s', options.log_dir)


def get_options():
  """Resolve all the command-line options."""

  parser = argparse.ArgumentParser()
  parser.add_argument('--log_dir', default='./validate_bom_results',
                      help='Path to root directory for report output.')

  validate_bom__config.init_argument_parser(parser)
  validate_bom__deploy.init_argument_parser(parser)
  validate_bom__test.init_argument_parser(parser)

  options = parser.parse_args()
  validate_bom__config.validate_options(options)
  validate_bom__test.validate_options(options)

  if not os.path.exists(options.log_dir):
    os.makedirs(options.log_dir)

  return options


def main():
  """The main controller."""
  options = get_options()

  deployer = validate_bom__deploy.make_deployer(options)
  config_script = validate_bom__config.make_script(options)
  file_set = validate_bom__config.get_files_to_upload(options)

  try:
    deployer.deploy(config_script, file_set)

    test_controller = validate_bom__test.ValidateBomTestController(deployer)
    test_controller.run_tests()
  finally:
    if sys.exc_info()[0] is not None:
      logging.error('Caught Exception')
      logging.exception('Caught Exception')
    deployer.collect_logs()
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

  print 'PASSED {0}, skipped {1}'.format(num_passed, num_skipped)
  return 0


if __name__ == '__main__':
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  sys.exit(main())
