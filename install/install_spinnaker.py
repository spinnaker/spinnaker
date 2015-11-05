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

"""
Installs spinnaker onto the local machine.

--release_path must be specified using either a path or storage service URI
   (either Google Compute Storage or Amazon S3).

Spinnaker depends on openjdk-8-jre. If this isnt installed but some other
equivalent JDK 1.8 is installed, then you can run install_fake_openjdk8.sh
to fake out the package manager. That script is included with this script.
"""

import argparse
import os
import re
import subprocess
import sys
import tempfile

import install_runtime_dependencies

from spinnaker.run import run_and_monitor
from spinnaker.run import check_run_and_monitor
from spinnaker.run import check_run_quick
from spinnaker.run import run_quick


def get_user_config_dir(options):
    """Returns the directory used to hold deployment configuration info."""
    return '/root/.spinnaker'


def get_config_install_dir(options):
    """Returns the directory used to hold the installation master config.

    These are not intended to be overriden, but -local variants can be added.
    """
    return (os.path.join(get_spinnaker_dir(options), 'config'))


def get_spinnaker_dir(options):
    """Returns the spinnaker installation directory."""
    path = options.spinnaker_dir or '/opt/spinnaker'
    if not os.path.exists(path):
        print 'Creating spinnaker_dir=' + path
        safe_mkdir(path)
    return path


def init_argument_parser(parser):
    install_runtime_dependencies.init_argument_parser(parser)
    parser.add_argument(
        '--dependencies', default=True, action='store_true',
        help='Install the runtime system dependencies.')
    parser.add_argument(
        '--nodependencies', dest='dependencies', action='store_false')

    parser.add_argument(
        '--spinnaker', default=True, action='store_true',
        help='Install spinnaker subsystems.')
    parser.add_argument(
        '--nospinnaker', dest='spinnaker', action='store_false')

    parser.add_argument(
        '--spinnaker_dir', default=None,
        help='Nonstandard path to install spinnaker files into.')

    parser.add_argument(
        '--release_path', default=None,
        help='The path to the release being installed.')


def safe_mkdir(dir):
    """Create a local directory if it does not already exist.

    Args:
      dir [string]: The path to the directory to create.
    """
    result = run_quick('sudo mkdir -p "{dir}"'.format(dir=dir), echo=False)
    if result.returncode:
      raise RuntimeError('Could not create directory "{dir}": {error}'.format(
          dir=dir, error=result.stdout))


def path_exists(path):
   """Determine if a path exists or not.

   Args:
     path [string]: A local or bucket path.
   """
   if path.startswith('gs://'):
       command = 'gsutil ls {path} >& /dev/null'.format(path=path)
   elif path.startswith('s3://'):
       command = 'awscli s3 ls {path} >& /dev/null'.format(path=path)
   else:
       return os.path.exists(path)

   return run_quick(command, echo=False).returncode == 0


def start_copy_file(options, source, target, dir=False):
   """Copy a file.

   Args:
     source [string]: The path to copy from is either local or the URI for
        a storage service (Amazon S3 or Google Cloud Storage).
     target [string]: A local path to copy to.

   Returns:
     A subprocess instance performing the copy.
   """
   if source.startswith('gs://'):
     if dir:
       safe_mkdir(target)
     command = ('sudo bash -c'
                ' "PATH=$PATH gsutil -m -q cp {R} \"{source}\"{X} \"{target}\""'
                .format(source=source, target=target,
                        R='-R' if dir else '',
                        X='/*' if dir else ''))
   elif source.startswith('s3://'):
     command = ('sudo bash -c'
                ' "PATH=$PATH aws s3 cp {R} --region {region}'
                ' \"{source}\" \"{target}\""'
                .format(source=source, target=target, region=options.region,
                        R='--recursive' if dir else ''))
   else:
     # Use a shell to copy here to handle wildcard expansion.
     command = 'sudo cp "{source}" "{target}"'.format(
        source=source, target=target)

   process = subprocess.Popen(command, stderr=subprocess.PIPE, shell=True)
   return process


def start_copy_dir(options, source, target):
  return start_copy_file(options, source, target, dir=True)


def check_wait_for_copy_complete(jobs):
  """Waits for each of the subprocesses to finish.

  Args:
    jobs [list of subprocess]: Jobs we are waiting on.

  Raises:
    RuntimeError if any of the copies failed.
  """
  for j in jobs:
    stdout, stderr = j.communicate()

    if j.returncode != 0:
        output = stdout or stderr or ''
        error = 'COPY FAILED with {0}: {1}'.format(j.returncode, output.strip())
        raise RuntimeError(error)


def get_release_metadata(options, bucket):
  """Gets metadata files from the release.

  This sets the global PACKAGE_LIST and CONFIG_LIST variables
  telling us specifically what we'll need to install.

  Args:
    options [namespace]: The argparse namespace with options.
    bucket [string]: The path or storage service URI to pull from.
  """
  spinnaker_dir = get_spinnaker_dir(options)
  safe_mkdir(spinnaker_dir)
  job = start_copy_file(options,
                        os.path.join(bucket, 'config/release_config.cfg'),
                        spinnaker_dir)
  check_wait_for_copy_complete([job])

  with open(os.path.join(spinnaker_dir, 'release_config.cfg'), 'r') as f:
    content = f.read()
    global PACKAGE_LIST
    global CONFIG_LIST
    PACKAGE_LIST = (re.search('\nPACKAGE_LIST="(.*?)"', content)
                   .group(1).split())
    CONFIG_LIST = (re.search('\nCONFIG_LIST="(.*?)"', content)
                  .group(1).split())


def check_google_path(path):
  check_result = run_quick('gsutil --version', echo=False)
  if check_result.returncode:
      error = """
ERROR: gsutil is required to retrieve the spinnaker release from GCS.
       If you already have gsutil, fix your path.
       Otherwise follow the instructions at
       https://cloud.google.com/storage/docs/gsutil_install?hl=en#install
       and be sure you run gsutil config.
       Then run again.
"""
      raise RuntimeError(error)

  result = run_quick('gsutil ls ' + path, echo=False)
  if result.returncode:
      error = ('The path "{dir}" does not seem to exist within GCS.'
               ' gsutil ls returned "{stdout}"\n'.format(
                    dir=path,  stdout=result.stdout.strip()))
      raise RuntimeError(error)


def check_s3_path(path):
  check_result = run_quick('aws --version', echo=False)
  if check_result.returncode:
    error = """
ERROR: aws is required to retrieve the spinnaker release from S3.
       If you already have aws, fix your path.
       Otherwise install awscli with "sudo apt-get install awscli".
       Then run again.
"""
    raise RuntimeError(error)

  result = run_quick('aws s3 ls ' + path, echo=False)
  if result.returncode:
      error = ('The path "{dir}" does not seem to exist within S3.'
               ' aws s3 ls returned "{stdout}"\n'.format(
                    dir=path,  stdout=result.stdout.strip()))
      raise RuntimeError(error)


def check_release_dir(options):
  """Verify the options specify a release_path we can read.

  Args:
    options [namespace]: The argparse namespace
  """
  if not options.release_path:
    error = ('--release_path cannot be empty.'
             ' Either specify a --release or a --release_path.')
    raise ValueError(error)

  if os.path.exists(options.release_path):
      return

  if options.release_path.startswith('gs://'):
      check_google_path(options.release_path)
  elif options.release_path.startswith('s3://'):
      check_s3_path(options.release_path)
  else:
      error = 'Unknown path --release_path={dir}\n'.format(
          dir=options.release_path)
      raise ValueError(error)


def check_options(options):
  """Verify the options make sense.

  Args:
    options [namespace]: The options from argparser.
  """
  install_runtime_dependencies.check_options(options)
  check_release_dir(options)
  if (options.release_path.startswith('s3://')
      and not options.region):
    raise ValueError('--region is required with an S3 release-uri.')


def inject_spring_config_location(options, subsystem):
  """Add spinnaker.yml to the spring config location path.

  This might be temporary. Once this is standardized perhaps the packages will
  already be shipped with this.
  """
  if subsystem == "deck":
    return

  path = os.path.join('/opt', subsystem, 'bin', subsystem)
  with open(path, 'r') as f:
      content = f.read()
  match = re.search('\nDEFAULT_JVM_OPTS=(.+)\n', content)
  if not match:
      raise ValueError('Expected DEFAULT_JVM_OPTS in ' + path)
  value = match.group(1)

  if value.find('-Dspring.config.location=') >= 0:
      sys.stderr.write(
          'WARNING: spring.config.location was already explicitly defined.'
          '\nLeaving ' + match.group(0) + '\n')  # Show whole thing.
      return

  new_content = [content[0:match.start(1)]]

  offset = 1 if value[0] == '\'' or value[0] == '"' else 0
  quote = '"' if value[0] == '\'' else '\''
  root = '/opt/spinnaker/config'
  home = '/root/.spinnaker'
  new_content.append(value[0:offset])
  new_content.append('{quote}-Dspring.config.location={root}/,{home}/{quote}'
                     .format(quote=quote, home=home, root=root))
  new_content.append(' ')

  new_content.append(content[match.start(1) + 1:])
  fd,temp = tempfile.mkstemp()
  os.write(fd, ''.join(new_content))
  os.close(fd)

  check_run_quick(
        'chmod --reference={path} {temp}'.format(path=path, temp=temp))
  check_run_quick('sudo mv {temp} {path}'.format(temp=temp, path=path))


def install_spinnaker_packages(options, bucket):
  """Install the spinnaker packages from the specified path.

  Args:
    bucket [string]: The path to install from, or a storage service URI.
  """
  if not options.spinnaker:
      return

  print 'Installing Spinnaker components from {0}.'.format(bucket)

  install_config_dir = get_config_install_dir(options)
  spinnaker_dir = get_spinnaker_dir(options)

  jobs = []

  ###########################
  # Copy Configuration files
  ###########################
  print 'Copying configuration files.'
  safe_mkdir(install_config_dir)

  # For now we are copying both the old configuration files
  # and the new ones.
  # The new ones are not yet fully working so we are keeping
  # the old ones around. It's particularly messy because the two
  # cohabitate the same directory in the bucket. We separate them
  # out in the installation so that the old -local files arent
  # intercepted (with precedence) when using the new files.
  # The new files are not enabled by default.

  for cfg in CONFIG_LIST:
    jobs.append(start_copy_file(options,
                                os.path.join(bucket, 'config', cfg),
                                install_config_dir))
  jobs.append(
      start_copy_file(
          options,
          os.path.join(bucket, 'config/settings.js'),
          os.path.join(bucket, install_config_dir + '/settings.js')))
  check_wait_for_copy_complete(jobs)
  jobs = []

  #############
  # Copy Tests
  #############
  tests_dir = os.path.join(spinnaker_dir, 'tests')
  if path_exists(os.path.join(bucket, 'tests')):
      print 'Copying tests.'
      jobs.append(
          start_copy_dir(options,
                         os.path.join(bucket, 'tests'),
                         tests_dir))

  ###########################
  # Copy Subsystem Packages
  ###########################
  print 'Downloading spinnaker release packages...'
  package_dir = os.path.join(spinnaker_dir, 'install')
  safe_mkdir(package_dir)
  for pkg in PACKAGE_LIST:
    jobs.append(start_copy_file(options,
                                os.path.join(bucket, pkg), package_dir))

  check_wait_for_copy_complete(jobs)

  for pkg in PACKAGE_LIST:
    print 'Installing {0}.'.format(pkg)

    # Let this fail because it may have dependencies
    # that we'll pick up below.
    run_and_monitor('sudo dpkg -i ' + os.path.join(package_dir, pkg))
    check_run_and_monitor('sudo apt-get install -f -y')
    # Convert package name to install directory name.
    inject_spring_config_location(options, pkg[0:pkg.find('_')])

  # Install package dependencies
  check_run_and_monitor('sudo apt-get install -f -y')


def install_spinnaker(options):
  """Install the spinnaker packages.

  Args:
    options [namespace]: The argparse options.
  """
  if not (options.spinnaker or options.dependencies):
      return

  # The bucket might just be a plain-old path.
  # But could be a gs:// URL to a path in a Google Cloud Storage bucket.
  bucket = options.release_path

  get_release_metadata(options, bucket)
  install_spinnaker_packages(options, bucket)

  spinnaker_dir = get_spinnaker_dir(options)

  #####################################
  # Copy Scripts and Cassandra Schemas
  #####################################
  install_dir = os.path.join(spinnaker_dir, 'install')
  script_dir = os.path.join(spinnaker_dir, 'scripts')
  pylib_dir = os.path.join(spinnaker_dir, 'pylib')
  cassandra_dir = os.path.join(spinnaker_dir, 'cassandra')

  jobs = []
  print 'Installing spinnaker scripts.'

  # Note this also copies some install files that may already be there
  # depending on when this script is being run. The files in the release
  # may be different, but they are the release we are installing.
  # If this is an issue, we can look into copy without overwriting.
  jobs.append(start_copy_dir(options,
                             os.path.join(bucket, 'install'),
                             install_dir))
  jobs.append(start_copy_dir(options,
                             os.path.join(bucket, 'runtime'), script_dir))
  jobs.append(start_copy_dir(options,
                             os.path.join(bucket, 'pylib'), pylib_dir))

  print 'Installing cassandra schemas.'
  jobs.append(start_copy_dir(options,
                             os.path.join(bucket, 'cassandra'), cassandra_dir))
  check_wait_for_copy_complete(jobs)

  # Use chmod since +x is convienent.
  # Fork a shell to do the wildcard expansion.
  check_run_quick('sudo chmod +x {files}'
                  .format(files=os.path.join(spinnaker_dir, 'scripts/*.sh')))
  check_run_quick('sudo chmod +x {files}'
                  .format(files=os.path.join(spinnaker_dir, 'install/*.sh')))

  user_config_dir = get_user_config_dir(options)
  install_config_dir = get_config_install_dir(options)
  local_yml_path = os.path.join(user_config_dir, 'spinnaker-local.yml')
  if not os.path.exists(local_yml_path):
    print 'Copying a default spinnaker-local.yml'
    prototype_path = os.path.join(install_config_dir,
                                  'default-spinnaker-local.yml')
    local_yml_content = make_default_spinnaker_yml_from_path(prototype_path)

    fd,temp = tempfile.mkstemp()
    os.write(fd, local_yml_content)
    os.close(fd)

    commands = ['mkdir -p {config_dir}'
                    .format(config_dir=user_config_dir),
                'cp {temp} {config_dir}/spinnaker-local.yml'
                    .format(temp=temp, config_dir=user_config_dir),
                'chmod 600 {config_dir}/spinnaker-local.yml'
                    .format(temp=temp, config_dir=user_config_dir),
                'rm -f {temp}'.format(temp=temp)]
    check_run_quick('sudo bash -c "{commands}"'
                    .format(commands=' && '.join(commands)), echo=True)


def make_default_spinnaker_yml_from_path(prototype_path):
  with open(prototype_path, 'r') as f:
     content = f.read()
  return content


def main():
  parser = argparse.ArgumentParser()
  init_argument_parser(parser)
  options = parser.parse_args()

  check_options(options)

  if options.dependencies:
    install_runtime_dependencies.install_runtime_dependencies(options)
  else:
      if install_runtime_dependencies.check_java_version() is not None:
          install_runtime_dependencies.install_java(options)
      if options.update_os:
          install_runtime_dependencies.install_os_updates(options)
      if options.spinnaker:
          install_runtime_dependencies.install_apache(options)

  install_spinnaker(options)


if __name__ == '__main__':
     main()
