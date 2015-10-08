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

If running this on a machine without GCS access (either because it does not
have a scope or does not have gsutil configured) you can load from an
existing directory containing the release. Otherwise you must load from a
gcs bucket containing the release
    --release       specifies thename of a release to install from the
                    standard spinnaker release repository. The standard
                    repository is currently gs:// (i.e. release is any bucket)

    --release_path  specifies an explicit path to the release instead of
                    using --release.

Spinnaker depends on openjdk-8-jre. If this isnt installed but some other
equivalent JDK 1.8 is installed, then you can do something like the following
to satisfy the package manager:

cat > ignore-openjdk-8-jre.txt <<EOF
Section: misc
Priority: optional
Standards-Version: 3.9.2

Package: ignore-openjdk-8-jre
Version: 1.0
Provides: openjdk-8-jre
Description: Ignore openjdk-8-jre dependency
EOF

equivs-build ignore-openjdk-8-jre.txt
sudo dkg -i ignore-openjdk-8-jre_1.0_all.deb
"""

import argparse
import os
import re
import subprocess
import sys

import google_install_loader
import install_runtime_dependencies

from install_utils import fetch_or_die
from install_utils import run
from install_utils import run_or_die


# The root path to look up standard releases by name.
RELEASE_REPOSITORY = 'gs://'


HOME = os.environ['HOME'] if 'HOME' in os.environ else '/root'
def get_config_dir(options):
    return os.path.join(HOME, '.spinnaker')


def get_config_template_dir(options):
    return (options.config_template_dir
            or os.path.join(get_spinnaker_dir(options), 'config_templates'))


def get_spinnaker_dir(options):
    path = options.spinnaker_dir or '/opt/spinnaker'
    if not os.path.exists(path):
        print 'Creating spinnaker_dir=' + path
        safe_mkdir(path)
    return path


class SetReleaseName(argparse.Action):
    def __call__(self, parser, namespace, values, options_string=None):
        if isinstance(values, list):
          raise ValueError(
              'Did not expect multiple arguments for "--release"')
        setattr(namespace, self.dest, '{release_root}{name}'.format(
            release_root=RELEASE_REPOSITORY, name=values))


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
        '--release', action=SetReleaseName, dest='release_path',
        help='A named release is implied to be a GCS bucket.')
    parser.add_argument(
        '--release_path', default=None,
        help='The path to the release being installed.')

    parser.add_argument(
        '--config_template_dir', default=None,
        help='Nonstandard place to keep *-local.yml templates'
             ' to restore from when reconfiguring.')


def safe_mkdir(dir):
    process = subprocess.Popen('sudo mkdir -p {dir}'.format(dir=dir),
                               stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, shell=True)
    stdout, stderr = process.communicate()
    if process.returncode:
        raise RuntimeError('Could not create directory "{dir}": {error}'.format(
            dir=dir, error=stdout))


def start_copy_file(source, target):
   if source[0:3] == 'gs:':
     command = ('sudo bash -c'
                ' "PATH=$PATH gsutil -m -q cp \"{source}\" \"{target}\""'
                .format(source=source, target=target))
   else:
     # Use a shell to copy here to handle wildcard expansion.
     command = 'sudo cp "{source}" "{target}"'.format(
         source=source, target=target)

   process = subprocess.Popen(command, stderr=subprocess.PIPE, shell=True)
   return process


def wait_for_copy_complete(jobs):
  for j in jobs:
      stdout, stderr = j.communicate()
      if j.returncode != 0:
          sys.stderr.write('COPY FAILED with {0}\n'.format(stderr))


def get_release_metadata(options, bucket):
  spinnaker_dir = get_spinnaker_dir(options)
  safe_mkdir(spinnaker_dir)
  job = start_copy_file(os.path.join(bucket, 'config/release_config.cfg'),
                        spinnaker_dir)
  wait_for_copy_complete([job])

  with open(os.path.join(spinnaker_dir, 'release_config.cfg'), 'r') as f:
    content = f.read()
    global PACKAGE_LIST
    global CONFIG_LIST
    PACKAGE_LIST = (re.search('\nPACKAGE_LIST="(.*?)"', content)
                   .group(1).split())
    CONFIG_LIST = (re.search('\nCONFIG_LIST="(.*?)"', content)
                  .group(1).split())


def check_release_dir(options):
  if not options.release_path:
    error = ('--release_path cannot be empty.'
             ' Either specify a --release or a --release_path.')
    sys.stderr.write(error)
    raise ValueError(error)

  if os.path.exists(options.release_path):
      return

  if not options.release_path[0:3] == 'gs:':
      error = 'Unknown path --release_path={dir}\n'.format(dir=options.release_path)
      sys.stderr.write(error)
      raise ValueError(error)

  code = subprocess.Popen('gsutil v', shell=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).wait()
  if not code:
      error = """
ERROR: gsutil is required to retrieve the spinnaker release from GCS.
       If you already have gsutil, fix your path.
       Otherwise follow the instructions at
       https://cloud.google.com/storage/docs/gsutil_install?hl=en#install
       and be sure you run gsutil config.
       Then run again.
"""
      sys.stderr.write(error)
      raise RuntimeError(error)

  code = subprocess.Popen('gsutil ls ' + options.release_path, shell=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).wait()
  if code:
      error = ('--release={dir} does not seem to exist within GCS.'
               ' Check the permissions.\n'.format(dir=options.release_path))
      sys.stderr.write(error)
      raise RuntimeError(error)

def check_options(options):
  install_runtime_dependencies.check_options(options)
  check_release_dir(options)


def install_spinnaker_packages(options, bucket):
  if not options.spinnaker:
      return

  print 'Installing Spinnaker components from {0}.'.format(bucket)

  config_dir = get_config_dir(options)
  template_dir = get_config_template_dir(options)
  spinnaker_dir = get_spinnaker_dir(options)

  jobs = []

  ###########################
  # Copy Configuration files
  ###########################
  print 'Copying configuration files.'
  safe_mkdir(config_dir)
  safe_mkdir(template_dir)
  for cfg in CONFIG_LIST:
    jobs.append(start_copy_file(os.path.join(bucket, 'config', cfg),
                                config_dir))
    jobs.append(start_copy_file(os.path.join(bucket, 'config', cfg),
                                template_dir))

  jobs.append(
      start_copy_file(os.path.join(bucket, 'config/deck_settings.js'),
                      template_dir))
  jobs.append(
        start_copy_file(
            os.path.join(bucket, 'config/default_spinnaker_config.cfg'),
            template_dir))

  #############
  # Copy Tests
  #############
  print 'Copying tests.'
  tests_dir = os.path.join(spinnaker_dir, 'tests')
  safe_mkdir(tests_dir)
  jobs.append(start_copy_file(os.path.join(bucket, 'tests/*'), tests_dir))

  ###########################
  # Copy Subsystem Packages
  ###########################
  print 'Downloading spinnaker release packages...'
  package_dir = os.path.join(spinnaker_dir, 'install')
  safe_mkdir(package_dir)
  for pkg in PACKAGE_LIST:
    jobs.append(start_copy_file(os.path.join(bucket, pkg), package_dir))

  wait_for_copy_complete(jobs)

  for pkg in PACKAGE_LIST:
    print 'Installing {0}.'.format(pkg)

    # Let this fail because it may have dependencies
    # that we'll pick up below.
    run('sudo dpkg -i ' + os.path.join(package_dir, pkg))
    run_or_die('sudo apt-get install -f -y')

  # Install package dependencies
  run_or_die('sudo apt-get install -f -y')

  run_or_die('sudo cp {source} {target}'.format(
      source=os.path.join(template_dir, 'deck_settings.js'),
      target='/var/www/settings.js'))


def install_spinnaker(options):
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
  safe_mkdir(install_dir)
  jobs.append(start_copy_file(os.path.join(bucket, 'install/*'),
                              install_dir))
  safe_mkdir(script_dir)
  jobs.append(start_copy_file(os.path.join(bucket, 'scripts/*'), script_dir))
  safe_mkdir(pylib_dir)
  jobs.append(start_copy_file(os.path.join(bucket, 'pylib/*'), pylib_dir))

  print 'Installing cassandra schemas.'
  safe_mkdir(cassandra_dir)
  jobs.append(start_copy_file(os.path.join(bucket, 'cassandra/*'),
                              cassandra_dir))

  wait_for_copy_complete(jobs)

  # Use chmod since +x is convienent.
  # Fork a shell to do the wildcard expansion.
  run_or_die('sudo chmod +x {files}'
             .format(files=os.path.join(spinnaker_dir, 'scripts/*.sh')))
  run_or_die('sudo chmod +x {files}'
             .format(files=os.path.join(spinnaker_dir, 'install/*.sh')))


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

  # This is really just a signal
  if google_install_loader.running_on_gce():
    google_install_loader.write_instance_metadata(
          'spinnaker-sentinal', 'READY')

if __name__ == '__main__':
   main()
