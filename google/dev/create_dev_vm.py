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
import subprocess
import sys
import tempfile
import time

from install.install_utils import run
from install.install_utils import run_or_die
from install.install_utils import run_or_die_no_result

__NEXT_STEP_INSTRUCTIONS = """
To finish the installation, do the following (with or without tunnel):

(1) Log into your new instance (with or without tunneling ssh-flags):

gcloud compute ssh --project {project} --zone {zone} {instance}\
 --ssh-flag="-L 9000:localhost:9000"\
 --ssh-flag="-L 8084:localhost:8084"


(2) Set up the build environment:

source /opt/spinnaker/install/bootstrap_vm.sh


(3) Build and run the sources:

  ../spinnaker/google/dev/run_dev.sh


For more help, see the Spinnaker Build & Run Book:

https://docs.google.com/document/d/1Q_ah8eG3Imyw-RWS1DSp_ItM2pyn56QEepCeaOAPaKA

"""


def get_project(options):
    """Determine the default project name.

    The default project name is the gcloud configured default project.
    """
    if not options.project:
      stdout, stderr = run_or_die('gcloud config list', echo=False)
      options.project = re.search('project = (.*)\n', stdout).group(1)
    return options.project


def init_argument_parser(parser):
    parser.add_argument(
        '--instance',
        default='{user}-spinnaker-dev'.format(user=os.environ['USER']),
        help='The name of the GCE instance to create.')
    parser.add_argument('--project', default=None)
    parser.add_argument('--zone', default='us-central1-f')
    parser.add_argument('--disk_size', default='200GB',
                        help='Warnings appear if disk size < 200GB')
    parser.add_argument('--machine_type', default='n1-standard-16')
    parser.add_argument(
        '--nopersonal', default=False, action='store_true',
        help='Do not copy personal files (.gitconfig, etc.)')
    parser.add_argument(
        '--copy_private_files', default=False, action='store_true',
        help='Also copy private files (.ssh/id_rsa*, .git-credentials, etc)')

    parser.add_argument(
        '--master_config', default=None,
        help='If specified, the path to the master config file.')
    parser.add_argument(
        '--address', default=None,
        help='The IP address to assign to the new instance.')


def copy_file(options, source, target):
    if os.path.exists(source):
        print 'Copying {source}'.format(source=source)
        command = ' '.join([
            'gcloud', 'compute', 'copy-files',
            '--project', get_project(options),
            '--zone', options.zone,
            source, target])

        while True:
            code, stdout, stderr = run(command, echo=False)
            if not code:
                break
            print 'New instance does not seem ready yet...retry in 5s.'
            time.sleep(5)


def copy_home_files(options, type, file_list, source_dir=None):
    print 'Copying {type} files...'.format(type=type)

    home=os.environ['HOME']
    for file in file_list:
       source = '{0}/{1}'.format(home, file)
       target = '{instance}:{file}'.format(
                instance=options.instance, file=file)
       copy_file(options, source, target)
    print 'Finished copying {type} files.'.format(type=type)


def copy_private_files(options):
   copy_home_files(options, 'private', ['.ssh/id_rsa', '.git-credentials'])


def copy_personal_files(options):
  copy_home_files(options, 'personal',
                  ['.gitconfig', '.emacs', '.bashrc', '.screenrc'])


def create_instance(options):
    project = get_project(options)
    print 'Creating instance {project}/{zone}/{instance}'.format(
        project=project, zone=options.zone, instance=options.instance)
    print 'with machine type {type} and boot disk size {disk_size}...'.format(
        type=options.machine_type, disk_size=options.disk_size)

    dev_dir = os.path.dirname(sys.argv[0])
    install_dir = '{dir}/../install'.format(dir=dev_dir)
    pylib_dir = '{dir}/../pylib'.format(dir=dev_dir)

    with open('{dir}/install_development.py'.format(dir=dev_dir), 'r') as f:
        # Remove leading install. package reference to module imports
        # because we're going to place this in the same package as
        # the things it is importing (no need for PYTHONPATH)
        content = f.read()
        content = content.replace('install.install', 'install')

    fd, temp_install_development = tempfile.mkstemp()
    os.write(fd, content)
    os.close(fd)

    startup_command = 'install_development.py'

    metadata_files = [
        'startup-script={install_dir}/google_install_loader.py'
        ',py_install_utils={install_dir}/install_utils.py'
        ',py_install_development={temp_install_development}'
        ',py_install_spinnaker={install_dir}/install_spinnaker.py'
        ',sh_bootstrap_vm={dev_dir}/bootstrap_vm.sh'
        ',py_install_runtime_dependencies='
        '{install_dir}/install_runtime_dependencies.py'
        .format(install_dir=install_dir,
                dev_dir=dev_dir,
                temp_install_development=temp_install_development)]

    metadata = ','.join([
        'startup_py_command={startup_command}'.format(
            startup_command=startup_command),
        'startup_loader_files='
        'py_install_utils'
        '+py_install_development'
        '+py_install_spinnaker'
        '+py_install_runtime_dependencies'
        '+sh_bootstrap_vm'])

    command = ['gcloud', 'compute', 'instances', 'create',
               options.instance,
               '--project', get_project(options),
               '--zone', options.zone,
               '--machine-type', options.machine_type,
               '--image', 'ubuntu-14-04',
               '--scopes', 'compute-rw,storage-rw',
               '--boot-disk-size={size}'.format(size=options.disk_size),
               '--metadata', metadata,
               '--metadata-from-file={files}'.format(
                   files=','.join(metadata_files))]
    if options.address:
        command.extend(['--address', options.address])

    try:
      run_or_die_no_result(' '.join(command), echo=True)
    finally:
      os.remove(temp_install_development)


def copy_master_config(options):
    print 'Creating .spinnaker directory...'
    p = subprocess.Popen('gcloud compute ssh --command "mkdir -p .spinnaker"'
                         ' --project={project} --zone={zone} {instance}'
                         .format(project=get_project(options),
                                 zone=options.zone,
                                 instance=options.instance),
                         shell=True, close_fds=True,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    p.communicate()

    # Extract out right hand side of GOOGLE_JSON_CREDENTIAL_PATH if any.
    # Replace ~ with $HOME to get the actual path.
    with open(options.master_config, 'r') as f:
        content = f.read()

    match = re.search(r'(?m)^GOOGLE_JSON_CREDENTIAL_PATH=(.*)$', content)
    if match:
       json_credential_path = match.groups()[0].replace('~', os.environ['HOME'])
       json_credential_path = json_credential_path.replace(
           '$HOME', os.environ['HOME'])
    else:
        json_credential_path = None


    # GCloud compute ssh uses the local $LOGNAME and creates a user with
    # that name, not your GCP account name.
    gcp_user = os.environ['LOGNAME']

    fix_permissions = [
        'cd /home/{gcp_user}/.spinnaker'.format(gcp_user=gcp_user),
        'chmod 600 spinnaker_config.cfg'
    ]

    temp_path = None
    actual_path = options.master_config
    if json_credential_path:
       target_location = '/home/{gcp_user}/.spinnaker/credentials.json'.format(
           gcp_user=gcp_user)

       content = re.sub('(?m)(GOOGLE_JSON_CREDENTIAL_PATH=).*',
                        r'\1{path}'.format(path=target_location),
                        content)
       fd, temp_path = tempfile.mkstemp()
       os.write(fd, content)
       os.close(fd)
       actual_path = temp_path

       # Copy the credentials here. The cfg file will be copied after.
       copy_file(options, json_credential_path,
                 '{instance}:.spinnaker/credentials.json'.format(
                     instance=options.instance))
       fix_permissions.append('chmod 600 credentials.json')

    copy_file(options, actual_path,
              '{instance}:.spinnaker/spinnaker_config.cfg'.format(
                    instance=options.instance))
    
    print 'Fixing credentials.'
    # Ideally this should be a parameter to copy-files so it is always
    # protected, but there doesnt seem to be an API for it.
    p = subprocess.Popen('gcloud compute ssh --command "{fix_permissions}"'
                         ' --project={project} --zone={zone} {instance}'
                         .format(fix_permissions=';'.join(fix_permissions),
                                 project=get_project(options),
                                 zone=options.zone,
                                 instance=options.instance),
                         shell=True, close_fds=True,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    print p.communicate()

    if temp_path:
      os.remove(temp_path)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    init_argument_parser(parser)
    options = parser.parse_args()

    if options.master_config and not os.path.exists(options.master_config):
      sys.stderr.write('ERROR: {path} does not exist.'.format(
          path=options.master_config))
      sys.exit(-1)

    create_instance(options)

    if not options.nopersonal:
      copy_personal_files(options)

    if options.copy_private_files:
      copy_private_files(options)

    if options.master_config:
      copy_master_config(options)

    print __NEXT_STEP_INSTRUCTIONS.format(
        project=get_project(options),
        zone=options.zone,
        instance=options.instance)
