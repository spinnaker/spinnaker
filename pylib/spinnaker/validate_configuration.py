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

import os
import re
import sys

from configurator import Configurator

from fetch import fetch
from fetch import is_google_instance
from fetch import GOOGLE_INSTANCE_METADATA_URL
from fetch import GOOGLE_METADATA_URL
from fetch import GOOGLE_OAUTH_URL


def _host_regex_token():
  ip6_group = '[a-fA-F0-9]{1,4}'
  ip6_full_host_token='(?:{group}:){{7}}{group}'.format(group=ip6_group)

  # This isn't strictly correct but allows for '::' abbreviations.
  # The spec says there can only be one use. This permits multiple and
  # other nonsensical use of ':'.
  ip6_abbrev_host_token='(?:{group}::?){{1,6}}:?'.format(group=ip6_group)
  ip6_host_token='(?:{full}|{abbrev})'.format(
      full=ip6_full_host_token, abbrev=ip6_abbrev_host_token)
    
  ip4_host_token = '(?:[0-9]{1,3}\.){3}[0-9]{1,3}'
  name_host_token = '(?:[a-z]|(?:[a-z][-\.a-z0-9]*[a-z0-9]))'
  host_token = '(?:{ip4})|(?:{ip6})|(?:{name})'.format(
      ip4=ip4_host_token, ip6=ip6_host_token, name=name_host_token)
  return host_token



class ValidateConfig(object):
  @property
  def errors(self):
     return self.__errors

  @property
  def warnings(self):
     return self.__warnings

  def __init__(self, configurator=None):
    if not configurator:
      configurator = Configurator()

    self.__bindings = configurator.bindings
    self.__user_config_dir = configurator.user_config_dir
    self.__installation_config_dir = configurator.installation_config_dir
    self.__warnings = []
    self.__errors = []

  def validate(self):
    """Validate the configuration.

    Returns:
      True or False after print result to stdout
    """

    # TODO: Add more verification here
    # This is representative for the time being.
    self.verify_at_least_one_provider_enabled()
    self.verify_google_scopes()
    self.verify_external_dependencies()
    self.verify_security()

    found_local = False   
    for ymldir in [self.__user_config_dir, self.__installation_config_dir]:
      yml_path = os.path.join(ymldir, 'spinnaker-local.yml')
      if not os.path.exists(yml_path):
        continue
      found_local = True

    if not found_local:
      self.__warnings.append(
          'There is no custom spinnaker-local.yml in either'
          ' "{user}" or "{install}"'.format(
               user=self.__user_config_dir,
               install=self.__installation_config_dir))

    if self.__warnings:
      print ('{path} has non-fatal configuration warnings:\n   * {warnings}'
             .format(path=yml_path, warnings='\n   * '.join(self.__warnings)))

    if not self.__errors:
      print '{path} seems ok.'.format(path=yml_path)
      return True
    else:
      print ('{path} has configuration errors:\n   * {errors}'
             .format(path=yml_path, errors='\n   * '.join(self.__errors)))
      return False

  def check_validate(self):
    """Validate the configuration.

    Raise a ValueError if the configuration is invalid.
    """
    ok = self.validate()
    if not ok:
      msg = 'Configuration seems invalid.\n  * {errors}'.format(
          errors='\n  * '.join(self.__errors))
      raise ValueError(msg)

  def is_reference(self, value):
    """Determine if a YAML value is an unresolved variable reference or not.

    Args:
      value [string]: value to check.
    """
    return isinstance(value, basestring) and value.startswith('${')

  def verify_true_false(self, name):
    """Verify name has a True or False value.

    Args:
      name [string]: variable name.
    """
    value = self.__bindings.get(name)
    if self.is_reference(value):
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False

    if isinstance(value, bool):
      return True

    self.__errors.append('{name}={value!r} is not valid.'
                         ' Must be boolean true or false.'
                         .format(name=name, value=value))
    return False

  def verify_baseUrl(self, name, required, scheme_optional=False):
    """Verify value of variable |name| is a valid url.
    Args:
      name [string]: variable name.
      required [bool]: If True value cannot be empty.
      scheme_optional: True if the URL protocol scheme is not required.
    """
    try:
      value = self.__bindings.get(name)
    except KeyError:
      if not required:
        return True
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False
      
    if self.is_reference(value):
      if not required:
        return True
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False

    # We don't really need a full URL since we're validating base urls,
    # (without query parameters and fragments), so the scheme will be optional.
    scheme_token = '[a-z0-9]+'
    host_token = _host_regex_token()
    port_token = '[1-9][0-9]*'
    path_token = '(?:[-\._+a-zA-Z0-9]|(?:%[0-9a-fA-F]{2}))+'
    url_re = re.compile('^'
                          '({scheme}://){scheme_optional}'
                          '({host})(:{port})?'
                          '((?:/{path})*/?)'
                        '$'
                        .format(
                          scheme=scheme_token,
                          scheme_optional = '?' if scheme_optional else '',
                          host=host_token,
                          port=port_token,
                          path=path_token
                        ))
    match = url_re.match(value)
    return match != None
       
  def verify_host(self, name, required):
    """Verify value of variable |name| is a valid hostname.

    Args:
      name [string]: variable name.
      required [bool]: If True, the value of variable |name| cannot be empty.
    """
    try:
      value = self.__bindings.get(name)
    except KeyError:
      if not required:
        return True
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False
      
    if self.is_reference(value):
      if not required:
        return True
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False

    host_token = _host_regex_token()
    host_regex = '^({host})$'.format(host=host_token)

    if not value:
      if not required:
        return True
      else:
        self.__errors.append(
            'No host provided for "{name}".'.format(name=name))
        return False

    if re.match(host_regex, value):
      return True

    self.__errors.append(
       'name="{value}" does not look like {regex}'.format(
         value=value, regex=host_regex))
    return False

  def verify_at_least_one_provider_enabled(self):
    providers = self.__bindings.get('providers')
    for name,attrs in providers.items():
      if attrs.get('enabled', False):
        return True
    self.__errors.append('None of the providers are enabled.')
    return False

  def verify_google_scopes(self):
    """Verify that if we are running on Google that our scopes are valid."""
    if not is_google_instance():
      return

    if not self.verify_true_false('providers.google.enabled'):
      return

    if not self.__bindings.get('providers.google.enabled'):
      return

    result = fetch(
        GOOGLE_INSTANCE_METADATA_URL + '/service-accounts/', google=True)
    service_accounts = result.content if result.ok() else ''

    required_scopes = [GOOGLE_OAUTH_URL + '/compute']
    found_scopes = []

    for account in filter(bool, service_accounts.split('\n')):
      if account[-1] == '/':
        # Strip off trailing '/' so we can take the basename.
        account = account[0:-1]

      result = fetch(
          os.path.join(GOOGLE_INSTANCE_METADATA_URL, 'service-accounts',
                       os.path.basename(account), 'scopes'),
          google=True)

      # cloud-platform scope implies all the other scopes.
      have = str(result.content)
      if have.find('https://www.googleapis.com/auth/cloud-platform') >= 0:
        found_scopes.extend(required_scopes)

      for scope in required_scopes:
        if have.find(scope) >= 0:
          found_scopes.append(scope)

    for scope in required_scopes:
      if not scope in found_scopes:
        self.__errors.append(
            'Missing required scope "{scope}".'.format(scope=scope))

  def verify_external_dependencies(self):
    """Verify that the external dependency references make sense."""
    ok = self.verify_host('services.cassandra.host', required=False)
    ok = self.verify_host('services.redis.host', required=False) and ok
    return ok

  def verify_user_access_only(self, path):
    """Verify only the user has permissions to operate on the supplied path.

    Args:
      path [string]: Path to local file.
    """
    if not path or not os.path.exists(path):
      return True
    stat = os.stat(path)
    if stat.st_mode & 077:
      self.__errors.append('"{path}" should not have non-owner access.'
                           ' Mode is {mode}.'
                           .format(path=path,
                                   mode='%03o' % (stat.st_mode & 0xfff)))
      return False
    return True

  def verify_security(self):
    """Verify the permissions on the sensitive configuration files."""
    ok = True
    for path in [
        self.__bindings.get('providers.google.primaryCredentials.jsonPath'),
        os.path.join(self.__user_config_dir, 'spinnaker-local.yml'),
        os.path.join(self.__installation_config_dir,'spinnaker-local.yml'),
        os.path.join(os.environ.get('HOME', '/root'),'.aws/credentials')]:
      ok = self.verify_user_access_only(path) and ok
    return ok

if __name__ == '__main__':
  sys.exit(0 if ValidateConfig().validate() else -1)
