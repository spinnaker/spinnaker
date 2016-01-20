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

The export is archived and uploaded to your S3 or GCS bucket.

## Usage

Simply clone this repo on your Spinnaker instance and execute `./import_export.py`.

Your instance role must have access to your bucket. You can also use your own credentials or keys by configuring the cloud sdk before you use the tool.

AWS: `aws configure`

GCE: `gcloud auth login`

```
$ ./import_export.py
usage: spinio [-h] --cloud {aws,gcp} --mode {import,export} --bucket BUCKET
              [--importFile IMPORTFILE]
```

## Spinnaker upgrade example

* Create a bucket to store the export
* Use the tool to export the archive to your bucket.
```
./import_export.py --cloud gcp --mode export --bucket BUCKET-NAME
```
Take note of the resulting archive filename that is uploaded.

* Stop your current instance and launch a fresh Spinnaker instance.
* Run the tool in import mode on your new instance with the name of the archive step 2 created.
```
./import_export.py --cloud gcp --mode import --bucket BUCKET-NAME --importFile FILENAME
```

Your imported items should now be visible on your new instance. Take some time to confirm everything is correct before terminating your old Spinnaker instance.

If you do not specify an importFile, the most recent archive in your bucket will be used.

'''

import os
import shutil
from distutils import spawn
import argparse
import time

parser = argparse.ArgumentParser()
parser.add_argument('--cloud', required=True, choices=['aws', 'gcp'], help='Choose cloud provider')
parser.add_argument('--mode', required=True, choices=['import', 'export'], help='Choose mode')
parser.add_argument('--bucket', required=True, help='bucket name to store or download archive')
parser.add_argument('--importFile', help='bucket archive file to import, if missing then the most recent archive will be used ')
args = parser.parse_args()

exportFile = 'spinnaker_export_' + str(time.time()) + '.tgz'

keyspaces = {}
keyspaces['front50'] = ['project', 'application', 'pipeline', 'strategy', 'notifications']
keyspaces['rush'] = ['execution']
keyspaces['echo'] = ['trigger', 'execution', 'action_instance']

commands = {
    'aws': {
        'cli': 'aws',
        'upload': 'aws s3 cp archive/{0} s3://{1}'.format(exportFile, args.bucket),
        'download': 'aws s3 cp s3://{0}'.format(args.bucket),
        'list': 'aws s3 ls s3://{0}'.format(args.bucket)
    },
    'gcp': {
        'cli': 'gsutil',
        'upload': 'gsutil cp archive/{0} gs://{1}'.format(exportFile, args.bucket),
        'download': 'gsutil cp gs://{0}'.format(args.bucket),
        'list': 'gsutil ls gs://{0}/spinnaker_export*'.format(args.bucket)
    }
}

importFile = ""
if args.mode == 'import':
    if args.importFile:
        importFile = args.importFile
    else:
        importFile = os.popen(commands[args.cloud]['list']).read()
        if args.cloud == 'gcp':
            importFile = importFile.split("\n")[-2].split(args.bucket + '/')[-1]
        if args.cloud == 'aws':
            importFile = importFile.split("\n")[-2].split(" ")[-1]
if not spawn.find_executable(commands[args.cloud]['cli']):
    raise Exception('Cannot find cloud sdk on path')

if not spawn.find_executable('cqlsh'):
    raise Exception('cqlsh not found on path')

if args.mode == 'import':
    if os.path.exists('import'):
        shutil.rmtree('import')
    os.mkdir('import')
    os.system('cd import && ' + commands[args.cloud]['download'] + '/' + importFile + ' ' + importFile)
    os.system('cd import && tar -xvf ' + importFile)
    os.system('service redis-server stop')
    os.system('cp import/dump.rdb /var/lib/redis/dump.rdb')
    os.system('chown redis: /var/lib/redis/dump.rdb')
    os.system('service redis-server start')
    for keyspace, tables in keyspaces.items():
        for table in tables:
            os.system('cqlsh -e "COPY ' + keyspace + '.' + table + ' FROM \'import/' + keyspace + '.' + table + '.csv\' WITH HEADER = \'true\';" 2> /tmp/spinnaker_import_log.txt')
    os.system('rm -rf import')
    print "Spinnaker Import Complete"

if args.mode == 'export':
    if os.path.exists('export'):
        shutil.rmtree('export')
    if os.path.exists('archive'):
        shutil.rmtree('archive')
    os.mkdir('export')
    os.mkdir('archive')
    for keyspace, tables in keyspaces.items():
        for table in tables:
            os.system('cqlsh -e "COPY ' + keyspace + '.' + table + ' TO \'export/' + keyspace + '.' + table + '.csv\' WITH HEADER = \'true\';"')
            os.system("sed -i -e 's/\\\\n/ /g' export/" + keyspace + '.' + table + '.csv')
    os.system("redis-cli --scan --pattern 'com.netflix.spinnaker.oort*' | xargs -d '\n' redis-cli del")
    os.system('redis-cli SAVE')
    os.system('cp /var/lib/redis/dump.rdb export/dump.rdb')
    os.system('cd export && tar -czf ../archive/' + exportFile + ' .')
    os.system(commands[args.cloud]['upload'])
    os.system('rm -rf export')
    os.system('rm -rf archive')
    print "Spinnaker Export Complete"
