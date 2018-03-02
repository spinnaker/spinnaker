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
import yaml

from buildtool.__main__ import (
    STANDARD_LOG_LEVELS,
    preprocess_args,
    add_standard_parser_args)
from buildtool.metrics import MetricsManager
from buildtool import add_parser_argument


import validate_bom__config
import validate_bom__deploy
import validate_bom__test

from spinnaker.run import run_quick


def build_report(test_controller):
  """Report on the test results."""
  options = test_controller.options
  citest_log_dir = os.path.join(options.log_dir, 'citest_logs')
  if not os.path.exists(citest_log_dir):
    logging.warning('%s does not exist -- no citest logs.', citest_log_dir)
    return None

  response = run_quick(
      'cd {log_dir}'
      '; python -m citest.reporting.generate_html_report --index *.journal'
      .format(log_dir=citest_log_dir))
  if response.returncode != 0:
    logging.error('Error building report: %s', response.stdout)
  logging.info('Logging information is in %s', options.log_dir)

  return test_controller.build_summary()


def get_options(args):
  """Resolve all the command-line options."""

  args, defaults = preprocess_args(
      args, default_home_path_filename='validate_bom.yml')

  parser = argparse.ArgumentParser(prog='validate_bom.sh')
  add_standard_parser_args(parser, defaults)
# DEPRECATED - use output_dir instead
  add_parser_argument(parser, 'log_dir', defaults, './validate_bom_results',
                      help='Path to root directory for report output.')

  MetricsManager.init_argument_parser(parser, defaults)
  validate_bom__config.init_argument_parser(parser, defaults)
  validate_bom__deploy.init_argument_parser(parser, defaults)
  validate_bom__test.init_argument_parser(parser, defaults)

  options = parser.parse_args(args)
  options.program = 'validate_bom'
  options.command = 'validate_bom'        # metrics assumes a "command" value.
  options.log_dir = options.output_dir    # deprecated
  validate_bom__config.validate_options(options)
  validate_bom__test.validate_options(options)

  if not os.path.exists(options.log_dir):
    os.makedirs(options.log_dir)

  if options.influxdb_database == 'SpinnakerBuildTool':
    options.influxdb_database = 'SpinnakerValidate'

  # Add platform/spinnaker_type to each influxdb metric we produce.
  # We'll use this to distinguish what was being tested.
  context_labels = 'platform=%s,deployment_type=%s' % (
      validate_bom__deploy.determine_deployment_platform(options),
      options.deploy_spinnaker_type)
  latest_unvalidated_suffix = '-latest-unvalidated'
  if options.deploy_version.endswith(latest_unvalidated_suffix):
    bom_series = options.deploy_version[:-len(latest_unvalidated_suffix)]
  else:
    bom_series = options.deploy_version[:options.deploy_version.rfind('-')]
  context_labels += ',version=%s' % bom_series

  if options.influxdb_add_context_labels:
    context_labels += ',' + options.influxdb_add_context_labels
  options.influxdb_add_context_labels = context_labels

  return options


def main(options, metrics):
  """The main controller."""
  outcome_success = False
  deployer = validate_bom__deploy.make_deployer(options, metrics)
  test_controller = validate_bom__test.ValidateBomTestController(deployer)
  init_script, config_script = validate_bom__config.make_scripts(options)
  file_set = validate_bom__config.get_files_to_upload(options)

  try:
    deployer.deploy(init_script, config_script, file_set)
    _, failed, _ = test_controller.run_tests()
    outcome_success = not failed
  finally:
    if sys.exc_info()[0] is not None:
      logging.error('Caught Exception')
      logging.exception('Caught Exception')
    if options.deploy_undeploy or options.deploy_always_collect_logs:
      deployer.collect_logs()
    if options.deploy_undeploy:
      deployer.undeploy()
    else:
      logging.info('Skipping undeploy because --deploy_undeploy=false')

    summary = build_report(test_controller)
    if summary:
      print summary

    if options.testing_enabled or not outcome_success:
      # Only record the outcome if we were testing
      # or if we failed [to deploy/undeploy].
      metrics.inc_counter('ValidationControllerOutcome',
                          {'success': outcome_success})

  logging.info('Exiting with code=%d', test_controller.exit_code)
  return test_controller.exit_code


def wrapped_main():
  options = get_options(sys.argv[1:])

  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d'
             ' [%(threadName)s.%(process)d] %(message)s',
      datefmt='%H:%M:%S',
      level=STANDARD_LOG_LEVELS[options.log_level])

  logging.debug(
      'Running with options:\n   %s',
      '\n   '.join(yaml.dump(vars(options), default_flow_style=False)
                   .split('\n')))

  metrics = MetricsManager.startup_metrics(options)
  try:
    return main(options, metrics)
  finally:
    MetricsManager.shutdown_metrics()


if __name__ == '__main__':
  sys.exit(wrapped_main())
