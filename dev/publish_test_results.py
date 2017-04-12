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

# Provides facilities for publishing Nightly Build 'posts' to
# our github.io site.

import argparse
import datetime
import os
import sys
import yaml

from spinnaker.run import check_run_quick

# Path to the posts directory in the spinnaker.github.io site.
POSTS_DIR = '_posts'

class TestResultPublisher():

  def __init__(self, options):
    self.__nightly_version = options.nightly_version
    self.__githubio_repo_uri = options.githubio_repo_uri
    self.__repo_name = ''
    self.__test_results_file = options.test_results_file
    self.__test_results_html = ''


  def __checkout_githubio_repo(self):
    """Clones the spinnaker.github.io git repo.
    """
    check_run_quick('git clone {0}'.format(self.__githubio_repo_uri))
    self.__repo_name = os.path.basename(self.__githubio_repo_uri)
    if self.__repo_name.endswith('.git'):
      self.__repo_name = self.__repo_name.replace('.git', '')

  def __read_test_results(self):
    """Read the test results into memory.
    """
    with open(self.__test_results_file, 'r') as results_file:
      self.__test_results_html = results_file.read()

  def __format_nightly_post(self):
    # Initialized with 'front matter' necessary for the post.
    timestamp = '{:%Y-%m-%d %H:%M:%S}'.format(datetime.datetime.now())
    post_lines = [
      '---',
      'title: Spinnaker Nightly Build Version {version}'.format(version=self.__nightly_version),
      'date: {date}'.format(date=timestamp),
      'categories: nightly-builds',
      '---',
      ''
    ]
    post_lines.append('# Spinnaker Nightly Build Version {version}'
                      .format(version=self.__nightly_version))
    post_lines.append('\n# Test results\n')
    post_lines.append(self.__test_results_html)
    post = '\n'.join(post_lines)
    return post

  def __publish_post(self, post_content):
    day = '{:%Y-%m-%d}'.format(datetime.datetime.now())
    post_name = '{day}-{version}-nightly.md'.format(day=day, version=self.__nightly_version)
    post_path = os.path.join(self.__repo_name, POSTS_DIR, post_name)
    # Path to post file relative to the git root.
    post_rel_path = os.path.join(POSTS_DIR, post_name)
    with open(post_path, 'w') as post_file:
      post_file.write(post_content)

    check_run_quick('git -C {0} add {1}'.format(self.__repo_name, post_rel_path))
    message = 'Nightly Build post for version {0}'.format(self.__nightly_version)
    check_run_quick('git -C {0} commit -m "{1}"'.format(self.__repo_name, message))
    check_run_quick('git -C {0} push origin master'.format(self.__repo_name))

  def publish_nightly_post(self):
    """Creates and publishes Nightly Build post for the spinnaker.github.io site.

    'Publishing' in this case means creating a new file, 'git add'ing it to the
    local git repository for spinnaker.github.io, and then pushing a commit
    to origin/master.

    A private key that has access to --githubio_repo needs added
    to a running ssh-agent on the machine this script will run on:

    > <copy or rsync the key to the vm>
    > eval `ssh-agent`
    > ssh-add ~/.ssh/<key with access to github repos>
    """
    self.__checkout_githubio_repo()
    self.__read_test_results()
    post = self.__format_nightly_post()
    self.__publish_post(post)

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments."""
    parser.add_argument('--githubio_repo_uri', default='', required=True,
                        help='The ssh uri of the spinnaker.github.io repo to'
                        'commit the nightly build post to, e.g. git@github.com:spinnaker/spinnaker.github.io.')
    parser.add_argument('--nightly_version', default='', required=True,
                        help='The version of Spinnaker we build during our nightly build.')
    parser.add_argument('--test_results_file', default='', required=True,
                        help='The file containing the test results summary, e.g. index.html.')

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    result_publisher = cls(options)
    result_publisher.publish_nightly_post()

if __name__ == '__main__':
  sys.exit(TestResultPublisher.main())
