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

import os
import re
import sys

import yaml_util


class InstallationParameters(object):
  """Describes a standard release installation layout.

  Contains constants for where different parts of the release are installed.

  Attributes:
    USER_CONFIG_DIR: Path to directory containing installation configuration
       files for the individual subsystems.

    LOG_DIR: Path to directory where individual log files are written.

    SUBSYSTEM_ROOT_DIR: Path to directory containing spinnaker subsystem
        installation directories.

    SPINNAKER_INSTALL_DIR: Path to the root spinnaker installation directory.

    UTILITY_SCRIPT_DIR: Path to directory containing spinnaker maintenance
       and other utility scripts.

    EXTERNAL_DEPENDENCY_SCRIPT_DIR: Path to directory containing maintenance
        and utility scripts for managing dependencies outside spinnaker itself.

    INSTALLED_CONFIG_DIR: Path to directory containing the master configuration
       files for the release. These are intended to be read-only.

    DECK_INSTALL_DIR: Path to directory where deck is installed, which is
        typically different from the other spinnaker subsystems.

    HACK_DECK_SETTINGS_FILENAME: The name of the settings file for deck
        is non-standard and recorded here for the time being.
  """

  USER_CONFIG_DIR = '/home/spinnaker/.spinnaker'
  LOG_DIR = '/opt/spinnaker/logs'

  SUBSYSTEM_ROOT_DIR = '/opt'
  SPINNAKER_INSTALL_DIR = '/opt/spinnaker'
  UTILITY_SCRIPT_DIR = '/opt/spinnaker/bin'
  EXTERNAL_DEPENDENCY_SCRIPT_DIR = '/opt/spinnaker/scripts'

  INSTALLED_CONFIG_DIR = SPINNAKER_INSTALL_DIR + '/config'

  DECK_INSTALL_DIR = '/var/www/html'
  HACK_DECK_SETTINGS_FILENAME = 'settings.js'
  ENVIRONMENT_VARIABLE_PATH = '/etc/default/spinnaker'


class Configurator(object):
  """Defines methods for manipulating spinnaker configuration data."""

  @property
  def bindings(self):
    """Returns the system level yaml bindings.

    This is spinnaker.yml with spinnaker-local imposed on top of it.
    """
    if self.__bindings is None:
      self.__bindings = yaml_util.load_bindings(
          self.installation_config_dir, self.user_config_dir)
    return self.__bindings

  @property
  def installation(self):
    """Returns the installation configuration (directory locations)."""
    return self.__installation

  @property
  def installation_config_dir(self):
    """Returns the location of the system installed config directory."""
    return self.__installation.INSTALLED_CONFIG_DIR

  @property
  def deck_install_dir(self):
    """Returns the location of the deck directory for the active settings.js"""
    return self.__installation.DECK_INSTALL_DIR

  @property
  def user_config_dir(self):
    """Returns the user (or system's) .spinnaker directory for overrides."""
    return self.__installation.USER_CONFIG_DIR

  def __init__(self, installation_parameters=None, bindings=None):
    """Constructor

    Args:
      installation_parameters [InstallationParameters] if None then use default
      bindings [YamlBindings] Allows bindings to be explicitly injected for
         testing. Otherwise they are loaded on demand.
    """
    if not installation_parameters:
      installation_parameters = InstallationParameters()
      if os.geteuid():
          # If we are not running as root and there is an installation on
          # this machine as well as a user/.spinnaker directory then it is
          # ambiguous which we are validating. For safety we'll force this
          # to be the normal system installation. Warn that we are doing this.
          installed_bindings = yaml_util.load_bindings(
               installation_parameters.INSTALLED_CONFIG_DIR,
               installation_parameters.USER_CONFIG_DIR,
               only_if_local=True)
          user_config = os.path.join(os.environ['HOME'], '.spinnaker')
          personal_path = os.path.join(user_config, 'spinnaker-local.yml')
          deck_dir = installation_parameters.DECK_INSTALL_DIR
          if os.path.exists(personal_path):
            if installed_bindings is not None:
                sys.stderr.write(
                  'WARNING: You have both personal and system Spinnaker'
                  ' configurations on this machine.\n'
                  'Choosing the personal configuration in "{path}".\n'
                  .format(path=user_config))
            deck_dir = os.path.join(os.environ.get('PWD', '.'), 'deck')
            if not os.path.exists(deck_dir):
              raise RuntimeError(
                       'To use a personal configuration, this program must be'
                       ' run from your top-level build directory that you'
                       ' cloned the repositories to.\n'
                       'We assume you are a developer since you have a'
                       ' $HOME/.spinnaker directory. If you meant to operate on'
                       ' the installed Spinnaker, run this with sudo.')

          # If we are not root, allow for a non-standard installation location.
          installation_parameters.INSTALLED_CONFIG_DIR = os.path.abspath(
             os.path.join(os.path.dirname(__file__), '../../config'))
          installation_parameters.USER_CONFIG_DIR = user_config
          installation_parameters.DECK_INSTALL_DIR = deck_dir

    self.__installation = installation_parameters
    self.__bindings = bindings   # Either injected or loaded on demand.
    self.load_environment_variables()

  @staticmethod
  def export_environment_variables(content):
    for match in re.finditer('^([^\s]+?)=([^\n]*)(?:\#.+)?', content,
                             re.MULTILINE):
      os.environ[match.group(1)] = match.group(2)

  def load_environment_variables(self, path=None):
    if not path:
      path = self.__installation.ENVIRONMENT_VARIABLE_PATH
    try:
      with open(path, 'r') as f:
        content = f.read()
      self.export_environment_variables(content)
    except IOError:
      pass

  def update_deck_settings(self):
    """Update the settings.js file from configuration info."""
    source_path = os.path.join(self.installation_config_dir, 'settings.js')
    with open(source_path, 'r') as f:
      source = f.read()

    settings = self.process_deck_settings(source)
    target_path = os.path.join(self.deck_install_dir, 'settings.js')
    print 'Rewriting deck settings in "{path}".'.format(path=target_path)
    with open(target_path, 'w') as f:
      f.write(''.join(settings))

  def process_deck_settings(self, source):
    offset = source.find('// BEGIN reconfigure_spinnaker')
    if offset < 0:
      raise ValueError(
        'deck settings file does not contain a'
        ' "# BEGIN reconfigure_spinnaker" marker.')
    end = source.find('// END reconfigure_spinnaker')
    if end < 0:
      raise ValueError(
        'deck settings file does not contain a'
        ' "// END reconfigure_spinnaker" marker.')

    original_block = source[offset:end]
    # Remove all the explicit declarations in this block
    # Leaving us with just comments
    block = re.sub('\n\s*var\s+\w+\s*=(.+)\n', '\n', original_block)
    settings = [source[:offset]]

    # Now iterate over the comments looking for var specifications
    offset = 0
    for match in re.finditer('//\s*var\s+(\w+)\s*=\s*(.+?);?\n', block) or []:
      settings.append(block[offset:match.end()])
      offset = match.end()
      name = match.group(1)
      value = self.bindings.replace(match.group(2))
      if isinstance(value, bool):
        # Convert to javascript bool value by lowercasing the string
        settings.append('var {name} = {value};\n'.format(
           name=name, value=str(value).lower()))
      else:
        # Quote strings, don't quote numbers.
        settings.append('var {name} = {value!r};\n'.format(
           name=name, value=value))

    settings.append(block[offset:])
    settings.append(source[end:])
    return ''.join(settings)
