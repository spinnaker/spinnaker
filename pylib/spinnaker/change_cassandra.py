# Copyright 2016 Google Inc. All Rights Reserved.
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

"""Tweak an installation to enable cassandra or not.

This module/program lets you change a deployment's persistence mechanisms
for front50 and echo. If using cassandra for either, then a cassandra
server will be started (if cassandra was configured to run on this host).
Otherwise, cassandra will be shut down. Either way, the yaml configuration
files will be updated to persist the configuration.

By default only spinnaker-local will be updated. However you can specify
whether to update spinnaker.yml and/or spinnaker-local.yml.

When using s3 or gcs storage (for front50) you can also override the bucket
that is used. You will also need to have suitable credentials. For s3, this
usually means an $HOME/.aws/credentials file. For gcs, this means you are
either on GCE and managing the current project and have storage-rw OAuth scopes in the
instance, or you have a providers.google.primaryCredentials.jsonPath configured
with service account credentials.

Usage:
   --echo=(cassandra|inMemory)  --front50=(cassandra|gcs|s3|azs)
   [--bucket=<storage_bucket_name>]
   [--change_local=(true|false)]
   [--change_defaults=(true|false)]

Default is to --change_local only.

To change back, run the script again with the newly desired --echo and/or
--front50 mechanisms.
"""

import argparse
import errno
import os
import socket
import time
from yaml_util import YamlBindings
from configurator import Configurator

ECHO_CHOICES = ['cassandra', 'inMemory']

FRONT50_CHOICES = ['cassandra', 's3', 'gcs', 'redis', 'azs']

_ECHO_KEYS = ['services.echo.cassandra.enabled',
              'services.echo.inMemory.enabled']
_FRONT50_KEYS = ['services.front50.cassandra.enabled',
                 'services.front50.redis.enabled',
                 'services.front50.s3.enabled',
                 'services.front50.gcs.enabled',
                 'services.front50.storage_bucket',
                 'services.front50.azs.enabled']

SPINNAKER_INSTALLED_PATH = '/opt/spinnaker/cassandra/SPINNAKER_INSTALLED_CASSANDRA'
SPINNAKER_DISABLED_PATH = '/opt/spinnaker/cassandra/SPINNAKER_DISABLED_CASSANDRA'


def cassandra_installed():
  return os.system('service --status-all 2>1 | grep -q cassandra') == 0


def service_is_running(name):
  return os.system('service --status-all 2>1 | grep -q {name} | grep " + "'
                   .format(name=name)) == 0


class CassandraChanger(object):
  @classmethod
  def init_argument_parser(cls, parser):
    parser.add_argument('--echo', required=True,
                        help='Mechanism to use for echo [{echo_choices}]'.format(
                            echo_choices=ECHO_CHOICES))
    parser.add_argument('--front50', required=True,
                        help='Mechanism to use for front50 [{front50_choices}]'.format(
                            front50_choices=FRONT50_CHOICES))
    parser.add_argument('--bucket', default='',
                        help='Bucket to use for front50 if s3 or gcs.')
    parser.add_argument('--change_defaults', default=False,
                        help='Change the defaults in spinnaker.yml')
    parser.add_argument('--change_local', default=True,
                        help='Change the defaults in spinnaker-local')


  def __init__(self, options):
    if not options.echo in ECHO_CHOICES:
      raise ValueError('--echo="{echo}" is not in {choices}'.format(
        echo=options.echo, choices=ECHO_CHOICES))

    if not options.front50 in FRONT50_CHOICES:
      raise ValueError('--front50="{front50}" is not in {choices}'.format(
        echo=options.front50, choices=FRONT50_CHOICES))

    # The configurator holds our "old" configuration so we can reference it later.
    self.__configurator = Configurator()

    self.__bindings = YamlBindings()
    self.__options = options

    config = {'echo': {'cassandra': {'enabled': options.echo == 'cassandra'},
                       'inMemory':  {'enabled': options.echo == 'inMemory'},
                      },
              'front50': {'cassandra': {'enabled': options.front50 == 'cassandra'},
                          'redis': {'enabled': options.front50 == 'redis'},
                          's3': {'enabled': options.front50 == 's3'},
                          'gcs': {'enabled': options.front50 == 'gcs'},
                          'azs': {'enabled': options.front50 == 'azs'}
                         }}
    if options.bucket:
        config['front50']['storage_bucket'] = options.bucket
    self.__bindings.import_dict({'services': config})

  def disable_cassandra(self):
    if os.path.exists(SPINNAKER_INSTALLED_PATH):
      os.remove(SPINNAKER_INSTALLED_PATH)
      open(SPINNAKER_DISABLED_PATH, 'w').close()

    if cassandra_installed():
      with open('/etc/init/cassandra.override', 'w') as f:
        f.write('manual')
      print 'Stopping cassandra service...'
      os.system('service cassandra stop || true')

  def enable_cassandra(self):
    is_installed = cassandra_installed()
    if os.path.exists(SPINNAKER_DISABLED_PATH):
      os.remove(SPINNAKER_DISABLED_PATH)
      open(SPINNAKER_INSTALLED_PATH, 'w').close()

    if is_installed:
      try:
        os.remove('/etc/init/cassandra.override')
      except OSError as err:
        if err.errno == errno.ENOENT:
           pass

    cassandra_host = self.__configurator.bindings['services.cassandra.host']
    cassandra_port = self.__configurator.bindings['services.cassandra.port']
    if (cassandra_host in ['localhost', '127.0.0.1', '0.0.0.0']
        or cassandra_host == socket.gethostname()):
      if not is_installed:
        raise RuntimeError('Cassandra is not installed - install cassandra and try again.')
      print 'Starting cassandra service...'
      os.system('service cassandra start')
      sock = socket.socket()
      while True:
        try:
          sock.connect((cassandra_host, cassandra_port))
          break
        except IOError as err:
          time.sleep(0.1)
      sock.close()
      print 'Installing cassandra keyspaces...'
      os.system('cqlsh -f "/opt/spinnaker/cassandra/create_echo_keyspace.cql"')
      os.system('cqlsh -f "/opt/spinnaker/cassandra/create_front50_keyspace.cql"')
    else:
      print 'Not starting cassandra because we are consuming it from {host}'.format(
        host=cassandra_host)

  def change(self):
    paths = []
    if str(self.__options.change_defaults).lower() == "true":
        path = '/opt/spinnaker/config/spinnaker.yml'
        with open(path, 'r'):
          paths.append(path)

        try:
          # Also the default spinnaker local, if present,
          # so that it is consistent with the spinnaker.yml defaults
          path = '/opt/spinnaker/config/default-spinnaker-local.yml'
          with open(path, 'r'):
            paths.append(path)
        except IOError:
          pass

    if str(self.__options.change_local).lower() == "true":
      for path in ['/opt/spinnaker/config/spinnaker-local.yml',
                   os.path.join(os.environ.get('HOME'),
                                '.spinnaker', 'spinnaker-local.yml')]:
        try:
          with open(path, 'r'):
            paths.append(path)
        except IOError:
          print 'Could not open {path} to apply --change_local.'.format(path=path)

    for path in paths:
        print 'Updating {path}'.format(path=path)
        with open(path, 'r') as f:
          yml = f.read()

        stat = os.stat(path)
        yml = self._do_change_yml(yml, path, _ECHO_KEYS)
        yml = self._do_change_yml(yml, path, _FRONT50_KEYS)
        f = os.open(path, os.O_WRONLY | os.O_TRUNC, 0600)
        os.write(f, yml)
        os.close(f)
        os.chown(path, stat.st_uid, stat.st_gid)

    if self.__options.echo != 'cassandra' and self.__options.front50 != 'cassandra':
        self.disable_cassandra()
    else:
        self.enable_cassandra()
    self.maybe_restart_spinnaker()

  def maybe_restart_spinnaker(self):
    if not service_is_running('echo'):
      print 'Echo wasnt running'
    elif not self.__configurator.bindings.get(
          'services.echo.{which}.enabled'.format(which=self.__options.echo)):
      print 'Restarting echo...'
      os.system('service echo restart || true')
    else:
      print 'Echo was unchanged.'

    if not service_is_running('front50'):
      print 'Front50 wasnt running'
    elif (not self.__configurator.bindings.get(
           'services.front50.{which}.enabled'.format(which=self.__options.front50))
        or (self.__options.front50 in ['s3', 'gcs']
            and self.__configurator.bindings['services.front50.storage_bucket']
               != self.__options.bucket)):
      print 'Restarting front50...'
      os.system('service front50 restart || true')
    else:
      print 'Front50 was unchanged.'

  def _do_change_yml(self, yml, path, keys):
    if not yml:
     return yml

    for key in keys:
      try:
        yml = self.__bindings.transform_yaml_source(yml, key)
      except KeyError:
        if self.__bindings[key]:
          raise
        print '{key} is not in {path}, leaving as it was.'.format(key=key, path=path)
    return yml


def main():
    parser = argparse.ArgumentParser()
    CassandraChanger.init_argument_parser(parser)
    options = parser.parse_args()
    changer = CassandraChanger(options)
    changer.change()

if __name__ == '__main__':
    main()
