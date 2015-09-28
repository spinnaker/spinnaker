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

import glob
import os
import re
import sys

from install.install_utils import fetch
from install.install_utils import run
from validate_configuration import ValidateConfig

METADATA_URL = 'http://metadata.google.internal/computeMetadata/v1'

class InstallationParameters(object):
  """Describes a standard release installation layout.

  Contains constants for where different parts of the release are installed.

  Attributes:
    CONFIG_DIR: Path to directory containing installation configuration files
       for the indivual subsystems.

    LOG_DIR: Path to directory where individual log files are written.

    SUBSYSTEM_ROOT_DIR: Path to directory containing spinnaker subsystem
        installation directories.

    SPINNAKER_INSTALL_DIR: Path to the root spinnaker installation directory.

    UTILITY_SCRIPT_DIR: Path to directory containing spinnaker maintainence
       and other utility scripts.

    EXTERNAL_DEPENDENCY_SCRIPT_DIR: Path to directory containing maintainence
        and utility scripts for managing dependencies outside spinnaker itself.

    CONFIG_TEMPLATE_DIR: Path to directory containing the master configuration
       template files used as the basis for reconfiguring an installation.

    DECK_INSTALL_DIR: Path to directory where deck is installed, which is
        typically different from the other spinnaker subsystems.

    HACK_DECK_SETTINGS_FILENAME: The name of the settings file for deck
        is non-standard and recorded here for the time being.
  """

  CONFIG_DIR = '/root/.spinnaker'
  LOG_DIR = '/opt/spinnaker/logs'

  SUBSYSTEM_ROOT_DIR = '/opt'
  SPINNAKER_INSTALL_DIR = '/opt/spinnaker'
  UTILITY_SCRIPT_DIR = '/opt/spinnaker/scripts'
  EXTERNAL_DEPENDENCY_SCRIPT_DIR = '/opt/spinnaker/scripts'

  CONFIG_TEMPLATE_DIR = SPINNAKER_INSTALL_DIR + '/config_templates'

  DECK_INSTALL_DIR = '/var/www'
  HACK_DECK_SETTINGS_FILENAME = 'deck_settings.js'


class ConfigureUtil(object):
  """Defines methods for manipulating spinnaker configuration data."""

  def __init__(self, installation_parameters=None):
    """Constructor

    Args:
      installation_parameters: An InstallationParameters instance.
    """
    self.__installation = installation_parameters or InstallationParameters()

  def validate_or_die(self):
    """Validate the master configuration."""
    ok = ValidateConfig(parameters=self.__installation).validate()
    if not ok:
      sys.stderr.write(
        '*** ERROR *** Configuration seems invalid.\n')
      sys.stderr.write(
        '*** ERROR *** Please resolve these issues and try again.')
      raise SystemExit('Configuration does not look valid.')

  def update_all_config_files(self, bindings):
    """Update all the derived configuration files using the given bindings.

    Args:
      bindings: name/value dictionary of the templated variable bindings.
    """
    installation = self.__installation
    print 'Updating configurations in ' + installation.CONFIG_DIR

    binding_copy = {}
    binding_copy.update(bindings)
    bindings = binding_copy

    for cfg in glob.glob(installation.CONFIG_TEMPLATE_DIR + '/*.yml'):
      self.replace_variables_in_file(
          cfg,
          installation.CONFIG_DIR + '/' + os.path.basename(cfg),
          bindings.items())

    credential_path = bindings.get('GOOGLE_JSON_CREDENTIAL_PATH', '')
    with open(installation.CONFIG_DIR + '/gce-kms-local.yml', 'r') as f:
      content = f.read()
    if credential_path:
        content = content.replace('$GOOGLE_JSON_CREDENTIAL_PATH',
                                  credential_path)
    else:
        content = content.replace('jsonPath:', '#jsonPath:')
    with open(installation.CONFIG_DIR + '/gce-kms-local.yml', 'w') as f:
        f.write(content)

    self.replace_variables_in_file(
        installation.CONFIG_TEMPLATE_DIR
            + '/' + installation.HACK_DECK_SETTINGS_FILENAME,
        installation.DECK_INSTALL_DIR + '/settings.js',
        bindings.items())
    return

  def load_bindings(self):
    """Load the specified bindings from master configuration file.

    Returns:
      Dictionary of all the variable bindings with defaults or overriden files.
    """
    installation = self.__installation

    # Get default values and ensure required things are defined
    # so user config can leave them out before including user config.
    bindings = self.load_config(
        installation.CONFIG_TEMPLATE_DIR + '/default_spinnaker_config.cfg')
    bindings.update(
        self.load_config(installation.CONFIG_DIR + '/spinnaker_config.cfg'))

    # Auto-define IGOR_ENABLED.
    # It is unfortunate that we need this, but seems to be used internally.
    # We dont start igor if we dont need it, so this is a safety mechanism.
    if bindings.get('IGOR_ENABLED', '') == '':
      bindings['IGOR_ENABLED'] = (
          'true' if bindings.get('JENKINS_ADDRESS', '') != ''
                 else 'false')

    managed_project = bindings.get('GOOGLE_MANAGED_PROJECT_ID', '')
    if not managed_project:
        code, managed_project = fetch(
          METADATA_URL + '/project/project-id',
          google=True)
        if code != 200:
          raise SystemExit('GOOGLE_MANAGED_PROJECT_ID is required if you are'
                           ' not running on Google Compute Engine.')
        bindings['GOOGLE_MANAGED_PROJECT_ID'] = managed_project

    return bindings

  @staticmethod
  def load_config(config_path):
    """Load configuration file into a dictionary.

    Returns:
      Dictionary of key/value pairs found only in the configuration file.
      (no inherited values)
    """
    with open(config_path, 'r') as f:
      content = f.read()

    result = {}
    for match in re.findall(r'^([A-Za-z]\w*)=(.*)', content, re.M):
      result[match[0]] = match[1]
    return result

  @staticmethod
  def replace_variables_in_file(source_path, target_path, binding_pairs):
    """Replace all the bound variables in the specified paths.

    For a given variable name, value pair replace occurances of the variable
    with the provided value. A variable can occur in three different ways:
        $NAME
        ${NAME}
        ${NAME:default}
    Each of these variations will be replace with just the value.

    Args:
      source_path: The file path containing variables to replace.
      target_path: The file path to write the replaced content into.
      binding_pairs: A list of (name, value) pairs to replace.
    """
    with open(source_path, 'r') as f:
      content = f.read()

    for name,value in binding_pairs:
      content = content.replace('$' + name, value)
      content = re.sub('\${{{name}}}'.format(name=name), value, content)
      content = re.sub('\${{{name}:[^}}]*}}'.format(name=name), value, content)

    with open(target_path, 'w') as f:
      f.write(content)
