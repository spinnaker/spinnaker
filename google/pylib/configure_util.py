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
import yaml

from fetch import AWS_METADATA_URL
from fetch import GOOGLE_METADATA_URL
from fetch import fetch
from fetch import get_google_project
from fetch import is_aws_instance
from fetch import is_google_instance

from validate_configuration import ValidateConfig


def normalize_path(path):
  """Explicitly populate $HOME and ~ in the path."""

  return os.path.expandvars(os.path.expanduser(path))


class Bindings(dict):
  """Manages the bindings of configuration variables.

  Bindings are either name/string variable pairs or name/dict yaml pairs.
  """
  @property
  def variables(self):
    return self.__variable_bindings

  def __init__(self):
    self.__variable_bindings = {}
    self.__yaml_bindings = {}

  def clone(self):
    copy = self.__class__()
    copy.__variable_bindings.update(self.__variable_bindings)
    copy.__yaml_bindings.update(self.__yaml_bindings)
    return copy

  def get_variable(self, name, default=None):
    """Get the variable binding for the given name.

    Existing environment variables have the highest precedence.
    Then config values from the loaded config files.
    Then the default value.
    """
    config_value = self.__variable_bindings.get(name, default)
    value = os.environ.get(name, config_value)
    if value is None:
      raise KeyError(name)

    return value

  def get_yaml(self, name, default=None):
    value = self.__yaml_bindings.get(name, default)
    if value is None:
      raise KeyError(name)
    return value

  def set_variable(self, name, value):
    self.__variable_bindings[name] = value

  def replace_variables(self, content):
    result = self.__just_replace_yaml(content)
    return self.__just_replace_variables(result)

  def update_from_config(self, config_path):
    """Load configuration file into the bindings.

    Overrides anything already present.
    """
    with open(config_path, 'r') as f:
      content = f.read()

    for match in re.findall(r'^([A-Za-z]\w*)[ \t]*=[ \t]*(.*)', content, re.M):
      self.__variable_bindings[match[0]] = match[1]

    for match in re.findall(r'^@([A-Za-z]\w*)[ \t]*=[ \t]*(.*)', content, re.M):
      self.__add_yaml_variable(match[0], match[1])

  def __add_yaml_variable(self, name, value):
    if name != 'GOOGLE_CREDENTIALS':
      raise ValueError('@ Can only be used for GOOGLE_CREDENTIALS')
    substituted_value = self.__just_replace_variables(value)
    parts = substituted_value.split(':')
    if len(parts) == 4:
        # Sometimes project names can be in the form <domain>:project
        # since we're using ':' to separate, there is a conflct.
        # We could consider using a different separator, but the obvious
        # ones are also taken or awkward for other reasons.
        # The domain case is rare. We'll join them together if it exists.

        if re.match('^[a-z]+\.[a-z]+$', parts[1]):
            parts = [parts[0], parts[1] + ':' + parts[2], parts[3]]

    if len(parts) != 3:
      raise ValueError('@{0}={1} is not in the form <account>:<project>:<path>'
                       .format(name, value))
    if not parts[1]:
      parts[1] = get_google_project()
      if parts[1] is None:
          raise ValueError(
              'An account must be associated with a Google Cloud Platform'
              ' project id if you are not running on Google Compute Engine.')

    account_info_decl = {'name': parts[0], 'project': parts[1]}
    account_info_ref  = dict(account_info_decl)
    if parts[2]:
      account_info_decl['jsonPath'] = normalize_path(parts[2])

    if not 'GOOGLE_CREDENTIALS_DECLARATION' in self.__yaml_bindings:
      self.__yaml_bindings['GOOGLE_CREDENTIALS_REFERENCE'] = []
      self.__yaml_bindings['GOOGLE_CREDENTIALS_DECLARATION'] = []
    self.__yaml_bindings['GOOGLE_CREDENTIALS_DECLARATION'].append(
        account_info_decl)
    self.__yaml_bindings['GOOGLE_CREDENTIALS_REFERENCE'].append(
        account_info_ref)

  def resolve_inner_variables(self):
    """Resolve variable values that are in themselves variable references.

    This should not be called until the very end or the variables will be
    resolved with lower precedence variables should a higher level source
    that overrides the variable be added later.
    """
    for name,value in self.__variable_bindings.items():
       if not value.startswith('$'):
         continue
       while value.startswith('$'):
          indirect = self.__just_replace_variables(value)
          if indirect == value:
             break
          value = indirect
       self.__variable_bindings[name] = indirect

  def __rotate_yaml_bindings(self, key):
    value = self.__yaml_bindings[key]
    self.__yaml_bindings[key] = value[-1:] + value[:-1]

  def maybe_inject_primary_google_credentials(self):
    creds = self.__yaml_bindings.get('GOOGLE_CREDENTIALS_DECLARATION', [])
    primary_account = self.__variable_bindings.get(
        'GOOGLE_PRIMARY_ACCOUNT_NAME', '')
    primary_project = self.__variable_bindings.get(
        'GOOGLE_PRIMARY_MANAGED_PROJECT_ID', '')
    primary_json = self.__variable_bindings.get(
        'GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH', '')

    for cred in creds:
      if cred['name'] == primary_account:
        if (cred['project'] != primary_project
            or ('jsonPath' in cred and cred.get('jsonPath', '') != primary_json)
            or ('jsonPath' not in cred and cred.get('jsonPath', '')
                and primary_json)):
          raise ValueError('Primary google credentials differ from'
                           ' explicit @GOOGLE_CREDENTIALS')
        return

    self.__add_yaml_variable(
        'GOOGLE_CREDENTIALS', '{name}:{project}:{json}'.format(
            name=primary_account, project=primary_project, json=primary_json))
    self.__rotate_yaml_bindings('GOOGLE_CREDENTIALS_DECLARATION')
    self.__rotate_yaml_bindings('GOOGLE_CREDENTIALS_REFERENCE')

  def __just_replace_variables(self, content):
    result = content
    for name,value in self.__variable_bindings.items():
        result = re.sub(r'\${name}(\W|$)'.format(name=name),
                        r'{value}\1'.format(value=value),
                        result)
        result = re.sub(r'\${{{name}}}'.format(name=name), value, result)
        result = re.sub(r'\${{{name}:[^}}]*}}'.format(name=name), value, result)
    return result

  def __just_replace_yaml(self, content):
    for name,value in self.__yaml_bindings.items():
       result = []
       last_offset = 0
       for match in re.finditer(r'\${name}(\W|$)'.format(name=name), content):
          result.append(content[last_offset:match.start()])
          last_offset = match.end() - 1  # without word break
          text = yaml.dump(value, default_flow_style=False)
          result.append(
              self.__fix_yaml_formatting(text, content, match.start()))
       result.append(content[last_offset:])
       content = ''.join(result)

    return content

  @staticmethod
  def __fix_yaml_formatting(yaml, original, offset):
    begin_line = original.rfind('\n', 0, offset)
    # if not found, begin_line is -1
    # whether found or not, next char is start of line.
    segment = original[begin_line + 1:offset]

    # match the line indentation, however it was specified
    indent = re.match(r'^(\s+)', segment).group(1)
    if len(indent) != len(segment):
      # we're starting to replace on an existing line
      # so subsequent lines, if any, should have extra indentation.
      indent += '  '
    return yaml.replace('\n', '\n' + indent)


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
      bindings: Bindings of the templated variable bindings.
    """
    installation = self.__installation
    print 'Updating configurations in ' + installation.CONFIG_DIR

    bindings = bindings.clone()
    for cfg in glob.glob(installation.CONFIG_TEMPLATE_DIR + '/*.yml'):
      self.replace_variables_in_file(
          cfg,
          installation.CONFIG_DIR + '/' + os.path.basename(cfg),
          bindings)

    self.replace_variables_in_file(
        installation.CONFIG_TEMPLATE_DIR
            + '/' + installation.HACK_DECK_SETTINGS_FILENAME,
        installation.DECK_INSTALL_DIR + '/settings.js',
        bindings)
    return

  def load_bindings(self):
    """Load the specified bindings from master configuration file.

    Returns:
      Dictionary of all the variable bindings with defaults or overriden files.
    """
    installation = self.__installation

    # Get default values and ensure required things are defined
    # so user config can leave them out before including user config.
    bindings = Bindings()
    bindings.update_from_config(
        installation.CONFIG_TEMPLATE_DIR + '/default_spinnaker_config.cfg')

    custom_config_path = installation.CONFIG_DIR + '/spinnaker_config.cfg'
    if not os.path.exists(custom_config_path):
      sys.stderr.write(
          'WARNING: {path} does not exist.\n'
          '         There is no personal master configuration file.\n'
          .format(path=custom_config_path))
    else:
      bindings.update_from_config(custom_config_path)

    bindings.resolve_inner_variables()

    # Auto-define IGOR_ENABLED.
    # It is unfortunate that we need this, but seems to be used internally.
    # We dont start igor if we dont need it, so this is a safety mechanism.
    if bindings.get_variable('IGOR_ENABLED', '') == '':
      bindings.set_variable('IGOR_ENABLED',
          'true' if bindings.get_variable('JENKINS_ADDRESS', '') != ''
                 else 'false')

    self.__init_aws_bindings(bindings)
    self.__init_google_bindings(bindings)
    return bindings

  def __init_aws_bindings(self, bindings):
    # Auto-define AWS_ENABLED.
    if bindings.get_variable('AWS_ENABLED', '') == '':
      bindings.set_variable(
          'AWS_ENABLED',
          'true' if bindings.get_variable('AWS_ACCESS_KEY', '') != ''
                 else 'false')

  def __init_google_bindings(self, bindings):
    # Auto-define GOOGLE_ENABLED.
    if bindings.get_variable('GOOGLE_ENABLED', '') == '':
      bindings.set_variable(
          'GOOGLE_ENABLED',
          'true' if (is_google_instance()
              or bindings.get_variable('GOOGLE_PRIMARY_MANAGED_PROJECT_ID',
                                       '') != '')
              else 'false')

    if bindings.get_variable('GOOGLE_ENABLED').lower() == 'true':
      managed_project = bindings.get_variable(
        'GOOGLE_PRIMARY_MANAGED_PROJECT_ID', '')

      if not managed_project:
        project_id = get_google_project()
        if project_id is None:
            raise ValueError('GOOGLE_PRIMARY_MANAGED_PROJECT_ID is required'
                             ' since you have GOOGLE_ENABLED=true and are not'
                             ' running on Google Cloud Platform.')
        bindings.set_variable('GOOGLE_PRIMARY_MANAGED_PROJECT_ID', project_id)
      bindings.maybe_inject_primary_google_credentials()

    path = bindings.get_variable('GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH', '')
    normalized = normalize_path(path)
    if path != normalized:
      bindings.set_variable('GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH', normalized)

  @staticmethod
  def replace_variables_in_file(source_path, target_path, bindings):
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
      bindings: The Bindings to use for replacement.
    """
    with open(source_path, 'r') as f:
      content = f.read()
    content = bindings.replace_variables(content)
    with open(target_path, 'w') as f:
      f.write(content)
