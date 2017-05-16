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

It is responsible for deploying spinnaker (via Halyard) remotely.
"""

import distutils
import logging
import os
import stat
import tempfile
import time
import traceback

from spinnaker.run import (
    run_quick,
    check_run_quick,
    check_run_and_monitor,
    run_and_monitor)


SUPPORTED_DEPLOYMENT_TYPES = ['localdebian', 'distributed']
SUPPORTED_DISTRIBUTED_PLATFORMS = ['kubernetes']
HALYARD_SERVICES = ['halyard']
SPINNAKER_SERVICES = [
    'clouddriver', 'echo', 'fiat', 'front50', 'gate', 'igor', 'orca',
    'rosco'
]


def ensure_empty_ssh_key(path):
  """Ensure there is an ssh key at the given path.

  It is assumed that this key has no password associated with it so we
  can use it for ssh/scp.
  """

  pub_path = path + '.pub'
  if os.path.exists(pub_path):
    return

  logging.debug('Creating %s SSH key', path)
  check_run_quick(
      'ssh-keygen -N "" -t rsa -f {path} -C $USER'
      '; sed "s/^ssh-rsa/$USER:ssh-rsa/" -i {path}'
      .format(path=path))


def write_data_to_secure_path(data, path=None, is_script=False):
  """Write data to a path with user-only access.

  Args:
    path: [string] Path to file or None to create a temporary file.
    is_script: [bool] True if data is a script (and should be executable).

  Returns:
    path to file written.
  """
  # pylint: disable=invalid-name
  if path is None:
    fd, path = tempfile.mkstemp()
  else:
    fd = os.open(path, os.O_WRONLY | os.O_CREAT)

  maybe_executable = stat.S_IXUSR if is_script else 0
  flags = stat.S_IRUSR | stat.S_IWUSR | maybe_executable
  os.fchmod(fd, flags)
  os.write(fd, data)
  os.close(fd)
  return path


def write_script_to_path(script, path=None):
  """Write the script to a path as a secure, user-only executable file.

  Args:
    script: [list] Sequence of bash statements to script.
    path: [string] Path to file to write, or None to create a temp file.

  Returns:
    path written
  """
  data = ['#!/bin/bash',
          'set -e',
          'set -x']
  data.extend(script)
  return write_data_to_secure_path(
      '\n'.join(data), path=path, is_script=True)


class BaseValidateBomDeployer(object):
  """Base class/interface for Deployer that uses Halyard to deploy Spinnaker.

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """

  @property
  def options(self):
    """The options bound at construction."""
    return self.__options

  def __init__(self, options, runtime_class=None):
    if runtime_class:
      self.__spinnaker_deployer = runtime_class(options)
    else:
      self.__spinnaker_deployer = self
    self.__options = options

  def make_port_forward_command(self, service, local_port, remote_port):
    """Return the command used to forward ports to the given service.

    Returns:
      array of commandline arguments to create a subprocess with.
    """
    return self.__spinnaker_deployer.do_make_port_forward_command(
        service, local_port, remote_port)

  def deploy(self, config_script, files_to_upload):
    """Deploy and configure spinnaker.

    The deployment configuration is specified via the bound options.
    The runtime configuration is passed to the call.

    Args:
      config_script: [list] The sequence of bash commands to run in order
         to configure spinnaker.
      file_to_upload: [set] A set of file paths to upload to the deployed
         instance before running the config_script. Presumably these will
         be referenced by the script.
    """
    platform = self.options.deploy_hal_platform
    logging.info('Deploying with hal on %s...', platform)
    script = []
    self.add_install_hal_script_statements(script)
    self.add_platform_deploy_script_statements(script)
    script.extend(config_script)
    self.add_hal_deploy_script_statements(script)
    script.append('hal --color=false deploy apply')
    self.add_post_deploy_statements(script)

    if not self.options.deploy_deploy:
      logging.warning('Skipping deployment because --deploy_deploy=false\n'
                      ' Would have run:\n%s\n and uploaded:\n%s\n\n',
                      script, files_to_upload)
      return
    self.do_deploy(script, files_to_upload)
    logging.info('Finished deploying to %s', platform)

  def undeploy(self):
    """Remove the spinnaker deployment and reclaim resources."""
    # Consider also undeploying from options.deploy_spinnaker_platform
    # with self.__runtime_deployer
    platform = self.options.deploy_hal_platform
    logging.info('Undeploying hal on %s...', platform)
    if not self.options.deploy_undeploy:
      logging.warning(
          'Skipping undeploy because --deploy_undeploy=false\n')
      return

    self.do_undeploy()
    logging.info('Finished undeploying from %s', platform)

  def collect_logs(self):
    """Collect all the microservice log files."""
    log_dir = os.path.join(self.options.log_dir, 'service_logs')
    if not os.path.exists(log_dir):
      os.makedirs(log_dir)

    all_services = list(SPINNAKER_SERVICES)
    all_services.extend(HALYARD_SERVICES)
    for service in all_services:
      try:
        logging.debug('Fetching logs for "%s"...', service)
        deployer = (self if service in HALYARD_SERVICES
                    else self.__spinnaker_deployer)
        deployer.do_fetch_service_log_file(service, log_dir)
      except Exception as ex:
        message = ('Error fetching log for service "{service}": {ex}'
                   '\n{trace}'
                   .format(service=service, ex=ex,
                           trace=traceback.format_exc()))
        logging.error(message)
        write_data_to_secure_path(
            message, os.path.join(log_dir, service + '.log'))

  def do_make_port_forward_command(self, service, local_port, remote_port):
    """Hook for concrete platforms to return the port forwarding command.

    Returns:
      array of commandline arguments to create a subprocess with.
    """
    raise NotImplementedError(self.__class__.__name__)

  def do_deploy(self, script, files_to_upload):
    """Hook for specialized platforms to implement the concrete deploy()."""
    # pylint: disable=unused-argument
    raise NotImplementedError(self.__class__.__name__)

  def do_undeploy(self):
    """Hook for specialized platforms to implement the concrete undeploy()."""
    raise NotImplementedError(self.__class__.__name__)

  def add_install_hal_script_statements(self, script):
    """Adds the sequence of Bash statements to fetch and install halyard."""
    options = self.options
    script.append('curl -s -O {url}'.format(url=options.halyard_install_script))
    install_params = ['-y']
    if options.halyard_repository is not None:
      install_params.extend(['--repository', options.halyard_repository])
    if options.spinnaker_repository is not None:
      install_params.extend(
          ['--spinnaker-repository', options.spinnaker_repository])
    script.append('sudo bash ./InstallHalyard.sh {install_params}'
                  .format(install_params=' '.join(install_params)))
    return script

  def add_platform_deploy_script_statements(self, script):
    """Hook for deployment platform to add specific hal statements."""
    pass

  def add_hal_deploy_script_statements(self, script):
    """Adds the hal deploy statements prior to "apply"."""
    options = self.options
    type_args = ['--type', options.deploy_spinnaker_type]

    if options.deploy_spinnaker_type == 'distributed':
      # Kubectl required for the next hal command, so install it if needed.
      script.append(
          'if ! `which kubectl >& /dev/null`; then'
          ' curl -LO https://storage.googleapis.com/kubernetes-release/release'
          '/$(curl -s https://storage.googleapis.com/kubernetes-release/release'
          '/stable.txt)/bin/linux/amd64/kubectl'
          '; chmod +x ./kubectl'
          '; sudo mv ./kubectl /usr/local/bin/kubectl'
          '; fi')
      if options.injected_deploy_spinnaker_account:
        type_args.extend(['--account-name',
                          options.injected_deploy_spinnaker_account])

    script.append('hal --color=false config deploy edit {args}'
                  .format(args=' '.join(type_args)))

  def add_post_deploy_statements(self, script):
    """Add any statements following "hal deploy apply"."""
    pass


class KubernetesValidateBomDeployer(BaseValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Google Cloud Platform.

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """
  def __init__(self, options, **kwargs):
    super(KubernetesValidateBomDeployer, self).__init__(options, **kwargs)

  @classmethod
  def init_platform_argument_parser(cls, parser):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for the free function init_argument_parser().
    """
    parser.add_argument(
        '--deploy_k8s_namespace',
        default='spinnaker',
        help='Namespace for the account Spinnaker is deployed into.')

  @classmethod
  def validate_options_helper(cls, options):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for make_deployer().
    """
    if options.deploy_distributed_platform != 'kubernetes':
      return

    if not options.k8s_account_name:
      raise ValueError('--deploy_distributed_platform="kubernetes" requires'
                       ' a --k8s_account_name be configured.')
    if hasattr(options, "injected_deploy_spinnaker_account"):
      raise ValueError('deploy_spinnaker_account was already set to "{0}"'
                       .format(options.injected_deploy_spinnaker_account))
    options.injected_deploy_spinnaker_account = options.k8s_account_name

  def __get_pod_name(self, k8s_namespace, service):
    """Determine the pod name for the deployed service."""
    response = check_run_quick(
        'kubectl get pods --namespace {namespace}'
        ' | gawk -F "[[:space:]]+" "/{service}-v/ {{print \\$1}}" | tail -1'
        .format(namespace=k8s_namespace, service=service))
    pod = response.stdout.strip()
    if not pod:
      message = 'There is no pod for "${service}" in ${namespace}'.format(
          service=service)
      logging.error(message)
      raise ValueError(mesage)

    if response.returncode != 0:
      message = 'Could not find pod for "${service}".: {error}'.format(
          service=service,
        error=response.stdout.strip())
      logging.error(message)
      raise ValueError(mesage)
    else:
      print '{0} -> "{1}"'.format(service, response.stdout)

    return response.stdout.strip()

  def do_make_port_forward_command(self, service, local_port, remote_port):
    """Implements interface."""
    options = self.options
    k8s_namespace = options.deploy_k8s_namespace
    service_pod = self.__get_pod_name(k8s_namespace, service)

    return [
        'kubectl', '--namespace', k8s_namespace,
        'port-forward', service_pod,
        '{local}:{remote}'.format(local=local_port, remote=remote_port)
    ]

  def do_deploy(self, script, files_to_upload):
    """Implements the BaseBomValidateDeployer interface."""
    # This is not yet supported in this script.
    # To deploy spinnaker to kubernetes, you need to go through
    # a halyard VM deployment. Halyard itself can be deployed to K8s.
    # This script doesnt.
    super(KubernetesValidateBomDeployer, self).do_deploy(
        script, files_to_upload)

  def do_undeploy(self):
    """Implements the BaseBomValidateDeployer interface."""
    super(KubernetesValidateBomDeployer, self).do_undeploy()
    # kubectl delete namespace spinnaker

  def do_fetch_service_log_file(self, service, log_dir):
    k8s_namespace = self.options.deploy_k8s_namespace
    service_pod = self.__get_pod_name(k8s_namespace, service)
    path = os.path.join(log_dir, service + '.log')
    write_data_to_secure_path('', path)
    check_run_quick(
        'kubectl -n {namespace} logs {pod} >> {path}'
        .format(namespace=k8s_namespace, pod=service_pod, path=path))

class GoogleValidateBomDeployer(BaseValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Google Cloud Platform.

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """

  EMPTY_SSH_KEY = os.path.join(os.environ['HOME'], '.ssh', 'google_empty')

  def __init__(self, options, **kwargs):
    super(GoogleValidateBomDeployer, self).__init__(options, **kwargs)

  @classmethod
  def init_platform_argument_parser(cls, parser):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for the free function init_argument_parser().
    """
    parser.add_argument(
        '--google_deploy_project',
        default=None,
        help='Google project to deploy to if --deploy_hal_platform is "gce".')
    parser.add_argument(
        '--google_deploy_zone',
        default='us-central1-f',
        help='Google zone to deploy to if --deploy_hal_platform is "gce".')
    parser.add_argument(
        '--google_deploy_instance',
        default=None,
        help='Google instance to deploy to if --deploy_hal_platform is "gce".')

  @classmethod
  def validate_options_helper(cls, options):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for make_deployer().
    """
    if not options.google_deploy_project:
      raise ValueError('--google_deploy_project not specified.')
    if not options.google_deploy_instance:
      raise ValueError('--google_deploy_instance not specified.')

  def do_make_port_forward_command(self, service, local_port, remote_port):
    """Implements interface."""
    options = self.options
    return [
        'gcloud', 'alpha', 'compute', 'ssh',
        '--quiet', '--verbosity=none',
        options.google_deploy_instance,
        '--ssh-key-file={ssh_key}'.format(ssh_key=self.EMPTY_SSH_KEY),
        '--project={project}'.format(project=options.google_deploy_project),
        '--zone={zone}'.format(zone=options.google_deploy_zone),
        '--', '-L {local_port}:localhost:{remote_port}'.format(
            local_port=local_port, remote_port=remote_port),
        '-N']

  def do_deploy(self, script, files_to_upload):
    """Implements the BaseBomValidateDeployer interface."""
    options = self.options
    ensure_empty_ssh_key(self.EMPTY_SSH_KEY)

    script_path = write_script_to_path(script, path=None)
    files_to_upload.add(script_path)
    if options.jenkins_master_name:
      write_data_to_secure_path(
          os.environ.get('JENKINS_MASTER_PASSWORD'),
          path=os.path.join(os.sep, 'tmp', 'jenkins_{name}_password'
                            .format(name=options.jenkins_master_name)),
          is_script=True)

    try:
      logging.info('Creating "%s" in project "%s"',
                   options.google_deploy_instance,
                   options.google_deploy_project)
      check_run_and_monitor(
          'gcloud compute instances create'
          ' --machine-type n1-standard-4'
          ' --image-family ubuntu-1404-lts'
          ' --image-project ubuntu-os-cloud'
          ' --metadata block-project-ssh-keys=TRUE'
          ' --project {project} --zone {zone}'
          ' --scopes {scopes}'
          ' --metadata-from-file ssh-keys={ssh_key}'
          ' {instance}'
          .format(project=options.google_deploy_project,
                  zone=options.google_deploy_zone,
                  scopes='compute-rw,storage-full,logging-write,monitoring',
                  ssh_key=self.EMPTY_SSH_KEY,
                  instance=options.google_deploy_instance))

      copy_files = (
          'gcloud compute copy-files '
          ' --ssh-key-file {ssh_key}'
          ' --project {project} --zone {zone}'
          ' {files} {instance}:.'
          .format(project=options.google_deploy_project,
                  ssh_key=self.EMPTY_SSH_KEY,
                  zone=options.google_deploy_zone,
                  instance=options.google_deploy_instance,
                  files=' '.join(files_to_upload)))
      logging.info('Copying files %s', copy_files)

      # pylint: disable=unused-variable
      for retry in range(0, 10):
        result = run_quick(copy_files)
        if result.returncode == 0:
          break
        time.sleep(2)

      if result.returncode != 0:
        check_run_quick(copy_files)
    finally:
      os.remove(script_path)

    logging.info('Running install script')
    check_run_and_monitor(
        'gcloud compute ssh'
        ' --ssh-key-file {ssh_key}'
        ' --project {project} --zone {zone} {instance}'
        ' --command "sudo ./{script_name}"'
        .format(project=options.google_deploy_project,
                zone=options.google_deploy_zone,
                instance=options.google_deploy_instance,
                ssh_key=self.EMPTY_SSH_KEY,
                script_name=os.path.basename(script_path)))

  def do_undeploy(self):
    """Implements the BaseBomValidateDeployer interface."""
    options = self.options
    if options.deploy_spinnaker_type == 'distributed':
      run_and_monitor(
          'gcloud compute ssh'
          ' --ssh-key-file {ssh_key}'
          ' --project {project} --zone {zone} {instance}'
          ' --command "sudo hal deploy clean"'
          .format(project=options.google_deploy_project,
                  zone=options.google_deploy_zone,
                  instance=options.google_deploy_instance,
                  ssh_key=self.EMPTY_SSH_KEY))
    check_run_and_monitor(
        'gcloud -q compute instances delete'
        ' --project {project} --zone {zone} {instance}'
        .format(project=options.google_deploy_project,
                zone=options.google_deploy_zone,
                instance=options.google_deploy_instance))

  def do_fetch_service_log_file(self, service, log_dir):
    """Implements the BaseBomValidateDeployer interface."""
    options = self.options
    write_data_to_secure_path('', os.path.join(log_dir, service + '.log'))
    check_run_quick(
        'gcloud compute copy-files '
        ' --ssh-key-file {ssh_key}'
        ' --project {project} --zone {zone}'
        ' {instance}:/var/log/spinnaker/{service}/{service}.log'
        ' {log_dir}'
        .format(project=options.google_deploy_project,
                zone=options.google_deploy_zone,
                instance=options.google_deploy_instance,
                ssh_key=self.EMPTY_SSH_KEY,
                service=service,
                log_dir=log_dir))


def make_deployer(options):
  """Public interface to instantiate the desired Deployer.

  Args:
    options: [Namespace] from an argument parser given to init_argument_parser
  """
  if options.deploy_hal_platform == 'gce':
    hal_klass = GoogleValidateBomDeployer
  else:
    raise ValueError(
        'Invalid --deploy_hal_platform=%s', options.deploy_hal_platform)

  if options.deploy_spinnaker_type not in SUPPORTED_DEPLOYMENT_TYPES:
    raise ValueError(
        'Invalid --deploy_spinnaker_type "{0}". Must be one of {1}'
        .format(options.deploy_spinnaker_type, SUPPORTED_DEPLOYMENT_TYPES))

  # This is the class for accessing the Spinnaker deployment if other than Hal.
  spin_klass = None

  if options.deploy_spinnaker_type == 'distributed':
    if (options.deploy_distributed_platform
        not in SUPPORTED_DISTRIBUTED_PLATFORMS):
      raise ValueError(
          'A "distributed" deployment requires --deploy_distributed_platform')
    if options.deploy_distributed_platform == 'kubernetes':
      spin_klass = KubernetesValidateBomDeployer
    else:
      raise ValueError(
          'Unknown --deploy_distributed_platform.'
          ' This must be the value of one of the following parameters: {0}'
          .format(SUPPORTED_DISTRIBUTED_PLATFORMS))

  hal_klass.validate_options_helper(options)
  if spin_klass:
    spin_klass.validate_options_helper(options)

  return hal_klass(options, runtime_class=spin_klass)


def init_argument_parser(parser):
  """Initialize the argument parser with deployment and configuration params.

  Args:
    parser: [ArgumentParser] The argument parser to add the parameters to.
  """
  def make_bool_value(value):
    """Helper function for converting boolean command line arguments."""
    return bool(distutils.util.strtobool(value))

  # pylint: disable=line-too-long
  parser.add_argument(
      '--halyard_install_script',
      default='https://raw.githubusercontent.com/spinnaker/halyard/master/install/nightly/InstallHalyard.sh',
      help='The URL to the InstallHalyard.sh script.')

  parser.add_argument(
      '--halyard_repository',
      default='https://dl.bintray.com/spinnaker-releases/debians',
      help='The location of the halyard repository.')

  parser.add_argument(
      '--spinnaker_repository',
      default='https://dl.bintray.com/spinnaker-releases/debians',
      help='The location of the spinnaker repository.')

  parser.add_argument(
      '--deploy_spinnaker_type', required=True, choices=SUPPORTED_DEPLOYMENT_TYPES,
      help='The type of spinnaker deployment to create.')

  parser.add_argument(
      '--deploy_hal_platform', required=True, choices=['gce'],
      help='Platform to deploy Halyard onto.'
           ' Halyard will then deploy Spinnaker.')

  parser.add_argument(
      '--deploy_distributed_platform', default='kubernetes',
      choices=SUPPORTED_DISTRIBUTED_PLATFORMS,
      help='The paltform to deploy spinnaker to when'
           ' --deploy_spinnaker_type=distributed')

  parser.add_argument(
      '--deploy_deploy', default=True,
      type=make_bool_value,
      help='Actually perform the deployment.'
           ' This is for facilitating debugging with this script.')

  parser.add_argument(
      '--deploy_undeploy', default=True,
      type=make_bool_value,
      help='Actually perform the undeployment.'
           ' This is for facilitating debugging with this script.')

  GoogleValidateBomDeployer.init_platform_argument_parser(parser)
  KubernetesValidateBomDeployer.init_platform_argument_parser(parser)
