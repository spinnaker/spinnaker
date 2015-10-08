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

import argparse
import collections
import os
import subprocess
import sys

from install.install_utils import run
from install.install_utils import run_or_die_no_result


SourceRepository = collections.namedtuple('SourceRepository', ['name', 'owner'])
class Refresher(object):
  __OPTIONAL_REPOSITORIES = [SourceRepository('citest', 'google')]
  __REQUIRED_REPOSITORIES = [
      SourceRepository('gce-kms', 'spinnaker'),
      SourceRepository('clouddriver', 'spinnaker'),
      SourceRepository('orca', 'spinnaker'),
      SourceRepository('front50', 'spinnaker'),
      SourceRepository('rush', 'spinnaker'),
      SourceRepository('echo', 'spinnaker'),
      SourceRepository('rosco', 'spinnaker'),
      SourceRepository('gate', 'spinnaker'),
      SourceRepository('igor', 'spinnaker'),
      SourceRepository('deck', 'spinnaker')]

  def __init__(self, options):
      self.__options = options

  def get_branch_name(self, name):
    p = subprocess.Popen('git -C {dir} rev-parse --abbrev-ref HEAD'
                         .format(dir=name),
                         shell=True,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = p.communicate()
    if p.returncode:
      sys.stderr.write('Could not determine branch: ' + stderr + '\n')
      raise RuntimeError(stderr)
    return stdout.strip()

  def get_github_repository_url(self, repository, owner=None):
      """Determine the URL for a given github repository.

      Args:
        respository: The upstream SourceRepository.
        owner: The explicit owner for the repository we want. If not provided
               then use the github_user in the bound options.
      """

      user = owner or self.__options.github_user
      if not user:
          raise ValueError('No --github_user specified.')

      if user == 'default':
          user = repository.owner
      url_pattern = ('https://github.com/{user}/{name}.git'
                     if self.__options.use_https
                     else 'git@github.com:{user}/{name}.git')
      return url_pattern.format(user=user, name=repository.name)

  def git_clone(self, repository, required=True, owner=None):
      """Clone the specified repository

      Args:
        repository: The name of the github repository (without owner).
        required: Whether the clone must succeed or not.
        owner: An explicit repository owner.
               If not provided use the configured options.
      """
      name = repository.name
      upstream_user = repository.owner
      origin_url = self.get_github_repository_url(repository, owner=owner)
      upstream_url = 'https://github.com/{upstream_user}/{name}.git'.format(
              upstream_user=upstream_user, name=name)

      # Dont echo because we're going to hide some failure.
      print 'Cloning {name} from {origin_url}.'.format(
          name=name, origin_url=origin_url)
      code, stdout, stderr = run('git clone ' + origin_url, echo=False)
      if not code:
          if stdout:
              print stdout
      else:
          if repository in self.__OPTIONAL_REPOSITORIES:
             sys.stderr.write('WARNING: Missing optional repository {name}.\n'
                              .format(name=name))
             sys.stderr.write('         Continue on without it.\n')
             return
          sys.stderr.write(stderr or stdout)
          sys.stderr.write(
              'FATAL: Cannot continue without required'
              ' repository {name}.\n'
              '       Consider using github to fork one from {upstream}.\n'.
              format(name=name, upstream=upstream_url))
          raise SystemExit('Repository {url} not found.'.format(url=origin_url))

      if self.__options.add_upstream and origin_url != upstream_url:
          print '  Adding upstream repository for {origin}.'.format(
              origin=origin_url)
          run_or_die_no_result('git -C {dir} remote add upstream {url}'
                               .format(dir=name, url=upstream_url),
                               echo=False)

      if self.__options.disable_upstream_push:
          which = 'upstream' if origin_url != upstream_url else 'origin'
          print '  Disabling git pushes to {which}'.format(which=which)
          run_or_die_no_result(
              'git -C {dir} remote set-url --push {which} disabled'
              .format(dir=name, which=which),
              echo=False)

  def pull_from_origin(self, repository):
      name = repository.name
      owner = repository.owner
      if not os.path.exists(name):
          self.git_clone(repository)
          return

      print 'Updating {name} from origin'.format(name=name)
      branch = self.get_branch_name(name)
      if branch != 'master':
          sys.stderr.write(
              'WARNING: Updating {name} branch={branch}, *NOT* "master"\n'
              .format(name=name, branch=branch))
      run_or_die_no_result('git -C {dir} pull origin {branch}'
                           .format(dir=name, branch=branch),
                           echo=True)

  def pull_from_upstream_if_master(self, repository):
      name = repository.name
      if not os.path.exists(name):
          self.pull_from_origin(repository)
      branch = self.get_branch_name(name)
      if branch != 'master':
          sys.stderr.write('Skipping {name} because it is in branch={branch}.\n'
                           .format(name=name, branch=branch))
          return

      print 'Pulling master {name} from upstream'.format(name=name)
      run_or_die_no_result('git -C {name} pull upstream master'
                           .format(name=name),
                           echo=True)

  def push_to_origin_if_master(self, repository):
      name = repository.name
      if not os.path.exists(name):
          sys.stderr.write('Skipping {name} because it does not yet exist.\n'
                           .format(name=name))
          return

      branch = self.get_branch_name(name)
      if branch != 'master':
          sys.stderr.write('Skipping {name} because it is in branch={branch}.\n'
                           .format(name=name, branch=branch))
          return

      print 'Pushing {name} to origin'.format(name=name)
      run_or_die_no_result('git -C {dir} push origin master'.format(dir=name),
                           echo=True)

  def push_all_to_origin_if_master(self):
    all_repos = self.__REQUIRED_REPOSITORIES + self.__OPTIONAL_REPOSITORIES
    for repository in all_repos:
        self.push_to_origin_if_master(repository)

  def pull_all_from_upstream_if_master(self):
    all_repos = self.__REQUIRED_REPOSITORIES + self.__OPTIONAL_REPOSITORIES
    for repository in all_repos:
        self.pull_from_upstream_if_master(repository)

  def pull_all_from_origin(self):
    all_repos = self.__REQUIRED_REPOSITORIES + self.__OPTIONAL_REPOSITORIES
    for repository in all_repos:
        self.pull_from_origin(repository)

  def write_gradle_run_script(self, repository):
      name = repository.name
      path = '{name}/start_dev.sh'.format(name=name)
      with open(path, 'w') as f:
          f.write("""#!/bin/bash
cd $(dirname $0)
LOG_DIR=${{LOG_DIR:-../logs}}

bash -c "(./gradlew > $LOG_DIR/{name}.log) 2>&1\
 | tee -a $LOG_DIR/{name}.log >& $LOG_DIR/{name}.err &"
""".format(name=name))
      os.chmod(path, 0777)

  def write_deck_run_script(self, repository):
      name = repository.name
      path = '{name}/start_dev.sh'.format(name=name)
      with open(path, 'w') as f:
          f.write("""#!/bin/bash
cd $(dirname $0)
LOG_DIR=${{LOG_DIR:-../logs}}

npm install >& $LOG_DIR/{name}.log

# Append to the log file we just started.
bash -c "(npm start >> $LOG_DIR/{name}.log) 2>&1\
 | tee -a $LOG_DIR/{name}.log >& $LOG_DIR/{name}.err &"
""".format(name=name))
      os.chmod(path, 0777)

  def update_spinnaker_run_scripts(self):
    for repository in self.__REQUIRED_REPOSITORIES:
      name = repository.name
      if not os.path.exists(name):
        continue

      if name == 'deck':
        self.write_deck_run_script(repository)
      else:
        self.write_gradle_run_script(repository)

  @classmethod
  def init_extra_argument_parser(cls, parser):
      """Initialize additional arguments for managing remote repositories.

      This is to sync the origin and upstream repositories. The intent
      is to ultimately sync the origin from the upstream repository, but
      this might be in two steps so the upstream can be verified [again]
      before pushing the changes to the origin.
      """

      # Note that we only pull the master branch from upstream.
      # Pulling other branches dont normally make sense.
      parser.add_argument('--pull_upstream', default=False,
                          action='store_true',
                          help='If the local branch is master, then refresh it'
                               ' from the upstream repository.'
                               ' Otherwise leave as is.')
      parser.add_argument('--nopull_upstream',
                          dest='pull_upstream',
                          action='store_false')

      parser.add_argument('--refresh_master_from_upstream',
                          dest='pull_upstream',
                          help='DEPRECATED '
                               'If the local branch is master, then refresh it'
                               ' from the upstream repository.'
                               ' Otherwise leave as is.')
      parser.add_argument('--norefresh_master_from_upstream',
                          help='DEPRECATED',
                          dest='pull_upstream',
                          action='store_false')

      # Note we only push master branches to origin.
      # To push another branch, you must explicitly push it with git.
      # Perhaps it could make sense to coordinate branches with a common name
      # across multiple repositories to push a conceptual change touching
      # multiple repositories, but for now we are being conservative with
      # what we push.
      parser.add_argument('--push_master', default=False,
                          action='store_true',
                          help='If the local branch is master then push it to'
                               ' the origin repository. Otherwise do not.')
      parser.add_argument('--nopush_master',
                          dest='push_master')

      parser.add_argument('--push_master_to_origin', default=False,
                          dest='push_master',
                          action='store_true',
                          help='DEPRECATED '
                               'If the local branch is master then push it to'
                               ' the origin repository. Otherwise do not.')
      parser.add_argument('--nopush_master_to_origin',
                          help='DEPRECATED',
                          dest='push_master_to_origin')

  @classmethod
  def init_argument_parser(cls, parser):
      parser.add_argument('--use_https', default=True, action='store_true',
                          help='Use https when cloning github repositories.')
      parser.add_argument('--use_ssh', dest='use_https', action='store_false',
                          help='Use SSH when cloning github repositories.')

      parser.add_argument('--add_upstream', default=True,
                          action='store_true',
                          help='Add upstream repository when cloning.')
      parser.add_argument('--noadd_upstream', dest='add_upstream',
                          action='store_false')

      parser.add_argument('--disable_upstream_push', default=True,
                          action='store_true',
                          help='Disable future pushes to the upstream'
                               ' repository when cloning a repository.')
      parser.add_argument('--nodisable_upstream_push',
                          dest='disable_upstream_push',
                          action='store_false')

      parser.add_argument('--pull_origin', default=False,
                          action='store_true',
                          help='Refresh the local branch from the origin.')
      parser.add_argument('--nopull_origin', dest='pull_origin',
                          action='store_false')

      parser.add_argument('--refresh_from_origin',
                          dest='pull', action='store_true',
                          help='DEPRECATED '
                               'Refresh the local branch from the origin.')
      parser.add_argument('--norefresh_from_origin', dest='pull',
                          help='DEPRECATED',
                          action='store_false')

      parser.add_argument('--github_user', default=None,
                          help='Pull from this github user\'s repositories.'
                               ' If the user is "default" then use the'
                               ' authoritative (upstream) repository.')

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    cls.init_extra_argument_parser(parser)
    options = parser.parse_args()

    builder = cls(options)
    nothing = True
    if options.pull_upstream:
        nothing = False
        builder.pull_all_from_upstream_if_master()
    if options.push_master:
        nothing = False
        builder.push_all_to_origin_if_master()
    if options.pull_origin:
        nothing = False
        builder.pull_all_from_origin()
    builder.update_spinnaker_run_scripts()

    if nothing:
      sys.stderr.write('No pull/push options were specified.\n')
    else:
      print 'DONE'


if __name__ == '__main__':
  Refresher.main()
