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

"""Coordinates a global build of a Spinnaker "release".

The term "release" here is more of an encapsulated build. This is not
an official release. It is meant for developers.

The gradle script does not yet coordinate a complete build, so
this script fills that gap for the time being. It triggers all
the subsystem builds and then publishes the resulting artifacts.

Publishing is typically to bintray for debian and redhat packages and
a docker repository for containers.

Usage:
  export BINTRAY_USER=
  export BINTRAY_KEY=

  # subject/repository are the specific bintray repository
  # owner and name components that specify the repository you are updating.
  # The repository must already exist, but can be empty.
  BINTRAY_REPOSITORY=subject/repository

  # cd <build root containing subsystem subdirectories>
  # this is where you ran refresh_source.sh from

  ./spinnaker/dev/build_release.sh --bintray_repo=$BINTRAY_REPOSITORY
"""

import argparse
import datetime
import fnmatch
import glob
import json
import os
import multiprocessing
import multiprocessing.pool
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time

import refresh_source

from google.cloud import pubsub
from spinnaker.run import run_quick

SUBSYSTEM_LIST = ['clouddriver', 'orca', 'front50',
                  'echo', 'rosco', 'gate', 'igor', 'fiat', 'deck', 'spinnaker']
ADDITIONAL_SUBSYSTEMS = ['spinnaker-monitoring', 'halyard']

VALID_PLATFORMS = ['debian', 'redhat']

GCB_BUILD_STATUS_TIMEOUT = 1200


def determine_project_root():
  return os.path.abspath(os.path.dirname(__file__) + '/..')

def determine_modules_with_debians(gradle_root):
  files = glob.glob(os.path.join(gradle_root, '*', 'build', 'debian', 'control'))
  dirs = [os.path.dirname(os.path.dirname(os.path.dirname(file))) for file in files]
  if os.path.exists(os.path.join(gradle_root, 'build', 'debian', 'control')):
    dirs.append(gradle_root)
  return dirs

def determine_modules_with_redhats(gradle_root):
  dirs = []
  for dirname, subdirs, files in os.walk(gradle_root):
    for fname in files:
      if fnmatch.fnmatch(fname, '*.rpm'):
        dirs.append( os.path.dirname(os.path.dirname(dirname)) )
  return dirs

def determine_package_version(platform, gradle_root):
  if platform == 'debian':
    root = determine_modules_with_debians(gradle_root)
    if not root: return None
    with open(os.path.join(root[0], 'build', 'debian', 'control')) as f:
      content = f.read()
    match = re.search('(?m)^Version: (.*)', content)
    return match.group(1)

  elif platform == 'redhat':
    root = determine_modules_with_redhats(gradle_root)
    if not root: return None
    comp = os.path.basename(os.path.normpath(gradle_root))
    build_root = os.getcwd()
    version_file = '{}-rpm-version.txt'.format(comp)
    version = open(version_file, 'r').read().rstrip()
    if re.match('-$', version): version = version + '0'
    return version

def run_shell_and_log(cmd_list, logfile, cwd=None):
  for cmd in cmd_list:
    parsed = shlex.split(cmd)
    log = None
    if not os.path.exists(logfile):
      log = open(logfile, 'w')
    else:
      log = open(logfile, 'a')
    log.write('Executing command: {}\n---\n'.format(cmd))
    subprocess.check_call(parsed, stdout=log, stderr=log, cwd=cwd)
    log.write('\n---\nFinished executing command: {}'.format(cmd))
    if log:
      log.close()


class BuildFailure(object):
  def __init__(self, component, exception):
    self.__component = component
    self.__exception = exception

  @property
  def component(self):
    return self.__component

  @property
  def exception(self):
    return self.__exception


class Builder(object):
  """Knows how to coordinate a Spinnaker release."""

  def __init__(self, options, build_number=None, container_builder=None, sync_branch=None):
      self.__package_list = []
      self.__build_failures = []
      self.__background_processes = []

      os.environ['NODE_ENV'] = os.environ.get('NODE_ENV', 'dev')
      self.__build_number = build_number or os.environ.get('BUILD_NUMBER') or '{:%Y%m%d%H%M%S}'.format(datetime.datetime.utcnow())
      self.__gcb_service_account = options.gcb_service_account
      self.__options = options
      if (container_builder and container_builder not in ['gcb', 'docker', 'gcb-trigger']):
        raise ValueError('Invalid container_builder. Must be empty, "gcb" or "docker"')

      self.refresher = refresh_source.Refresher(options)
      if options.bintray_repo and options.build:
        self.__verify_bintray()

      self.__project_dir = determine_project_root()
      self.__sync_branch = sync_branch

  def determine_gradle_root(self, name):
      if self.__options.platform == "debian":
        gradle_root = (name if name != 'spinnaker'
                else os.path.join(self.__project_dir, 'experimental/buildDeb'))
      gradle_root = name if name != 'spinnaker' else self.__project_dir
      return gradle_root

  def start_deb_build(self, name):
    """Start a subprocess to build and publish the designated component.

    This function runs a gradle 'candidate' task using the last git tag as the
    package version and the Bintray configuration passed through arguments. The
    'candidate' task release builds the source, packages the debian and jar
    files, and publishes those to the respective Bintray '$org/$repository'.

    The naming of the gradle task is a bit unfortunate because of the
    terminology used in the Spinnaker product release process. The artifacts
    produced by this script are not 'release candidate' artifacts, they are
    pre-validation artifacts. Maybe we can modify the task name at some point.

    The gradle 'candidate' task throws a 409 if the package we are trying to
    publish already exists. We'll publish unique package versions using build
    numbers. These will be transparent to end users since the only meaningful
    version is the Spinnaker product version.

    We will use -Prelease.useLastTag=true and ensure the last git tag is the
    version we want to use. This tag has to be of the form 'X.Y.Z-$build' or
    'vX.Y.Z-$build for gradle to use the tag as the version. This script will
    assume that the source has been properly tagged to use the latest tag as the
    package version for each component.

    Args:
      name [string]: Name of the subsystem repository.
    """
    self.__debian_build(name, self.__options, self.__build_number, self.determine_gradle_root(name))

  @classmethod
  def __debian_build(cls, name, options, build_number, gradle_root):
    jarRepo = options.jar_repo
    parts = options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo>')
    org, packageRepo = parts[0], parts[1]
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']

    target = 'candidate'
    extra_args = [
      '--stacktrace',
      '-Prelease.useLastTag=true',
      '-PbintrayPackageBuildNumber={number}'.format(number=build_number),
      '-PbintrayOrg="{org}"'.format(org=org),
      '-PbintrayPackageRepo="{repo}"'.format(repo=packageRepo),
      '-PbintrayJarRepo="{jarRepo}"'.format(jarRepo=jarRepo),
      '-PbintrayKey="{key}"'.format(key=bintray_key),
      '-PbintrayUser="{user}"'.format(user=bintray_user)
    ]

    if options.maven_custom_init_file:
      extra_args.append('-I {}'.format(options.maven_custom_init_file))

    if options.info_gradle:
      extra_args.append('--info')
    if options.debug_gradle:
      extra_args.append('--debug')

    if options.gradle_cache_path:
      extra_args.append('--gradle-user-home={}'.format(options.gradle_cache_path))

    if (not options.run_unit_tests or
            (name == 'deck' and not 'CHROME_BIN' in os.environ)):
      extra_args.append('-x test')

    if name == 'orca' and not options.run_unit_tests:
      extra_args.append('-x junitPlatformTest')

    if name == 'halyard':
      extra_args.append('-PbintrayPackageDebDistribution=trusty-nightly')
    else: 
      extra_args.append('-PbintrayPackageDebDistribution=trusty,xenial')

    cmds = [
      './gradlew {extra} {target}'.format(extra=' '.join(extra_args), target=target)
    ]
    logfile = '{name}-debian-build.log'.format(name=name)
    if os.path.exists(logfile):
      os.remove(logfile)
    run_shell_and_log(cmds, logfile, cwd=gradle_root)

  def start_rpm_build(self, name):
    """Start a subprocess to build and publish the designated component.

    This function runs a gradle 'buildRpm' task using the last git tag as the
    package version and the Bintray configuration passed through arguments. The
    'buildRpm' task release builds the source, packages the redhat and jar
    files, and publishes those to the respective Bintray '$org/$repository'.

    The naming of the gradle task is a bit unfortunate because of the
    terminology used in the Spinnaker product release process. The artifacts
    produced by this script are not 'release candidate' artifacts, they are
    pre-validation artifacts. Maybe we can modify the task name at some point.

    The gradle 'buildRpm' task throws a 409 if the package we are trying to
    publish already exists. We'll publish unique package versions using build
    numbers. These will be transparent to end users since the only meaningful
    version is the Spinnaker product version.

    We will use -Prelease.useLastTag=true and ensure the last git tag is the
    version we want to use. This tag has to be of the form 'X.Y.Z-$build' or
    'vX.Y.Z-$build for gradle to use the tag as the version. This script will
    assume that the source has been properly tagged to use the latest tag as the
    package version for each component.

    Args:
      name [string]: Name of the subsystem repository.
    """
    gradle_root = self.determine_gradle_root(name)
    self.__redhat_build(name, self.__options, self.__build_number, gradle_root)

  @classmethod
  def __redhat_build(cls, name, options, build_number, gradle_root):
    jarRepo = options.jar_repo
    parts = options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo>')
    org, packageRepo = parts[0], parts[1]
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']

    target = 'buildRpm'
    extra_args = [
      '--stacktrace',
      '-Prelease.useLastTag=true',
      '-PbintrayPackageBuildNumber={number}'.format(number=build_number)
    ]

    if options.debug_gradle:
      extra_args.append('--debug')

    if options.gradle_cache_path:
      extra_args.append('--gradle-user-home={}'.format(options.gradle_cache_path))

    if (not options.run_unit_tests or
            (name == 'deck' and not 'CHROME_BIN' in os.environ)):
      extra_args.append('-x test')

    if not options.run_unit_tests and name == 'orca':
      extra_args.append('-x junitPlatformTest')
      extra_args.append('-x generateHtmlTestReports')

    # Currently spinnaker is in a separate location
    cmds = [
      './gradlew {extra} {target}'.format(extra=' '.join(extra_args), target=target)
    ]
    logfile = '{name}-rhel-build.log'.format(name=name)
    if os.path.exists(logfile):
      os.remove(logfile)
    run_shell_and_log(cmds, logfile, cwd=gradle_root)

  def start_container_build(self, name):
    """Start a subprocess to build a container image of the subsystem.

    Uses either Google Container Builder or Docker with configuration files
    produced during BOM generation to build the container images. The
    configuration files are assumed to be in the parent directory of the
    subsystem's Gradle root.

    Args:
      name [string]: Name of the subsystem repository.
    """
    gradle_root = self.determine_gradle_root(name)
    if self.__options.container_builder == 'gcb':
      self.__gcb_build(name, gradle_root, self.__options.gcb_service_account, self.__options.gcb_project)
    elif self.__options.container_builder == 'gcb-trigger':
      self.__gcb_trigger_build(name,
                               gradle_root,
                               self.__options.gcb_service_account,
                               self.__options.gcb_service_account_json,
                               self.__options.gcb_project,
                               self.__options.gcb_mirror_base_url,
                               self.__sync_branch)
    elif self.__options.container_builder == 'docker':
      self.__docker_build(name, gradle_root)
    else:
      raise NotImplemented(
          'container_builder="{0}"'.format(self.__options.container_builder))

  def start_jar_build(self, name):
    """Start a subprocess to build a JAR for the given subcomponent

    Relies on gradle's installDist task to produce build artifacts to be
    packaged into an installable zip file.

    Args:
      name [string]: Name of the subsystem repository.
    """
    gradle_root = self.determine_gradle_root(name)
    self.__jar_build(name, gradle_root)

  @classmethod
  def __gcb_build(cls, name, gradle_root, gcb_service_account, gcb_project):
    # Local .gradle dir stomps on GCB's .gradle directory when the gradle
    # wrapper is installed, so we need to delete the local one.
    # The .gradle dir is transient and will be recreated on the next gradle
    # build, so this is OK.
    gradle_cache = '{name}/.gradle'.format(name=name)
    if os.path.isdir(gradle_cache):
      # Tell rmtree to delete the directory even if it's non-empty.
      shutil.rmtree(gradle_cache)
    cmds = [
      ('gcloud container builds submit'
       ' --account={account} --project={project} --config="../{name}-gcb.yml" .'
       .format(name=name, account=gcb_service_account, project=gcb_project))
    ]
    logfile = '{name}-gcb-build.log'.format(name=name)
    if os.path.exists(logfile):
      os.remove(logfile)
    run_shell_and_log(cmds, logfile, cwd=gradle_root)

  @classmethod
  def __gcb_trigger_build(cls, name, gradle_root, gcb_service_account, gcb_service_account_json, gcb_project, mirror_base_url, sync_branch):
    logfile = '{name}-gcb-triggered-build.log'.format(name=name)
    tag = cls.__tag_gcb_mirror(name, mirror_base_url, gradle_root, sync_branch, logfile)
    subscription = cls.__configure_gcb_pubsub(name, gcb_service_account_json)
    cls.__listen_gcb_build_status(name, subscription, tag, gcb_project, gcb_service_account, logfile)

  @classmethod
  def __tag_gcb_mirror(cls, name, mirror_base_url, gradle_root, sync_branch, logfile):
    add_mirror_cmds = [
      'git remote add mirror {base_url}/{name}.git'.format(base_url=mirror_base_url, name=name),
      'git fetch mirror'
    ]
    run_shell_and_log(add_mirror_cmds, logfile, cwd=gradle_root)

    all_remote_branches = run_quick('git -C {name} branch -r'.format(name=name),
                                    echo=False).stdout.strip().splitlines()
    checkout_cmd = ''
    print all_remote_branches
    if 'mirror/{}'.format(sync_branch) in all_remote_branches:
      checkout_cmd = 'git checkout mirror/{branch}'.format(branch=sync_branch)
    else:
      checkout_cmd = 'git checkout {branch}'.format(branch=sync_branch)

    tag = run_quick('cat {name}-gcb-trigger.yml'.format(name=name), echo=False).stdout.strip()
    cmds = [
      checkout_cmd,
      'git merge origin/{branch}'.format(branch=sync_branch),
      'git push mirror {branch}'.format(branch=sync_branch),
      'git push mirror {tag}'.format(name=name, tag=tag)
    ]
    if os.path.exists(logfile):
      os.remove(logfile)
    run_shell_and_log(cmds, logfile, cwd=gradle_root)
    return tag

  @classmethod
  def __configure_gcb_pubsub(cls, name, gcb_service_account_json):
    pubsub_client = pubsub.Client.from_service_account_json(gcb_service_account_json)
    topic = pubsub_client.topic('cloud-builds') # GCB creates a topic named this automatically.
    print 'Creating subscription: cloud-builds-{}'.format(name)
    subscription = topic.subscription('cloud-builds-{name}'.format(name=name))
    subscription.create()
    if not subscription.exists():
      raise LookupError('GCB pubsub subscription creation for subscription id {} failed.'.format(subscription.name))
    return subscription

  @classmethod
  def __listen_gcb_build_status(cls, name, subscription, tag, gcb_project, gcb_service_account, logfile):
    """Poll Google Cloud Pubsub for the GCB build status.
    """
    start_time = datetime.datetime.now()
    time_elapsed = (datetime.datetime.now() - start_time).seconds
    completed = False
    try:
      while not completed and time_elapsed < GCB_BUILD_STATUS_TIMEOUT:
        pulled = subscription.pull()
        for ack_id, message in pulled:
          comp_name = ''
          if name == 'spinnaker-monitoring':
            comp_name = 'monitoring-daemon'
          else:
            comp_name = name
          payload = json.loads(message.data)
          repo_name = payload['source']['repoSource']['repoName']
          tag_name = payload['source']['repoSource']['tagName']
          if repo_name == comp_name and tag_name == tag:
            subscription.acknowledge([ack_id])
            status = payload['status']
            print 'Received status: {} for building tag {} of {}'.format(status, tag_name, comp_name)
            if status in ['SUCCESS', 'FAILURE']:
              completed = True
              build_id = payload['id']
              print 'Retrieving logs for build_id: {}'.format(build_id)
              get_log_cmd = ('gcloud container builds log --project {project} --account {account} {id}'
                             .format(project=gcb_project, account=gcb_service_account, id=build_id))
              build_log = run_quick(get_log_cmd, echo=False).stdout.strip()
              with open(logfile, 'a') as log:
                log.write('Fetching GCB build logs with: {}\n---\n'.format(get_log_cmd))
                log.write(build_log)
                log.write('\n---\nFinished fetching GCB build logs')

                if status == 'FAILURE':
                  raise Exception('Triggered GCB build for {name} failed.'.format(name=comp_name))
        time_elapsed = (datetime.datetime.now() - start_time).seconds
      if time_elapsed >= GCB_BUILD_STATUS_TIMEOUT:
        raise Exception('GCB triggered build for {} timed out'.format(name))
    finally:
      subscription.delete()

  @classmethod
  def __docker_build(cls, name, gradle_root):
    docker_tag = run_quick('cat {name}-docker.yml', echo=False).stdout.strip()
    cmds = [
      'docker build -f Dockerfile -t {docker_tag} .'.format(name=name, docker_tag=docker_tag),
      'docker push {docker_tag}'.format(name=name, docker_tag=docker_tag)
    ]
    logfile = '{name}-docker-build.log'.format(name=name)
    if os.path.exists(logfile):
      os.remove(logfile)
    run_shell_and_log(cmds, logfile, cwd=gradle_root)

  @classmethod
  def __jar_build(cls, name, gradle_root):
    version = run_quick('cat {name}-component-version.yml'.format(name=name),
                        echo=False).stdout.strip()
    cmds = [
      './release/all.sh {version} nightly'.format(version=version),
    ]
    logfile = '{name}-jar-build.log'.format(name=name)
    if os.path.exists(logfile):
      os.remove(logfile)
    run_shell_and_log(cmds, logfile, cwd=gradle_root)

  def __do_jar_build(self, subsys):
    if self.__options.do_jar_build:
      try:
        self.start_jar_build(subsys)
      except Exception as ex:
        self.__build_failures.append(BuildFailure(subsys, ex))

  def __do_build(self, subsys):
    if self.__options.platform == 'debian':
      try:
        self.start_deb_build(subsys)
      except Exception as ex:
        self.__build_failures.append(BuildFailure(subsys, ex))
    elif self.__options.platform == 'redhat':
      try:
        self.start_rpm_build(subsys)
      except Exception as ex:
        self.__build_failures.append(BuildFailure(subsys, ex))

  def __do_container_build(self, subsys):
    try:
      # HACK: Space out the container builds to address scalability concerns.
      full_subsystem_list = SUBSYSTEM_LIST + ADDITIONAL_SUBSYSTEMS
      time.sleep(2 * full_subsystem_list.index(subsys))
      self.start_container_build(subsys)
    except Exception as ex:
      self.__build_failures.append(BuildFailure(subsys, ex))

  def __check_build_failures(self, subsystems):
    if self.__build_failures:
      msg_lines = ['Builds failed:\n']
      should_exit = False
      for failure in self.__build_failures:
        if failure.component in subsystems:
          should_exit = True
          msg_lines.append('Building component {} failed with exception: \n{}\n'.format(failure.component, failure.exception))
      if should_exit:
        msg = '\n'.join(msg_lines)
        raise RuntimeError(msg)
      else:
        print 'Ignoring errors on optional subsystems {0!r}'.format(
          [failure.component for failure in self.__build_failures])

  def build_container_images(self):
    """Build the Spinnaker packages as container images.
    """
    subsystems = [comp for comp in SUBSYSTEM_LIST if comp != 'spinnaker']
    subsystems.append('spinnaker-monitoring')

    if self.__options.container_builder:
      weighted_processes = self.__options.cpu_ratio * multiprocessing.cpu_count()
      pool = multiprocessing.pool.ThreadPool(
        processes=int(max(1, weighted_processes)))
      pool.map(self.__do_container_build, subsystems)

    self.__check_build_failures(subsystems)

  def build_jars(self):
    """Build the Spinnaker packages as jars
    """
    subsystems = ['halyard']

    if self.__options.do_jar_build:
      weighted_processes = self.__options.cpu_ratio * multiprocessing.cpu_count()
      pool = multiprocessing.pool.ThreadPool(
        processes=int(max(1, weighted_processes)))
      pool.map(self.__do_jar_build, subsystems)

    self.__check_build_failures(subsystems)

  def build_packages(self):
      """Build all the Spinnaker packages."""
      all_subsystems = []
      all_subsystems.extend(SUBSYSTEM_LIST)
      all_subsystems.extend(ADDITIONAL_SUBSYSTEMS)

      if self.__options.build:
        # Build in parallel using half available cores
        # to keep load in check.
        weighted_processes = self.__options.cpu_ratio * multiprocessing.cpu_count()
        pool = multiprocessing.pool.ThreadPool(
            processes=int(max(1, weighted_processes)))
        pool.map(self.__do_build, all_subsystems)

      self.__check_build_failures(SUBSYSTEM_LIST)

  @classmethod
  def init_argument_parser(cls, parser):
      refresh_source.Refresher.init_argument_parser(parser)
      parser.add_argument('--platform', default='debian', action='store',
                          help='Select which platform to build for.'
                               ' Valid options are: {}.'.format(
                               ', '.join(VALID_PLATFORMS)))
      parser.add_argument('--build', default=True, action='store_true',
                          help='Build the sources.')
      parser.add_argument('--info_gradle', default=False, action='store_true',
                          help='Run gradle with --info.')
      parser.add_argument('--debug_gradle', default=False, action='store_true',
                          help='Run gradle with --debug.')
      parser.add_argument(
        '--cpu_ratio', type=float, default=1.25,  # 125%
        help='Number of concurrent threads as ratio of available cores.')

      parser.add_argument('--nobuild', dest='build', action='store_false')
      config_path = os.path.join(determine_project_root(), 'config')

      parser.add_argument(
          '--config_source', default=config_path,
          help='Path to directory for release config file templates.')

      parser.add_argument('--release_path', default='',
                          help='Specifies the path to the release to build.'
                             ' The release name is assumed to be the basename.'
                             ' The path can be a directory, GCS URI or S3 URI.')
      parser.add_argument(
          '--gcb_project', default='',
          help='The google project id to publish containers to'
               'if the container builder is gcp.')
      parser.add_argument(
          '--bintray_repo', default='',
          help='Publish to this bintray repo.\n'
               'This requires BINTRAY_USER and BINTRAY_KEY are set.')
      parser.add_argument(
          '--jar_repo', default='',
          help='Publish produced jars to this repo.\n'
               'This requires BINTRAY_USER and BINTRAY_KEY are set.')

      parser.add_argument(
          '--wipe_package_on_409', default=False, action='store_true',
          help='Work around BinTray conflict errors by deleting the entire package'
               ' and retrying. Removes all prior versions so only intended for dev'
               ' repos.\n')
      parser.add_argument(
          '--nowipe_package_on_409', dest='wipe_package_on_409',
          action='store_false')

      parser.add_argument(
          '--gcb_service_account', default='',
          help='Google service account to invoke the gcp container builder with.')
      parser.add_argument(
          '--gcb_service_account_json', default='',
          help='Path to service account credentials to invoke the gcp container builder with.')
      parser.add_argument(
          '--gcb_mirror_base_url', default='git@github.com:spinnaker-release',
          help='Base URL for the Spinnaker repositories GCB is configured to trigger builds from. Must use SSH protocol.')
      parser.add_argument(
          '--gradle_cache_path', default='{home}/.gradle'.format(home=os.environ.get('HOME', '')),
          help='Path to a gradle cache directory to use for the builds.')
      parser.add_argument(
          '--run_unit_tests', type=bool, default=False,
          help='Run unit tests during build for all components other than Deck.')
      parser.add_argument(
          '--do_jar_build', type=bool, default=True,
          help='Build & publish jars independently to GCS.')
      parser.add_argument('--maven_custom_init_file', default=os.path.join(os.path.dirname(__file__), 'maven-init.gradle'),
          help='Path to a gradle init file to add to the debian builds.'
               'Used to specify any custom behavior in the gradle builds.'
               'Argument is a file path relative to the directory this script is executed in.'
               'The default value assumes we run this script from the parent directory of spinnaker/spinnaker.')

  def __verify_bintray(self):
    if not os.environ.get('BINTRAY_KEY', None):
      raise ValueError('BINTRAY_KEY environment variable not defined')
    if not os.environ.get('BINTRAY_USER', None):
      raise ValueError('BINTRAY_USER environment variable not defined')


  @classmethod
  def do_build(cls, options, build_number=None, container_builder=None, sync_branch=None):
    if options.build and not (options.bintray_repo):
      sys.stderr.write('ERROR: Missing a --bintray_repo')
      return -1

    if options.platform not in VALID_PLATFORMS:
      sys.stderr.write('ERROR: {} is an invalid --platform. Please us one of {}'
          .format(options.platform, ', '.join(VALID_PLATFORMS)))
      return -1

    builder = cls(options, build_number=build_number,
                  container_builder=container_builder, sync_branch=sync_branch)
    if options.pull_origin:
        builder.refresher.pull_all_from_origin()

    print "Starting JAR build..."
    builder.build_jars()

    print "Starting package build..."
    builder.build_packages()

    if container_builder:
      print "Starting container build..."
      builder.build_container_images()

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()
    cls.do_build(options)

if __name__ == '__main__':
  sys.exit(Builder.main())
