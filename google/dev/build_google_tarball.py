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

# Given an existing GCE image, create a tarball for it.
# PYTHONPATH=pylib python google/dev/build_google_tarball.py --image=$IMAGE_NAME --tarball_uri=gs://$RELEASE_URI/$(basename $RELEASE_URI).tar.gz
#
# For more information, see
# https://cloud.google.com/compute/docs/creating-custom-image#export_an_image_to_google_cloud_storage

import argparse
import os
import re
import sys
import time

from spinnaker.run import run_quick
from spinnaker.run import check_run_quick


def get_default_project():
  """Determine the default project name.

  The default project name is the gcloud configured default project.
  """
  result = check_run_quick('gcloud config list', echo=False)
  return re.search('project = (.*)\n', result.stdout).group(1)


class Builder(object):
  def __init__(self, options):
    self.options = options
    self.check_for_image_tarball()
    self.__zone = options.zone
    self.__project = options.project or get_default_project()
    self.__instance = options.instance

    # Override gcloud account argument, but only if we specified an explicit account.
    self.__gcloud_account_arg = ('' if not options.service_account
                                 else '--account={0}'.format(options.service_account))

  def deploy_instance(self):
    """Deploy an instance (from an image) so we can get at its disks.

    This isnt necessarily efficient, but is simple since we already have
    means to create images.
    """
    if self.__instance:
      print 'Using existing instance {name}'.format(name=self.__instance)
      return

    if not self.options.image:
        raise ValueError('Neither --instance nor --image was specified.')

    instance = 'build-spinnaker-tarball-{unique}'.format(
        unique=time.strftime('%Y%m%d%H%M%S'))

    print 'Deploying temporary instance {name}'.format(name=instance)
    check_run_quick('gcloud {account} compute instances create {name}'
                    ' --zone={zone} --project={project}'
                    ' --image={image} --image-project={image_project}'
                    ' --scopes compute-rw,storage-rw'
                    .format(account=self.__gcloud_account_arg,
                            name=instance,
                            zone=self.__zone,
                            project=self.__project,
                            image=self.options.image,
                            image_project=self.options.image_project),
                    echo=False)
    self.__instance = instance

    print 'Making snapshot {name}-snapshot'.format(name=self.__instance)
    print '  Stopping (to snapshot)'
    check_run_quick('gcloud {account} compute instances stop {name}'
                    ' --zone={zone} --project={project}'
                    .format(account=self.__gcloud_account_arg, name=instance,
                            zone=self.__zone, project=self.__project),
                    echo=True)
    print '  Making snapshot'
    check_run_quick('gcloud {account} compute disks snapshot {name}'
                    ' --zone {zone} --project={project}'
                    ' --snapshot-names {name}-snapshot'
                    .format(account=self.__gcloud_account_arg, name=instance,
                            zone=self.__zone, project=self.__project),
                    echo=True)
    print '  Restarting'
    check_run_quick('gcloud {account} compute instances start {name}'
                    ' --zone={zone} --project={project}'
                    .format(account=self.__gcloud_account_arg, name=instance,
                            zone=self.__zone, project=self.__project),
                    echo=True)
    print 'Finished snapshot'


  def cleanup_instance(self):
    """If we deployed an instance, tear it down."""
    if self.options.instance:
      print 'Leaving pre-existing instance {name}'.format(
          self.options.instance)
      return

    print 'Deleting snapshot {name}-snapshot'.format(name=self.__instance)
    check_run_quick('gcloud {account} compute snapshots delete -q {name}-snapshot --project={project}'
                    .format(account=self.__gcloud_account_arg,
                            name=self.__instance,
                            project=self.__project),
                    echo=False)

    print 'Deleting instance {name}'.format(name=self.__instance)
    run_quick('gcloud {account} compute instances delete -q {name}'
              '  --zone={zone} --project={project}'
              .format(account=self.__gcloud_account_arg,
                      name=self.__instance,
                      zone=self.__zone,
                      project=self.__project),
              echo=False)

  def check_for_image_tarball(self):
    """See if the tarball aleady exists."""
    uri = self.options.tarball_uri
    if (not uri.startswith('gs://')):
      error = ('--tarball_uri must be a Google Cloud Storage URI'
               ', not "{uri}"'
               .format(uri=uri))
      raise ValueError(error)

    result = run_quick('gsutil ls {uri}'.format(uri=uri), echo=False)
    if not result.returncode:
      error = 'tarball "{uri}" already exists.'.format(uri=uri)
      raise ValueError(error)

  def __extract_image_tarball_helper(self):
    """Helper function for make_image_tarball that does the work.

    Note that the work happens on the instance itself. So this function
    builds a remote command that it then executes on the prototype instance.
    """
    print 'Creating image tarball.'

    tar_path = self.options.tarball_uri
    tar_name = os.path.basename(tar_path)
    remote_script = [
      'sudo mkdir /mnt/exportdisk',
      'sudo mkfs.ext4 -F /dev/disk/by-id/google-export-disk',
      'sudo mount -t ext4 -o discard,defaults'
          ' /dev/disk/by-id/google-export-disk /mnt/exportdisk',

      'sudo mkdir /mnt/snapshotdisk',
      'sudo mount /dev/disk/by-id/google-snapshot-disk /mnt/snapshotdisk',
      'cd /mnt/snapshotdisk',
      'sudo rm -rf home/*',

      'sudo dd if=/dev/disk/by-id/google-snapshot-disk'
          ' of=/mnt/exportdisk/disk.raw bs=4096',
      'cd /mnt/exportdisk',

      'sudo tar czvf {tar_name} disk.raw'.format(tar_name=tar_name),
      'gsutil -q cp /mnt/exportdisk/{tar_name} {output_path}'.format(
          tar_name=tar_name, output_path=tar_path)
    ]

    command = '; '.join(remote_script)
    print 'Running: {0}'.format(command)
    check_run_quick('gcloud {account} compute ssh --command="{command}"'
                    ' --project {project} --zone {zone} {instance}'
                    .format(account=self.__gcloud_account_arg,
                            command=command.replace('"', r'\"'),
                            project=self.__project,
                            zone=self.__zone,
                            instance=self.__instance))

  def create_tarball(self):
    """Create a tar.gz file from the instance specified by the options.

    The file will be written to options.tarball_uri.
    It can be later turned into a GCE image by passing it as the --source-uri
    to gcloud images create.
    """
    project = self.__project
    basename = os.path.basename(self.options.tarball_uri).replace('_', '-')
    first_dot = basename.find('.')
    if first_dot:
        basename = basename[0:first_dot]

    disk_name = '{name}-export'.format(name=basename)
    print 'Attaching external disk "{disk}" to extract image tarball.'.format(
        disk=disk_name)

    check_run_quick('gcloud {account} compute disks create '
                    ' {disk_name} --project {project} --zone {zone}'
                    .format(account=self.__gcloud_account_arg,
                            disk_name=disk_name,
                            project=self.__project,
                            zone=self.__zone),
                    echo=False)

    check_run_quick('gcloud {account} compute instances attach-disk {instance}'
                    ' --disk={disk_name} --device-name=export-disk'
                    ' --project={project} --zone={zone}'
                    .format(account=self.__gcloud_account_arg,
                            instance=self.__instance,
                            disk_name=disk_name,
                            project=self.__project,
                            zone=self.__zone),
                    echo=False)

    print 'Attaching snapshot "{name}-snapshot".'.format(name=self.__instance)
    check_run_quick('gcloud {account} compute disks create '
                    ' {name}-snapshot --source-snapshot {name}-snapshot'
                    ' --project {project} --zone {zone}'
                    .format(account=self.__gcloud_account_arg,
                            name=self.__instance,
                            project=self.__project,
                            zone=self.__zone),
                    echo=False)

    check_run_quick('gcloud {account} compute instances attach-disk {instance}'
                    ' --disk={instance}-snapshot --device-name=snapshot-disk'
                    ' --project={project} --zone={zone}'
                    .format(account=self.__gcloud_account_arg, instance=self.__instance,
                            project=self.__project, zone=self.__zone),
                    echo=False)
    try:
      self.__extract_image_tarball_helper()
    finally:
      print 'Detaching and deleting external disks.'
      for name in [disk_name, '{name}-snapshot'.format(name=self.__instance)]:
        run_quick('gcloud {account} compute instances detach-disk -q {instance}'
                  ' --disk={name} --project={project} --zone={zone}'
                  .format(account=self.__gcloud_account_arg,
                          instance=self.__instance,
                          name=name,
                          project=self.__project,
                          zone=self.__zone),
                  echo=False)
        run_quick('gcloud {account} compute disks delete -q {name}'
                  ' --project={project} --zone={zone}'
                  .format(account=self.__gcloud_account_arg,
                          name=name,
                          project=self.__project,
                          zone=self.__zone),
                  echo=False)


def init_argument_parser(parser):
    parser.add_argument(
        '--tarball_uri', required=True,
        help='A path to a Google Cloud Storage bucket or path within one.')

    parser.add_argument(
      '--service_account', default='',
      help='If specified, use this gcloud account rather than the default. To add'
           ' new accounts, use gcloud auth activate-service-account.')
    parser.add_argument(
        '--instance', default='',
        help='If specified use this instance, otherwise use deploy a new one.')
    parser.add_argument(
        '--image', default='', help='The image to tar if no --instance.')
    parser.add_argument(
        '--image_project', default='', help='The project for --image.')

    parser.add_argument('--zone', default='us-central1-f')
    parser.add_argument(
        '--project', default='',
        help='GCE project to write image to.'
        ' If not specified then use the default gcloud project.')


if __name__ == '__main__':
  parser = argparse.ArgumentParser()
  init_argument_parser(parser)
  options = parser.parse_args()

  builder = Builder(options)
  builder.deploy_instance()
  try:
    builder.create_tarball()
  finally:
    builder.cleanup_instance()

  print 'DONE'

