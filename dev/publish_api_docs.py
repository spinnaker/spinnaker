#!/usr/bin/python
#
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
#
# Given a top-level Spinnaker version, this script generates and pushes API documentation to spinnaker.github.io.
# It assumes the user has provided the location to a swagger-codegen-cli jarfile.
#
# Gate serves the JSON that describes the docs at {gate}/v2/api-docs.
# This script starts Gate and Redis (since Gate depends on Redis to start), with Gate running on a
# port other than 8084 to avoid collisions.

import argparse
import os
import sys
import urllib2
import time
import yaml

from spinnaker.run import check_run_quick

class ApiDocsPublisher():

  def __init__(self, options):
    self.__githubio_repo_uri = options.githubio_repo_uri
    self.__swagger_codegen_cli_jar_path = options.swagger_codegen_cli_jar_path
    self.__spinnaker_version = options.spinnaker_version

    self.__repo_name = os.path.basename(self.__githubio_repo_uri)
    self.__gate_port = options.gate_port
    self.__gate_uri = 'http://localhost:{port}'.format(port=self.__gate_port)

  def __clone_docs_repo(self):
    check_run_quick('git clone {repo_uri}'.format(repo_uri=self.__githubio_repo_uri))

  def __determine_gate_version(self):
    bom_file = 'bom.yml'
    check_run_quick('gsutil cat gs://halconfig/bom/{spinnaker_version}.yml > {bom_file}'
                    .format(spinnaker_version=self.__spinnaker_version, bom_file=bom_file))

    with open(bom_file, 'r') as stream:
      try:
        bom = yaml.load(stream)
        return bom['services']['gate']['version']
      except yaml.YAMLError as err:
        print 'Failed to load Gate version from BOM.'
        raise err

  def __generate_docs(self, gate_version):
    json_file = 'docs.json'
    templates_directory = '{repo_name}/_api_templates'.format(repo_name=self.__repo_name)

    check_run_quick('sudo apt-get install -y --force-yes spinnaker-gate={gate_version}'
                    .format(gate_version=gate_version))

    check_run_quick('sudo sed -i \'s/port: 8084/port: {port}/\' /opt/gate/config/gate.yml'
                    .format(port=self.__gate_port))

    check_run_quick('sudo service redis-server restart')
    check_run_quick('sudo service gate restart')

    self.__wait_for_gate_startup()

    check_run_quick('curl {gate_uri}/v2/api-docs > {json_file}'
                    .format(gate_uri=self.__gate_uri, json_file=json_file))
    check_run_quick(
      'java -jar {jar_path} generate -i {json_file} -l html2 -o . -t {templates_directory}'
      .format(jar_path=self.__swagger_codegen_cli_jar_path, json_file=json_file,
              templates_directory=templates_directory))

    check_run_quick('sudo service gate stop')
    check_run_quick('sudo service redis-server stop')

  def __wait_for_gate_startup(self):
    for _ in range(0, 30):
      try:
        if urllib2.urlopen('{gate_uri}/health'.format(gate_uri=self.__gate_uri)).getcode() == 200:
          return True
      except urllib2.URLError:
        print 'Gate is not ready...'
        time.sleep(1)

    raise RuntimeError('Gate did not start.')

  def __push_docs_to_repo(self):
    docs_path = 'reference/api/docs.html'
    commit_message = ('docs(api): API Documentation for Spinnaker {spinnaker_version}'
                      .format(spinnaker_version=self.__spinnaker_version))

    check_run_quick('mv index.html {repo_name}/{docs_path}'.format(repo_name=self.__repo_name, docs_path=docs_path))
    check_run_quick('git -C {repo_name} add {docs_path}'.format(repo_name=self.__repo_name, docs_path=docs_path))
    check_run_quick('git -C {repo_name} commit -m "{message}"'.format(repo_name=self.__repo_name, message=commit_message))
    check_run_quick('git -C {repo_name} push origin master'.format(repo_name=self.__repo_name))

  def publish_api_docs(self):
    self.__clone_docs_repo()
    gate_version = self.__determine_gate_version()
    self.__generate_docs(gate_version)
    self.__push_docs_to_repo()

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments."""
    parser.add_argument('--githubio_repo_uri', default='', required=False,
                        help='The ssh uri of the spinnaker.github.io repo to'
                        'commit the API docs to, e.g. git@github.com:spinnaker/spinnaker.github.io.')
    parser.add_argument('--spinnaker_version', default='', required=True,
                        help="The top-level Spinnaker version to use to generate the API docs.")
    parser.add_argument('--swagger_codegen_cli_jar_path', default='', required=True,
                        help='The location of a swagger-codegen-cli jarfile.')
    parser.add_argument('--gate_port', default='8090', required=False,
                        help="The port to run Gate on.")

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    result_publisher = cls(options)
    result_publisher.publish_api_docs()

if __name__ == '__main__':
  sys.exit(ApiDocsPublisher.main())
