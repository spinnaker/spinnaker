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

Publishing is typically to bintray. It is currently possible to publish
to a filesystem or storage bucket but this option will be removed soon
since the installation from these sources is no longer supported.

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
import fnmatch
import os
import multiprocessing
import multiprocessing.pool
import re
import shutil
import subprocess
import sys
import tempfile
import urllib2
import zipfile
from urllib2 import HTTPError

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

  def __init__(self, options):
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

  def determine_gradle_root(self, name):
      gradle_root = (name if name != 'spinnaker'
              else os.path.join(self.__project_dir, 'experimental/buildDeb'))
      gradle_root = name if name != 'spinnaker' else self.__project_dir
      return gradle_root

  def start_build_target(self, name, target):
      """Start a subprocess to build the designated target.

      Args:
        name [string]: The name of the subsystem repository.
        target [string]: The gradle build target.

      Returns:
        BackgroundProcess
      """
      # Currently spinnaker is in a separate location
      gradle_root = self.determine_gradle_root(name)
      print 'Building {name}...'.format(name=name)
      return BackgroundProcess.spawn(
          'Building {name}'.format(name=name),
          'cd "{gradle_root}"; ./gradlew {target}'.format(
              gradle_root=gradle_root, target=target))

  def publish_to_bintray(self, source, package, version, path, debian_tags=''):
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']
    parts = self.__options.bintray_repo.split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --bintray_repo to be in the form <owner>/<repo')
    subject, repo = parts[0], parts[1]

    deb_filename = os.path.basename(path)
    if (deb_filename.startswith('spinnaker-')
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
              # Since we are building from source, we dont really have a version
              # yet, since we are still modifying the code. Either we need to generate a new
              # version number every time or we dont want to publish these.
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
                # Maybe it didnt exist. Try again anyway.
                print 'Delete {url} got {ex}. Try again anyway.'.format(url=url, ex=ex)
                pass
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
              pkg_data = """{{
                "name": "{package}",
                "licenses": ["Apache-2.0"],
                "vcs_url": "https://github.com/spinnaker/{gitname}.git",
                "website_url": "http://spinnaker.io",
                "github_repo": "spinnaker/{gitname}",
                "public_download_numbers": false,
                "public_stats": false
              }}'""".format(package=package,
                            gitname=package.replace('spinnaker-', ''))

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
    path = 'InstallSpinnaker.sh'
    gradle_root = self.determine_gradle_root('spinnaker')
    version = determine_package_version(gradle_root, '.')

    self.publish_to_bintray(source, package='spinnaker', version=version,
                            path='InstallSpinnaker.sh')

  def publish_file(self, source, package, version):
    """Write a file to the bintray repository.

    Args:
      source [string]: The path to the source to copy must be local.
    """
    path = os.path.basename(source)
    debian_tags = ';'.join(['deb_component=spinnaker',
                            'deb_distribution=trusty,utopic,vivid,wily',
                            'deb_architecture=all'])

    self.publish_to_bintray(source, package=package, version=version,
                            path=path, debian_tags=debian_tags)


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
      gradle_root = self.determine_gradle_root(name)
      if os.path.exists(os.path.join(name, '{name}-web'.format(name=name))):
          submodule = '{name}-web'.format(name=name)
      elif os.path.exists(os.path.join(name, '{name}-core'.format(name=name))):
          submodule = '{name}-core'.format(name=name)
      else:
          submodule = '.'

      version = determine_package_version(gradle_root, submodule)
      build_dir = '{submodule}/build/distributions'.format(submodule=submodule)

      deb_dir = os.path.join(gradle_root, build_dir)
      non_spinnaker_name = '{name}_{version}_all.deb'.format(
            name=name, version=version)

      if os.path.exists(os.path.join(deb_dir,
                                     'spinnaker-' + non_spinnaker_name)):
        deb_file = 'spinnaker-' + non_spinnaker_name
      else:
        deb_file = non_spinnaker_name

      if not os.path.exists(os.path.join(deb_dir, deb_file)):
         error = ('.deb for name={name} version={version} is not in {dir}\n'
                  .format(name=name, version=version, dir=deb_dir))
         raise AssertionError(error)

      from_path = os.path.join(gradle_root, build_dir, deb_file)
      print 'Adding {path}'.format(path=from_path)
      self.__package_list.append(deb_file)
      if self.__options.bintray_repo:
        self.publish_file(from_path, name, version)

      if self.__release_dir:
        to_path = os.path.join(self.__release_dir, deb_file)
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

      # Copy subsystem packages.
      processes = []
      for subsys in SUBSYSTEM_LIST:
          processes.append(self.start_copy_debian_target(subsys))

      print 'Waiting for package copying to finish....'
      for p in processes:
        p.check_wait()

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

      parser.add_argument(
        '--wipe_package_on_409', default=False, action='store_true',
        help='Work around BinTray conflict errors by deleting the entire package and'
             ' retrying. Removes all prior versions so only intended for dev repos.\n')
      parser.add_argument(
        '--nowipe_package_on_409', dest='wipe_package_on_409', action='store_false')


  def __verify_bintray(self):
    if not os.environ.get('BINTRAY_KEY', None):
      raise ValueError('BINTRAY_KEY environment variable not defined')
    if not os.environ.get('BINTRAY_USER', None):
      raise ValueError('BINTRAY_USER environment variable not defined')


  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()

    if not (options.release_path or options.bintray_repo):
      sys.stderr.write(
           'ERROR: Missing either a --release_path or --bintray_repo')
      return -1

    builder = cls(options)
    if options.pull_origin:
        builder.refresher.pull_all_from_origin()

    builder.build_tests()
    builder.build_packages()

    if options.bintray_repo:
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


    if options.release_path:
      print '\nFINISHED writing release to {dir}'.format(
        dir=builder.__release_dir)

if __name__ == '__main__':
  sys.exit(Builder.main())
