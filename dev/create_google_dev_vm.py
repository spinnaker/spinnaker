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
import os
import re
import sys
import tempfile
import time

from spinnaker.run import run_quick
from spinnaker.run import check_run_quick
from spinnaker.yaml_util import YamlBindings


__NEXT_STEP_INSTRUCTIONS = """
To finish the installation, follow these steps:

(1) Log into your new instance (with or without tunneling ssh-flags):

  gcloud compute ssh --project {project} --zone {zone} {instance}\
 --ssh-flag="-L 9000:localhost:9000"\
 --ssh-flag="-L 8084:localhost:8084"


(2) Wait for the installation to complete:

  tail -f /var/log/startupscript.log

  When the instance startup script finishes installing the developer tools
  you will be ready to continue. ^C to terminate the tail process.


(3) Set up the build environment:

  source /opt/spinnaker/install/bootstrap_dev.sh


(4a) Build and run directly from the sources:

  ../spinnaker/dev/run_dev.sh

- or -

(4b) Build a release:

  ../spinnaker/dev/build_release.sh --release_path=$RELEASE_PATH

  A release can be used to produce a cloud provider image or installed directly.


For more help, see the Spinnaker Build & Run Book:

  https://docs.google.com/document/d/1Q_ah8eG3Imyw-RWS1DSp_ItM2pyn56QEepCeaOAPaKA

"""


def get_project(options):
    """Determine the default project name.

    The default project name is the gcloud configured default project.
    """
    if not options.project:
      result = check_run_quick('gcloud config list', echo=False)
      options.project = re.search('project = (.*)\n', result.stdout).group(1)
    return options.project


def init_argument_parser(parser):
    parser.add_argument(
        '--instance',
        default='{user}-spinnaker-dev'.format(user=os.environ['USER']),
        help='The name of the GCE instance to create.')
    parser.add_argument(
        '--project', default=None,
        help='The Google Project ID to create the new instance in.'
        ' If left empty, use the default project gcloud was configured with.')

    parser.add_argument(
        '--zone', default='us-central1-f',
        help='The Google Cloud Platform zone to create the new instance in.')

    parser.add_argument(
        '--disk_type',  default='pd-standard',
        help='The Google Cloud Platform disk type to use for the new instance.'
        '  The default is pd-standard. For a list of other available options,'
        ' see "gcloud compute disk-types list".')
    parser.add_argument('--disk_size', default='200GB',
                        help='Warnings appear if disk size < 200GB')
    parser.add_argument('--machine_type', default='n1-highmem-8')
    parser.add_argument(
        '--nopersonal', default=False, action='store_true',
        help='Do not copy personal files (.gitconfig, etc.)')
    parser.add_argument(
        '--copy_private_files', default=False, action='store_true',
        help='Also copy private files (.ssh/id_rsa*, .git-credentials, etc)')

    parser.add_argument(
        '--aws_credentials', default=None,
        help='If specified, the path to the aws credentials file.')
    parser.add_argument(
        '--master_yml', default=None,
        help='If specified, the path to the master spinnaker-local.yml file.')
    parser.add_argument(
        '--address', default=None,
        help='The IP address to assign to the new instance. The address may'
             ' be an IP address or the name or URI of an address resource.')
    parser.add_argument(
        '--scopes', default='compute-rw,storage-rw',
        help='Create the instance with these scopes.'
        'The default are the minimal scopes needed to run the development'
        ' scripts. This is currently "compute-rw,storage-rw".')


def copy_file(options, source, target):
    if os.path.exists(source):
        # TODO(ewiseblatt): we can use scp here instead, and pass the
        # credentials we want to copy with rather than the additional command
        # below. But we need to figure out the IP address to copy to.
        # For now, do it the long way.
        print 'Copying {source}'.format(source=source)
        command = ' '.join([
            'gcloud compute copy-files',
            '--project', get_project(options),
            '--zone', options.zone,
            source,
            '{instance}:{target}'.format(instance=options.instance,
                                         target=target)])
        while True:
            result = run_quick(command, echo=False)
            if not result.returncode:
                break
            print 'New instance does not seem ready yet...retry in 5s.'
            time.sleep(5)

        command = ' '.join([
            'gcloud compute ssh',
            '--command="chmod 600 /home/{gcp_user}/{target}"'.format(
                gcp_user=os.environ['LOGNAME'], target=target),
            options.instance,
            '--project', get_project(options),
            '--zone', options.zone])
        check_run_quick(command, echo=False)


def copy_home_files(options, type, file_list, source_dir=None):
    print 'Copying {type} files...'.format(type=type)

    home=os.environ['HOME']
    for file in file_list:
       source = '{0}/{1}'.format(home, file)
       copy_file(options, source, file)
    print 'Finished copying {type} files.'.format(type=type)


def copy_private_files(options):
   copy_home_files(options, 'private',
                   ['.ssh/id_rsa', '.ssh/google_compute_engine',
                    '.git-credentials'])


def copy_personal_files(options):
  copy_home_files(options, 'personal',
                  ['.gitconfig', '.emacs', '.bashrc', '.screenrc'])


def create_instance(options):
    """Creates new GCE VM instance for development."""
    project = get_project(options)
    print 'Creating instance {project}/{zone}/{instance}'.format(
        project=project, zone=options.zone, instance=options.instance)
    print 'with machine type {type} and boot disk size {disk_size}...'.format(
        type=options.machine_type, disk_size=options.disk_size)

    dev_dir = os.path.dirname(__file__)
    install_dir = '{dir}/../install'.format(dir=dev_dir)
    pylib_spinnaker_dir = '{dir}/../pylib/spinnaker'.format(dir=dev_dir)

    with open('{dir}/install_development.py'.format(dir=dev_dir), 'r') as f:
        # Remove leading install. package reference to module imports
        # because we're going to place this in the same package as
        # the things it is importing (no need for PYTHONPATH)
        content = f.read()
        content = content.replace('install.install', 'install')
        content = content.replace('from spinnaker.', 'from ')

    fd, temp_install_development = tempfile.mkstemp()
    os.write(fd, content)
    os.close(fd)
    with open('{dir}/install_runtime_dependencies.py'.format(dir=install_dir),
              'r') as f:
        content = f.read()
        content = content.replace('install.install', 'install')
        content = content.replace('from spinnaker.', 'from ')
    fd, temp_install_runtime = tempfile.mkstemp()
    os.write(fd, content)
    os.close(fd)

    startup_command = ['install_development.py',
                       '--package_manager']

    metadata_files = [
        'startup-script={dev_dir}/google_install_loader.py'
        ',py_fetch={pylib_spinnaker_dir}/fetch.py'
        ',py_run={pylib_spinnaker_dir}/run.py'
        ',py_install_development={temp_install_development}'
        ',sh_bootstrap_dev={dev_dir}/bootstrap_dev.sh'
        ',py_install_runtime_dependencies={temp_install_runtime}'
        .format(dev_dir=dev_dir, pylib_spinnaker_dir=pylib_spinnaker_dir,
                temp_install_runtime=temp_install_runtime,
                temp_install_development=temp_install_development)]

    metadata = ','.join([
        'startup_py_command={startup_command}'.format(
            startup_command='+'.join(startup_command)),
        'startup_loader_files='
        'py_fetch'
        '+py_run'
        '+py_install_development'
        '+py_install_runtime_dependencies'
        '+sh_bootstrap_dev'])

    command = ['gcloud', 'compute', 'instances', 'create',
               options.instance,
               '--project', get_project(options),
               '--zone', options.zone,
               '--machine-type', options.machine_type,
               '--image', 'ubuntu-14-04',
               '--scopes', 'compute-rw,storage-rw',
               '--boot-disk-size={size}'.format(size=options.disk_size),
               '--boot-disk-type={type}'.format(type=options.disk_type),
               '--metadata', metadata,
               '--metadata-from-file={files}'.format(
                   files=','.join(metadata_files))]
    if options.address:
        command.extend(['--address', options.address])

    try:
      check_run_quick(' '.join(command), echo=True)
    finally:
      os.remove(temp_install_development)
      os.remove(temp_install_runtime)


def copy_master_yml(options):
    """Copy the specified master spinnaker-local.yml, and credentials.

    This will look for paths to credentials within the spinnaker-local.yml, and
    copy those as well. The paths to the credentials (and the reference
    in the config file) will be changed to reflect the filesystem on the
    new instance, which may be different than on this instance.

    Args:
      options [Namespace]: The parser namespace options contain information
        about the instance we're going to copy to, as well as the source
        of the master spinnaker-local.yml file.
    """
    print 'Creating .spinnaker directory...'
    check_run_quick('gcloud compute ssh --command "mkdir -p .spinnaker"'
                    ' --project={project} --zone={zone} {instance}'
                    .format(project=get_project(options),
                            zone=options.zone,
                            instance=options.instance),
                    echo=False)

    bindings = YamlBindings()
    bindings.import_path(options.master_yml)

    try:
      json_credential_path = bindings.get(
          'providers.google.primaryCredentials.jsonPath')
    except KeyError:
      json_credential_path = None

    gcp_home = os.path.join('/home', os.environ['LOGNAME'], '.spinnaker')

    # If there are credentials, write them to this path
    gcp_credential_path = os.path.join(gcp_home, 'google-credentials.json')

    with open(options.master_yml, 'r') as f:
        content = f.read()

    # Replace all the occurances of the original credentials path with the
    # path that we are going to place the file in on the new instance.
    if json_credential_path:
        new_content = content.replace(json_credential_path, gcp_credential_path)

    fd, temp_path = tempfile.mkstemp()
    os.write(fd, new_content)
    os.close(fd)
    actual_path = temp_path

    # Copy the credentials here. The cfg file will be copied after.
    copy_file(options, actual_path, '.spinnaker/spinnaker-local.yml')

    if json_credential_path:
        copy_file(options, json_credential_path,
                  '.spinnaker/google-credentials.json')

    if temp_path:
      os.remove(temp_path)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    init_argument_parser(parser)
    options = parser.parse_args()

    if options.master_yml and not os.path.exists(options.master_yml):
      sys.stderr.write('ERROR: {path} does not exist.'.format(
          path=options.master_yml))
      sys.exit(-1)

    create_instance(options)

    if not options.nopersonal:
      copy_personal_files(options)

    if options.copy_private_files:
      copy_private_files(options)

    if options.master_yml:
      copy_master_yml(options)

    if options.aws_credentials:
        print 'Creating .aws directory...'
        check_run_quick('gcloud compute ssh --command "mkdir -p .aws"'
                        ' --project={project} --zone={zone} {instance}'
                        .format(project=get_project(options),
                              zone=options.zone,
                              instance=options.instance),
                        echo=False)
        copy_file(options, options.aws_credentials, '.aws/credentials')

    print __NEXT_STEP_INSTRUCTIONS.format(
        project=get_project(options),
        zone=options.zone,
        instance=options.instance)
