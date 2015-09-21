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


import refresh_source
from install.install_utils import run_or_die_no_result


SUBSYSTEM_LIST = ['gce-kms', 'clouddriver', 'orca', 'front50',
                  'rush', 'echo', 'rosco', 'gate', 'igor', 'deck']


def is_gcs_path(path):
  return len(path) >= 5 and path[0:5] == 'gs://'


def ensure_bucket(name, project=''):
  if not project:
      p = subprocess.Popen('gcloud config list', shell=True,
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE)
      stdout, stderr = p.communicate()
      match = re.search('(?m)^project = (.*)', stdout)
      if not match:
        error = ('gcloud is not configured with a default project.\n'
                 'run gcloud config or provide a --release_project.\n')
        sys.stderr.write(error + '\n')
        raise RuntimeError(error)
      project = match.groups()[0]

  p = subprocess.Popen('gsutil list -p ' +  project, shell=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  stdout, stderr = p.communicate()
  if p.returncode:
    error = ('Could not create Google Cloud Storage bucket'
             '"{name}" in project "{project}":\n{error}'
             .format(name=name, project=project, error=stderr))
    sys.stderr.write(error + '\n')
    raise SystemError(error)

  bucket = 'gs://{name}'.format(name=name)
  if re.search('(?m)^{bucket}'.format(bucket=bucket), stdout):
    sys.stderr.write(
        'WARNING: "{bucket}" already exists. Overwriting.\n'.format(
        bucket=bucket))
  else:
    print 'Creating bucket "{bucket}" in project "{project}".'.format(
        bucket=bucket, project=project)
    run_or_die_no_result('gsutil mb -p {project} {bucket}'
                         .format(project=project, bucket=bucket))


class BackgroundProcess(
    collections.namedtuple('BackgroundProcess', ['name', 'subprocess'])):
  @staticmethod
  def spawn(name, args):
        sp = subprocess.Popen(args, shell=True, close_fds=True,
                              stdout=sys.stdout, stderr=subprocess.STDOUT)
        return BackgroundProcess(name, sp)
  def wait(self):
    if not self.subprocess:
      return None
    return self.subprocess.wait()

  def wait_or_die(self):
    if self.wait():
      error = '{name} failed.'.format(name=self.name)
      sys.stderr.write(error + '\n')
      raise SystemError(error)


NO_PROCESS = BackgroundProcess('nop', None)


class Builder(object):
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
          os.path.dirname(sys.argv[0]) + '/..')
      self.__release_dir = os.path.join(options.release_repository_root,
                                        options.release)
      if is_gcs_path(self.__release_dir):
          ensure_bucket(name=self.__release_dir[5:].split('/')[0],
                        project=options.release_project)

  def start_build_target(self, name, target):
      print 'Building {name}...'.format(name=name)
      return BackgroundProcess.spawn(
          'Building {name}'.format(name=name),
          'cd {name}; ./gradlew {target}'.format(name=name, target=target))

  def start_copy_file(self, source, target):
      if is_gcs_path(target):
        return BackgroundProcess.spawn(
            'Copying {source}'.format,
            'gsutil -q -m cp {source} {target}'
            .format(source=source, target=target))
      else:
        try:
          os.makedirs(os.path.dirname(target))
        except OSError:
          pass

        shutil.copy(source, target)
        return NO_PROCESS

  def start_copy_debian_target(self, name):
      if os.path.exists(os.path.join(name, '{name}-web'.format(name=name))):
          submodule = '{name}-web'.format(name=name)
      elif os.path.exists(os.path.join(name, '{name}-core'.format(name=name))):
          submodule = '{name}-core'.format(name=name)
      else:
          submodule = '.'

      with open(os.path.join(name, submodule, 'build/debian/control')) as f:
         content = f.read()
      match = re.search('(?m)^Version: (.*)', content)
      version = match.groups()[0]
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
              sys.stderr.write(error)
              raise AssertionError(error)

      from_path = os.path.join(name, build_dir, package)
      to_path = os.path.join(self.__options.release_repository_root,
                             self.__options.release,
                             package)
      print 'Adding {path}'.format(path=from_path)
      self.__package_list.append(package)
      return self.start_copy_file(from_path, to_path)


  def __do_build(self, subsys):
    self.start_build_target(subsys, 'buildDeb').wait_or_die()

  def build_packages(self):
      if self.__options.build:
        # Build in parallel using half available cores
        # to keep load in check.
        pool = multiprocessing.pool.ThreadPool(
            processes=multiprocessing.cpu_count() / 2)
        pool.map(self.__do_build, SUBSYSTEM_LIST)

      processes = []
      for subsys in SUBSYSTEM_LIST:
          processes.append(self.start_copy_debian_target(subsys))
          if subsys == 'deck':
              source_config = os.path.join(
                  self.__project_dir, 'config/settings.js')
              target_config = os.path.join(
                  self.__release_dir, 'config/deck_settings.js')
          else:
              yml = '{name}-local.yml'.format(name=subsys)
              source_config = os.path.join(
                  self.__project_dir, 'config', yml)
              target_config = os.path.join(
                  self.__release_dir, 'config', yml)
              self.__config_list.append(yml)
          processes.append(self.start_copy_file(source_config, target_config))

      print 'Waiting for package copying to finish....'
      for p in processes:
        p.wait_or_die()

  def copy_dependency_files(self):
    source_dir = os.path.join(self.__project_dir, 'cassandra')
    target_dir = os.path.join(self.__release_dir, 'cassandra')
    processes = []
    for file in glob.glob(os.path.join(source_dir, '*')):
      processes.append(
         self.start_copy_file(file,
                              os.path.join(target_dir, os.path.basename(file))))

    print 'Waiting for dependency scripts.'
    for p in processes:
      p.wait_or_die()

  def copy_install_scripts(self):
    source_dir = os.path.join(self.__project_dir, 'install')
    target_dir = os.path.join(self.__release_dir, 'install')

    processes = []
    for file in glob.glob(os.path.join(source_dir, '*.py')):
       processes.append(
         self.start_copy_file(file,
                              os.path.join(target_dir, os.path.basename(file))))
    for file in glob.glob(os.path.join(source_dir, '*.sh')):
       processes.append(
         self.start_copy_file(file,
                              os.path.join(target_dir, os.path.basename(file))))

    print 'Waiting for install scripts to finish.'
    for p in processes:
      p.wait_or_die()

  def copy_admin_scripts(self):
    processes = []
    for file in glob.glob(os.path.join(self.__project_dir, 'pylib/*.py')):
      processes.append(
          self.start_copy_file(
            file,
            os.path.join(self.__release_dir, 'pylib', os.path.basename(file))))

    for file in glob.glob(os.path.join(self.__project_dir, 'runtime/*.sh')):
      processes.append(
          self.start_copy_file(
            file,
            os.path.join(self.__release_dir,
                         'scripts', os.path.basename(file))))

    print 'Waiting for admin scripts to finish.'
    for p in processes:
      p.wait_or_die()


  def copy_config_files(self):
    source_dir = os.path.join(self.__project_dir, 'config')
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
        os.path.join(source_dir, 'default_spinnaker_config.cfg'),
        os.path.join(target_dir, 'default_spinnaker_config.cfg')).wait_or_die()

      self.start_copy_file(
        temp_file, os.path.join(target_dir, 'release_config.cfg')).wait_or_die()
    finally:
      os.remove(temp_file)

  def add_python_test_zip(self, test_name):
    temp_dir = tempfile.mkdtemp()
    zip_file = os.path.join(temp_dir, test_name + '.zip')
    open(os.path.join(temp_dir, '__init__.py'), 'w').close()
    with open(os.path.join(temp_dir, '__main__.py'), 'w') as f:
      f.write("""
from {test_name} import main
import sys

if __name__ == '__main__':
  retcode = main()
  sys.exit(retcode)
""".format(test_name=test_name))

    try:
      # Add citest sources as baseline
      # TODO(ewiseblatt): 20150810
      # Eventually this needs to be the transitive closure,
      # but there are currently no other dependencies.
      run_or_die_no_result('cd citest; zip -R {zip_file} citest *.py'
                           .format(zip_file=zip_file),
                           echo=False)
      run_or_die_no_result('cd citest/spinnaker'
                           '; zip -g {zip_file} spinnaker_testing *.py'
                           '; cd spinnaker_system'
                           '; zip -g {zip_file} {test_name}.py'
                           .format(zip_file=zip_file, test_name=test_name),
                           echo=False)
      run_or_die_no_result(
            'cd {temp_dir}; zip -g {zip_file} __init__.py __main__.py'
            .format(temp_dir=temp_dir, zip_file=zip_file),
            echo=False)
      p = self.start_copy_file(
          zip_file, os.path.join(self.__release_dir, 'tests',
                                 os.path.basename(zip_file)))
      p.wait_or_die()
    finally:
      shutil.rmtree(temp_dir, ignore_errors=True)

  def add_test_zip_files(self):
     if not os.path.exists('citest'):
        print 'Adding citest repository'
        self.refresher.git_clone('citest', owner='google')

     print 'Adding tests...'
     self.add_python_test_zip('aws_kato_test')
     self.add_python_test_zip('kato_test')
     self.add_python_test_zip('smoke_test')

  @classmethod
  def init_argument_parser(cls, parser):
      refresh_source.Refresher.init_argument_parser(parser)
      parser.add_argument('--build', default=True, action='store_true',
                          help='Build the sources.')
      parser.add_argument('--nobuild', dest='build', action='store_false')

      parser.add_argument('--release', default='', required=True,
                          help='Specifies the name of the release to build.')
      parser.add_argument(
        '--release_project', default='',
        help='If release repository is a GCS bucket then this is the project'
        ' owning the bucket. The default is the project configured as the'
        ' default for gcloud.')
      parser.add_argument('--release_repository_root', default='gs://',
                          help='The path to the repository root to stick'
                            ' releases under. If this is gs:// then create a'
                            ' bucket (or store within one)')

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    builder = cls(options)
    if options.refresh_from_origin:
        builder.refresher.refresh_all_from_origin()

    builder.build_packages()
    builder.copy_dependency_files()
    builder.copy_install_scripts()
    builder.copy_admin_scripts()
    builder.copy_config_files()
    builder.add_test_zip_files()

    print '\nFINISHED writing release to {dir}'.format(
        dir=builder.__release_dir)


if __name__ == '__main__':
  Builder.main()
