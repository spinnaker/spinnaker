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

import configure_util

from install.install_utils import fetch
from install.google_install_loader import running_on_gce
from install.google_install_loader import INSTANCE_METADATA_URL
from install.google_install_loader import METADATA_URL

class ValidateConfig(object):
  def __init__(self, parameters=None):
    if not parameters:
        parameters = configure_util.InstallationParameters()
    self.__bindings = configure_util.ConfigureUtil(parameters).load_bindings()
    self.__errors = []
    self.__config_dir = parameters.CONFIG_DIR

  def validate(self):
    config_path = os.path.join(self.__config_dir, 'spinnaker_config.cfg')
    self.verify_gce_scopes()
    self.verify_gce_provider()
    self.verify_aws_provider()
    self.verify_docker()
    self.verify_jenkins()
    self.verify_external_dependencies()
    self.verify_security()

    if not self.__errors:
      print '{path} seems ok.'.format(path=config_path)
      return True
    else:
      print '{path} seems to have configuration errors:\n    {errors}'.format(
        path=config_path, errors = '\n    '.join(self.__errors))
      return False

  def verify_true_false(self, name):
    value = self.__bindings.get_variable(name, None)
    if value is None:
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False

    if value.lower() in ['true', 'false']:
      return True

    self._errors.append('{name}="{value}" is not valid.'
                        ' Must be "true" or "false".'
                        .format(name=name, value=value))
    return False

  def verify_host(self, name, required):
    value = self.__bindings.get_variable(name, None)
    if value is None:
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False

    host_regex = '^[-_\.a-z0-9]+$'
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
       'name="{value}" does not look like {regex}'.format(regex=host_regex))
    return False


  def verify_host_port(self, name, required):
    value = self.__bindings.get_variable(name, None)
    if value is None:
      self.__errors.append('Missing "{name}".'.format(name=name))
      return False

    address_regex = '^[-_\.a-z0-9]+(:[0-9]+)?(/[-_a-zA-Z0-9\+%/]+)?$'
    if not value:
      if not required:
        return True
      else:
        self.__errors.append(
            'No address provided for "{name}".'.format(name=name))
        return False

    if re.match(address_regex, value):
      return True

    self.__errors.append(
       'name="{value}" is not in <host>[:<port>][/path] form.'
       .format(name=name, value=value))
    return False

  def verify_gce_scopes(self):
    if not running_on_gce():
      return

    auth_url_path = 'https://www.googleapis.com/auth'
    code, service_accounts = fetch(INSTANCE_METADATA_URL + '/service-accounts/',
                           google=True)
    if code != 200:
      service_accounts = ''

    required_scopes = [auth_url_path + '/compute']
    found_scopes = []

    for account in filter(bool, service_accounts.split('\n')):
      if account[-1] == '/':
        # Strip off trailing '/' so we can take the basename.
        account = account[0:-1]

      code, have = fetch(
        os.path.join(INSTANCE_METADATA_URL, 'service-accounts',
                     os.path.basename(account), 'scopes'),
        google=True)

      # cloud-platform scope implies all the other scopes.
      if have.find('https://www.googleapis.com/auth/cloud-platform') >= 0:
        found_scopes.extend(required_scopes)

      for scope in required_scopes:
        if have.find(scope) >= 0:
          found_scopes.append(scope)

    for scope in required_scopes:
      if not scope in found_scopes:
        self.__errors.append(
            'Missing required scope "{scope}".'.format(scope=scope))

  def verify_aws_provider(self):
    if not self.verify_true_false('AWS_ENABLED'):
      return False

    # TODO(ewiseblatt): 20150518
    # Need to verify this. I cannot find a source.
    # It looks like secret keys can have slashes but access keys cannot.
    # Without a source I'm being overly generous.
    aws_key_regex = '^[+/a-zA-Z0-9]+$'

    if self.__bindings.get_variable('AWS_ENABLED', '').lower() != 'true':
      return True

    # Intentionally keeping these values private in the errors.
    ok = True
    if not re.match(
          aws_key_regex, self.__bindings.get_variable('AWS_ACCESS_KEY', '')):
      self.__errors.append('AWS_ACCESS_KEY does not look like {regex}.'
                           .format(regex=aws_key_regex))
      ok = False
    if not re.match(
          aws_key_regex, self.__bindings.get_variable('AWS_SECRET_KEY', '')):
      self.__errors.append('AWS_SECRET_KEY does not look like {regex}.'
                           .format(regex=aws_key_regex))
      ok = False

    return ok

  def verify_gce_provider(self):
    code, project_id = fetch(METADATA_URL + '/project/project-id',
                             google=True)
    if code != 200:
      project_id = None

    # https://cloud.google.com/compute/docs/reference/latest/instances
    # The * here could be further restricted to {0,61} because length
    # is bounded.
    # NOTE(ewiseblatt) 20150518:
    # We might want to restrict this further because internal name decoration
    # (e.g. adding a health check) for created components will push beyond GCE
    # limits.
    gce_name_regex = '^[a-z]([-a-z0-9]{0,61}[a-z0-9])?$'
    gce_project_with_domain_regex = ('^[a-z]+\.[a-z]+'
                                     ':[a-z]([-a-z0-9]{0,61}[a-z0-9])?$')
    managed_project_id = self.__bindings.get_variable(
        'GOOGLE_PRIMARY_MANAGED_PROJECT_ID', '')
    ok = True
    if not managed_project_id:
      if not project_id:
        self.__errors.append(
            'GOOGLE_PRIMARY_MANAGED_PROJECT_ID is not set, and it does not appear'
            ' that you are running in a Google Cloud Platform project to'
            ' to use as the default.')
        ok = False
    else:
      if (not re.match(gce_name_regex, managed_project_id)
          and not re.match(gce_project_with_domain_regex, managed_project_id)):
        ok = False
        self.__errors.append(
            'GOOGLE_PRIMARY_MANAGED_PROJECT_ID="{id}" does not look like {regex}.'
            .format(id=managed_project_id, regex=gce_name_regex))
      if managed_project_id != project_id:
        path = self.__bindings.get_variable('GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH',
                                            '')
        if not path:
          ok = False
          self.__errors.append(
              'GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH is required because'
              ' GOOGLE_PRIMARY_MANAGED_PROJECT_ID="{mid}"'
              ' is not this project "{pid}".'
              .format(mid=managed_project_id, pid=project_id))
        elif not os.path.exists(path):
          ok = False
          self.__errors.append(
              'GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH="{path}" does not exist.'
              .format(path=path))

    # Verify account name.
    account_name = self.__bindings.get_variable('GOOGLE_PRIMARY_ACCOUNT_NAME', '')
    account_name_regex = '^[-_a-zA-Z0-9]+$'

    if not re.match(account_name_regex, account_name):
      ok = False
      self.__errors.append(
          'GOOGLE_PRIMARY_ACCOUNT_NAME="{value}" does not look like {regex}.'
          .format(value=account_name, regex=account_name_regex))
    return ok

  def verify_docker(self):
    ok = self.verify_host_port('DOCKER_ADDRESS', required=False)
    ok = (self.verify_host_port('DOCKER_TARGET_REPOSITORY', required=False)
          and ok)

    if self.verify_true_false('DOCKER_ENABLED'):
      if (self.__bindings.get_variable('DOCKER_ENABLED', '').lower() == 'true'
          and not self.__bindings.get_variable('DOCKER_TARGET_REPOSITORY', '')):
        ok = False
        self.__errors.append('DOCKER_ENABED but DOCKER_TARGET_REPOSITORY'
                             ' is not provided.')
    return ok

  def verify_jenkins(self):
    ok = self.verify_host_port('JENKINS_ADDRESS', required=False)
    if self.__bindings.get_variable('JENKINS_ADDRESS', ''):
        # TODO(ewiseblatt): 20140925
        # Look into the IGOR_ENABLED flag (used at least in gate)
        igor_enabled = self.__bindings.get_variable('IGOR_ENABLED', '').lower()
        if igor_enabled == 'false':
          # Dont bother checking other things since they dont matter.
          print ('WARNING: JENKINS_ADDRESS is provided but'
                 ' IGOR_ENABLED is false.'
                 ' Therefore igor will not be available.')

        if igor_enabled == '':
          ok = False
          self.__errors.append('JENKINS_ADDRESS is provided but IGOR_ENABLED'
                               ' is not explicitly set to true or false.')

        if not self.__bindings.get_variable('JENKINS_USERNAME', ''):
            ok = False
            self.__errors.append('JENKINS_ADDRESS is provided,'
                                 ' but not JENKINS_USERNAME.')

        if not self.__bindings.get_variable('JENKINS_PASSWORD', ''):
            # igor will throw an exception if it does not have a password.
            ok = False
            self.__errors.append('JENKINS_ADDRESS is provided,'
                                 ' but not JENKINS_PASSWORD.')


  def verify_external_dependencies(self):
    ok = self.verify_host('CASSANDRA_HOST', required=False)
    ok = self.verify_host('REDIS_HOST', required=False) and ok
    return ok

  def verify_user_access_only(self, path):
    if not os.path.exists(path):
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
    ok = self.verify_user_access_only(
      self.__bindings.get_variable('GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH', ''))
    ok = self.verify_user_access_only(
        os.path.join(self.__config_dir, 'spinnaker_config.cfg')) and ok
    return ok

if __name__ == '__main__':
  if os.geteuid():
    sys.stderr.write('You must run this as root.\n')
    sys.exit(-1)

  sys.exit(0 if ValidateConfig().validate() else -1)
