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

"""Coordinates a global build of a Spinnaker release.

Spinnaker components use Gradle. This particular script might be more
appropriate to be a Gradle script. However this script came from a
context in which writing it in python was more convenient. It could be
replaced with a gradle script in the future without impacting other scripts
or the overall development process if having this be a Gradle script
is more maintainable.

This script builds all the components, and squirrels them away into a filesystem
somewhere (local, Amazon Simple Storage Service or Google Cloud Storage).
The individual components are built using their respective repository's Gradle
build. This script coordinates those builds and adds additional runtime
administrative scripts into the release.

TODO(ewiseblatt): 20151007
This should [also] generate a Debian package that can be installed.
The default should be to generate a .deb package rather than write a filesystem
tree. However, for historical development reasons, that is not yet done.
"""

import argparse
import collections
import fnmatch
import os
import multiprocessing
import multiprocessing.pool
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile


import refresh_source
from spinnaker.run import check_run_quick
from spinnaker.run import run_quick


SUBSYSTEM_LIST = ['clouddriver', 'orca', 'front50',
                  'rush', 'echo', 'rosco', 'gate', 'igor', 'deck', 'spinnaker']


def ensure_gcs_bucket(name, project=''):
  """Ensure that the desired GCS bucket exists, creating it if needed.

  Args:
    name [string]: The bucket name.
    project [string]: Optional Google Project id that will own the bucket.
      If none is provided, then the bucket will be associated with the default
      bucket configured to gcloud.

  Raises:
    RutimeError if the bucket could not be created.
  """
  bucket = 'gs://'+ name
  if not project:
      config_result = run_quick('gcloud config list', echo=False)
      error = None
      if config_result.returncode:
        error = 'Could not run gcloud: {error}'.format(
            error=config_result.stdout)
      else:
        match = re.search('(?m)^project = (.*)', config_result.stdout)
        if not match:
          error = ('gcloud is not configured with a default project.\n'
                   'run gcloud config or provide a --google_project.\n')
      if error:
        raise SystemError(error)

      project = match.group(1)

  list_result = run_quick('gsutil list -p ' +  project, echo=False)
  if list_result.returncode:
    error = ('Could not create Google Cloud Storage bucket'
             '"{name}" in project "{project}":\n{error}'
             .format(name=name, project=project, error=list_result.stdout))
    raise RuntimeError(error)

  if re.search('(?m)^{bucket}/\n'.format(bucket=bucket), list_result.stdout):
    sys.stderr.write(
        'WARNING: "{bucket}" already exists. Overwriting.\n'.format(
        bucket=bucket))
  else:
    print 'Creating GCS bucket "{bucket}" in project "{project}".'.format(
        bucket=bucket, project=project)
    check_run_quick('gsutil mb -p {project} {bucket}'
                    .format(project=project, bucket=bucket),
                    echo=True)


def ensure_s3_bucket(name, region=""):
  """Ensure that the desired S3 bucket exists, creating it if needed.

  Args:
    name [string]: The bucket name.
    region [string]: The S3 region for the bucket. If empty use aws default.

  Raises:
    RutimeError if the bucket could not be created.
  """
  bucket = 's3://' + name
  list_result = run_quick('aws s3 ls ' + bucket, echo=False)
  if not list_result.returncode:
    sys.stderr.write(
        'WARNING: "{bucket}" already exists. Overwriting.\n'.format(
        bucket=bucket))
  else:
    print 'Creating S3 bucket "{bucket}"'.format(bucket=bucket)
    command = 'aws s3 mb ' + bucket
    if region:
      command += ' --region ' + region
    check_run_quick(command, echo=False)


class BackgroundProcess(
    collections.namedtuple('BackgroundProcess', ['name', 'subprocess'])):
  """Denotes a running background process.

  Attributes:
    name [string]: The visible name of the process for reporting.
    subproces [subprocess]: The subprocess instance.
  """

  @staticmethod
  def spawn(name, args):
      sp = subprocess.Popen(args, shell=True, close_fds=True,
                            stdout=sys.stdout, stderr=subprocess.STDOUT)
      return BackgroundProcess(name, sp)

  def wait(self):
    if not self.subprocess:
      return None
    return self.subprocess.wait()

  def check_wait(self):
    if self.wait():
      error = '{name} failed.'.format(name=self.name)
      raise SystemError(error)


NO_PROCESS = BackgroundProcess('nop', None)

def determine_project_root():
  return os.path.abspath(os.path.dirname(__file__) + '/..')

def determine_package_version(gradle_root, submodule):
  with open(os.path.join(gradle_root, submodule,
                         'build/debian/control')) as f:
     content = f.read()
  match = re.search('(?m)^Version: (.*)', content)
  return match.group(1)


class Builder(object):
  """Knows how to coordinate a Spinnaker release."""

  def __init__(self, options, tarfile):
      self.__tarfile = tarfile
      self.__package_list = []
      self.__build_failures = []
      self.__background_processes = []

      os.environ['NODE_ENV'] = os.environ.get('NODE_ENV', 'dev')
      self.__options = options
      self.refresher = refresh_source.Refresher(options)
      if options.bintray_repo:
        self.__verify_bintray()


      # NOTE(ewiseblatt):
      # This is the GCE directory.
      # Ultimately we'll want to go to the root directory and install
      # standard stuff and gce stuff.
      self.__project_dir = determine_project_root()
      self.__release_dir = options.release_path

      if self.__release_dir.startswith('gs://'):
          ensure_gcs_bucket(name=self.__release_dir[5:].split('/')[0],
                            project=options.google_project)
      elif self.__release_dir.startswith('s3://'):
          ensure_s3_bucket(name=self.__release_dir[5:].split('/')[0],
                           region=options.aws_region)

  def start_build_target(self, name, target):
      """Start a subprocess to build the designated target.

      Args:
        name [string]: The name of the subsystem repository.
        target [string]: The gradle build target.

      Returns:
        BackgroundProcess
      """
      # Currently spinnaker is in a separate location
      gradle_root = (name if name != 'spinnaker'
              else os.path.join(self.__project_dir, 'experimental/buildDeb'))
      print 'Building {name}...'.format(name=name)
      return BackgroundProcess.spawn(
          'Building {name}'.format(name=name),
          'cd "{gradle_root}"; ./gradlew {target}'.format(
              gradle_root=gradle_root, target=target))

  @staticmethod
  def __filter_file(info, filter):
    """Determine if a record should be included in the tarfile or not.

    Args:
      info [tarinfo]: The tarinfo to consider.
      filter [string]: The file filter expression.

    Returns:
      info to include, or None.
    """
    if info.isdir():
      return info  # keep dir so we can recurse into it
    elif fnmatch.fnmatch(info.name, filter):
      return info
    else:
      return None

  def tar_dir(self, source, target, filter='*'):
    """Add contents of directory to tarfile.

    Args:
      source [string]: The path to add from.
      target [string]: The destination path to add into.
      filter [string]: The file filter specifying which files to include.
    """
    fn = lambda info: self.__filter_file(info, filter)
    self.__tarfile.add(source, arcname=target, recursive=True, filter=fn)

  def publish_install_script(self, source):
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']
    parts = self.__options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo')
    subject, repo = parts[0], parts[1]
    path = 'install_spinnaker.sh'

    gradle_root = os.path.join(self.__project_dir, 'experimental/buildDeb')
    version = determine_package_version(gradle_root, '.')
    command = ['curl -s -X PUT -T ', '"{source}"'.format(source=source),
               '-u{user}:{key}'.format(user=bintray_user, key=bintray_key),
               '"https://api.bintray.com/content'
               '/{subject}/{repo}/spinnaker/{version}/{path}'
               ';publish=1;override=1"'
                   .format(subject=subject, repo=repo, version=version,
                           path=path,
                           dist=self.__debian_distribution)]

    result = check_run_quick(' '.join(command), echo=True)
    return


  def publish_file(self, source, package, version):
    """Write a file to the bintray repository.

    Args:
      source [string]: The path to the source to copy must be local.
    """
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']

    parts = self.__options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo')

    # TODO(ewiseblatt):
    # Use urllib2 rather than curl but using curl for now to handle
    # authentication
    subject, repo = parts[0], parts[1]
    path = os.path.basename(source)
    command = ['curl -s -X PUT -T ', '"{source}"'.format(source=source),
               '-u{user}:{key}'.format(user=bintray_user, key=bintray_key),
               '"https://api.bintray.com/content'
               '/{subject}/{repo}/{package}/{version}/{path}'
               ';deb_distribution={dist};deb_component=spinnaker'
               ';deb_architecture=all'
               ';publish=1;override=1"'
                   .format(subject=subject, repo=repo, package=package,
                           version=version, path=path,
                           dist=self.__debian_distribution)]

    result = check_run_quick(' '.join(command), echo=True)
    return


  def start_copy_file(self, source, target):
      """Start a subprocess to copy the source file.

      Args:
        source [string]: The path to the source to copy must be local.
        target [string]: The target path can also be a storage service URI.

      Returns:
        BackgroundProcess
      """
      if target.startswith('s3://'):
        return BackgroundProcess.spawn(
            'Copying {source}'.format,
            'aws s3 cp "{source}" "{target}"'
            .format(source=source, target=target))
      elif target.startswith('gs://'):
        return BackgroundProcess.spawn(
            'Copying {source}'.format,
            'gsutil -q -m cp "{source}" "{target}"'
            .format(source=source, target=target))
      else:
        try:
          os.makedirs(os.path.dirname(target))
        except OSError:
          pass

        shutil.copy(source, target)
        return NO_PROCESS

  def start_copy_debian_target(self, name):
      """Copies the debian package for the specified subsystem.

      Args:
        name [string]: The name of the subsystem repository.
      """
      gradle_root = (name if name != 'spinnaker'
              else os.path.join(self.__project_dir, 'experimental/buildDeb'))
      if os.path.exists(os.path.join(name, '{name}-web'.format(name=name))):
          submodule = '{name}-web'.format(name=name)
      elif os.path.exists(os.path.join(name, '{name}-core'.format(name=name))):
          submodule = '{name}-core'.format(name=name)
      else:
          submodule = '.'

      version = determine_package_version(gradle_root, submodule)
      build_dir = '{submodule}/build/distributions'.format(submodule=submodule)
      package = '{name}_{version}_all.deb'.format(name=name, version=version)

      if not os.path.exists(os.path.join(gradle_root, build_dir, package)):
          if os.path.exists(os.path.join(gradle_root, build_dir,
                            '{submodule}_{version}_all.deb'
                            .format(submodule=submodule, version=version))):
              # This is for front50 only
              package = '{submodule}_{version}_all.deb'.format(
                submodule=submodule, version=version)
          else:
              error = ('Cannot find .deb for name={name} version={version}\n'
                       .format(name=name, version=version))
              raise AssertionError(error)

      from_path = os.path.join(gradle_root, build_dir, package)
      print 'Adding {path}'.format(path=from_path)
      self.__package_list.append(package)
      if self.__options.bintray_repo:
        self.publish_file(from_path, name, version)

      if self.__release_dir:
        to_path = os.path.join(self.__release_dir, package)
        return self.start_copy_file(from_path, to_path)
      else:
        return NO_PROCESS

  def __do_build(self, subsys):
    try:
      self.start_build_target(subsys, 'buildDeb').check_wait()
    except Exception as ex:
      self.__build_failures.append(subsys)

  def build_packages(self):
      """Build all the Spinnaker packages."""
      if self.__options.build:
        # Build in parallel using half available cores
        # to keep load in check.
        pool = multiprocessing.pool.ThreadPool(
            processes=min(1,
                        self.__options.cpu_ratio * multiprocessing.cpu_count()))
        pool.map(self.__do_build, SUBSYSTEM_LIST)
      
      if self.__build_failures:
        raise RuntimeError('Builds failed for {0!r}'.format(
          self.__build_failures))

      source_config_dir = self.__options.config_source
      processes = []

      if self.__tarfile:
          # Copy global spinnaker config (and sample local).
          for yml in ['default-spinnaker-local.yml']:
            source_config = os.path.join(source_config_dir, yml)
            self.__tarfile.add(source_config, os.path.join('config', yml))

      # Copy subsystem configuration files.
      for subsys in SUBSYSTEM_LIST:
          processes.append(self.start_copy_debian_target(subsys))
          if not self.__tarfile:
            continue

          if subsys == 'deck':
              source_config = os.path.join(source_config_dir, 'settings.js')
              self.__tarfile.add(source_config, 'config/settings.js')
          else:
              source_config = os.path.join(source_config_dir, subsys + '.yml')
              yml = os.path.basename(source_config)
              self.__tarfile.add(source_config, os.path.join('config', yml))

      print 'Waiting for package copying to finish....'
      for p in processes:
        p.check_wait()

  def copy_dependency_files(self):
    """Copy additional files used by external dependencies into release."""
    source_dir = os.path.join(self.__project_dir, 'cassandra')
    self.tar_dir(source_dir, 'cassandra')

  def copy_install_scripts(self):
    """Copy installation scripts into release."""
    source_dir = os.path.join(self.__project_dir, 'install')
    self.tar_dir(source_dir, 'install', filter='*.py')
    self.tar_dir(source_dir, 'install', filter='*.sh')

  def copy_admin_scripts(self):
    """Copy administrative/operational support scripts into release."""
    self.tar_dir(os.path.join(self.__project_dir, 'pylib'),
                 'pylib', filter='*.py')
    self.tar_dir(os.path.join(self.__project_dir, 'runtime'),
                 'scripts', filter='*.sh')

  def copy_release_config(self):
    """Copy configuration files into release."""
    source_dir = self.__options.config_source
    self.tar_dir(source_dir, 'config')

    # This is the contents of the release_config.cfg file.
    # Which acts as manifest to inform the installer what packages to install.
    fd, temp_file = tempfile.mkstemp()
    os.write(fd, '# This file is not intended to be user-modified.\n'
                 'PACKAGE_LIST="{packages}"\n'
                 .format(packages=' '.join(self.__package_list)))
    os.close(fd)
    self.__tarfile.add(temp_file, 'release_config.cfg')
    os.remove(temp_file)

  @staticmethod
  def __zip_dir(zip_file, source_path, arcname=''):
    """Zip the contents of a directory.

    Args:
      zip_file: [ZipFile] The zip file to write into.
      source_path: [string] The directory to add.
      arcname: [string] Optional name for the source to appear as in the zip.
    """
    if arcname:
      # Effectively replace os.path.basename(parent_path) with arcname.
      arcbase = arcname + '/'
      parent_path = source_path
    else:
      # Will start relative paths from os.path.basename(source_path).
      arcbase = ''
      parent_path = os.path.dirname(source_path)

    # Copy the tree at source_path adding relative paths into the zip.
    rel_offset = len(parent_path) + 1
    entries = os.walk(source_path)
    for root, dirs, files in entries:
      for dirname in dirs:
        abs_path = os.path.join(root, dirname)
        zip_file.write(abs_path, arcbase + abs_path[rel_offset:])
      for filename in files:
        abs_path = os.path.join(root, filename)
        zip_file.write(abs_path, arcbase + abs_path[rel_offset:])

  def add_python_test_zip(self, test_name):
    """Build encapsulated python zip file for the given test test_name.

    This allows integration tests to be packaged with the release, at least
    for the time being. This is useful for testing them, or validating the
    initial installation and configuration.
    """
    test_py = '{test_name}.py'.format(test_name=test_name)
    testdir = os.path.join(self.__project_dir, 'build/tests')
    try:
      os.makedirs(testdir)
    except OSError:
      pass

    zip_path = os.path.join(testdir, test_py + '.zip')
    zip = zipfile.ZipFile(zip_path, 'w')

    try:
      zip.writestr('__main__.py', """
from {test_name} import main
import sys

if __name__ == '__main__':
  retcode = main()
  sys.exit(retcode)
""".format(test_name=test_name))

      # Add citest sources as baseline
      # TODO(ewiseblatt): 20150810
      # Eventually this needs to be the transitive closure,
      # but there are currently no other dependencies.
      zip.writestr('__init__.py', '')
      self.__zip_dir(zip, 'citest/citest', 'citest')
      self.__zip_dir(zip,
                     'citest/spinnaker/spinnaker_testing', 'spinnaker_testing')
      self.__zip_dir(zip, 'pylib/yaml', 'yaml')

      zip.write('citest/spinnaker/spinnaker_system/' + test_py, test_py)
      zip.close()

      if self.__tarfile:
        self.__tarfile.add(zip_path, 'tests/{py}.zip'.format(py=test_py))
    finally:
      pass


  def build_tests(self):
     if not os.path.exists('citest'):
        print 'Adding citest repository'
        try:
          self.refresher.git_clone(
              refresh_source.SourceRepository('citest', 'google'))
        except Exception as ex:
          sys.stderr.write('*** Omitting tests: {0}\n'.format(ex.message))
          return

     print 'Adding tests...'
     self.add_python_test_zip('aws_kato_test')
     self.add_python_test_zip('kato_test')
     self.add_python_test_zip('smoke_test')
     self.add_python_test_zip('server_group_tests')

  @classmethod
  def init_argument_parser(cls, parser):
      refresh_source.Refresher.init_argument_parser(parser)
      parser.add_argument('--build', default=True, action='store_true',
                          help='Build the sources.')
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
        '--google_project', default='',
        help='If release repository is a GCS bucket then this is the project'
        ' owning the bucket. The default is the project configured as the'
        ' default for gcloud.')

      parser.add_argument(
        '--aws_region', default='',
        help='If release repository is a S3 bucket then this is the AWS'
        ' region to add the bucket to if the bucket did not already exist.')

      parser.add_argument(
        '--bintray_repo', default='',
        help='Publish to this bintray repo.\n'
             'This requires BINTRAY_USER and BINTRAY_KEY are set.')


  def __verify_bintray(self):
    if not os.environ.get('BINTRAY_KEY', None):
      raise ValueError('BINTRAY_KEY environment variable not defined')
    if not os.environ.get('BINTRAY_USER', None):
      raise ValueError('BINTRAY_USER environment variable not defined')

    # We are currently limited to ubuntu because of assumptions in
    # how we publish to bintray.
    if not os.path.exists('/etc/lsb-release'):
      raise ValueError('This does not appear to be an ubuntu distribution.')
    with open('/etc/lsb-release', 'r') as f:
      content = f.read()
    match = re.search('DISTRIB_CODENAME=(.+)', content)
    if match is None:
      raise ValueError('Could not determine debian distribution name')
    self.__debian_distribution = match.group(1)
    result = check_run_quick('uname -m', echo=False)


  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    if not (options.release_path or options.bintray_repo):
      sys.stderr.write(
           'ERROR: Missing either a --release_path or --bintray_repo')
      return -1

    tz = None
    if options.release_path:
      fd, tarfile_path = tempfile.mkstemp()
      os.close(fd)
      tz = tarfile.open(tarfile_path, mode='w:gz')

    builder = cls(options, tz)
    if options.pull_origin:
        builder.refresher.pull_all_from_origin()

    builder.build_tests()
    builder.build_packages()

    if options.bintray_repo:
      fd, temp_path = tempfile.mkstemp()
      with open(os.path.join(determine_project_root(),
                             'install/install_spinnaker.sh'),
                'r') as f:
          content = f.read()
          match = re.search(
                'DEFAULT_REPOSITORY="https://dl\.bintray\.com/(.+)"',
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

    if not tz:
      return 0


    print 'Building spinnaker.tar.gz'
    builder.copy_dependency_files()
    builder.copy_install_scripts()
    builder.copy_admin_scripts()
    builder.copy_release_config()

    tz.close()
    processes = []
    try:
      print 'Copying spinnaker.tar.gz'
      processes.append(builder.start_copy_file(
        tarfile_path, os.path.join(options.release_path, 'spinnaker.tar.gz')))
      processes.append(builder.start_copy_file(
        os.path.join(determine_project_root(),
                     'install/install_spinnaker.sh'),
        os.path.join(options.release_path, 'install_spinnaker.sh')))
      for p in processes:
        p.check_wait()
    finally:
      shutil.copy(tarfile_path, './created_spinnaker.tar.gz')   # !!!
      os.remove(tarfile_path)

    print '\nFINISHED writing release to {dir}'.format(
        dir=builder.__release_dir)
    return 0

if __name__ == '__main__':
  sys.exit(Builder.main())
