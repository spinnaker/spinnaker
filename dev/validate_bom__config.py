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

from validate_bom__deploy import write_data_to_secure_path


class AzsStorageConfiguratorHelper(object):
  """Helper class for StorageConfigurator to handle AZS."""

  @classmethod
  def init_argument_parser(cls, parser):
    """Implements interface."""
    parser.add_argument(
        '--storage_azs_account_name', default=None,
        help='The name for the Azure Storage Account to use.'
             ' This is only used if --spinnaker_storage=azs.')
    parser.add_argument(
        '--storage_azs_credentials', default=None,
        help='Path to Azure Storage Account credentials to configure'
             'spinnaker storage. This is only used if --spinnaker_storage=azs.')

  @classmethod
  def validate_options(cls, options):
    """Implements interface."""
    if not options.storage_azs_credentials:
      raise ValueError('Specified --spinnaker_storage="azs"'
                       ' but not --storage_azs_credentials')

  @classmethod
  def add_files_to_upload(cls, options, file_set):
    """Implements interface."""
    file_set.add(options.storage_azs_credentials)

  @classmethod
  def add_config(cls, options, script):
    """Implements interface."""
    script.append(
        'AZS_PASSWORD=$(cat {file})'
        .format(file=os.path.basename(options.storage_azs_credentials)))
    hal = (
        'hal -q --log=info config storage azs edit'
        ' --storage-account-name {name}'
        ' --storage-account-key "$AZS_PASSWORD"'
        .format(name=options.storage_azs_account_name))
    script.append(hal)


class S3StorageConfiguratorHelper(object):
  """Helper class for StorageConfigurator to handle S3."""

  REGIONS = ['us-east-2', 'us-east-1', 'us-west-1', 'us-west-2',
             'ca-central-1',
             'ap-south-1', 'ap-northeast-2', 'ap-southeast-1',
             'ap-southeast-2', 'ap-northeast-1',
             'eu-central-1', 'eu-west-1', 'eu-west-2', 'sa-east-1']

  @classmethod
  def init_argument_parser(cls, parser):
    """Implements interface."""
    parser.add_argument(
        '--storage_s3_bucket', default=None,
        help='The name for the AWS S3 bucket to use.'
             ' This is only used if --spinnaker_storage=s3.')
    parser.add_argument(
        '--storage_s3_assume_role', default='role/spinnakerManaged',
        help='Use AWS SecurityToken Service to assume this role.')

    parser.add_argument(
        '--storage_s3_region', choices=cls.REGIONS,
        help='The name for the AWS region to create the bucket in.'
             ' This is only used if the bucket does not already exist.')

    parser.add_argument(
        '--storage_s3_endpoint', help='The s3 endpoint.')

    parser.add_argument(
        '--storage_s3_access_key_id', default=None,
        help='AWS Access Key ID for AWS account owning s3 storage.')
    parser.add_argument(
        '--storage_s3_credentials', default=None,
        help='Path to file containing the secret access key for the S3 account')

  @classmethod
  def validate_options(cls, options):
    """Implements interface."""
    if not options.storage_s3_credentials:
      raise ValueError('--storage_s3_credentials is required.')

    if not options.storage_s3_access_key_id:
      raise ValueError('--storage_s3_access_key_id is required.')

    if not options.storage_s3_region:
      raise ValueError('--storage_s3_region is required.')

  @classmethod
  def add_files_to_upload(cls, options, file_set):
    """Implements interface."""
    if options.storage_s3_credentials:
      file_set.add(options.storage_s3_credentials)

  @classmethod
  def add_config(cls, options, script):
    """Implements interface."""
    command = ['hal -q --log=info config storage s3 edit']
    if options.storage_s3_access_key_id:
      command.extend(['--access-key-id', options.storage_s3_access_key_id])
    if options.storage_s3_bucket:
      command.extend(['--bucket', options.storage_s3_bucket])
    if options.storage_s3_assume_role:
      command.extend(['--assume-role', options.storage_s3_assume_role])
    if options.storage_s3_region:
      command.extend(['--region', options.storage_s3_region])
    if options.storage_s3_endpoint:
      command.extend(['--endpoint', options.storage_s3_endpoint])
    if options.storage_s3_credentials:
      command.extend(['--secret-access-key < {file}'.format(
          file=os.path.basename(options.storage_s3_credentials))])

    script.append(' '.join(command))


class GcsStorageConfiguratorHelper(object):
  """Helper class for StorageConfigurator to handle GCS."""

  LOCATIONS = [
      # multi-regional bucket
      'us', 'eu', 'ap',

      # regional bucket
      'us-central1', 'us-east1', 'us-west1', 'us-east4',
      'europe-west1',
      'asia-east1', 'asia-northeast1', 'asia-southeast1'
  ]

  @classmethod
  def init_argument_parser(cls, parser):
    """Implements interface."""
    parser.add_argument(
        '--storage_gcs_bucket', default=None,
        help=('URI for specific Google Storage bucket to use.'
              ' This is suggested if using gcs storage, though can be left'
              ' empty to let Halyard create one.'))
    parser.add_argument(
        '--storage_gcs_location', choices=cls.LOCATIONS, default='us-central1',
        help=('Location for the bucket if it needs to be created.'))
    parser.add_argument(
        '--storage_gcs_project', default=None,
        help=('URI for specific Google Storage bucket project to use.'
              ' If empty, use the --deploy_google_project.'))
    parser.add_argument(
        '--storage_gcs_credentials', default=None,
        help='Path to google credentials file to configure spinnaker storage.'
             ' This is only used if --spinnaker_storage=gcs.'
             ' If left empty then use application default credentials.')

  @classmethod
  def validate_options(cls, options):
    """Implements interface."""
    if not options.storage_gcs_bucket:
      raise ValueError('Specified --spinnaker_storage="gcs"'
                       ' but not --storage_gcs_bucket')

  @classmethod
  def add_files_to_upload(cls, options, file_set):
    """Implements interface."""
    if options.storage_gcs_credentials:
      file_set.add(options.storage_gcs_credentials)

  @classmethod
  def add_config(cls, options, script):
    """Implements interface."""
    project = options.storage_gcs_project or options.deploy_google_project
    hal = (
        'hal -q --log=info config storage gcs edit'
        ' --project {project}'
        ' --bucket {bucket}'
        ' --bucket-location {location}'
        .format(project=project,
                bucket=options.storage_gcs_bucket,
                location=options.storage_gcs_location))
    if options.storage_gcs_credentials:
      hal += (' --json-path ./{filename}'
              .format(filename=os.path.basename(
                  options.storage_gcs_credentials)))
    script.append(hal)


class StorageConfigurator(object):
  """Controls hal config storage for Spinnaker Storage ."""

  HELPERS = {
      'azs': AzsStorageConfiguratorHelper,
      'gcs': GcsStorageConfiguratorHelper,
      's3': S3StorageConfiguratorHelper
  }

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--spinnaker_storage', required=True, choices=self.HELPERS.keys(),
        help='The storage type to configure.')
    for helper in self.HELPERS.values():
      helper.init_argument_parser(parser)

  def validate_options(self, options):
    """Implements interface."""
    helper = self.HELPERS.get(options.spinnaker_storage, None)
    if helper is None:
      raise ValueError('Unknown --spinnaker_storage="{0}"'
                       .format(options.spinnaker_storage))
    helper.validate_options(options)

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    helper = self.HELPERS.get(options.spinnaker_storage, None)
    if helper is None:
      raise ValueError('Unknown --spinnaker_storage="{0}"'
                       .format(options.spinnaker_storage))
    helper.add_files_to_upload(options, file_set)

  def add_config(self, options, script):
    """Implements interface."""
    helper = self.HELPERS.get(options.spinnaker_storage, None)
    if helper is None:
      raise ValueError('Unknown --spinnaker_storage="{0}"'
                       .format(options.spinnaker_storage))
    helper.add_config(options, script)
    script.append('hal -q --log=info config storage edit --type {type}'
                  .format(type=options.spinnaker_storage))


class AwsConfigurator(object):
  """Controls hal config provider aws."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    # pylint: disable=line-too-long
    parser.add_argument(
        '--aws_access_key_id', default=None,
        help='The AWS ACCESS_KEY_ID.')
    parser.add_argument(
        '--aws_credentials', default=None,
        help='A path to a file containing the AWS SECRET_ACCESS_KEY')

    parser.add_argument(
        '--aws_account_name', default='my-aws-account',
        help='The name of the primary AWS account to configure.')
    parser.add_argument(
        '--aws_account_id', default=None,
        help='The AWS account id for the account.'
             ' See http://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html')
    parser.add_argument(
        '--aws_account_role', default='role/spinnakerManaged',
        help=' The account will assume this role.')

    parser.add_argument(
        '--aws_account_regions', default='us-east-1,us-west-2',
        help='The AWS account regions the account will manage.')
    parser.add_argument(
        '--aws_account_pem_path', default=None,
        help='The path to the PEM file for the keypair to use.'
             'The basename minus suffix will be the name of the keypair.')

  def validate_options(self, options):
    """Implements interface."""
    options.aws_account_enabled = options.aws_access_key_id is not None

    if options.aws_account_enabled and not options.aws_credentials:
      raise ValueError(
          '--aws_access_key_id given, but not --aws_credentials')

    if options.aws_account_enabled and not options.aws_account_id:
      raise ValueError(
          '--aws_access_key_id given, but not --aws_account_id')

    if options.aws_account_enabled and not options.aws_account_role:
      raise ValueError(
          '--aws_access_key_id given, but not --aws_account_role')

  def add_config(self, options, script):
    """Implements interface."""
    if not options.aws_access_key_id:
      return

    account_params = [options.aws_account_name,
                      '--assume-role', options.aws_account_role,
                      '--account-id', options.aws_account_id]

    if options.aws_account_pem_path:
      basename = os.path.basename(options.aws_account_pem_path)
      script.append('mv {file} .ssh/'.format(file=basename))
      account_params.extend(
          ['--default-key-pair', os.path.splitext(basename)[0]])
    if options.aws_account_regions:
      account_params.extend(['--regions', options.aws_account_regions])

    script.append('hal -q --log=info config provider aws enable')
    script.append(
        'hal -q --log=info config provider aws edit '
        ' --access-key-id {id} --secret-access-key < {file}'
        .format(id=options.aws_access_key_id,
                file=os.path.basename(options.aws_credentials)))
    script.append(
        'hal -q --log=info config provider aws account add {params}'
        .format(params=' '.join(account_params)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.aws_credentials:
      file_set.add(options.aws_credentials)
    if options.aws_account_pem_path:
      file_set.add(options.aws_account_pem_path)


class AppengineConfigurator(object):
  """Controls hal config provider for appengine"""

  def init_argument_parser(self, parser):
    """Implements interface."""
    # pylint: disable=line-too-long
    parser.add_argument(
        '--appengine_account_project', default=None,
        help='The Google Cloud Platform project this Spinnaker account will manage.')

    parser.add_argument(
        '--appengine_account_name', default='my-appengine-account',
        help='The name of the primary Appengine account to configure.')
    parser.add_argument(
        '--appengine_account_credentials', default=None,
        help='Path to file containing the JSON Oauth credentials for the AppEngine account.')

    parser.add_argument(
        '--appengine_account_git_username', default=None,
        help='The name of the remote git user.')
    parser.add_argument(
        '--appengine_account_git_https_credentials', default=None,
        help='Path to file containing the password for the remote git repository.')
    parser.add_argument(
        '--appengine_account_git_oauth_credentials', default=None,
        help='Path to file containing the password for the remote git repository.')

    parser.add_argument(
        '--appengine_account_ssh_private_key_path', default=None)
    parser.add_argument(
        '--appengine_account_ssh_private_key_passphrase', default=None)

    parser.add_argument(
        '--appengine_account_local_repository_directory', default=None)

  def validate_options(self, options):
    """Implements interface."""

    options.appengine_account_enabled = (options.appengine_account_project
                                         is not None)
    if not options.appengine_account_enabled:
      return

  def add_config(self, options, script):
    """Implements interface."""
    if not options.appengine_account_project:
      return

    script.append('hal -q --log=info config provider appengine enable')
    account_params = [
        options.appengine_account_name,
        '--project', options.appengine_account_project
    ]
    if options.appengine_account_credentials:
      account_params.extend(
          ['--json-path',
           os.path.basename(options.appengine_account_credentials)])
    if options.appengine_account_local_repository_directory:
      account_params.extend(
          ['--local-repository-directory',
           options.appengine_account_local_repository_directory])
    script.append(
        'hal -q --log=info config provider appengine account add {params}'
        .format(params=' '.join(account_params)))

    hal_edit = ('hal -q --log=info'
                ' config provider appengine account edit {name}'
                .format(name=options.appengine_account_name))

    # Maybe config github
    if options.appengine_account_git_username:
      git_params = ['--git-https-username',
                    options.appengine_account_git_username]
      if options.appengine_account_git_oauth_credentials:
        git_params.append(
            '--github-oauth-access-token < {file}'
            .format(
                file=os.path.basename(
                    options.appengine_account_git_oauth_credentials)))
      elif options.appengine_account_git_https_credentials:
        git_params.append(
            '--git-https-password < {path}'
            .format(
                path=os.path.basename(
                    options.appengine_account_git_https_credentials)))
      script.append(
          '{hal} {params}'.format(hal=hal_edit, params=' '.join(git_params)))

    # Maybe config ssh
    if options.appengine_account_ssh_private_key_path:
      ssh_params = ['--ssh-private-key-file-path',
                    options.appengine_account_local_repository_directory]
      if options.appengine_account_ssh_private_key_passphrase:
        ssh_params.append('--ssh-private-key-passphrase < {path}'.format(
            path=os.path.basename(
                options.appengine_account_ssh_private_key_passphrase)))
      script.append(
          '{hal} {params}'.format(hal=hal_edit, params=' '.join(ssh_params)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.appengine_account_credentials:
      file_set.add(options.appengine_account_credentials)
    if options.appengine_account_git_https_credentials:
      file_set.add(options.appengine_account_git_https_credentials)
    if options.appengine_account_git_oauth_credentials:
      file_set.add(options.appengine_account_git_oauth_credentials)
    if options.appengine_account_ssh_private_key_passphrase:
      file_set.add(options.appengine_account_ssh_private_key_passphrase)


class AzureConfigurator(object):
  """Controls hal config provider azure."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    # pylint: disable=line-too-long
    parser.add_argument(
        '--azure_account_credentials', default=None,
        help='Path to Azure credentials file containing the appKey'
             ' for the service principal.')
    parser.add_argument(
        '--azure_account_name', default='my-azure-account',
        help='The name of the primary Azure account to configure.')
    parser.add_argument(
        '--azure_account_client_id', default=None,
        help='The Azure clientId for the service principal.')
    parser.add_argument(
        '--azure_account_subscription_id', default=None,
        help='The subscriptionId for the service principal.')
    parser.add_argument(
        '--azure_account_tenant_id', default=None,
        help='The tenantId for the service principal.')
    parser.add_argument(
        '--azure_account_object_id', default=None,
        help='The objectId of the service principal.'
             ' Needed to bake Windows images.')

    parser.add_argument(
        '--azure_account_default_key_vault', default=None,
        help='The name of the KeyValue containing the default user/password'
             ' to create VMs.')
    parser.add_argument(
        '--azure_account_default_resource_group', default=None,
        help='The default for non-application specific resources.')
    parser.add_argument(
        '--azure_account_packer_resource_group', default=None,
        help='Used by packer when baking images.')
    parser.add_argument(
        '--azure_account_packer_storage_account', default=None,
        help='The storage account ot use if baking images with packer.')


  def validate_options(self, options):
    """Implements interface."""
    options.azure_account_enabled = (options.azure_account_subscription_id
                                     is not None)
    if not options.azure_account_enabled:
      return

    if ((options.azure_account_packer_resource_group != None)
        != (options.azure_account_packer_storage_account != None)):
      raise ValueError(
          '--azure_account_packer_resource_group'
          ' and --azure_account_packer_storage_account'
          ' must either both be set or neither be set.')

    for name in ['client_id', 'credentials', 'subscription_id', 'tenant_id',
                 'default_key_vault', 'default_resource_group']:
      key = 'azure_account_' + name
      if not getattr(options, key):
        raise ValueError(
            '--{0} is required with --azure_account_subscription_id.'
            .format(key))

  def add_config(self, options, script):
    """Implements interface."""
    if not options.azure_account_credentials:
      return

    account_params = [
        options.azure_account_name,
        '--client-id', options.azure_account_client_id,
        '--default-key-vault', options.azure_account_default_key_vault,
        '--default-resource-group',
        options.azure_account_default_resource_group,
        '--subscription-id', options.azure_account_subscription_id,
        '--tenant-id', options.azure_account_tenant_id
    ]
    if options.azure_account_object_id:
      account_params.extend(['--object-id', options.azure_account_object_id])
    if options.azure_account_packer_resource_group:
      account_params.extend(['--packer-resource-group',
                             options.azure_account_packer_resource_group])
    if options.azure_account_packer_storage_account:
      account_params.extend(['--packer-storage-account',
                             options.azure_account_packer_storage_account])

    script.append('hal -q --log=info config provider azure enable')
    script.append(
        'hal -q --log=info config provider azure account add {params}'
        ' --app-key < {creds}'
        .format(params=' '.join(account_params),
                creds=os.path.basename(options.azure_account_credentials)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.azure_account_credentials:
      file_set.add(options.azure_account_credentials)


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

    script.append('hal -q --log=info config provider google enable')
    script.append(
        'hal -q --log=info config provider google account add {params}'
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
        '--k8s_account_context',
        help='The kubernetes context for the primary Kubernetes account.')
    parser.add_argument(
        '--k8s_account_namespaces', default='validate-bom',
        help='The kubernetes namespaces for the primary Kubernetes account.')
    parser.add_argument(
        '--k8s_account_docker_account', default=None,
        help='The docker registry account to use with the --k8s_account')

  def validate_options(self, options):
    """Implements interface."""
    options.k8s_account_enabled = options.k8s_account_credentials is not None
    if options.k8s_account_credentials:
      if not options.k8s_account_docker_account:
        raise ValueError('--k8s_account_docker_account was not specified.')

  def add_config(self, options, script):
    """Implements interface."""
    if not options.k8s_account_credentials:
      return
    if not options.k8s_account_docker_account:
      raise ValueError(
          '--k8s_account_credentials without --k8s_account_docker_account')

    account_params = [options.k8s_account_name]
    account_params.extend([
        '--docker-registries', options.k8s_account_docker_account,
        '--kubeconfig-file', os.path.basename(options.k8s_account_credentials)
    ])
    if options.k8s_account_context:
      account_params.extend(['--context', options.k8s_account_context])
    if options.k8s_account_namespaces:
      account_params.extend(['--namespaces', options.k8s_account_namespaces])

    script.append('hal -q --log=info config provider kubernetes enable')
    script.append('hal -q --log=info config provider kubernetes account'
                  ' add {params}'
                  .format(params=' '.join(account_params)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.k8s_account_credentials:
      file_set.add(options.k8s_account_credentials)


class DockerConfigurator(object):
  """Controls hal config provider docker."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--docker_account_address', default=None,
        help='Registry address to pull and deploy images from.')
    parser.add_argument(
        '--docker_account_name', default='my-docker-account',
        help='The name of the primary Docker account to configure.')
    parser.add_argument(
        '--docker_account_registry_username', default=None,
        help='The username for the docker registry.')
    parser.add_argument(
        '--docker_account_credentials', default=None,
        help='Path to plain-text password file.')
    parser.add_argument(
        '--docker_account_repositories', default=None,
        help='Additional list of repositories to cache images from.')

  def validate_options(self, options):
    """Implements interface."""
    options.docker_account_enabled = options.docker_account_address is not None

  def add_config(self, options, script):
    """Implements interface."""
    if not options.docker_account_address:
      return

    account_params = [options.docker_account_name,
                      '--address', options.docker_account_address]
    if options.docker_account_credentials:
      cred_basename = os.path.basename(options.docker_account_credentials)
      account_params.extend(
          ['--password-file', cred_basename])
    if options.docker_account_registry_username:
      account_params.extend(
          ['--username', options.docker_account_registry_username])
    if options.docker_account_repositories:
      account_params.extend(
          ['--repositories', options.docker_account_repositories])

    script.append('hal -q --log=info config provider docker-registry enable')
    script.append('hal -q --log=info config provider docker-registry account'
                  ' add {params}'
                  .format(params=' '.join(account_params)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.docker_account_credentials:
      file_set.add(options.docker_account_credentials)


class JenkinsConfigurator(object):
  """Controls hal config ci."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--jenkins_master_name', default=None,
        help='The name of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_address, '
        ' --jenkins_master_user, and --jenkins_master_credentials'
        ' or an environment variable JENKINS_MASTER_PASSWORD')
    parser.add_argument(
        '--jenkins_master_address', default=None,
        help='The network address of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_name, '
        ' --jenkins_master_user, and --jenkins_master_credentials'
        ' or an environment variable JENKINS_MASTER_PASSWORD')
    parser.add_argument(
        '--jenkins_master_user', default=None,
        help='The name of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_address, '
        ' --jenkins_master_name, and --jenkins_master_credentials'
        ' or an environment variable JENKINS_MASTER_PASSWORD')
    parser.add_argument(
        '--jenkins_master_credentials', default=None,
        help='The password for the jenkins master to configure.'
             ' If provided, this takes pre cedence over'
             ' any JENKINS_MASTER_PASSWORD environment variable value.')

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
        and os.environ.get('JENKINS_MASTER_PASSWORD') is None):
      raise ValueError('--jenkins_master_name was provided,'
                       ' but no JENKINS_MASTER_PASSWORD environment variable')
    options.jenkins_master_enabled = options.jenkins_master_name is not None

  def add_config(self, options, script):
    """Implements interface."""
    name = options.jenkins_master_name or None
    address = options.jenkins_master_address or None
    user = options.jenkins_master_user or None
    if options.jenkins_master_credentials:
      password_file = os.path.basename(options.jenkins_master_credentials)
    elif os.environ.get('JENKINS_MASTER_PASSWORD', None):
      password_file = 'jenkins_{name}_password'.format(
          name=options.jenkins_master_name)
    else:
      password_file = None

    if ((name is None) != (address is None)
        or (name is None) != (user is None)):
      raise ValueError('Either all of --jenkins_master_name,'
                       ' --jenkins_master_address, --jenkins_master_user'
                       ' or none of them must be supplied.')
    if name is None:
      return
    if password_file is None:
      raise ValueError(
          'No --jenkins_master_credentials or JENKINS_MASTER_PASSWORD'
          ' environment variable was supplied.')
    script.append('hal -q --log=info config ci jenkins enable')
    script.append('hal -q --log=info config ci jenkins master'
                  ' add {name}'
                  ' --address {address}'
                  ' --username {user}'
                  ' --password < {password_file}'
                  .format(name=options.jenkins_master_name,
                          address=options.jenkins_master_address,
                          user=options.jenkins_master_user,
                          password_file=os.path.basename(password_file)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.jenkins_master_credentials:
      file_set.add(options.jenkins_master_credentials)
    elif os.environ.get('JENKINS_MASTER_PASSWORD', None):
      path = write_data_to_secure_path(
          os.environ.get('JENKINS_MASTER_PASSWORD'),
          'jenkins_{0}_password'.format(options.jenkins_master_name))
      file_set.add(path)


class MonitoringConfigurator(object):
  """Controls hal config monitoring."""

  def init_argument_parser(self, parser):
    """Implements interface."""
    parser.add_argument(
        '--monitoring_prometheus_gateway', default=None,
        help='If provided, and which is "prometheus",'
             ' configure to use the gateway server at thsi URL.')
    parser.add_argument(
        '--monitoring_install_which', default=None,
        help='If provided, install monitoring with these params.')

  def validate_options(self, options):
    """Implements interface."""
    if (options.monitoring_prometheus_gateway
        and (options.monitoring_install_which != 'prometheus')):
      raise ValueError('gateway is only applicable to '
                       ' --monitoring_install_which="prometheus"')

  def add_config(self, options, script):
    """Implements interface."""
    if not options.monitoring_install_which:
      return

    script.append('hal -q --log=info config metric-stores {which} enable'
                  .format(which=options.monitoring_install_which))
    if options.monitoring_prometheus_gateway:
      script.append('hal -q --log=info config metric-stores prometheus edit'
                    ' --push-gateway {gateway}'
                    .format(gateway=options.monitoring_prometheus_gateway))

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
    MonitoringConfigurator(),
    StorageConfigurator(),
    AwsConfigurator(),
    AppengineConfigurator(),
    AzureConfigurator(),
    DockerConfigurator(),
    GoogleConfigurator(),
    KubernetesConfigurator(),
    JenkinsConfigurator(),
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
