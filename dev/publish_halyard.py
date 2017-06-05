#!/usr/bin/python -u
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
#
# Publishes a new stable version of Halyard.
#
# 'Publishing' in this case means checking out Halyard at a certain
# git commit hash and running a Gradle build to produce and push a Debian
# to Bintray with the 'distribution' set as 'trusty-stable'.
#
# A private key that has access to --halyard_repo_uri needs to be added
# to a running ssh-agent on the machine this script will run on:
#
# > <copy or rsync the key to the vm>
# > eval `ssh-agent`
# > ssh-add ~/.ssh/<key with access to github repo>
#
# If you are running this script on Jenkins, you can configure Jenkins to handle SSH credentials.

import argparse
import datetime
import os
import sys
import yaml

from annotate_source import Annotator
from build_release import BackgroundProcess
from publish_bom import format_stable_branch
from spinnaker.run import check_run_quick


class HalyardPublisher(object):
  """Publishes a nightly version of Halyard to be a stable version.
  """

  def __init__(self, options, build_number=None):
    self.__annotator = None
    self.__build_number = build_number or os.environ.get('BUILD_NUMBER')
    self.__github_publisher = options.github_publisher
    self.__hal_nightly_bucket_uri = options.hal_nightly_bucket_uri
    self.__halyard_repo_uri = options.halyard_repo_uri
    self.__nightly_version = options.nightly_version
    self.__options = options
    self.__patch_release = options.patch_release
    self.__stable_branch = ''
    self.__stable_version = options.stable_version
    self.__stable_version_tag = ''
    self.__docs_repo_name = options.docs_repo_name
    self.__docs_repo_owner = options.docs_repo_owner

  def __checkout_halyard_repo(self):
    """Clones the Halyard git repo at the commit at which we built the nightly version.
    """
    bucket_uri = self.__hal_nightly_bucket_uri
    local_bucket_name = os.path.basename(bucket_uri)
    print 'cloning: {0}'.format(self.__halyard_repo_uri)
    check_run_quick('git clone {0}'.format(self.__halyard_repo_uri))

    # Read the Halyard nightly bucket file.
    print ('Fetching Halyard nightly build info from {0}. Writing locally to {1}.'
           .format(bucket_uri, local_bucket_name))
    if not os.path.exists(local_bucket_name):
        os.mkdir(local_bucket_name)
    check_run_quick('gsutil rsync -r -d {remote_uri} {local_bucket}'
                    .format(remote_uri=bucket_uri, local_bucket=local_bucket_name))
    nightly_commit_file = '{0}/nightly-version-commits.yml'.format(local_bucket_name)
    nightly_commit_dict = {}
    with open(nightly_commit_file, 'r') as ncf:
      nightly_commit_dict = yaml.load(ncf.read())

    # Check Halyard out at the correct commit.
    commit_to_build = nightly_commit_dict.get(self.__nightly_version, None)
    if not commit_to_build:
      raise ValueError('No commit hash recorded in {bucket} for Halyard nightly version {nightly}, exiting.'
                       .format(bucket=bucket_uri, nightly=self.__nightly_version))
    print ('Checking out Halyard from {0} repo at commit {1}.'
           .format(self.__halyard_repo_uri, commit_to_build))
    check_run_quick('git -C halyard checkout {0}'.format(commit_to_build))

  def __update_versions_tracking_file(self):
    """Updates the global versions.yml tracking file to point at the new
    version of Halyard.
    """
    check_run_quick('hal admin publish latest-halyard {}'
                    .format(self.__stable_version))

  def __tag_halyard_repo(self):
    """Tags the Halyard repo with a tag derived from --stable_version.
    """
    self.__stable_version_tag = self.__stable_version
    if not self.__stable_version_tag.startswith('version-'):
      self.__stable_version_tag = 'version-' + self.__stable_version_tag
    self.__annotator = Annotator(self.__options, path='halyard', next_tag=self.__stable_version_tag)
    self.__annotator.parse_git_tree()
    self.__annotator.tag_head()
    self.__annotator.delete_unwanted_tags()

  def __build_halyard(self):
    """Builds a Halyard debian with distribution as 'trusty-stable'.
    """
    jarRepo = self.__options.jar_repo
    parts = self.__options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo>')
    org, packageRepo = parts[0], parts[1]
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']
    extra_args = [
      '--debug',
      '-Prelease.useLastTag=true',
      '-PbintrayPackageBuildNumber={number}'.format(
        number=self.__build_number),
      '-PbintrayOrg="{org}"'.format(org=org),
      '-PbintrayPackageRepo="{repo}"'.format(repo=packageRepo),
      '-PbintrayJarRepo="{jarRepo}"'.format(jarRepo=jarRepo),
      '-PbintrayKey="{key}"'.format(key=bintray_key),
      '-PbintrayUser="{user}"'.format(user=bintray_user),
      '-PbintrayPackageDebDistribution=trusty-stable'
    ]

    BackgroundProcess.spawn(
      'Building and publishing Debians for stable Halyard...',
      'cd "halyard"; ./gradlew {extra} candidate; cd ".."'.format(
        extra=' '.join(extra_args))
    ).check_wait()

  def __push_halyard_tag_and_branch(self):
    """Pushes a stable branch and git version tag to --github_publisher's Halyard repository.
    """
    major, minor, _ = self.__stable_version.split('.')
    self.__stable_branch = format_stable_branch(major, minor)

    if self.__patch_release:
      check_run_quick('git -C halyard checkout {0}'.format(self.__stable_branch))
    else:
      # Create new release branch.
      check_run_quick('git -C halyard checkout -b {0}'.format(self.__stable_branch))

    repo_to_push = 'git@github.com:{owner}/halyard.git'.format(owner=self.__github_publisher)
    check_run_quick('git -C halyard remote add release {url}'
                    .format(url=repo_to_push))

    print ('Pushing Halyard stable branch {branch} to {repo}'
           .format(branch=self.__stable_branch, repo=repo_to_push))
    check_run_quick('git -C halyard push release {branch}'.format(branch=self.__stable_branch))

    print ('Pushing Halyard stable version tag {tag} to {repo}'
           .format(tag=self.__stable_version_tag, repo=self.__halyard_repo_uri))
    check_run_quick('git -C halyard push release {tag}'.format(tag=self.__stable_version_tag))

  def __generate_halyard_docs(self):
    """Builds Halyard's CLI, which writes the new documentation locally to halyard/docs/commands.md
    """
    BackgroundProcess.spawn(
      'Building Halyard\'s CLI to generate documentation...',
      'cd halyard/halyard-cli; make; cd ../..'  # The Makefile looks up a directory to find `gradlew`.
    ).check_wait()

  def __publish_halyard_docs(self):
    """ Formats Halyard's documentation, then pushes to Spinnaker's documentation repository.
    """
    docs_source = 'halyard/docs/commands.md'
    docs_target = '{repo_name}/reference/halyard/commands.md'.format(repo_name=self.__docs_repo_name)

    repo_uri = 'git@github.com:{repo_owner}/{repo_name}'.format(repo_owner=self.__docs_repo_owner,
                                                                repo_name=self.__docs_repo_name)
    check_run_quick('git clone {repo_uri}'.format(repo_uri=repo_uri))

    with open(docs_source, 'r') as source:
      with open(docs_target, 'w') as target:
        header = '\n'.join([
          '---',
          'layout: single',
          'title:  "Commands"',
          'sidebar:',
          '  nav: reference',
          '---',
          '',
          'Published: {}'.format(datetime
              .datetime
              .utcnow()
              .strftime('%Y-%m-%d %H:%M:%S')),
          '',
        ])
        target.write(header + source.read())

    commit_message = 'docs(halyard): {version}'.format(version=self.__stable_version)
    check_run_quick('git -C {repo_name} add reference/halyard/commands.md'.format(repo_name=self.__docs_repo_name))
    check_run_quick('git -C {repo_name} commit -m "{message}"'
                    .format(repo_name=self.__docs_repo_name, message=commit_message))
    check_run_quick('git -C {repo_name} push origin master'.format(repo_name=self.__docs_repo_name))

  def publish_stable_halyard(self):
    self.__checkout_halyard_repo()
    self.__tag_halyard_repo()
    self.__build_halyard()
    self.__push_halyard_tag_and_branch()
    self.__generate_halyard_docs()
    self.__publish_halyard_docs()
    self.__update_versions_tracking_file()

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments."""
    parser.add_argument('--bintray_repo', default='',
                        help='Publish to this bintray repo.\n'
                        'This requires BINTRAY_USER and BINTRAY_KEY are set.')
    parser.add_argument('--github_publisher', default='', required=True,
                        help="The owner of the remote repo the branch and tag are pushed to.")
    parser.add_argument('--hal_nightly_bucket_uri', default='',
                        help='The URI of the Halyard nightly build bucket.')
    parser.add_argument('--halyard_repo_uri', default='', required=True,
                        help='The ssh uri of the Halyard repo to build the stable release from.')
    parser.add_argument('--jar_repo', default='',
                        help='Publish produced jars to this repo.\n'
                        'This requires BINTRAY_USER and BINTRAY_KEY are set.')
    parser.add_argument('--nightly_version', default='', required=True,
                        help='The nightly version of Halyard that we are promoting.')
    parser.add_argument('--patch_release', default=False, action='store_true',
                        help='Make a patch release.')
    parser.add_argument('--stable_version', default='', required=True,
                        help='The stable version we are publishing the chosen nightly Halyard version as.')
    parser.add_argument('--docs_repo_name', default='spinnaker.github.io', required=True,
                        help="The name of the Spinnaker docs repository")
    parser.add_argument('--docs_repo_owner', default='spinnaker', required=True,
                        help="The owner of the Spinnaker docs repository")
    # Initialize parser for composed Annotator.
    Annotator.init_argument_parser(parser)

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    halyard_publisher = cls(options)
    halyard_publisher.publish_stable_halyard()

if __name__ == '__main__':
  sys.exit(HalyardPublisher.main())
