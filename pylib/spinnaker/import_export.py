#!/usr/bin/python

'''

# Spinnaker Import/Export Tool

Python cli tool that imports and exports the following Spinnaker items:

* Applications
  * Pipelines
  * Deployment Strategies
  * Task Logs
* Rush
  * Executions
* Echo
  * Triggers
  * Executions

This is helpful for performing a Spinnaker upgrade. Simply export your items, shut down your instance, launch a fresh instance and run the import.

The export is archived and uploaded to your S3 or GCE bucket.

## Usage

Simply clone this repo on your Spinnaker instance and execute `./spinio`.

Your instance role must have access to your bucket. You can also use your own credentials or keys by configuring the cloud sdk before you use the tool.

AWS: `aws configure`

GCE: `gcloud auth login`

```
$ ./import_export.py
usage: spinio [-h] --cloud {aws,gcloud} --mode {import,export} --bucket BUCKET
              [--importFile IMPORTFILE]
```

## Spinnaker upgrade example

* Create a bucket to store the export
* Use the tool to export the archive to your bucket.
```
./import_export.py --cloud gcloud --mode export --bucket BUCKET-NAME
```
Take note of the resulting archive filename that is uploaded.

* Stop your current instance and launch a fresh Spinnaker instance.
* Run the tool in import mode on your new instance with the name of the archive step 2 created.
```
./import_export.py --cloud gcloud --mode import --bucket BUCKET-NAME --importFile FILENAME
```

Your imported items should now be visible on your new instance. Take some time to confirm everything is correct before terminating your old Spinnaker instance.

'''

import os
import shutil
from distutils import spawn
import argparse
import time

parser = argparse.ArgumentParser()
parser.add_argument('--cloud', required=True, choices=['aws', 'gcloud'], help='choose cloud provider')
parser.add_argument('--mode', required=True, choices=['import', 'export'], help='Choose mode')
parser.add_argument('--bucket', required=True, help='bucket name to store or download archive')
parser.add_argument('--importFile', help='archive file name to import ')
args = parser.parse_args()

keyspaces = {}
keyspaces['front50'] = ['project', 'application', 'pipeline', 'strategy', 'notifications']
keyspaces['rush'] = ['execution']
keyspaces['echo'] = ['trigger', 'execution', 'action_instance']

exportFile = 'spinnaker_export_' + str(time.time()) + '.tgz'

up = 'aws s3 cp ' + exportFile + ' s3://' + args.bucket

if args.cloud == 'gcloud':
    up = 'gsutil cp ' + exportFile + ' gs://' + args.bucket

if not spawn.find_executable(args.cloud):
    raise Exception('Cannot find cloud sdk on path')

if not spawn.find_executable("cqlsh"):
    raise Exception('cqlsh not found on path')

if args.mode == 'import':
    if not args.importFile:
        raise Exception('--importFile required in import mode')

if args.mode == 'import':
    down = 'aws s3 cp s3://' + args.bucket + ' ' + args.importFile
    if args.cloud == 'gcloud':
	down = 'gsutil cp gs://' + args.bucket + '/' + args.importFile + ' .'
    if os.path.exists('import'):
        shutil.rmtree('import')
    os.mkdir('import')
    os.system('cd import && ' + down)
    os.system('cd import && tar -xvf ' + args.importFile)
    for keyspace, tables in keyspaces.items():
        for table in tables:
            os.system('cqlsh -e "COPY ' + keyspace + '.' + table + ' FROM \'import/' + keyspace + '.' + table + '.csv\' WITH HEADER = \'true\';"')
if args.mode == 'export':
    if os.path.exists('export'):
        shutil.rmtree('export')
    os.mkdir('export')
    for keyspace, tables in keyspaces.items():
        for table in tables:
            os.system('cqlsh -e "COPY ' + keyspace + '.' + table + ' TO \'export/' + keyspace + '.' + table + '.csv\' WITH HEADER = \'true\';"')
    os.system('cd export && tar -czf ../' + exportFile + ' .')
    os.system(up)
