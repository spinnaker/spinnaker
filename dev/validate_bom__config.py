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

      add_init
         Adds script commands used to initialize this component [before hal].

      add_config
         Adds script commands used to configure this component [via hal].

      add_files_to_upload
         Adds paths to files referenced by config options that should
         be uploaded with the script that will be referencing them.

  * The configurator may add other implicit parameters.
        <service>_account_enabled is set if it is configured.
        The flag is used to make test filtering easier.
"""


import logging
import os

from buildtool import (
    add_parser_argument,
    check_options_set,
    check_path_exists,
    raise_and_log_error,
    ConfigError)

from validate_bom__deploy import write_data_to_secure_path

from google.cloud import pubsub
from google.cloud import storage
from google.oauth2 import service_account


class Configurator(object):
  """Interface used to control hal configuration of a particular feature set."""

  def init_argument_parser(self, parser, defaults):
    """Adds command-line arguments to control configuration."""
    pass

  def validate_options(self, options):
    """Validates command-line arguments configuring feature set."""
    pass

  def add_init(self, options, script):
    """Writes bash commands to initialize feature set before Halyard.

    This is intended for monitoring or other things that might be
    desired before installing halyard and performing the remaining add_config.
    """
    pass

  def add_config(self, options, script):
    """Writes bash commands (e.g. hal) to configure feature set."""
    pass

  def setup_environment(self, options):
    """Configures external platform infrastructure."""
    pass

  def teardown_environment(self, options):
    """Cleans up resources created in setup_environment()."""
    pass

  def add_files_to_upload(self, options, file_set):
    """Adds paths to local config files that should be uploaded."""
    pass


class AzsStorageConfiguratorHelper(Configurator):
  """Helper class for StorageConfigurator to handle AZS."""

  @classmethod
  def init_argument_parser(cls, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'storage_azs_account_name', defaults, None,
        help='The name for the Azure Storage Account to use.'
             ' This is only used if --spinnaker_storage=azs.')
    add_parser_argument(
        parser, 'storage_azs_credentials', defaults, None,
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


class S3StorageConfiguratorHelper(Configurator):
  """Helper class for StorageConfigurator to handle S3."""

  REGIONS = ['us-east-2', 'us-east-1', 'us-west-1', 'us-west-2',
             'ca-central-1',
             'ap-south-1', 'ap-northeast-2', 'ap-southeast-1',
             'ap-southeast-2', 'ap-northeast-1',
             'eu-central-1', 'eu-west-1', 'eu-west-2', 'sa-east-1']

  @classmethod
  def init_argument_parser(cls, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'storage_s3_bucket', defaults, None,
        help='The name for the AWS S3 bucket to use.'
             ' This is only used if --spinnaker_storage=s3.')
    add_parser_argument(
        parser, 'storage_s3_assume_role', defaults, 'role/spinnakerManaged',
        help='Use AWS SecurityToken Service to assume this role.')

    add_parser_argument(
        parser, 'storage_s3_region', defaults, None, choices=cls.REGIONS,
        help='The name for the AWS region to create the bucket in.'
             ' This is only used if the bucket does not already exist.')

    add_parser_argument(
        parser, 'storage_s3_endpoint', defaults, None,
        help='The s3 endpoint.')

    add_parser_argument(
        parser, 'storage_s3_access_key_id', defaults, None,
        help='AWS Access Key ID for AWS account owning s3 storage.')
    add_parser_argument(
        parser, 'storage_s3_credentials', defaults, None,
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


class GcsArtifactStorageConfiguratorHelper(Configurator):
  """Helper class for ArtifactConfigurator to handle GCS."""

  @classmethod
  def init_argument_parser(cls, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'artifact_gcs_credentials', defaults, None,
        help='Path to google credentials file to configure spinnaker'
             ' artifact storage.')
    add_parser_argument(
        parser, 'artifact_gcs_account_name', defaults, None,
        help='Account name to use for artifact downloads.')

  @classmethod
  def validate_options(cls, options):
    """Implements interface."""
    pass

  @classmethod
  def add_files_to_upload(cls, options, file_set):
    """Implements interface."""
    if options.artifact_gcs_credentials:
      file_set.add(options.artifact_gcs_credentials)

  @classmethod
  def add_config(cls, options, script):
    """Implements interface."""
    if options.artifact_gcs_account_name is not None:
      script.append('hal -q --log=info config artifact gcs enable')
      hal = (
          'hal -q --log=info config artifact gcs account add {name}'
          .format(name=options.artifact_gcs_account_name))
      if options.artifact_gcs_credentials:
        hal += (' --json-path ./{filename}'
                .format(filename=os.path.basename(
                    options.artifact_gcs_credentials)))
      script.append(hal)


class GcsStorageConfiguratorHelper(Configurator):
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
  def init_argument_parser(cls, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'storage_gcs_bucket', defaults, None,
        help=('URI for specific Google Storage bucket to use.'
              ' This is suggested if using gcs storage, though can be left'
              ' empty to let Halyard create one.'))
    add_parser_argument(
        parser, 'storage_gcs_location', defaults, 'us-central1',
        choices=cls.LOCATIONS,
        help=('Location for the bucket if it needs to be created.'))
    add_parser_argument(
        parser, 'storage_gcs_project', defaults, None,
        help=('URI for specific Google Storage bucket project to use.'
              ' If empty, use the --deploy_google_project.'))
    add_parser_argument(
        parser, 'storage_gcs_credentials', defaults, None,
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


class ArtifactConfigurator(Configurator):
  """Controls hal config artifact for Spinnaker artifact ."""

  HELPERS = [
      GcsArtifactStorageConfiguratorHelper,
  ]

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    for helper in self.HELPERS:
      helper.init_argument_parser(parser, defaults)

  def validate_options(self, options):
    """Implements interface."""
    for helper in self.HELPERS:
      helper.validate_options(options)

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    for helper in self.HELPERS:
      helper.add_files_to_upload(options, file_set)

  def add_config(self, options, script):
    """Implements interface."""
    for helper in self.HELPERS:
      helper.add_config(options, script)


class StorageConfigurator(Configurator):
  """Controls hal config storage for Spinnaker Storage ."""

  HELPERS = {
      'azs': AzsStorageConfiguratorHelper,
      'gcs': GcsStorageConfiguratorHelper,
      's3': S3StorageConfiguratorHelper
  }

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'spinnaker_storage',
        defaults, None,
        choices=self.HELPERS.keys(),
        help='The storage type to configure.')
    for helper in self.HELPERS.values():
      helper.init_argument_parser(parser, defaults)

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


class AwsConfigurator(Configurator):
  """Controls hal config provider aws."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    # pylint: disable=line-too-long
    add_parser_argument(
        parser, 'aws_access_key_id', defaults, None,
        help='The AWS ACCESS_KEY_ID.')
    add_parser_argument(
        parser, 'aws_credentials', defaults, None,
        help='A path to a file containing the AWS SECRET_ACCESS_KEY')

    add_parser_argument(
        parser, 'aws_account_name', defaults, 'my-aws-account',
        help='The name of the primary AWS account to configure.')
    add_parser_argument(
        parser, 'aws_account_id', defaults, None,
        help='The AWS account id for the account.'
             ' See http://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html')
    add_parser_argument(
        parser, 'aws_account_role', defaults, 'role/spinnakerManaged',
        help=' The account will assume this role.')

    add_parser_argument(
        parser, 'aws_account_regions', defaults, 'us-east-1,us-west-2',
        help='The AWS account regions the account will manage.')
    add_parser_argument(
        parser, 'aws_account_pem_path', defaults, None,
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


class AppengineConfigurator(Configurator):
  """Controls hal config provider for appengine"""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    # pylint: disable=line-too-long
    add_parser_argument(
        parser, 'appengine_account_project', defaults, None,
        help='The Google Cloud Platform project this Spinnaker account will manage.')

    add_parser_argument(
        parser, 'appengine_account_name', defaults, 'my-appengine-account',
        help='The name of the primary Appengine account to configure.')
    add_parser_argument(
        parser, 'appengine_account_credentials', defaults, None,
        help='Path to file containing the JSON Oauth credentials for the AppEngine account.')

    add_parser_argument(
        parser, 'appengine_account_git_username', defaults, None,
        help='The name of the remote git user.')
    add_parser_argument(
        parser, 'appengine_account_git_https_credentials', defaults, None,
        help='Path to file containing the password for the remote git repository.')
    add_parser_argument(
        parser, 'appengine_account_git_oauth_credentials', defaults, None,
        help='Path to file containing the password for the remote git repository.')

    add_parser_argument(
        parser, 'appengine_account_ssh_private_key_path', defaults, None)
    add_parser_argument(
        parser, 'appengine_account_ssh_private_key_passphrase', defaults, None)

    add_parser_argument(
        parser, 'appengine_account_local_repository_directory', defaults, None)

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


class AzureConfigurator(Configurator):
  """Controls hal config provider azure."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    # pylint: disable=line-too-long
    add_parser_argument(
        parser, 'azure_account_credentials', defaults, None,
        help='Path to Azure credentials file containing the appKey'
             ' for the service principal.')
    add_parser_argument(
        parser, 'azure_account_name', defaults, 'my-azure-account',
        help='The name of the primary Azure account to configure.')
    add_parser_argument(
        parser, 'azure_account_client_id', defaults, None,
        help='The Azure clientId for the service principal.')
    add_parser_argument(
        parser, 'azure_account_subscription_id', defaults, None,
        help='The subscriptionId for the service principal.')
    add_parser_argument(
        parser, 'azure_account_tenant_id', defaults, None,
        help='The tenantId for the service principal.')
    add_parser_argument(
        parser, 'azure_account_object_id', defaults, None,
        help='The objectId of the service principal.'
             ' Needed to bake Windows images.')

    add_parser_argument(
        parser, 'azure_account_default_key_vault', defaults, None,
        help='The name of the KeyValue containing the default user/password'
             ' to create VMs.')
    add_parser_argument(
        parser, 'azure_account_default_resource_group', defaults, None,
        help='The default for non-application specific resources.')
    add_parser_argument(
        parser, 'azure_account_packer_resource_group', defaults, None,
        help='Used by packer when baking images.')
    add_parser_argument(
        parser, 'azure_account_packer_storage_account', defaults, None,
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


class DcosConfigurator(Configurator):
  """Controls hal config provider DC/OS."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    # pylint: disable=line-too-long
    add_parser_argument(
        parser, 'dcos_cluster_name', defaults, 'my-dcos-cluster',
        help='The name for the primary DC/OS cluster.')
    add_parser_argument(
        parser, 'dcos_cluster_url', defaults, None,
        help='The URL to the primary DC/OS cluster.'
             ' This is required to enable DC/OS')
    add_parser_argument(
        parser, 'dcos_account_name', defaults, 'my-dcos-account',
        help='The name of the primary DC/OS account to configure.')
    add_parser_argument(
        parser, 'dcos_account_docker_account', defaults, None,
        help='The registered docker account name to use.')
    add_parser_argument(
        parser, 'dcos_account_uid', defaults, None,
        help='The DC/OS account user name the service principal.'
             'This is required if DC/OS is enabled.')
    add_parser_argument(
        parser, 'dcos_account_credentials', defaults, None,
        help='Path to DC/oS credentials file containing the password'
             ' for the uid. This is required if DC/OS is enabled.')

  def validate_options(self, options):
    """Implements interface."""
    options.dcos_account_enabled = (options.dcos_cluster_url is not None)
    if not options.dcos_account_enabled:
      return

    if options.dcos_account_uid is None:
      raise ValueError('--dcos_account_uid is not set')
    if options.dcos_account_credentials is None:
      raise ValueError('--dcos_account_uid provided without credentials')
    if options.dcos_account_docker_account is None:
      raise ValueError('--dcos_account_docker_account is not set')

  def add_config(self, options, script):
    """Implements interface."""
    if not options.dcos_account_enabled:
      return

    script.append(
        'hal -q --log=info config provider dcos cluster add'
        ' {name} --dcos-url {url} --skip-tls-verify'
        .format(name=options.dcos_cluster_name,
                url=options.dcos_cluster_url))

    script.append('hal -q --log=info config provider dcos enable')
    with open(options.dcos_account_credentials) as creds:
      script.append(
          'hal -q --log=info config provider dcos account add'
          ' {name}'
          ' --cluster {cluster}'
          ' --docker-registries {docker}'
          ' --uid {uid}'
          ' --password {creds}'
          .format(name=options.dcos_account_name,
                  cluster=options.dcos_cluster_name,
                  docker=options.dcos_account_docker_account,
                  uid=options.dcos_account_uid,
                  creds=creds.read()))


class GoogleConfigurator(Configurator):
  """Controls hal config provider google."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'google_account_project', defaults, None,
        help='Google project to deploy to if --host_platform is gce.')
    add_parser_argument(
        parser, 'google_account_credentials', defaults, None,
        help='Path to google credentials file for the google account.'
             'Adding credentials enables the account.')
    add_parser_argument(
        parser, 'google_account_name', defaults, 'my-google-account',
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
    if options.deploy_google_zone:
      script.append('hal -q --log=info config provider google bakery edit'
                    ' --zone {zone}'.format(zone=options.deploy_google_zone))
    script.append(
        'hal -q --log=info config provider google account add {params}'
        .format(params=' '.join(account_params)))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if options.google_account_credentials:
      file_set.add(options.google_account_credentials)


class KubernetesConfigurator(Configurator):
  """Controls hal config provider kubernetes."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'k8s_account_credentials', defaults, None,
        help='Path to k8s credentials file.')
    add_parser_argument(
        parser, 'k8s_account_name', defaults, 'my-kubernetes-account',
        help='The name of the primary Kubernetes account to configure.')
    add_parser_argument(
        parser, 'k8s_account_context', defaults, None,
        help='The kubernetes context for the primary Kubernetes account.')
    add_parser_argument(
        parser, 'k8s_account_namespaces', defaults, 'validate-bom',
        help='The kubernetes namespaces for the primary Kubernetes account.')
    add_parser_argument(
        parser, 'k8s_account_docker_account', defaults, None,
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


class DockerConfigurator(Configurator):
  """Controls hal config provider docker."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'docker_account_address', defaults, None,
        help='Registry address to pull and deploy images from.')
    add_parser_argument(
        parser, 'docker_account_name', defaults, 'my-docker-account',
        help='The name of the primary Docker account to configure.')
    add_parser_argument(
        parser, 'docker_account_registry_username', defaults, None,
        help='The username for the docker registry.')
    add_parser_argument(
        parser, 'docker_account_credentials', defaults, None,
        help='Path to plain-text password file.')
    add_parser_argument(
        parser, 'docker_account_repositories', defaults, None,
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


class GooglePubsubConfigurator(Configurator):
  """Controls hal config google pub/sub configuration."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'pubsub_google_project', defaults, None,
        help='The name of the GCE project the subscription lives in.')
    add_parser_argument(
        parser, 'pubsub_google_credentials_path', defaults, None,
        help='Path to service account credentials for the subscription.')
    add_parser_argument(
        parser, 'pubsub_google_subscription_name', defaults, None,
        help='The name of the pub/sub subscription to pull from.')
    add_parser_argument(
        parser, 'pubsub_google_name', defaults, None,
        help='The logical name of the configured subscription in Spinnaker.')
    add_parser_argument(
        parser, 'pubsub_google_template_path', defaults, None,
        help='Path to the Jinja message translation template.')

  def validate_options(self, options):
    """Implements interface."""
    options.pubsub_google_enabled = options.pubsub_google_project is not None

    if not options.pubsub_google_enabled:
      return

    check_options_set(
        options,
        ['pubsub_google_project', 'pubsub_google_credentials_path',
         'pubsub_google_subscription_name', 'pubsub_google_template_path'])
    check_path_exists(options.pubsub_google_credentials_path,
                      "pubsub_credentials_path")
    check_path_exists(options.pubsub_google_template_path,
                      "pubsub_template_path")

  def add_config(self, options, script):
    """Implements interface."""
    if not options.pubsub_google_enabled:
      return

    script.append('hal -q --log=info config pubsub google enable')
    subscription_cmd = ['hal -q --log=info config pubsub google subscription']
    if options.pubsub_google_name:
      subscription_cmd.append('add {}'.format(options.pubsub_google_name))
    if options.pubsub_google_project:
      subscription_cmd.append('--project ' + options.pubsub_google_project)
    if options.pubsub_google_credentials_path:
      subscription_cmd.append(
          '--json-path '
          + os.path.basename(options.pubsub_google_credentials_path))
    if options.pubsub_google_subscription_name:
      subscription_cmd.append(
          '--subscription-name ' + options.pubsub_google_subscription_name)
    if options.pubsub_google_template_path:
      subscription_cmd.append(
          '--template-path '
          + os.path.basename(options.pubsub_google_template_path))
    script.append(' '.join(subscription_cmd))

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    if not options.pubsub_google_enabled:
      return

    if options.pubsub_google_credentials_path:
      file_set.add(options.pubsub_google_credentials_path)
    if options.pubsub_google_template_path:
      file_set.add(options.pubsub_google_template_path)

class GcsPubsubNotficationConfigurator(Configurator):
  """Controls external (to Spinnaker) GCS -> Google Pubsub config."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'gcs_pubsub_bucket', defaults, None,
        help='The name of the bucket to create.')
    add_parser_argument(
        parser, 'gcs_pubsub_credentials_path', defaults, None,
        help='The path to the credentials used to manipulate GCS and pub/sub.')
    add_parser_argument(
        parser, 'gcs_pubsub_project', defaults, None,
        help='The name of the project for bucket, topic, and subscription.')
    add_parser_argument(
        parser, 'gcs_pubsub_topic', defaults, None,
        help='The name of the topic to create.')
    add_parser_argument(
        parser, 'gcs_pubsub_subscription', defaults, None,
        help='The name of the subscription to create.')

  def validate_options(self, options):
    """Implements interface."""
    options.gcs_pubsub_enabled = options.gcs_pubsub_bucket is not None
    if not options.gcs_pubsub_enabled:
      return

    check_options_set(
        options,
        ['gcs_pubsub_bucket', 'gcs_pubsub_topic', 'gcs_pubsub_project',
         'gcs_pubsub_credentials_path', 'gcs_pubsub_subscription'])
    if (options.gcs_pubsub_subscription
        != options.pubsub_google_subscription_name):
      raise_and_log_error(
          ConfigError('Inconsistent pub/sub subscription in configuration: '
                      ' --gcs_pubsub_subscription="{}"'
                      ' --pubsub_google_subscription_name="{}"'
                      .format(options.gcs_pubsub_subscription,
                              options.pubsub_google_subscription_name)))

  def __instantiate_clients(self, options):
    """Instantiates and returns publisher, subscriber, and storage clients.

    Returns:
      Tuple of (publisher_client, subscriber_client, storage_client)
    """
    scopes = [
        'https://www.googleapis.com/auth/devstorage.full_control',
        'https://www.googleapis.com/auth/cloud-platform',
        'https://www.googleapis.com/auth/pubsub',
    ]
    credentials = service_account.Credentials.from_service_account_file(
        options.gcs_pubsub_credentials_path,
        scopes=scopes)
    publisher_client = pubsub.PublisherClient(credentials=credentials)
    subscriber_client = pubsub.SubscriberClient(credentials=credentials)
    storage_client = storage.Client(credentials=credentials,
                                    project=options.gcs_pubsub_project)
    return (publisher_client, subscriber_client, storage_client)

  def setup_environment(self, options):
    """Implements interface."""
    if not options.gcs_pubsub_enabled:
      return

    publisher_client, subscriber_client, storage_client = self.__instantiate_clients(options)

    logging.info('Creating topic %s in project %s',
                 options.gcs_pubsub_topic, options.gcs_pubsub_project)
    topic_ref = publisher_client.topic_path(
        options.gcs_pubsub_project, options.gcs_pubsub_topic)
    publisher_client.create_topic(topic_ref)

    logging.info('Creating subscription %s in project %s',
                 options.gcs_pubsub_subscription, options.gcs_pubsub_project)
    subscription_ref = subscriber_client.subscription_path(
        options.gcs_pubsub_project, options.gcs_pubsub_subscription)
    subscriber_client.create_subscription(subscription_ref, topic_ref)
    storage_client.create_bucket(options.gcs_pubsub_bucket)

    bucket = storage_client.get_bucket(options.gcs_pubsub_bucket)
    notification = bucket.notification(
        options.gcs_pubsub_topic, topic_project=options.gcs_pubsub_project,
        payload_format=storage.notification.JSON_API_V1_PAYLOAD_FORMAT)
    notification.create()
    logging.debug('Created bucket notification %s', notification)

  def teardown_environment(self, options):
    """Implements interface."""
    if not options.gcs_pubsub_enabled:
      return

    publisher_client, subscriber_client, storage_client = self.__instantiate_clients(options)

    subscriber_client.delete_subscription(options.gcs_pubsub_subscription)
    publisher_client.delete_topic(options.gcs_pubsub_topic)
    storage_client.delete_bucket(options.gcs_pubsub_bucket)


class JenkinsConfigurator(Configurator):
  """Controls hal config ci."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'jenkins_master_name', defaults, None,
        help='The name of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_address, '
        ' --jenkins_master_user, and --jenkins_master_credentials'
        ' or an environment variable JENKINS_MASTER_PASSWORD')
    add_parser_argument(
        parser, 'jenkins_master_address', defaults, None,
        help='The network address of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_name, '
        ' --jenkins_master_user, and --jenkins_master_credentials'
        ' or an environment variable JENKINS_MASTER_PASSWORD')
    add_parser_argument(
        parser, 'jenkins_master_user', defaults, None,
        help='The name of the jenkins master to configure.'
        ' If provided, this also needs --jenkins_master_address, '
        ' --jenkins_master_name, and --jenkins_master_credentials'
        ' or an environment variable JENKINS_MASTER_PASSWORD')
    add_parser_argument(
        parser, 'jenkins_master_credentials', defaults, None,
        help='The password for the jenkins master to configure.'
             ' If provided, this takes precedence over'
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


class LoggingConfigurator(Configurator):
  """Controls hal config logging."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'google_cloud_logging', defaults, False, type=bool,
        help='Install Google Cloud Logging agent.')

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""

    if not options.google_cloud_logging:
      return

    # Upload install script from this repo
    basedir = os.path.join(os.path.dirname(__file__),
                           '..', 'google', 'google_cloud_logging')
    file_set.add(os.path.join(basedir, 'add_google_cloud_logging.sh'))
    file_set.add(os.path.join(basedir, 'spinnaker.conf'))

  def add_init(self, options, script):
    """Implements interface."""

    if not options.google_cloud_logging:
      return

    script.append('chmod +x ./add_google_cloud_logging.sh')
    script.append('sudo ./add_google_cloud_logging.sh')


class MonitoringConfigurator(Configurator):
  """Controls hal config monitoring."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    add_parser_argument(
        parser, 'monitoring_prometheus_gateway', defaults, None,
        help='If provided, and which is "prometheus",'
             ' configure to use the gateway server at thsi URL.')
    add_parser_argument(
        parser, 'monitoring_install_which', defaults, None,
        help='If provided, install monitoring with these params.')

  def validate_options(self, options):
    """Implements interface."""
    if (options.monitoring_prometheus_gateway
        and (options.monitoring_install_which != 'prometheus')):
      raise ValueError('gateway is only applicable to '
                       ' --monitoring_install_which="prometheus"')

  def __inject_prometheus_node_exporter(self, options, script):
    """Add installation instructions for node_exporter

    Add these to the start of the script so we can monitor installation.
    """
    version = '0.15.2'
    node_version = 'node_exporter-{0}.linux-amd64'.format(version)
    install_node_exporter = [
        'curl -s -S -L -o /tmp/node_exporter.gz'
        ' https://github.com/prometheus/node_exporter/releases/download'
        '/v{version}/{node_version}.tar.gz'
        .format(version=version, node_version=node_version),

        'sudo tar xzf /tmp/node_exporter.gz -C /opt',

        'sudo ln -fs /opt/{node_version}/node_exporter'
        ' /usr/bin/node_exporter'
        .format(node_version=node_version),

        'rm /tmp/node_exporter.gz',

        '(sudo /usr/bin/node_exporter < /dev/null > /dev/null 2>&1 &)',
    ]

    # Prepend install_node_exporter to the beginning of the list.
    # This is so we can monitor installation process itself,
    # at least from this point in the script execution (before halyard install).
    #
    # There is no prepend, only individual element insert.
    # But there is a reverse, so we'll do a bit of manipulation here
    script.reverse()
    install_node_exporter.reverse()
    script.extend(install_node_exporter)
    script.reverse()

  def add_init(self, options, script):
    """Implements interface."""
    if not options.monitoring_install_which:
      return

    # Start up monitoring now so we can monitor these VMs
    if (options.monitoring_install_which == 'prometheus'
        and options.deploy_spinnaker_type == 'localdebian'):
      self.__inject_prometheus_node_exporter(options, script)

  def add_config(self, options, script):
    """Implements interface."""
    if not options.monitoring_install_which:
      return

    script.append('mkdir -p  ~/.hal/default/service-settings')
    script.append('echo "host: 0.0.0.0"'
                  ' > ~/.hal/default/service-settings/monitoring-daemon.yml')

    script.append('hal -q --log=info config metric-stores {which} enable'
                  .format(which=options.monitoring_install_which))
    if options.monitoring_prometheus_gateway:
      script.append('hal -q --log=info config metric-stores prometheus edit'
                    ' --push-gateway {gateway}'
                    .format(gateway=options.monitoring_prometheus_gateway))


class NotificationConfigurator(Configurator):
  """Controls hal config notification."""
  pass


class SecurityConfigurator(Configurator):
  """Controls hal config security."""
  pass


class SpinnakerConfigurator(Configurator):
  """Controls spinnaker-local overrides."""

  def init_argument_parser(self, parser, defaults):
    """Implements interface."""
    pass

  def validate_options(self, options):
    """Implements interface."""
    pass

  def add_config(self, options, script):
    """Implements interface."""
    script.append('mkdir -p  ~/.hal/default/profiles')
    script.append('echo "management.security.enabled: false"'
                  ' > ~/.hal/default/profiles/spinnaker-local.yml')

  def add_files_to_upload(self, options, file_set):
    """Implements interface."""
    pass



CONFIGURATOR_LIST = [
    MonitoringConfigurator(),
    LoggingConfigurator(),
    SpinnakerConfigurator(),
    StorageConfigurator(),
    ArtifactConfigurator(),
    AwsConfigurator(),
    AppengineConfigurator(),
    AzureConfigurator(),
    DockerConfigurator(),
    DcosConfigurator(),  # Hal requires docker config first.
    GoogleConfigurator(),
    KubernetesConfigurator(),
    JenkinsConfigurator(),
    NotificationConfigurator(),
    SecurityConfigurator(),
    GcsPubsubNotficationConfigurator(),
    GooglePubsubConfigurator()
]


def init_argument_parser(parser, defaults):
  """Initialize the argument parser with configuration options.

  Args:
    parser: [ArgumentParser] The argument parser to add the options to.
  """
  for configurator in CONFIGURATOR_LIST:
    configurator.init_argument_parser(parser, defaults)


def validate_options(options):
  """Validate supplied options to ensure basic idea is ok.

  This doesnt perform a fine-grained check, just whether or not
  the arguments seem consistent or complete so we can fail fast.
  """
  for configurator in CONFIGURATOR_LIST:
    configurator.validate_options(options)


def make_scripts(options):
  """Creates the bash script for configuring Spinnaker.

  Returns a pair of lists of bash statement strings.
  The first is to be run before halyard is install to initialize the host,
  the second after halyard is installed in order to configure spinnaker.
  """
  init_script = []
  config_script = []
  for configurator in CONFIGURATOR_LIST:
    configurator.add_init(options, init_script)
    configurator.add_config(options, config_script)

  return init_script, config_script


def setup_environment(options):
  """Performs any external infrastructure or cloud provider config.
  """
  for configurator in CONFIGURATOR_LIST:
    configurator.setup_environment(options)


def teardown_environment(options):
  """Tears down any external infrastructure or cloud provider config.
  """
  for configurator in CONFIGURATOR_LIST:
    configurator.teardown_environment(options)


def get_files_to_upload(options):
  """Collects the paths to files that the configuration script will reference.

  Returns:
     A set of path strings.
  """
  file_set = set([])
  for configurator in CONFIGURATOR_LIST:
    configurator.add_files_to_upload(options, file_set)
  return file_set
