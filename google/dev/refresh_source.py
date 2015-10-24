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
import re
import sys

from spinnaker.run import run_and_monitor
from spinnaker.run import run_quick
from spinnaker.run import check_run_quick


class SourceRepository(
          collections.namedtuple('SourceRepository', ['name', 'owner'])):
  """Denotes a github repository.

  Attributes:
    name:  The [short] name of the repository.
    owner: The github user name owning the repository
  """
  pass


class Refresher(object):
  __OPTIONAL_REPOSITORIES = [SourceRepository('citest', 'google')]
  __REQUIRED_REPOSITORIES = [
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
      self.__extra_repositories = self.__OPTIONAL_REPOSITORIES
      if options.extra_repos:
        for extra in options.extra_repos.split(','):
          pair = extra.split('=')
          if len(pair) != 2:
            raise ValueError(
                'Invalid --extra_repos value "{extra}"'.format(extra=extra))
          self.__extra_repositories.append(SourceRepository(pair[0], pair[1]))

  def get_branch_name(self, name):
      """Determine which git branch a local repository is in.

      Args:
        name [string]: The repository name.

      Returns:
        The name of the branch.
      """
      result = run_quick('git -C {dir} rev-parse --abbrev-ref HEAD'
                         .format(dir=name),
                         echo=True)
      if result.returncode:
        error = 'Could not determine branch: ' + result.stdout
        raise RuntimeError(error)
      return result.stdout.strip()

  def get_github_repository_url(self, repository, owner=None):
      """Determine the URL for a given github repository.

      Args:
        respository [string]: The upstream SourceRepository.
        owner [string]: The explicit owner for the repository we want.
               If not provided then use the github_user in the bound options.
      """

      user = owner or self.__options.github_user
      if not user:
          raise ValueError('No --github_user specified.')

      if user == 'default' or user == 'upstream':
            user = repository.owner
      url_pattern = ('https://github.com/{user}/{name}.git'
                     if self.__options.use_https
                     else 'git@github.com:{user}/{name}.git')
      return url_pattern.format(user=user, name=repository.name)

  def git_clone(self, repository, required=True, owner=None):
      """Clone the specified repository

      Args:
        repository [string]: The name of the github repository (without owner).
        required [bool]: Whether the clone must succeed or not.
        owner [string]: An explicit repository owner.
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
      shell_result = run_and_monitor('git clone ' + origin_url, echo=False)
      if not shell_result.returncode:
          if shell_result.stdout:
              print shell_result.stdout
      else:
          if repository in self.__extra_repositories:
             sys.stderr.write('WARNING: Missing optional repository {name}.\n'
                              .format(name=name))
             sys.stderr.write('         Continue on without it.\n')
             return
          sys.stderr.write(shell_result.stderr or shell_result.stdout)
          sys.stderr.write(
              'FATAL: Cannot continue without required'
              ' repository {name}.\n'
              '       Consider using github to fork one from {upstream}.\n'.
              format(name=name, upstream=upstream_url))
          raise SystemExit('Repository {url} not found.'.format(url=origin_url))

      if self.__options.add_upstream and origin_url != upstream_url:
          print '  Adding upstream repository {upstream}.'.format(
              upstream=upstream_url)
          check_run_quick('git -C {dir} remote add upstream {url}'
                          .format(dir=name, url=upstream_url),
                          echo=False)

      if self.__options.disable_upstream_push:
          which = 'upstream' if origin_url != upstream_url else 'origin'
          print '  Disabling git pushes to {which} {upstream}'.format(
              which=which, upstream=upstream_url)
          check_run_quick(
              'git -C {dir} remote set-url --push {which} disabled'
              .format(dir=name, which=which),
              echo=False)

  def pull_from_origin(self, repository):
      """Pulls the current branch from the git origin.

      Args:
        repository [string]: The local repository to update.
      """
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
      check_run_quick('git -C {dir} pull origin {branch}'
                      .format(dir=name, branch=branch),
                      echo=True)

  def pull_from_upstream_if_master(self, repository):
      """Pulls the master branch fromthe upstream repository.

      This will only have effect if the local repository exists
      and is currently in the master branch.

      Args:
        repository [string]: The name of the local repository to update.
      """
      name = repository.name
      if not os.path.exists(name):
          self.pull_from_origin(repository)
      branch = self.get_branch_name(name)
      if branch != 'master':
          sys.stderr.write('Skipping {name} because it is in branch={branch}.\n'
                           .format(name=name, branch=branch))
          return

      print 'Pulling master {name} from upstream'.format(name=name)
      check_run_quick('git -C {name} pull upstream master'
                      .format(name=name),
                      echo=True)

  def push_to_origin_if_master(self, repository):
      """Pushes the current master branch of the local repository to the origin.

      This will only have effect if the local repository exists
      and is currently in the master branch.

      Args:
        repository [string]: The name of the local repository to push from.
      """
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
      check_run_quick('git -C {dir} push origin master'.format(dir=name),
                      echo=True)

  def push_all_to_origin_if_master(self):
    """Push all the local repositories current master branch to origin.

    This will skip any local repositories that are not currently in the master
    branch.
    """
    all_repos = self.__REQUIRED_REPOSITORIES + self.__extra_repositories
    for repository in all_repos:
        self.push_to_origin_if_master(repository)

  def pull_all_from_upstream_if_master(self):
    """Pull all the upstream master branches into their local repository.

    This will skip any local repositories that are not currently in the master
    branch.
    """
    all_repos = self.__REQUIRED_REPOSITORIES + self.__extra_repositories
    for repository in all_repos:
        self.pull_from_upstream_if_master(repository)

  def pull_all_from_origin(self):
    """Pull all the origin master branches into their local repository.

    This will skip any local repositories that are not currently in the master
    branch.
    """
    all_repos = self.__REQUIRED_REPOSITORIES + self.__extra_repositories
    for repository in all_repos:
        self.pull_from_origin(repository)

  def __determine_spring_config_location(self):
    root = '{dir}/config'.format(
        dir=os.path.abspath(os.path.join(os.path.dirname(sys.argv[0]), '..')))
    home = os.path.join(os.environ['HOME'] + '/.spinnaker')
    return ('{root}/spinnaker.yml'
            ',{home}/spinnaker-local.yml'
            ',{root}/'
            ',{home}/'
            .format(home=home, root=root))

  def write_gradle_run_script(self, repository):
      """Generate a dev_run.sh script for the local repository.

      Args:
         repository [string]: The name of the local repository to generate in.
      """
      name = repository.name
      path = '{name}/start_dev.sh'.format(name=name)

      with open(path, 'w') as f:
          f.write("""#!/bin/bash
cd $(dirname $0)
LOG_DIR=${{LOG_DIR:-../logs}}

DEF_SYS_PROPERTIES=""
if [[ -f $HOME/.spinnaker/spinnaker-local.yml ]]; then
   DEF_SYS_PROPERTIES="-Dspring.config.location='{spring_location}'"
fi
bash -c "(./gradlew $DEF_SYS_PROPERTIES $@ > $LOG_DIR/{name}.log) 2>&1\
 | tee -a $LOG_DIR/{name}.log >& $LOG_DIR/{name}.err &"
""".format(name=name, spring_location=self.__determine_spring_config_location()))
      os.chmod(path, 0777)

  def write_deck_run_script(self, repository):
      """Generate a dev_run.sh script for running deck locally.

      Args:
        repository [string]: The name of the local repository to generate in.
      """
      name = repository.name
      path = '{name}/start_dev.sh'.format(name=name)
      with open(path, 'w') as f:
          f.write("""#!/bin/bash
cd $(dirname $0)
LOG_DIR=${{LOG_DIR:-../logs}}

if [[ node_modules -ot .git ]]; then
  # Update npm, otherwise assume nothing changed and we're good.
  npm install >& $LOG_DIR/deck.log
else
  echo "deck npm node_modules looks up to date already."
fi

# Append to the log file we just started.
bash -c "(npm start >> $LOG_DIR/{name}.log) 2>&1\
 | tee -a $LOG_DIR/{name}.log >& $LOG_DIR/{name}.err &"
""".format(name=name))
      os.chmod(path, 0777)

  def update_spinnaker_run_scripts(self):
    """Regenerate the local dev_run.sh script for each local repository."""
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

      parser.add_argument(
        '--extra_repos', default=None,
        help='A comma-delimited list of name=owner optional repositories.'
             'name is the repository name,'
             ' owner is the authoritative github user name owning it.'
             ' The --github_user will still be used to determine the origin.')

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
