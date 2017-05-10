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

"""This is the "deploy" module for the validate_bom script.

It is responsible for configuring spinnaker via Halyard.

The Configurator interface is used internally to implement the public
interface, which is provided via free functions.
  * Each configurable aspect has its own Configurator class.

  * The class has the following methods:
      init_argument_parser
         Adds the configuration parameters for that aspect to the argparser.

      validate_options
         Performs a quick validation of the options to fail fast.

      add_config
         Adds script commands used to configure this component [via hal].

      add_files_to_upload
         Adds paths to files referenced by config options that should
         be uploaded with the script that will be referencing them.

  * The configurator may add other implicit parameters.
        <service>_account_enabled is set if it is configured.
        The flag is used to make test filtering easier.
"""


import os


class StorageConfigurator(object):
  """Controls hal config storage for Spinnaekr Storage ."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--spinnaker_storage', required=True, choices=['gcs'],
        help='The storage type to configure.')

    parser.add_argument(
        '--gcs_storage_bucket', default=None,
        help=('URI for specific Google Storage bucket to use.'
              ' This is suggested if using gcs storage, though can be left'
              ' empty to let Halyard create one.'))
    parser.add_argument(
        '--gcs_storage_project', default=None,
        help=('URI for specific Google Storage bucket project to use.'
              ' If empty, use the --google_deploy_project.'))
    parser.add_argument(
        '--gcs_storage_credentials', default=None,
        help='Path to google credentials file to configure spinnaker storage.'
             ' This is only used if --spinnaker_storage=gcs.'
             ' If left empty then use application default credentials.')


  def validate_options(self, options):
    """Implements interface."""
    if options.spinnaker_storage not in ['gcs']:
      raise ValueError('Unknown --spinnaker_storage="{0}"'
                       .format(options.spinnaker_storage))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.gcs_storage_credentials:
      file_set.add(options.gcs_storage_credentials)

  def add_config(self, options, script):
    """Implements interface."""
    project = options.gcs_storage_project or options.google_deploy_project
    if options.spinnaker_storage == 'gcs':
      hal = (
          'hal --color=false config storage gcs edit'
          ' --project {project}'
          ' --bucket {bucket}'
          ' --bucket-location {location}'
          .format(project=project,
                  bucket=options.gcs_storage_bucket,
                  location='us'))
      if options.gcs_storage_credentials:
        hal += (' --json-path ./{filename}'
                .format(filename=os.path.basename(
                    options.gcs_storage_credentials)))
      script.append(hal)
      script.append('hal --color=false config storage edit --type gcs')
      return

    raise NotImplementedError(options.spinnaker_storage)


class AwsConfigurator(object):
  """Controls hal config provider aws."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    # pylint: disable=line-too-long
    parser.add_argument(
        '--aws_account_credentials', default=None,
        help='Path to AWS credentials file.')
    parser.add_argument(
        '--aws_account_name', default='my-aws-account',
        help='The name of the primary AWS account to configure.')
    parser.add_argument(
        '--aws_account_id', default=None,
        help='The AWS account id for the account.'
             ' See http://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html')
    parser.add_argument(
        '--aws_account_regions', default='us-east-1',
        help='The AWS account regions the account will manage.')

  def validate_options(self, options):
    """Implements interface."""
    options.aws_account_enabled = options.aws_account_credentials is not None


  def add_config(self, options, script):
    """Implements interface."""
    if not options.aws_account_credentials:
      return

    account_params = [options.aws_account_name]
    if options.aws_account_id:
      account_params.extend(['--account-id', options.aws_account_id])
    if options.aws_account_regions:
      account_params.extend(['--account-regions', options.aws_account_regions])

    cred_basename = os.path.basename(options.aws_account_credentials)
    script.append('hal --color=false config provider aws enable')
    script.append('hal --color=false config provider aws account add {params}'
                  .format(params=' '.join(account_params)))
    script.append('sudo chown spinnaker:spinnaker {file}'
                  .format(file=cred_basename))
    script.append('sudo mkdir -p ~spinnaker/.aws')
    script.append('sudo mv {file} ~spinnaker/.aws/credentials'
                  .format(file=cred_basename))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.aws_account_credentials:
      file_set.add(options.aws_account_credentials)


class GoogleConfigurator(object):
  """Controls hal config provider google."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--google_account_project',
        default=None,
        help='Google project to deploy to if --host_platform is gce.')
    parser.add_argument(
        '--google_account_credentials', default=None,
        help='Path to google credentials file for the google account.'
             'Adding credentials enables the account.')
    parser.add_argument(
        '--google_account_name', default='my-google-account',
        help='The name of the primary google account to configure.')


  def validate_options(self, options):
    """Implements interface."""
    options.google_account_enabled = (
        options.google_account_credentials is not None)
    if options.google_account_credentials:
      if not options.google_account_project:
        raise ValueError('--google_account_project was not specified.')

  def add_config(self, options, script):
    """Implements interface."""
    if not options.google_account_credentials:
      return

    if not options.google_account_project:
      raise ValueError(
          '--google_account_credentials without --google_account_project')

    account_params = [options.google_account_name]
    account_params.extend([
        '--project', options.google_account_project,
        '--json-path', os.path.basename(options.google_account_credentials)])

    script.append('hal --color=false config provider google enable')
    script.append(
        'hal --color=false config provider google account add {params}'
        .format(params=' '.join(account_params)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.google_account_credentials:
      file_set.add(options.google_account_credentials)


class KubernetesConfigurator(object):
  """Controls hal config provider kubernetes."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--k8s_account_credentials', default=None,
        help='Path to k8s credentials file.')
    parser.add_argument(
        '--k8s_account_name', default='my-kubernetes-account',
        help='The name of the primary Kubernetes account to configure.')
    parser.add_argument(
        '--docker_registry', default=None,
        help='The docker registry to use with the --k8s_credentials')

  def validate_options(self, options):
    """Implements interface."""
    options.k8s_account_enabled = options.k8s_account_credentials is not None
    if options.k8s_account_credentials:
      if not options.docker_registry:
        raise ValueError('--docker_registry was not specified.')

  def add_config(self, options, script):
    """Implements interface."""
    if not options.k8s_account_credentials:
      return
    if not options.docker_registry:
      raise ValueError('--k8s_account_credentials without --docker_registry')

    account_params = [options.k8s_account_name]
    account_params.extend([
        '--docker-registries', options.docker_registry,
        '--kubeconfig-file', os.path.basename(options.k8s_account_credentials)
    ])
    script.append('hal --color=false config provider kubernetes enable')
    script.append('hal --color=false config provider kubernetes account'
                  ' add {params}'
                  .format(params=' '.join(account_params)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.k8s_account_credentials:
      file_set.add(options.k8s_account_credentials)


class JenkinsConfigurator(object):
  """Controls hal config ci."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--jenkins_master_name', default=None,
        help='The name of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_address, '
        ' --jenkins_master_user, and an environment variable'
        ' JENKINS_MASTER_PASSWORD')
    parser.add_argument(
        '--jenkins_master_address', default=None,
        help='The network address of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_name, '
        ' --jenkins_master_user, and an environment variable'
        ' JENKINS_MASTER_PASSWORD')
    parser.add_argument(
        '--jenkins_master_user', default=None,
        help='The name of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_address, '
        ' --jenkins_master_name, and an environment variable'
        ' JENKINS_MASTER_PASSWORD')

  def validate_options(self, options):
    """Implements interface."""
    if ((options.jenkins_master_name is None)
        != (options.jenkins_master_address is None)
        or ((options.jenkins_master_name is None)
            != (options.jenkins_master_user is None))):
      raise ValueError('Inconsistent jenkins_master specification: '
                       ' --jenkins_master_name="{0}"'
                       ' --jenkins_master_address="{1}"'
                       ' --jenkins_master_user="{2}"'
                       .format(options.jenkins_master_name,
                               options.jenkins_master_address,
                               options.jenkins_master_user))
    if (options.jenkins_master_name
        and os.environ.get('JEKNINS_MASTER_PASSWORD') is None):
      raise ValueError('--jenkins_master_name was provided,'
                       ' but no JENKINS_MASTER_PASSWORD environment variable')
    options.jenkins_master_enabled = options.jenkins_master_name is not None

  def add_config(self, options, script):
    """Implements interface."""
    name = options.jenkins_master_name or None
    address = options.jenkins_master_address or None
    user = options.jenkins_master_user or None
    password = os.environ.get('JENKINS_MASTER_PASSWORD')
    if ((name is None) != (address is None)
        or (name is None) != (user is None)):
      raise ValueError('Either all of --jenkins_master_name,'
                       ' --jenkins_master_address, --jenkins_master_user'
                       ' or none of them must be supplied.')
    if name is None:
      return
    if password is None:
      raise ValueError(
          'No JENKINS_MASTER_PASSWORD environment variable was supplied.')
    script.append('hal --color=false config ci jenkins enable')
    script.append('hal --color=false config ci jenkins master'
                  ' add {name}'
                  ' --address {address}'
                  ' --username {user}'
                  ' < jenkins_{name}_password'
                  .format(name=options.jenkins_master_name,
                          address=options.jenkins_master_address,
                          user=options.jenkins_master_user))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    pass


class MonitoringConfigurator(object):
  """Controls hal config monitoring."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    pass

  def validate_options(self, options):
    """Implements interface."""
    pass

  def add_config(self, options, script):
    """Implements interface."""
    pass

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    pass


class NotificationConfigurator(object):
  """Controls hal config notification."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    pass

  def validate_options(self, options):
    """Implements interface."""
    pass

  def add_config(self, options, script):
    """Implements interface."""
    pass

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    pass


class SecurityConfigurator(object):
  """Controls hal config security."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    pass

  def validate_options(self, options):
    """Implements interface."""
    pass

  def add_config(self, options, script):
    """Implements interface."""
    pass

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    pass


CONFIGURATOR_LIST = [
    StorageConfigurator(),
    AwsConfigurator(),
    GoogleConfigurator(),
    KubernetesConfigurator(),
    JenkinsConfigurator(),
    MonitoringConfigurator(),
    NotificationConfigurator(),
    SecurityConfigurator(),
]


def init_argument_parser(parser):
  """Initialize the argument parser with configuration options.

  Args:
    parser: [ArgumentParser] The argument parser to add the options to.
  """
  for configurator in CONFIGURATOR_LIST:
    configurator.init_argument_parser(parser)


def validate_options(options):
  """Validate supplied options to ensure basic idea is ok.

  This doesnt perform a fine-grained check, just whether or not
  the arguments seem consistent or complete so we can fail fast.
  """
  for configurator in CONFIGURATOR_LIST:
    configurator.validate_options(options)


def make_script(options):
  """Creates the bash script for configuring Spinnaker.

  Returns a list of bash statement strings.
  """
  script = []
  for configurator in CONFIGURATOR_LIST:
    configurator.add_config(options, script)
  return script


def get_files_to_upload(options):
  """Collects the paths to files that the configuration script will reference.

  Returns:
     A set of path strings.
  """
  file_set = set([])
  for configurator in CONFIGURATOR_LIST:
    configurator.add_files_to_upload(options, file_set)
  return file_set
