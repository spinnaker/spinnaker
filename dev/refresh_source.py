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
import sys

from spinnaker.run import check_run_and_monitor
from spinnaker.run import check_run_quick
from spinnaker.run import run_and_monitor
from spinnaker.run import run_quick


def get_repository_dir(name):
  """Determine the local directory that a given repository is in.

  We assume that refresh_source is being run in the build directory
  that contains all the repositories. Except spinnaker/ itself is not
  in the build directory so special case it.

  Args:
    name [string]: The repository name.
  """
  if name == 'spinnaker':
    return os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
  else:
    return name


class SourceRepository(
          collections.namedtuple('SourceRepository', ['name', 'owner'])):
  """Denotes a github repository.

  Attributes:
    name:  The [short] name of the repository.
    owner: The github user name owning the repository
  """
  pass


class Refresher(object):
  """Provides branch management capabilities across Spinnaker repositories.

  The Spinnaker repositories are federated across several independent
  repositories. This class provides convenient support to update local
  repositories from remote and vice-versa.

  The origin repository is specified using --github_user option. This specifies
  the github repository owner for the origin repositories. It is only relevant
  when a repository needs to be cloned to establish a local repository. The
  value 'upstream' can be used to indicate that the repository should be cloned
  from its authoritative source as opposed to another user's fork.

  When the refresher clones new repositories, it establishes an "upstream"
  remote to the authoritative repository (based on hard-coded mappings)
  unless the origin is the upstream. Upstream pulls are disabled (including
  when the origin is the upstream) and only the master branch can be pulled
  from upstream.

  If --pull_branch is used then the local repositories will pull their current
  branch from the origin repository. If a local repository does not yet exist,
  then it will be cloned from the --github_user using the branch specified
  by --pull_branch. The --pull_origin option is similar but implies that the
  branch is 'master'. This is intended to perform complete updates of the
  local repositories.

  --push_branch (or --push_master, implying 'master' branch) will push the
  local repository branch back to the origin, but only if the local repository
  is in the specified branch. This is for safety to prevent accidental pushes.
  It is assumed that multi-repository changes will have a common feature-branch
  name, and not all repositories will be affected.

  Of course, individual repositories can still be managed using explicit git
  commands. This class is intended for cross-cutting management.
  """

  __OPTIONAL_REPOSITORIES = [SourceRepository('citest', 'google')]
  __REQUIRED_REPOSITORIES = [
      SourceRepository('spinnaker', 'spinnaker'),
      SourceRepository('clouddriver', 'spinnaker'),
      SourceRepository('orca', 'spinnaker'),
      SourceRepository('front50', 'spinnaker'),
      SourceRepository('rush', 'spinnaker'),
      SourceRepository('echo', 'spinnaker'),
      SourceRepository('rosco', 'spinnaker'),
      SourceRepository('gate', 'spinnaker'),
      SourceRepository('igor', 'spinnaker'),
      SourceRepository('deck', 'spinnaker')]

  @property
  def pull_branch(self):
      """Gets the branch that we want to pull.

      This may raise a ValueError if the specification is inconsistent.
      This is determined lazily rather than at construction to be consistent
      with the push_branch property.
      """
      if self.__options.pull_origin:
          if (self.__options.pull_branch
              and self.__options.pull_branch != 'master'):
            raise ValueError(
                '--pull_origin is incompatible with --pull_branch={branch}'
                .format(branch=self.__options.pull_branch))
          return 'master'
      return self.__options.pull_branch

  @property
  def push_branch(self):
      """Gets the branch that we want to push.

      This may raise a ValueError if the specification is inconsistent.
      This is determined lazily rather than at construction because the
      option to push is not necessarily present depending on the use case.
      """
      if self.__options.push_master:
          if (self.__options.push_branch
              and self.__options.push_branch != 'master'):
            raise ValueError(
                '--push_origin is incompatible with --push_branch={branch}'
                .format(branch=self.__options.push_branch))
          return 'master'
      return self.__options.push_branch

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

  def get_remote_repository_url(self, path, which='origin'):
      """Determine the repository that a given path is from.

      Args:
        path [string]: The path to the repository
        which [string]: The remote repository name (origin or upstream).

      Returns:
        The origin url for path, or None if not a git repository.
      """
      result = run_quick('git -C {path} config --get remote.{which}.url'
                             .format(path=path, which=which),
                         echo=False)
      if result.returncode:
        return None
      return result.stdout.strip()

  def get_local_branch_name(self, name):
      """Determine which git branch a local repository is in.

      Args:
        name [string]: The repository name.

      Returns:
        The name of the branch.
      """
      result = run_quick('git -C "{dir}" rev-parse --abbrev-ref HEAD'
                         .format(dir=get_repository_dir(name)),
                         echo=False)
      if result.returncode:
        error = 'Could not determine branch: ' + result.stdout.strip()
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

  def git_clone(self, repository, owner=None):
      """Clone the specified repository

      Args:
        repository [string]: The name of the github repository (without owner).
        owner [string]: An explicit repository owner.
               If not provided use the configured options.
      """
      name = repository.name
      repository_dir = get_repository_dir(name)
      upstream_user = repository.owner
      branch = self.pull_branch
      origin_url = self.get_github_repository_url(repository, owner=owner)
      upstream_url = 'https://github.com/{upstream_user}/{name}.git'.format(
              upstream_user=upstream_user, name=name)

      # Dont echo because we're going to hide some failure.
      print 'Cloning {name} from {origin_url} -b {branch}.'.format(
          name=name, origin_url=origin_url, branch=branch)
      shell_result = run_and_monitor(
          'git clone {url} -b {branch}'.format(url=origin_url, branch=branch),
          echo=False)
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
              'FATAL: Cannot continue without required repository {name}.\n'
              '       Consider using github to fork one from {upstream}.\n'.
              format(name=name, upstream=upstream_url))
          raise SystemExit('Repository {url} not found.'.format(url=origin_url))

      if self.__options.add_upstream and origin_url != upstream_url:
          print '  Adding upstream repository {upstream}.'.format(
              upstream=upstream_url)
          check_run_quick('git -C "{dir}" remote add upstream {url}'
                              .format(dir=repository_dir, url=upstream_url),
                          echo=False)

      if self.__options.disable_upstream_push:
          which = 'upstream' if origin_url != upstream_url else 'origin'
          print '  Disabling git pushes to {which} {upstream}'.format(
              which=which, upstream=upstream_url)
          check_run_quick(
              'git -C "{dir}" remote set-url --push {which} disabled'
                  .format(dir=repository_dir, which=which),
              echo=False)

  def pull_from_origin(self, repository):
      """Pulls the current branch from the git origin.

      Args:
        repository [string]: The local repository to update.
      """
      name = repository.name
      repository_dir = get_repository_dir(name)
      if not os.path.exists(repository_dir):
          self.git_clone(repository)
          return

      print 'Updating {name} from origin'.format(name=name)
      branch = self.get_local_branch_name(name)
      if branch != self.pull_branch:
          sys.stderr.write(
              'WARNING: Updating {name} branch={branch}, *NOT* "{want}"\n'
                  .format(name=name, branch=branch, want=self.pull_branch))
      try:
        check_run_and_monitor('git -C "{dir}" pull origin {branch} --tags'
                                  .format(dir=repository_dir, branch=branch),
                              echo=True)
      except RuntimeError:
        result = check_run_and_monitor('git -C "{dir}" branch -r'
                                           .format(dir=repository_dir),
                                       echo=False)
        if result.stdout.find('origin/{branch}\n') >= 0:
          raise
        sys.stderr.write(
              'WARNING {name} branch={branch} is not known to the origin.\n'
              .format(name=name, branch=branch))

  def pull_from_upstream_if_master(self, repository):
      """Pulls the master branch fromthe upstream repository.

      This will only have effect if the local repository exists
      and is currently in the master branch.

      Args:
        repository [string]: The name of the local repository to update.
      """
      name = repository.name
      repository_dir = get_repository_dir(name)
      if not os.path.exists(repository_dir):
          self.pull_from_origin(repository)
      branch = self.get_local_branch_name(name)
      if branch != 'master':
          sys.stderr.write('Skipping {name} because it is in branch={branch}.\n'
                           .format(name=name, branch=branch))
          return

      print 'Pulling master {name} from upstream'.format(name=name)
      check_run_and_monitor('git -C "{dir}" pull upstream master --tags'
                                .format(dir=repository_dir),
                            echo=True)

  def push_to_origin_if_target_branch(self, repository):
      """Pushes the current target branch of the local repository to the origin.

      This will only have effect if the local repository exists
      and is currently in the target branch.

      Args:
        repository [string]: The name of the local repository to push from.
      """
      name = repository.name
      repository_dir = get_repository_dir(name)
      if not os.path.exists(repository_dir):
          sys.stderr.write('Skipping {name} because it does not yet exist.\n'
                               .format(name=name))
          return

      branch = self.get_local_branch_name(name)
      if branch != self.push_branch:
          sys.stderr.write(
              'Skipping {name} because it is in branch={branch}, not {want}.\n'
                  .format(name=name, branch=branch, want=self.push_branch))
          return

      print 'Pushing {name} to origin.'.format(name=name)
      check_run_and_monitor('git -C "{dir}" push origin {branch} --tags'.format(
                                dir=repository_dir, branch=self.push_branch),
                            echo=True)

  def push_all_to_origin_if_target_branch(self):
    """Push all the local repositories current target branch to origin.

    This will skip any local repositories that are not currently in the
    target branch.
    """
    all_repos = self.__REQUIRED_REPOSITORIES + self.__extra_repositories
    for repository in all_repos:
        self.push_to_origin_if_target_branch(repository)

  def pull_all_from_upstream_if_master(self):
    """Pull all the upstream master branches into their local repository.

    This will skip any local repositories that are not currently in the master
    branch.
    """
    all_repos = self.__REQUIRED_REPOSITORIES + self.__extra_repositories
    for repository in all_repos:
        self.pull_from_upstream_if_master(repository)

  def pull_all_from_origin(self):
    """Pull all the origin target branches into their local repository.

    This will skip any local repositories that are not currently in the
    target branch.
    """
    all_repos = self.__REQUIRED_REPOSITORIES + self.__extra_repositories
    for repository in all_repos:
        try:
          self.pull_from_origin(repository)
        except RuntimeError as ex:
          if repository in self.__extra_repositories and not os.path.exists(
              get_repository_dir(repository)):
              sys.stderr.write(
                   'IGNORING error "{msg}" in optional repository {name}'
                   ' because the local repository does not yet exist.\n'
                       .format(msg=ex.message, name=repository.name))
          else:
              raise

  def __determine_spring_config_location(self):
    root = '{dir}/config'.format(
        dir=os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
    home = os.path.join(os.environ['HOME'] + '/.spinnaker')
    return '{root}/,{home}/'.format(home=home, root=root)

  def write_gradle_run_script(self, repository):
      """Generate a dev_run.sh script for the local repository.

      Args:
         repository [string]: The name of the local repository to generate in.
      """
      name = repository.name
      path = '{name}/start_dev.sh'.format(name=name)

      with open(path, 'w') as f:
          f.write("""#!/bin/bash
d=$(dirname "$0")
cd "$d"
LOG_DIR=${{LOG_DIR:-../logs}}

DEF_SYS_PROPERTIES="-Dspring.config.location='{spring_location}'"
bash -c "(./gradlew $DEF_SYS_PROPERTIES $@ > '$LOG_DIR/{name}.log') 2>&1\
 | tee -a '$LOG_DIR/{name}.log' >& '$LOG_DIR/{name}.err' &"
""".format(name=name,
           spring_location=self.__determine_spring_config_location()))
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
d=$(dirname "$0")
cd "$d"
LOG_DIR=${{LOG_DIR:-../logs}}

if [[ node_modules -ot .git ]]; then
  # Update npm, otherwise assume nothing changed and we're good.
  npm install >& "$LOG_DIR/deck.log"
else
  echo "deck npm node_modules looks up to date already."
fi

# Append to the log file we just started.
bash -c "(npm start >> '$LOG_DIR/{name}.log') 2>&1\
 | tee -a '$LOG_DIR/{name}.log' >& '$LOG_DIR/{name}.err' &"
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

      # Note we only push target branches to origin specified by --push_branch
      # To push another branch, you must explicitly push it with git
      # (or another invocation).
      parser.add_argument('--push_master', action='store_true',
                          help='Push the current branch to origin if it is'
                          ' master. This is the same as --push_branch=master.')
      parser.add_argument('--nopush_master', dest='push_master',
                          action='store_false')

      parser.add_argument('--push_branch', default='',
                          help='If specified and the local repository is in'
                               ' this branch then push it to the origin'
                               ' repository. Otherwise do not push it.')

  @classmethod
  def init_argument_parser(cls, parser):
      """Initiaize command-line arguments."""
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
                          help='Refresh the local branch from the origin.'
                               ' If cloning, then clone the master branch.'
                               ' See --pull_branch for a more general option.')
      parser.add_argument('--nopull_origin', dest='pull_origin',
                          action='store_false')

      parser.add_argument('--pull_branch', default='',
                          help='Refresh the local branch from the origin if'
                               ' it is in the specified branch,'
                               ' otherwise skip it.'
                               ' If cloning, then clone this branch.')

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

    refresher = cls(options)
    in_repository_url = refresher.get_remote_repository_url('.')
    if in_repository_url:
      sys.stderr.write(
          'ERROR: You cannot run this script from within a local repository.\n'
          ' This directory is from "{url}".\n'
          ' Did you intend to be in the parent directory?\n'
        .format(url=in_repository_url))
      return -1

    try:
      # This is ok. Really we want to look for an exception validating these
      # properties so we can fail with a friendly error rather than stack.
      if (refresher.pull_branch != refresher.push_branch
          and refresher.pull_branch and refresher.push_branch):
        sys.stderr.write(
            'WARNING: pulling branch {pull} and pushing branch {push}'
                .format(pull=refresher.pull_branch,
                        push=refresher.push_branch))
    except Exception as ex:
      sys.stderr.write('FAILURE: {0}\n'.format(ex.message))
      return -1

    nothing = True
    if options.pull_upstream:
        nothing = False
        refresher.pull_all_from_upstream_if_master()
    if refresher.push_branch:
        nothing = False
        refresher.push_all_to_origin_if_target_branch()
    if refresher.pull_branch:
        nothing = False
        refresher.pull_all_from_origin()
    refresher.update_spinnaker_run_scripts()

    if nothing:
      sys.stderr.write('No pull/push options were specified.\n')
    else:
      print 'DONE'
    return 0


if __name__ == '__main__':
  sys.exit(Refresher.main())
