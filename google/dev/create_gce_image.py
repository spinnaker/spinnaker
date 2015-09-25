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

# python create_gce_image.py --release $RELEASE_NAME

import argparse
import os
import re
import sys
import tempfile
import time

from install.install_utils import run
from install.install_utils import run_or_die


TIME_DECORATOR = time.strftime('%Y%m%d%H%M%S')

# The root path to look up standard releases by name.
RELEASE_REPOSITORY = 'gs://'


def get_default_project():
    """Determine the default project name.

    The default project name is the gcloud configured default project.
    """
    stdout, stderr = run_or_die('gcloud config list', echo=False)
    return re.search('project = (.*)\n', stdout).group(1)


def get_target_image(options):
   """Determine the specified target image name to create."""
   if not options.image:
      options.image = os.path.basename(options.release_path).replace('_', '-')
   return options.image


def get_target_project(options):
   """Determine the specified target project to create the image in."""
   if not options.image_project:
      options.image_project = get_default_project(options)
   return options.image_project


def get_source_image(options):
   """Determine the specified source image name.

   If a name was not explicitly specified, then use the --source_pattern.
   """
   if options.source_image:
       return options.source_image

   stdout, stderr = run_or_die('gcloud compute images list', echo=False)

   match = re.search('{family}.*? '.format(family=options.source_image_family),
                     stdout)
   if not match:
       sys.stderr.write('No images found for family="{family}".\n{list}\n'
                        .format(family=options.source_image_family,
                                list=stdout))
       sys.exit(-1)
   options.source_image = match.group(0).strip() # remember it for next time
   return options.source_image


def check_for_image(options):
    """See if the specified image already exists so we can fail early."""
    print 'Checking if image "{image}" already exists in "{project}"...'.format(
        image=get_target_image(options), project=get_target_project(options))
    url = ('https://www.googleapis.com/compute/v1/'
           'projects/{project}/global/images/{image}'
           .format(project=get_target_project(options),
                   image=get_target_image(options)))
    retcode, stdout, stderr = run('gcloud compute images describe ' + url,
                                  echo=False)
    if retcode == 0:
        sys.stderr.write(
            'ERROR: An image "{name}" already exists in "{project}".\n'
            '\n    {description}\n\n'
            'Delete it or specify a different --image\n\n'
            .format(name=get_target_image(options),
                    project=get_target_project(options),
                    description=stdout.replace('\n', '\n    ')))
        sys.exit(-1)


class SetReleaseName(argparse.Action):
    def __call__(self, parser, namespace, values, options_string=None):
        if isinstance(values, list):
          raise ValueError(
              'Did not expect multiple arguments for "--release"')
        setattr(namespace, self.dest, '{release_root}{name}'.format(
            release_root=RELEASE_REPOSITORY, name=values))


def init_argument_parser(parser):
    """Initialize the command-line parameters."""
    user=os.environ['USER']

    default_project = get_default_project()
    parser.add_argument(
        '--release', action=SetReleaseName, dest='release_path',
        help='A named release is implied to be a GCS bucket.')
    parser.add_argument('--release_path', default=None)

    parser.add_argument('--spinnaker', default=True, action='store_true',
                        help='Add spinnaker subsystems to the image.')
    parser.add_argument(
       '--nospinnaker', dest='spinnaker', action='store_false')

    parser.add_argument(
        '--dependencies', default=True, action='store_true',
        help='Add spinnaker external service dependencies to the image.')
    parser.add_argument(
        '--nodependencies', dest='dependencies', action='store_true')

    parser.add_argument('--extra_install_flags', default='',
                        help='Extra arguments to pass to --install_spinnaker'
                             ' when setting up the prototype instance.')
    parser.add_argument('--create_image', dest='image')
    parser.add_argument('--image', default='', help='Name of image to create.')
    parser.add_argument('--image_project', default=default_project,
                        help='GCE project to write image to.')
    parser.add_argument('--source_image_project', default='ubuntu-os-cloud')
    parser.add_argument('--source_image_family', default='ubuntu-1404',
                        help='Used to discover a specific source image if'
                             ' -source_image is not specified.'
                             ' The default is ubuntu-1404')
    parser.add_argument('--source_image', default='',
                        help='Specifies a specific source_image. This could'
                        ' be left blank in favor of -source_image_family'
                        ' to use the latest version of a family of images.')

    parser.add_argument('--trace', default=False, action='store_true')
    parser.add_argument('--update_os', default=False, action='store_true')
    parser.add_argument('--zone', default='us-central1-c')
    parser.add_argument('--tmp_instance_name',
                        default='{user}-build-spinnaker-image-{time}'.format(
                            user=user, time=TIME_DECORATOR))


def create_prototype_instance(options):
    """Create an instance and install spinnaker onto it."""
    print 'Creating prototype instance with spinnaker installation...'
    startup_command = ['install_spinnaker.py',
                       '--package_manager',
                       '--release_path={0}'.format(options.release_path)]
    if not options.spinnaker:
        startup_command.append('--nospinnaker')
    if not options.dependencies:
        startup_command.append('--nodependencies')
    if options.update_os:
        startup_command.append('--update_os')
    if options.extra_install_flags:
        startup_command.extend(options.extra_install_flags.split())

    script_dir = os.path.dirname(sys.argv[0])
    install_dir = os.path.join(script_dir, '../install')
    metadata = ','.join(['startup_py_command={startup_command}'.format(
                             startup_command='+'.join(startup_command)),
                         'startup_loader_files='
                            'py_install_utils'
                            '+py_install_spinnaker'
                            '+py_install_runtime_dependencies'])

    file_list = (
        'startup-script={install_dir}/google_install_loader.py'
        ',py_install_utils={install_dir}/install_utils.py'
        ',py_install_spinnaker={install_dir}/install_spinnaker.py'
        ',py_install_runtime_dependencies='
        '{install_dir}/install_runtime_dependencies.py'
        .format(install_dir=install_dir))

    command = ['gcloud compute instances',
               'create', options.tmp_instance_name,
               '--project', get_target_project(options),
               '--image', get_source_image(options),
               '--image-project', options.source_image_project,
               '--zone', options.zone,
               '--machine-type', 'n1-standard-1',
               '--scopes', 'compute-rw,storage-rw',
               '--metadata', metadata,
               '--metadata-from-file', file_list]
    run_or_die(' '.join(command), echo=False)


def extract_image_from_instance(options):
    """Given an existing image, extract its boot disk into an image.

    This will delete the instance.
    """
    print 'Extracting boot disk from instance.'
    command = ['gcloud compute instances',
               'delete', options.tmp_instance_name,
               '--project', get_target_project(options),
               '--zone', options.zone,
               '--quiet', '--keep-disks', 'boot']
    run_or_die(' '.join(command), echo=False)

    print 'Creating image "{name}"...'.format(name=get_target_image(options))
    command = ['gcloud compute images',
               'create', get_target_image(options),
               '--project', get_target_project(options),
               '--source-disk', options.tmp_instance_name,
               '--source-disk-zone', options.zone]
    run_or_die(' '.join(command), echo=False)


def show_next_steps(options):
    print """
Created image {image}.

Try something like:
    gcloud compute instances create {image} \\
        --project $SPINNAKER_PROJECT \\
        --image {image} \\
        --image-project {image_project} \\
        --machine-type n1-standard-16 \\
        --zone {zone} \\
        --scopes=compute-rw \\
        --metadata=startup-script=/opt/spinnaker/install/first_time_boot.sh \\
        --metadata-from-file=\\
  spinnaker_config=$SPINNAKER_CONFIG_PATH,\\
  managed_project_credentials=$GOOGLE_JSON_CREDENTIALS_PATH

  You can leave off the managed_project_credentials metadata if
  $SPINNAKER_PROJECT is the same as the GOOGLE_MANAGED_PROJECT_ID
  in the spinnaker_config.
""".format(
    image=get_target_image(options),
    image_project=get_target_project(options),
    zone=options.zone)


def monitor_serial_port_until_metadata_key(
        options, project, zone, instance_name, metadata_key):
  """Monitor an instance's serial port output until it contains a metadata key

  Args:
    project: The project id owning the instance.
    zone: The zone the instance is in.
    instance_name: The name of the instance to monitor.
                      it is assumed to be in |project| and |zone|.
    metadata_key: The metada key to wait on.
  """
  print 'Waiting to finish setting up prototype instance...'
  pattern = ' - key: {key}'.format(key=metadata_key)
  offset = 0
  while True:
    if options.trace:
        code, stdout, stderr = run(
            ' '.join(['gcloud compute --project', project, 'instances',
                      'get-serial-port-output', '--zone', zone,
                      '--format text',
                      instance_name]),
            echo=False)
        if len(stdout) > offset:
          sys.stdout.write(stdout[offset:])
          offset = len(stdout)

        if code and offset:
            if stderr.find('If you would like to report this issue') > 0:
                print 'Ignoring gcloud crash...'
                continue
            break
    else:
        sys.stdout.write('.')
        sys.stdout.flush()

    code, stdout, stderr = run(
        ' '.join(['gcloud compute instances describe', instance_name,
                  '--project', project, '--zone', zone]),
        echo=False)

    if stdout.find(pattern) >= 0:
        break

    time.sleep(8)

  # Emit the remainder of the log file before we return.
  if options.trace:
      stdout, stderr = run_or_die(
          ['gcloud', 'compute', '--project', project, 'instances',
           'get-serial-port-output', '--zone', zone, '--format', 'text',
           instance_name])
      print stdout[offset:]

  print ''


def create_image(options):
    if not options.release_path:
      error = ('--release_path cannot be empty.'
               ' Either specify a --release or a --release_path.')
      sys.stderr.write(error)
      raise ValueError(error)      

    check_for_image(options)
    create_prototype_instance(options)
    monitor_serial_port_until_metadata_key(
        options,
        get_target_project(options),
        options.zone,
        options.tmp_instance_name,
        'spinnaker-sentinal')

    extract_image_from_instance(options)

    print 'Cleaning up extracted boot disk.'
    command = ['gcloud', 'compute', 'disks',
               'delete', options.tmp_instance_name,
               '--project', get_target_project(options),
               '--zone', options.zone, '--quiet']
    run_or_die(' '.join(command), echo=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    init_argument_parser(parser)

    options = parser.parse_args()
    create_image(options)
    show_next_steps
