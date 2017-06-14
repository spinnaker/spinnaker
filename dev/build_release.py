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
import base64
import collections
import datetime
import fnmatch
import glob
import os
import multiprocessing
import multiprocessing.pool
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib2
from urllib2 import HTTPError

import refresh_source
from spinnaker.run import check_run_quick
from spinnaker.run import run_quick

SUBSYSTEM_LIST = ['clouddriver', 'orca', 'front50',
                  'echo', 'rosco', 'gate', 'igor', 'fiat', 'deck', 'spinnaker']
ADDITIONAL_SUBSYSTEMS = ['spinnaker-monitoring', 'halyard']

VALID_PLATFORMS = ['debian', 'redhat']


class BackgroundProcess(
    collections.namedtuple('BackgroundProcess', ['name', 'subprocess', 'log'])):
  """Denotes a running background process.

  Attributes:
    name [string]: The visible name of the process for reporting.
    subprocess [subprocess]: The subprocess instance.
  """

  @staticmethod
  def spawn(name, args, logfile=None):
    sp = None
    log = None
    if logfile:
      log = open(logfile, 'w')
      sp = subprocess.Popen(args, shell=True, close_fds=True,
                            stdout=log, stderr=log)
    else:
      sp = subprocess.Popen(args, shell=True, close_fds=True,
                            stdout=sys.stdout, stderr=subprocess.STDOUT)
    return BackgroundProcess(name, sp, log)

  def wait(self):
    if not self.subprocess:
      return None
    return self.subprocess.wait()

  def check_wait(self):
    return_code = self.wait()
    if return_code:
      error = '{name} failed.'.format(name=self.name)
      raise SystemError(error)

    if not return_code is None and self.log:
      self.log.close()


NO_PROCESS = BackgroundProcess('nop', None, None)

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

class Builder(object):
  """Knows how to coordinate a Spinnaker release."""

  def __init__(self, options, build_number=None, container_builder=None):
      self.__package_list = []
      self.__build_failures = []
      self.__background_processes = []

      os.environ['NODE_ENV'] = os.environ.get('NODE_ENV', 'dev')
      self.__build_number = build_number or os.environ.get('BUILD_NUMBER') or '{:%Y-%m-%d}'.format(datetime.datetime.utcnow())
      self.__gcb_service_account = options.gcb_service_account
      self.__options = options
      if (container_builder and container_builder not in ['gcb', 'docker']):
        raise ValueError('Invalid container_builder. Must be empty, "gcb" or "docker"')

      self.refresher = refresh_source.Refresher(options)
      if options.bintray_repo and options.build:
        self.__verify_bintray()

      self.__project_dir = determine_project_root()

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

    Returns:
      BackgroundProcess
    """
    jarRepo = self.__options.jar_repo
    parts = self.__options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo>')
    org, packageRepo = parts[0], parts[1]
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']

    if self.__options.nebula:
      target = 'candidate'
      extra_args = [
          '--stacktrace',
          '-Prelease.useLastTag=true',
          '-PbintrayPackageBuildNumber={number}'.format(
              number=self.__build_number),
          '-PbintrayOrg="{org}"'.format(org=org),
          '-PbintrayPackageRepo="{repo}"'.format(repo=packageRepo),
          '-PbintrayJarRepo="{jarRepo}"'.format(jarRepo=jarRepo),
          '-PbintrayKey="{key}"'.format(key=bintray_key),
          '-PbintrayUser="{user}"'.format(user=bintray_user)
        ]
    else:
      target = 'buildDeb'
      extra_args = []

    if self.__options.debug_gradle:
      extra_args.append('--debug')

    if name == 'deck' and not 'CHROME_BIN' in os.environ:
      extra_args.append('-PskipTests')
    elif name == 'halyard':
      extra_args.append('-PbintrayPackageDebDistribution=trusty-nightly')

    # Currently spinnaker is in a separate location
    gradle_root = self.determine_gradle_root(name)
    print 'Building and publishing Debian for {name}...'.format(name=name)
    # Note: 'candidate' is just the gradle task name. It doesn't indicate
    # 'release candidate' status for the artifacts created through this build.
    return BackgroundProcess.spawn(
      'Building and publishing Debian for {name}...'.format(name=name),
      'cd "{gradle_root}"; ./gradlew {extra} {target}'.format(
          gradle_root=gradle_root, extra=' '.join(extra_args), target=target),
      logfile='{name}-debian-build.log'.format(name=name)
    )

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

    Returns:
      BackgroundProcess
    """
    jarRepo = self.__options.jar_repo
    parts = self.__options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo>')
    org, packageRepo = parts[0], parts[1]
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']

    if self.__options.nebula:
      target = 'buildRpm'
      extra_args = [
          '--stacktrace',
          '-Prelease.useLastTag=true',
          '-PbintrayPackageBuildNumber={number}'.format(
              number=self.__build_number)
        ]
    else:
      target = 'buildRpm'
      extra_args = [
          '-PbintrayPackageBuildNumber={number}'.format(
              number=self.__build_number)
      ]

    if self.__options.debug_gradle:
      extra_args.append('--debug')

    if name == 'deck' and not 'CHROME_BIN' in os.environ:
      extra_args.append('-PskipTests')

    # Currently spinnaker is in a separate location
    gradle_root = self.determine_gradle_root(name)
    print 'Building and publishing Redhat for {name}...'.format(name=name)
    # Note: 'buildRpm' is just the gradle task name. It doesn't indicate
    # 'release candidate' status for the artifacts created through this build.
    return BackgroundProcess.spawn(
      'Building and publishing Redhat for {name}...'.format(name=name),
      'cd "{gradle_root}"; ./gradlew {extra} {target}'.format(
          gradle_root=gradle_root, extra=' '.join(extra_args), target=target),
      logfile='{name}-rhel-build.log'.format(name=name)
    )

  def start_container_build(self, name):
    """Start a subprocess to build a container image of the subsystem.

    Uses either Google Container Builder or Docker with configuration files
    produced during BOM generation to build the container images. The
    configuration files are assumed to be in the parent directory of the
    subsystem's Gradle root.

    Args:
      name [string]: Name of the subsystem repository.

    Returns:
      BackgroundProcess
    """
    gradle_root = self.determine_gradle_root(name)
    if self.__options.container_builder == 'gcb':
      # Local .gradle dir stomps on GCB's .gradle directory when the gradle
      # wrapper is installed, so we need to delete the local one.
      # The .gradle dir is transient and will be recreated on the next gradle
      # build, so this is OK.
      gradle_cache = '{name}/.gradle'.format(name=name)
      if os.path.isdir(gradle_cache):
        # Tell rmtree to delete the directory even if it's non-empty.
        shutil.rmtree(gradle_cache)
      return BackgroundProcess.spawn(
          'Build/publishing container image for {name} with'
          ' Google Container Builder...'.format(name=name),
          'cd "{gradle_root}"'
          '; gcloud container builds submit --account={account} --project={project} --config="../{name}-gcb.yml" .'
        .format(gradle_root=gradle_root, name=name, account=self.__gcb_service_account,
                project=self.__options.gcb_project),
        logfile='{name}-gcb-build.log'.format(name=name)
      )
    elif self.__options.container_builder == 'docker':
      return BackgroundProcess.spawn(
          'Build/publishing container image for {name} with Docker...'.format(
              name=name),
          'cd "{gradle_root}"'
          ' ; docker build -f Dockerfile -t $(cat ../{name}-docker.yml) .'
          ' ; docker push $(cat ../{name}-docker.yml)'
          .format(gradle_root=gradle_root, name=name)
      )
    else:
      raise NotImplemented(
          'container_builder="{0}"'.format(self.__options.container_builder))


  def publish_to_bintray(self, source, package, version, path, debian_tags=''):
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']
    parts = self.__options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo>')
    subject, repo = parts[0], parts[1]

    pkg_filename = os.path.basename(path)
    if (pkg_filename.startswith('spinnaker-')
        and not package.startswith('spinnaker')):
      package = 'spinnaker-' + package

    if debian_tags and debian_tags[0] != ';':
      debian_tags = ';' + debian_tags

    url = ('https://api.bintray.com/content'
           '/{subject}/{repo}/{package}/{version}/{path}'
           '{debian_tags}'
           ';publish=1;override=1'
           .format(subject=subject, repo=repo, package=package,
                   version=version, path=path,
                   debian_tags=debian_tags))

    with open(source, 'r') as f:
        data = f.read()
        put_request = urllib2.Request(url)
        encoded_auth = base64.encodestring('{user}:{pwd}'.format(
            user=bintray_user, pwd=bintray_key))[:-1]  # strip eoln

        put_request.add_header('Authorization', 'Basic ' + encoded_auth)
        put_request.get_method = lambda: 'PUT'
        try:
            result = urllib2.urlopen(put_request, data)
        except HTTPError as put_error:
            if put_error.code == 409 and self.__options.wipe_package_on_409:
              # The problem here is that BinTray does not allow packages to change once
              # they have been published (even though we are explicitly asking it to
              # override). PATCH wont work either.
              # Since we are building from source, we don't really have a version
              # yet, since we are still modifying the code. Either we need to generate a new
              # version number every time or we don't want to publish these.
              # Ideally we could control whether or not to publish. However,
              # if we do not publish, then the repository will not be visible without
              # credentials, and adding conditional credentials into the packer scripts
              # starts getting even more complex.
              #
              # We cannot seem to delete individual versions either (at least not for
              # InstallSpinnaker.sh, which is where this problem seems to occur),
              # so we'll be heavy handed and wipe the entire package.
              print 'Got 409 on {url}.'.format(url=url)
              delete_url = ('https://api.bintray.com/content'
                            '/{subject}/{repo}/{path}'
                            .format(subject=subject, repo=repo, path=path))
              print 'Attempt to delete url={url} then retry...'.format(url=delete_url)
              delete_request = urllib2.Request(delete_url)
              delete_request.add_header('Authorization', 'Basic ' + encoded_auth)
              delete_request.get_method = lambda: 'DELETE'
              try:
                urllib2.urlopen(delete_request)
                print 'Deleted...'
              except HTTPError as ex:
                # Maybe it didn't exist. Try again anyway.
                print 'Delete {url} got {ex}. Try again anyway.'.format(url=url, ex=ex)
              print 'Retrying {url}'.format(url=url)
              result = urllib2.urlopen(put_request, data)
              print 'SUCCESS'

            elif put_error.code != 400:
              raise

            else:
              # Try creating the package and retrying.
              pkg_url = os.path.join('https://api.bintray.com/packages',
                                     subject, repo)
              print 'Creating an entry for {package} with {pkg_url}...'.format(
                  package=package, pkg_url=pkg_url)

              # All the packages are from spinnaker so we'll hardcode it.
              # Note spinnaker-monitoring is a github repo with two packages.
              # Neither is "spinnaker-monitoring"; that's only the github repo.
              gitname = (package.replace('spinnaker-', '')
                         if not package.startswith('spinnaker-monitoring')
                         else 'spinnaker-monitoring')
              pkg_data = """{{
                "name": "{package}",
                "licenses": ["Apache-2.0"],
                "vcs_url": "https://github.com/spinnaker/{gitname}.git",
                "website_url": "http://spinnaker.io",
                "github_repo": "spinnaker/{gitname}",
                "public_download_numbers": false,
                "public_stats": false
              }}'""".format(package=package, gitname=gitname)

              pkg_request = urllib2.Request(pkg_url)
              pkg_request.add_header('Authorization', 'Basic ' + encoded_auth)
              pkg_request.add_header('Content-Type', 'application/json')
              pkg_request.get_method = lambda: 'POST'
              pkg_result = urllib2.urlopen(pkg_request, pkg_data)
              pkg_code = pkg_result.getcode()
              if pkg_code >= 200 and pkg_code < 300:
                  result = urllib2.urlopen(put_request, data)

        code = result.getcode()
        if code < 200 or code >= 300:
          raise ValueError('{code}: Could not add version to {url}\n{msg}'
                           .format(code=code, url=url, msg=result.read()))

    print 'Wrote {source} to {url}'.format(source=source, url=url)

  def publish_install_script(self, source):
    gradle_root = self.determine_gradle_root('spinnaker')
    version = determine_package_version(self.__options.platform, gradle_root)

    self.publish_to_bintray(source, package='spinnaker', version=version,
                            path='InstallSpinnaker.sh')

  def publish_file(self, source, package, version):
    """Write a file to the bintray repository.

    Args:
      source [string]: The path to the source to copy must be local.
    """
    path = os.path.basename(source)
    debian_tags = ''
    if self.__options.platform == 'debian':
      debian_tags = ';'.join(['deb_component=spinnaker',
                              'deb_distribution=trusty,utopic,vivid,wily',
                              'deb_architecture=all'])

    self.publish_to_bintray(source, package=package, version=version,
                            path=path, debian_tags=debian_tags)

  def start_copy_debian_target(self, name):
      """Copies the debian package for the specified subsystem.

      Args:
        name [string]: The name of the subsystem repository.
      """
      pids = []
      gradle_root = self.determine_gradle_root(name)
      version = determine_package_version(self.__options.platform, gradle_root)
      if version is None:
        return []

      for root in determine_modules_with_debians(gradle_root):
        deb_dir = '{root}/build/distributions'.format(root=root)

        non_spinnaker_name = '{name}_{version}_all.deb'.format(
              name=name, version=version)

        if os.path.exists(os.path.join(deb_dir,
                                       'spinnaker-' + non_spinnaker_name)):
         deb_file = 'spinnaker-' + non_spinnaker_name
        elif os.path.exists(os.path.join(deb_dir, non_spinnaker_name)):
          deb_file = non_spinnaker_name
        else:
          module_name = os.path.basename(
            os.path.dirname(os.path.dirname(deb_dir)))
          deb_file = '{module_name}_{version}_all.deb'.format(
            module_name=module_name, version=version)

        if not os.path.exists(os.path.join(deb_dir, deb_file)):
          error = ('.deb for name={name} version={version} is not in {dir}\n'
                   .format(name=name, version=version, dir=deb_dir))
          raise AssertionError(error)

        from_path = os.path.join(deb_dir, deb_file)
        print 'Adding {path}'.format(path=from_path)
        self.__package_list.append(from_path)
        basename = os.path.basename(from_path)
        module_name = basename[0:basename.find('_')]
        if self.__options.bintray_repo:
          self.publish_file(from_path, module_name, version)

      return pids

  def start_copy_redhat_target(self, name):
      """Copies the redhat package for the specified subsystem.

      Args:
        name [string]: The name of the subsystem repository.
      """
      pids = []
      gradle_root = self.determine_gradle_root(name)
      version = determine_package_version(self.__options.platform, gradle_root)
      if version is None:
        return []

      for root in determine_modules_with_redhats(gradle_root):
        rpm_dir = '{root}/build/distributions'.format(root=root)

        non_spinnaker_name = '{name}-{version}.noarch.rpm'.format(
              name=name, version=version)

        if os.path.exists(os.path.join(rpm_dir,
                                       'spinnaker-' + non_spinnaker_name)):
          rpm_file = 'spinnaker-' + non_spinnaker_name
        elif os.path.exists(os.path.join(rpm_dir, non_spinnaker_name)):
          rpm_file = non_spinnaker_name
        else:
          module_name = os.path.basename(os.path.dirname(
            os.path.dirname(rpm_dir)))
          rpm_file = '{module_name}-{version}.noarch.rpm'.format(
            module_name=module_name, version=version)

        if not os.path.exists(os.path.join(rpm_dir, rpm_file)):
          error = ('.rpm for name={name} version={version} is not in {dir}\n'
                   .format(name=name, version=version, dir=rpm_dir))
          raise AssertionError(error)

        from_path = os.path.join(rpm_dir, rpm_file)
        print 'Adding {path}'.format(path=from_path)
        self.__package_list.append(from_path)
        basename = os.path.basename(from_path)
        module_name = re.search("^(.*)-{}.noarch.rpm$".format(version), basename).group(1)
        if self.__options.bintray_repo:
          self.publish_file(from_path, module_name, version)

      return pids

  def __do_build(self, subsys):
    if self.__options.platform == 'debian':
      try:
        self.start_deb_build(subsys).check_wait()
      except Exception as ex:
        self.__build_failures.append(subsys)

    elif self.__options.platform == 'redhat':
      try:
        self.start_rpm_build(subsys).check_wait()
      except Exception as ex:
        self.__build_failures.append(subsys)

  def __do_container_build(self, subsys):
    try:
      # HACK: Space out the container builds to address scalability concerns.
      full_subsystem_list = SUBSYSTEM_LIST + ADDITIONAL_SUBSYSTEMS
      time.sleep(2 * full_subsystem_list.index(subsys))
      self.start_container_build(subsys).check_wait()
    except Exception as ex:
      print ex
      self.__build_failures.append(subsys)

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

    if self.__build_failures:
      if set(self.__build_failures).intersection(set(subsystems)):
        raise RuntimeError('Builds failed for {0!r}'.format(
          self.__build_failures))
      else:
        print 'Ignoring errors on optional subsystems {0!r}'.format(
          self.__build_failures)
    return

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

      if self.__build_failures:
        if set(self.__build_failures).intersection(set(SUBSYSTEM_LIST)):
          raise RuntimeError('Builds failed for {0!r}'.format(
            self.__build_failures))
        else:
          print 'Ignoring errors on optional subsystems {0!r}'.format(
              self.__build_failures)

      if self.__options.nebula:
        return

      # Do not choke if there is nothing to copy
      wait_on = set(all_subsystems).difference(set(self.__build_failures))
      if len(wait_on) > 0:
        pool = multiprocessing.pool.ThreadPool(processes=len(wait_on))
        print 'Copying packages...'
        pool.map(self.__do_copy, wait_on)
      else:
        print 'Nothing to copy.'
      return

  def __do_copy(self, subsys):
    print 'Starting to copy {0}...'.format(subsys)
    if self.__options.platform == 'debian':
      pids = self.start_copy_debian_target(subsys)

    elif self.__options.platform == 'redhat':
      pids = self.start_copy_redhat_target(subsys)

    for p in pids:
      p.check_wait()
    print 'Finished copying {0}.'.format(subsys)

  @classmethod
  def init_argument_parser(cls, parser):
      refresh_source.Refresher.init_argument_parser(parser)
      parser.add_argument('--platform', default='debian', action='store',
                          help='Select which platform to build for.'
                               ' Valid options are: {}.'.format(
                               ', '.join(VALID_PLATFORMS)))
      parser.add_argument('--build', default=True, action='store_true',
                          help='Build the sources.')
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
          '--nebula', default=True, action='store_true',
          help='Use nebula to build "candidate" target and upload to bintray.')
      parser.add_argument(
          '--nonebula', dest='nebula', action='store_false',
          help='Explicitly "buildDeb" then curl upload them to bintray.')
      parser.add_argument(
          '--gcb_service_account', default='',
          help='Google service account to invoke the gcp container builder with.')


  def __verify_bintray(self):
    if not os.environ.get('BINTRAY_KEY', None):
      raise ValueError('BINTRAY_KEY environment variable not defined')
    if not os.environ.get('BINTRAY_USER', None):
      raise ValueError('BINTRAY_USER environment variable not defined')


  @classmethod
  def do_build(cls, options, build_number=None, container_builder=None):
    if options.build and not (options.bintray_repo):
      sys.stderr.write('ERROR: Missing a --bintray_repo')
      return -1

    if options.platform not in VALID_PLATFORMS:
      sys.stderr.write('ERROR: {} is an invalid --platform. Please us one of {}'
          .format(options.platform, ', '.join(VALID_PLATFORMS)))
      return -1

    builder = cls(options, build_number=build_number, container_builder=container_builder)
    if options.pull_origin:
        builder.refresher.pull_all_from_origin()

    builder.build_packages()
    if container_builder:
      builder.build_container_images()

    if options.build and options.bintray_repo:
      fd, temp_path = tempfile.mkstemp()
      with open(os.path.join(determine_project_root(), 'InstallSpinnaker.sh'),
                'r') as f:
          content = f.read()
          match = re.search(
                'REPOSITORY_URL="https://dl\.bintray\.com/(.+)"',
                content)
          content = ''.join([content[0:match.start(1)],
                             options.bintray_repo,
                             content[match.end(1):]])
          os.write(fd, content)
      os.close(fd)

      try:
        builder.publish_install_script(
          os.path.join(determine_project_root(), temp_path))
      finally:
        os.remove(temp_path)

      print '\nFINISHED writing release to {rep}'.format(
        rep=options.bintray_repo)

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()
    cls.do_build(options)

if __name__ == '__main__':
  sys.exit(Builder.main())
