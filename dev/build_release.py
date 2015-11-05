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
import tempfile
import time
import zipfile


import refresh_source
from spinnaker.run import check_run_quick
from spinnaker.run import run_quick


SUBSYSTEM_LIST = ['clouddriver', 'orca', 'front50',
                  'rush', 'echo', 'rosco', 'gate', 'igor', 'deck']


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


class Builder(object):
  """Knows how to coordinate a Spinnaker release."""

  def __init__(self, options):
      self.__package_list = []
      self.__config_list = []
      self.__background_processes = []

      os.environ['NODE_ENV'] = 'dev'
      self.__options = options
      self.refresher = refresh_source.Refresher(options)

      # NOTE(ewiseblatt):
      # This is the GCE directory.
      # Ultimately we'll want to go to the root directory and install
      # standard stuff and gce stuff.
      self.__project_dir = os.path.abspath(
          os.path.dirname(__file__) + '/..')
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
      print 'Building {name}...'.format(name=name)
      return BackgroundProcess.spawn(
          'Building {name}'.format(name=name),
          'cd {name}; ./gradlew {target}'.format(name=name, target=target))

  def start_copy_dir(self, source, target, filter='*'):
    if target.startswith('s3://'):
      return BackgroundProcess.spawn(
        'Copying {source}'.format,
        'aws s3 cp --recursive "{source}" "{target}"'
        ' --exclude "*" --include "{filter}"'
        .format(source=source, target=target, filter=filter))

    list = []
    for root, dirs, files in os.walk(source):
      postfix = root[len(source):]
      rel_target = (target
                    if not postfix
                    else os.path.join(target, root[len(source) + 1:]))
      for file in fnmatch.filter(files, filter):
        list.append(self.start_copy_file(os.path.join(root, file),
                                         os.path.join(rel_target, file)))

    print '  Waiting to finish copying directory {source}'.format(source=source)
    for p in list:
      p.check_wait()
    return NO_PROCESS

  def start_copy_file(self, source, target, dir=False):
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
      if os.path.exists(os.path.join(name, '{name}-web'.format(name=name))):
          submodule = '{name}-web'.format(name=name)
      elif os.path.exists(os.path.join(name, '{name}-core'.format(name=name))):
          submodule = '{name}-core'.format(name=name)
      else:
          submodule = '.'

      with open(os.path.join(name, submodule, 'build/debian/control')) as f:
         content = f.read()
      match = re.search('(?m)^Version: (.*)', content)
      version = match.group(1)
      build_dir = '{submodule}/build/distributions'.format(submodule=submodule)
      package = '{name}_{version}_all.deb'.format(name=name, version=version)

      if not os.path.exists(os.path.join(name, build_dir, package)):
          if os.path.exists(os.path.join(name, build_dir,
                            '{submodule}_{version}_all.deb'
                            .format(submodule=submodule, version=version))):
              # This is for front50 only
              package = '{submodule}_{version}_all.deb'.format(
                submodule=submodule, version=version)
          else:
              error = ('Cannot find .deb for name={name} version={version}\n'
                       .format(name=name, version=version))
              raise AssertionError(error)

      from_path = os.path.join(name, build_dir, package)
      to_path = os.path.join(self.__release_dir, package)

      print 'Adding {path}'.format(path=from_path)
      self.__package_list.append(package)
      return self.start_copy_file(from_path, to_path)


  def __do_build(self, subsys):
      self.start_build_target(subsys, 'buildDeb').check_wait()

  def build_packages(self):
      """Build all the Spinnaker packages."""
      if self.__options.build:
        # Build in parallel using half available cores
        # to keep load in check.
        pool = multiprocessing.pool.ThreadPool(
            processes=min(1,
                        self.__options.cpu_ratio * multiprocessing.cpu_count()))
        pool.map(self.__do_build, SUBSYSTEM_LIST)

      source_config_dir = self.__options.config_source
      processes = []

      # Copy global spinnaker config (and sample local).
      for yml in [ 'default-spinnaker-local.yml', 'spinnaker.yml']:
          source_config = os.path.join(source_config_dir, yml)
          target_config = os.path.join(self.__release_dir, 'config', yml)
          self.__config_list.append(yml)
          processes.append(self.start_copy_file(source_config, target_config))

      # Copy subsystem configuration files.
      for subsys in SUBSYSTEM_LIST:
          processes.append(self.start_copy_debian_target(subsys))
          if subsys == 'deck':
            source_config = os.path.join(source_config_dir, 'settings.js')
            target_config = os.path.join(
                self.__release_dir, 'config/settings.js')
            processes.append(self.start_copy_file(source_config, target_config))
          else:
              source_config = os.path.join(source_config_dir, subsys + '.yml')
              yml = os.path.basename(source_config)
              target_config = os.path.join(self.__release_dir, 'config', yml)
              self.__config_list.append(yml)
              processes.append(
                  self.start_copy_file(source_config, target_config))

      print 'Waiting for package copying to finish....'
      for p in processes:
        p.check_wait()

  def copy_dependency_files(self):
    """Copy additional files used by external dependencies into release."""
    source_dir = os.path.join(self.__project_dir, 'cassandra')
    target_dir = os.path.join(self.__release_dir, 'cassandra')
    processes = []
    processes.append(self.start_copy_dir(
      source_dir, target_dir, filter='*.cql'))

    print 'Waiting for dependency scripts.'
    for p in processes:
      p.check_wait()

  def copy_install_scripts(self):
    """Copy installation scripts into release."""
    source_dir = os.path.join(self.__project_dir, 'install')
    target_dir = os.path.join(self.__release_dir, 'install')

    processes = []
    processes.append(self.start_copy_dir(
      source_dir, target_dir, filter='*.py'))
    processes.append(self.start_copy_dir(
      source_dir, target_dir, filter='*.sh'))

    print 'Waiting for install scripts to finish.'
    for p in processes:
      p.check_wait()

  def copy_admin_scripts(self):
    """Copy administrative/operational support scripts into release."""
    processes = []
    processes.append(self.start_copy_dir(
      os.path.join(self.__project_dir, 'pylib'),
      os.path.join(self.__release_dir, 'pylib'),
      filter='*.py'))

    processes.append(self.start_copy_dir(
      os.path.join(self.__project_dir, 'runtime'),
      os.path.join(self.__release_dir, 'runtime'),
      filter='*.sh'))

    print 'Waiting for admin scripts to finish.'
    for p in processes:
      p.check_wait()

  def copy_release_config(self):
    """Copy configuration files into release."""
    source_dir = self.__options.config_source
    target_dir = os.path.join(self.__release_dir, 'config')

    # This is the contents of the release_config.cfg file.
    # Which acts as manifest to inform the installer what packages to install.
    fd, temp_file = tempfile.mkstemp()
    os.write(fd, '# This file is not intended to be user-modified.\n'
                 'CONFIG_LIST="{configs}"\n'
                 'PACKAGE_LIST="{packages}"\n'
                 .format(configs=' '.join(self.__config_list),
                    packages=' '.join(self.__package_list)))
    os.close(fd)

    try:
      self.start_copy_file(
        temp_file, os.path.join(target_dir, 'release_config.cfg')).check_wait()
    finally:
      os.remove(temp_file)

  def build_web_installer_zip(self):
    """Build encapsulated python zip file for install_spinnaker.py

    This is useful as an installer that can be pointed at a release somewhere,
    and just pull and install it onto any machine. Unfortunately you cannot
    directly run a zip through stdin so need to download the zip first, then
    run it. The zip is packaged as part of the release for distribution
    convenience.
    """
    fd, zip_path = tempfile.mkstemp()
    os.close(fd)

    zip = zipfile.ZipFile(zip_path, 'w')

    try:
      zip.writestr('__main__.py', """
from install_spinnaker import main
import os
import sys

if __name__ == '__main__':
  if len(sys.argv) == 1 and os.environ.get('RELEASE_PATH', ''):
      sys.argv.extend('--release_path', os.environ['RELEASE_PATH'])
  retcode = main()
  sys.exit(retcode)
""")

      dep_root = os.path.dirname(__file__) + '/..'
      deps = ['install/install_spinnaker.py',
              'install/install_runtime_dependencies.py',
              'pylib/spinnaker/run.py',
              'pylib/spinnaker/fetch.py']
      for file in deps:
          with open(os.path.join(dep_root, file), 'r') as f:
            zip.writestr(os.path.basename(file),
                         f.read().replace('from spinnaker.', 'from '))
      zip.close()
      zip = None

      shutil.move(zip_path, './install_spinnaker.py.zip')
      p = self.start_copy_file('./install_spinnaker.py.zip',
                               os.path.join(self.__release_dir,
                                            'install/install_spinnaker.py.zip'))
      p.check_wait()
    finally:
      if zip is not None:
        zip.close()

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
    fd, zip_path = tempfile.mkstemp()
    os.close(fd)

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
      test_py = '{test_name}.py'.format(test_name=test_name)
      zip.write('citest/spinnaker/spinnaker_system/' + test_py, test_py)
      zip.close()

      p = self.start_copy_file(
          zip_path, os.path.join(self.__release_dir, 'tests', test_py + '.zip'))
      p.check_wait()
    finally:
      os.remove(zip_path)

  def add_test_zip_files(self):
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

      config_path= os.path.abspath(os.path.join(os.path.dirname(__file__),
                                                '../config'))
      parser.add_argument(
          '--config_source', default=config_path,
          help='Path to directory for release config file templates.')

      parser.add_argument('--release_path', required=True,
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

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    builder = cls(options)
    if options.pull_origin:
        builder.refresher.pull_all_from_origin()

    builder.build_packages()
    builder.build_web_installer_zip()

    builder.copy_dependency_files()
    builder.copy_install_scripts()
    builder.copy_admin_scripts()
    builder.copy_release_config()
    builder.add_test_zip_files()

    print '\nFINISHED writing release to {dir}'.format(
        dir=builder.__release_dir)


if __name__ == '__main__':
  Builder.main()
