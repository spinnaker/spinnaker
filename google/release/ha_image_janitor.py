#!/usr/bin/python
#
# Copyright 2017 Google Inc. All Rights Reserved.
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
import datetime
import json
import os
import re
import sys
import yaml

from google.cloud import storage
from spinnaker.run import check_run_quick, run_quick

"""Provides a utility to clean up the Google Cloud VM images produced during a
 build of Spinnaker.

Only a small subset of VM images produced during a Spinnaker build are
referenced (by component version) by a Spinnaker bill of material (BOM). The
rest of the unreferenced VM images need periodically deleted. This script will
be run periodically to reap old, unreferenced images.
"""


RELEASED_VERSION_MATCHER = re.compile('^[0-9]+\.[0-9]+\.[0-9]+\.yml$')

SERVICES = [
  'clouddriver',
  'deck',
  'echo',
  'front50',
  'gate',
  'igor',
  'orca',
  'rosco',
  'fiat'
]

PUBLISHED_TAG_KEY = 'published'


def __partition_boms(gcs_client, bucket_name):
  def __bom_to_tag(bom_blob):
    name = os.path.basename(bom_blob.name)
    return RELEASED_VERSION_MATCHER.match(name)

  def __bom_to_version(bom_blob):
    return os.path.basename(bom_blob.name).replace('.yml', '')

  bucket = gcs_client.get_bucket(bucket_name)
  all_bom_blobs = [b for b in bucket.list_blobs(prefix='bom') if b.name.endswith('.yml')]
  bom_contents_by_name = {__bom_to_version(b): b.download_as_string() for b in all_bom_blobs}

  versions_to_tag = [__bom_to_version(bom) for bom in all_bom_blobs if __bom_to_tag(bom)]
  possible_versions_to_delete = [__bom_to_version(bom) for bom in all_bom_blobs if not __bom_to_tag(bom)]
  return (versions_to_tag, possible_versions_to_delete, bom_contents_by_name)


def __image_age_days(image_json):
  # HACK: Cut off the timezone because strptime() can't deal with timezones.
  # Timezone offset is 5 characters of the form: +00:00 or -00:00.
  timestamp = image_json['creationTimestamp'][:len(image_json['creationTimestamp'])-6]
  time_created = datetime.datetime.strptime(timestamp, '%Y-%m-%dT%H:%M:%S.%f')
  now = datetime.datetime.utcnow()
  return (now - time_created).days


def __tag_images(versions_to_tag, project, account, project_images, bom_contents_by_name):
  images_to_tag = set([])
  for bom_version in versions_to_tag:
    to_tag = [i for i in __derive_images_from_bom(bom_version, bom_contents_by_name) if i in project_images]
    images_to_tag.update(to_tag)
  for image in images_to_tag:
    result = run_quick('gcloud compute images describe --project={project} --account={account} --format=json {image}'
                       .format(project=project, account=account, image=image), echo=False)
    # Adding labels is idempotent, adding the same label again doesn't break anything.
    if not result.returncode:
      payload_str = result.stdout.strip()
      timestamp = json.loads(payload_str)['creationTimestamp']
      timestamp = timestamp[:timestamp.index('T')]
      check_run_quick(
        'gcloud compute images add-labels --project={project} --account={account} --labels={key}={timestamp} {image}'
        .format(project=project, account=account, key=PUBLISHED_TAG_KEY, timestamp=timestamp, image=image))


def __write_image_delete_script(possible_versions_to_delete, days_before, project,
                                account, project_images, bom_contents_by_name):
  images_to_delete = set([])
  print 'Calculating images for {} versions to delete.'.format(len(possible_versions_to_delete))
  for bom_version in possible_versions_to_delete:
    deletable = [i for i in __derive_images_from_bom(bom_version, bom_contents_by_name) if i in project_images]
    images_to_delete.update(deletable)
  delete_script_lines = []
  for image in images_to_delete:
    result = run_quick('gcloud compute images describe --project={project} --account={account} --format=json {image}'
                       .format(project=project, account=account, image=image), echo=False)
    json_str = ''
    if result.returncode:
      # Some BOMs may refer to service versions without HA images.
      print('Lookup for image {image} in project {project} failed, ignoring'.format(image=image, project=project))
      continue
    else:
      json_str = result.stdout.strip()
    payload = json.loads(json_str)

    if __image_age_days(payload) > days_before:
      labels = payload.get('labels', None)
      if not labels or not PUBLISHED_TAG_KEY in labels:
        line = 'gcloud compute images delete --project={project} --account={account} {image}'.format(project=project, account=account, image=image)
        delete_script_lines.append(line)
  delete_script = '\n'.join(delete_script_lines)
  timestamp = '{:%Y%m%d%H%M%S}'.format(datetime.datetime.utcnow())
  script_name = 'delete-images-{}'.format(timestamp)
  with open(script_name, 'w') as script:
    script.write(delete_script)
  print 'Wrote image janitor script to {}'.format(script_name)


def __derive_images_from_bom(bom_version, contents_by_name):
  bom_content_str = contents_by_name[bom_version]
  bom_dict = yaml.load(bom_content_str)
  service_entries = bom_dict['services']
  return [__format_image_name(s, service_entries) for s in SERVICES]


def __format_image_name(service_name, service_entries):
  service_version = service_entries[service_name]['version']
  dash_version = service_version.replace('.', '-')
  return 'spinnaker-{service}-{version}'.format(service=service_name,
                                                version=dash_version)


def __delete_unused_bom_images(options):
  client = None
  if options.json_path:
    client = storage.Client.from_service_account_json(options.json_path)
  else:
    client = storage.Client()
  versions_to_tag, possible_versions_to_delete, bom_contents_by_name = __partition_boms(client, options.bom_bucket_name)
  if options.additional_boms_to_tag:
    additional_boms_to_tag = options.additional_boms_to_tag.split(',')
    print('Adding additional BOM versions to tag: {}'.format(additional_boms_to_tag))
    versions_to_tag.extend(additional_boms_to_tag)
  print('Tagging versions: {}'.format(versions_to_tag))
  print('Deleting versions: {}'.format(possible_versions_to_delete))

  project = options.project
  service_account = options.service_account
  image_list_str = check_run_quick('gcloud compute images list --format=json --project={project} --account={account}'
                                   .format(project=project, account=service_account), echo=False).stdout.strip()
  image_list = json.loads(image_list_str)
  project_images = set([image['name'] for image in image_list])
  __tag_images(versions_to_tag, project, service_account, project_images,
               bom_contents_by_name)
  __write_image_delete_script(possible_versions_to_delete, options.days_before, project,
                              service_account, project_images,
                              bom_contents_by_name)


def init_argument_parser(parser):
  parser.add_argument('--additional_boms_to_tag', default='',
                      help='Comma-delimited list of additional BOM versions to tag'
                      'to avoid deletion.')
  parser.add_argument('--bom_bucket_name', default='halconfig',
                      help='The name of the Halyard bucket storing the BOMs.')
  parser.add_argument('--days_before', default=14,
                      help='Max age in days of nightly build BOMs to save.')
  parser.add_argument('--json_path', default='',
                      help='Path to the service account credentials with access to the BOM bucket.')
  parser.add_argument('--project', default='', required=True,
                      help='GCP project the HA images are stored in.')
  parser.add_argument('--service_account', default='', required=True,
                      help='Name of the service account to manipulate images with.')


def main():
  parser = argparse.ArgumentParser()
  init_argument_parser(parser)
  options = parser.parse_args()
  __delete_unused_bom_images(options)


if __name__ == '__main__':
  sys.exit(main())
