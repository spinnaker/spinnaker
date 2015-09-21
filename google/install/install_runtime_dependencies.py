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

from install_utils import fetch_or_die
from install_utils import run_or_die


APACHE2_VERSION='2.4.7-1ubuntu4.5'
CASSANDRA_VERSION='2.1.9'
OPENJDK_8_VERSION='8u45-b14-1~14.04'
REDIS_SERVER_VERSION='2:2.8.4-2'

DECK_PORT=9000


def init_argument_parser(parser, default_values={}):
    """Initialize ArgumentParser with commandline arguments for this module.
    """
    parser.add_argument('--apache',
                        default=default_values.get('apache', True),
                        action='store_true',
                        help='Install apache2 server.')
    parser.add_argument('--noapache', dest='apache', action='store_false')

    parser.add_argument('--cassandra',
                        default=default_values.get('cassandra', True),
                        action='store_true',
                        help='Install cassandra service.')
    parser.add_argument('--nocassandra', dest='cassandra',
                        action='store_false')

    parser.add_argument('--jdk',
                        default=default_values.get('jdk', True),
                        action='store_true',
                        help='Install openjdk.')
    parser.add_argument('--nojdk', dest='jdk', action='store_false')

    parser.add_argument(
        '--package_manager',
        default=default_values.get('package_manager', True),
        action='store_true',
        help='Allow modifications to package manager repository list')
    parser.add_argument('--nopackage_manager',
                        dest='package_manager', action='store_false')

    parser.add_argument('--redis',
                        default=default_values.get('redis', True),
                        action='store_true',
                        help='Install redis-server service.')
    parser.add_argument('--noredis', dest='redis', action='store_false')

    parser.add_argument('--update_os',
                        default=default_values.get('update_os', False),
                        action='store_true',
                        help='Install OS updates since the base image.')
    parser.add_argument(
        '--noupdate_os', dest='update_os', action='store_false')


def install_package_or_die(name, version=None, options=[]):
  """Install the specified package, with specific version if provide.

  Args:
    name: The unversioned package name.
    version: If provided, the specific version to install.
    options: Additional command-line options to apt-get install.
  """
  package_name = name
  if version:
      package_name += '={0}'.format(version)

  command = ['sudo apt-get -q -y']
  command.extend(options)
  command.extend(['install', package_name])
  run_or_die(' '.join(command), echo=True)


def install_os_updates(options):
  if not options.update_os:
      print 'Skipping os upgrades.'
      return

  print 'Upgrading packages...'
  run_or_die('sudo apt-get -y update', echo=True)
  run_or_die('sudo apt-get -y upgrade', echo=True)


def install_runtime_dependencies(options):
    """Install all the spinnaker runtime dependencies.

    Args:
      options: ArgumentParserNamespace can turn off individual dependencies.
    """
    install_java(options, which='jre')
    install_cassandra(options)
    install_redis(options)
    install_apache(options)
    install_os_updates(options)


def check_java_version():
    try:
      p = subprocess.Popen('java -version', shell=True, stdout=subprocess.PIPE)
      stdout, stderr = p.communicate()
      code = p.returncode()
    except OSError as error:
      return str(error)

    info = stdout + stderr
    if code != 0:
      return 'Java does not appear to be installed.'

    m = re.search(r'^openjdk version "(.*)"', info)
    if not m:
      m = re.search(r'^java version "(.*)"', info)
    if not m:
        return 'Unrecognized java version:\n{0}'.format(info)
    if m.group(1)[0:3] != '1.8':
      return ('Java {version} is currently installed.'
             ' However, version 1.8 is required.'.format(version=m.group(1)))
    print 'Found java {version}'.format(version=m.group(0))
    return None


def install_java(options, which='jre'):
    """Install java.

    TODO(ewiseblatt):
    This requires a package manager, but only because I'm not sure how
    to install it without one. If you are not using a package manager,
    then verison 1.8 must already be installed.

    Args:
      options: ArgumentParserNamespace options.
      which: Install either 'jre' or 'jdk'.
    """
    if not options.jdk:
        print '--nojdk skipping Java install.'
        return

    if which != 'jre' and which != 'jdk':
        raise ValueError('Expected which=(jdk|jre)')

    if not options.package_manager:
        msg = check_java_version()
        if msg:
          sys.stderr.write(
              ('{msg}\nSorry, Java must already be installed using the'
               ' package manager.\n'.format(msg=msg)))
          raise SystemExit('Java must already be installed.')
        else:
          print 'Using existing java.'
          return

    print 'Installing OpenJdk...'
    run_or_die('sudo add-apt-repository -y ppa:openjdk-r/ppa', echo=True)
    run_or_die('sudo apt-get -y update', echo=True)

    install_package_or_die('openjdk-8-{which}'.format(which=which),
                           version=OPENJDK_8_VERSION)
    cmd =  ['sudo', 'update-java-alternatives']
    if which == 'jre':
        cmd.append('--jre')
    cmd.extend(['-s', '/usr/lib/jvm/java-1.8.0-openjdk-amd64'])
    run_or_die(' '.join(cmd), echo=True)


def install_cassandra(options):
    """Install Cassandra.

    Args:
      options: ArgumentParserNamespace options.
    """
    if not options.cassandra:
        print '--nocassandra skipping Casssandra install.'
        return

    print 'Installing Cassandra...'
    if not options.package_manager:
        root = 'https://archive.apache.org/dist/cassandra/debian/pool/main/c'
        try:
          os.mkdir('downloads')
        except OSError:
          pass

        cassandra = 'cassandra_{ver}_all.deb'.format(ver=CASSANDRA_VERSION)
        tools = 'cassandra-tools_{ver}_all.deb'.format(ver=CASSANDRA_VERSION)

        content = fetch_or_die(
            '{root}/cassandra/{cassandra}'
            .format(root=root, cassandra=cassandra))
        with open('downloads/{cassandra}'
                  .format(cassandra=cassandra), 'w') as f:
            f.write(content)

        content = fetch_or_die(
            '{root}/cassandra/{tools}'
            .format(root=root, tools=tools))
        with open('downloads/{tools}'
                  .format(tools=tools), 'w') as f:
            f.write(content)

        run_or_die('sudo dpkg -i downloads/' + cassandra, echo=True)
        run_or_die('sudo dpkg -i downloads/' + tools, echo=True)
    else:
      run_or_die('sudo add-apt-repository -s'
                 ' "deb http://www.apache.org/dist/cassandra/debian 21x main"',
                 echo=True)

    run_or_die('sudo apt-get -q -y update', echo=True)
    install_package_or_die('cassandra', version=CASSANDRA_VERSION,
                           options=['--force-yes'])


def install_redis(options):
    """Install Redis-Server.

    Args:
      options: ArgumentParserNamespace options.
    """
    if not options.redis:
        print '--noredis skips Redis install.'
        return
    print 'Installing Redis...'
    install_package_or_die('redis-server', version=REDIS_SERVER_VERSION)


def install_apache(options):
    """Install Apache2

    This will update /etc/apache2/ports so Apache listens on DECK_PORT
    instead of its default port 80.

    Args:
      options: ArgumentParserNamespace options.
    """
    if not options.apache:
        print '--noapache skips Apache install.'
        return

    print 'Installing apache2...'
    install_package_or_die('apache2', version=APACHE2_VERSION)

    # Change apache to run on port $DECK_PORT by default.
    # We're writing back with cat so we can sudo.
    with open('/etc/apache2/ports.conf', 'r') as f:
        content = f.read()
    print 'Changing default port to {0}'.format(DECK_PORT)
    content = content.replace('Listen 80\n', 'Listen {0}\n'.format(DECK_PORT))
    run_or_die('sudo bash -c cat > /etc/apache2/ports.conf',
               input=content, echo=False)

    run_or_die('sudo apt-get install -f -y', echo=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    init_argument_parser(parser)
    options = parser.parse_args()
    install_runtime_dependencies(options)
