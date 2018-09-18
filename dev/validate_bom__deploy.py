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

from multiprocessing.pool import ThreadPool

import json
import logging
import os
import shutil
import stat
import tempfile
import time
import traceback

from buildtool import (
    add_parser_argument,
    check_subprocess,
    check_subprocess_sequence,
    check_subprocesses_to_logfile,
    scan_logs_for_install_errors,
    run_subprocess,
    write_to_path,
    raise_and_log_error,
    ConfigError,
    ExecutionError,
    ResponseError,
    TimeoutError,
    UnexpectedError)


SUPPORTED_DEPLOYMENT_TYPES = ['localdebian', 'distributed']
SUPPORTED_DISTRIBUTED_PLATFORMS = ['kubernetes', 'kubernetes_v2']
HALYARD_SERVICES = ['halyard']
SPINNAKER_SERVICES = [
    'clouddriver', 'echo', 'fiat', 'front50', 'gate', 'igor', 'orca',
    'rosco', 'kayenta', 'monitoring'
]


def replace_ha_services(services, options):
  """Replace services with their HA services.

  Given a list of services and options, return a new list of services where
  services that are enabled for HA are replaced with their HA counterparts.
  """

  transform_map = {}
  if options.ha_clouddriver_enabled:
    transform_map['clouddriver'] = \
        ['clouddriver-caching', 'clouddriver-rw', 'clouddriver-ro']
  if options.ha_echo_enabled:
    transform_map['echo'] = \
        ['echo-scheduler', 'echo-replica']

  transformed_services = []
  for service in services:
    transformed_services.extend(transform_map.get(service, [service]))
  return transformed_services


def ensure_empty_ssh_key(path, user):
  """Ensure there is an ssh key at the given path.

  It is assumed that this key has no password associated with it so we
  can use it for ssh/scp.
  """

  if os.path.exists(path):
    return

  logging.debug('Creating %s SSH key for user "%s"', path, user)
  check_subprocess_sequence([
      'ssh-keygen -N "" -t rsa -f {path} -C {user}'.format(
          path=path, user=user),
      'sed "s/^ssh-rsa/{user}:ssh-rsa/" -i {path}'.format(
          user=user, path=path)
  ])


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
  os.write(fd, data.encode('utf-8'))
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

  @property
  def metrics(self):
    """The metrics regisry bound at construction."""
    return self.__metrics

  @property
  def hal_user(self):
    """Returns the Halyard User within the deployment VM."""
    return self.__hal_user

  def __init__(self, options, metrics, runtime_class=None):
    if runtime_class:
      self.__spinnaker_deployer = runtime_class(options, metrics)
    else:
      self.__spinnaker_deployer = self
    self.__options = options
    self.__metrics = metrics
    self.__hal_user = options.deploy_hal_user
    logging.info('hal_user="%s"', self.__hal_user)

  def make_port_forward_command(self, service, local_port, remote_port):
    """Return the command used to forward ports to the given service.

    Returns:
      array of commandline arguments to create a subprocess with.
    """
    return self.__spinnaker_deployer.do_make_port_forward_command(
        service, local_port, remote_port)

  def deploy(self, init_script, config_script, files_to_upload):
    """Deploy and configure spinnaker.

    The deployment configuration is specified via the bound options.
    The runtime configuration is passed to the call.

    Args:
      init_script: [list] The sequence of bash commands to run in order
         to prepare the host before installing halyard and configuring.
      config_script: [list] The sequence of bash commands to run in order
         to configure spinnaker.
      file_to_upload: [set] A set of file paths to upload to the deployed
         instance before running the init_script. Presumably these will
         be referenced by the init_script or config_script.
    """
    deploy_labels = {}
    self.__metrics.track_and_time_call(
        'DeploySpinnaker',
        deploy_labels, self.__metrics.default_determine_outcome_labels,
        self.__wrapped_deploy, init_script, config_script, files_to_upload)

  def __wrapped_deploy(self, init_script, config_script, files_to_upload):
    platform = self.options.deploy_hal_platform
    logging.info('Deploying with hal on %s...', platform)
    script = list(init_script)
    self.add_install_hal_script_statements(script)
    if self.options.halyard_config_bucket_credentials:
      files_to_upload.add(self.options.halyard_config_bucket_credentials)
      self.add_inject_halyard_application_default_credentials(
          self.options.halyard_config_bucket_credentials, script)

    self.add_platform_deploy_script_statements(script)
    # Add the version first to avoid warnings or facilitate checks
    # with the configuration commands
    script.append('hal -q --log=info config version edit'
                  ' --version {version}'
                  .format(version=self.options.deploy_version))

    script.extend(config_script)
    self.add_hal_deploy_script_statements(script)

    # Dump the hal config so we log it for posterity
    script.append('hal -q --log=info config')

    script.append('sudo hal -q --log=info deploy apply')
    self.add_post_deploy_statements(script)

    if not self.options.deploy_deploy:
      logging.warning('Skipping deployment because --deploy_deploy=false\n')
      return
    self.do_deploy(script, files_to_upload)
    logging.info('Finished deploying to %s', platform)

  def undeploy(self):
    undeploy_labels = {}
    self.__metrics.track_and_time_call(
        'UndeploySpinnaker',
        undeploy_labels, self.__metrics.default_determine_outcome_labels,
        self.__wrapped_undeploy)

  def __wrapped_undeploy(self):
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

    def fetch_service_log(service):
      try:
        deployer = (self if service in HALYARD_SERVICES
                    else self.__spinnaker_deployer)
        deployer.do_fetch_service_log_file(service, log_dir)
      except Exception as ex:
        message = 'Error fetching log for service "{service}": {ex}'.format(
            service=service, ex=ex)
        if ex.message.find('No such file') >= 0:
          message += '\n    Perhaps the service never started.'
          # dont log since the error was already captured.
        else:
          logging.error(message)
          message += '\n{trace}'.format(
              trace=traceback.format_exc())

        write_data_to_secure_path(
            message, os.path.join(log_dir, service + '.log'))

    logging.info('Collecting server log files into "%s"', log_dir)
    all_services = replace_ha_services(SPINNAKER_SERVICES, self.options)
    all_services.extend(HALYARD_SERVICES)
    thread_pool = ThreadPool(len(all_services))
    thread_pool.map(fetch_service_log, all_services)
    thread_pool.terminate()

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

  def add_inject_halyard_application_default_credentials(
      self, local_path, script):
    """Inject google application credentials into halyards startup script.
    This is only so we can install halyard against a halyard test repo.
    We're doing this injection because halyard does not explicitly support this
    use case from installation, though does support the use of application
    default credentials.
    """
    script.append('first=$(head -1 /opt/halyard/bin/halyard)')
    script.append(
        'inject="export GOOGLE_APPLICATION_CREDENTIALS={path}"'
        .format(path='$(pwd)/' + os.path.basename(local_path)))
    script.append('remaining=$(tail -n +2 /opt/halyard/bin/halyard)')
    script.append('cat <<EOF | sudo tee /opt/halyard/bin/halyard\n'
                  '$first\n$inject\n$remaining\n'
                  'EOF')
    script.append('sudo chmod 755 /opt/halyard/bin/halyard')

    # Kill running halyard so it restarts with credentials.
    # This method awaiting support in halyard to terminate the job.
    # In the meantime, we'll kill all the java processes. Since this
    # is run on a newly provisioned VM, it should only be halyard.
    script.append('echo "Using nuclear option to stop existing halyard"')
    script.append('killall java || true') # hack
    script.append('echo "Restarting halyard..."')
    script.append('sudo su -c "hal -v" -s /bin/bash {user}'
                  .format(user=self.options.deploy_hal_user))
    script.append('for i in `seq 1 30`; do'
                  ' if hal --ready &> /dev/null; then break; fi;'
                  ' sleep 1; done')

  def add_install_hal_script_statements(self, script):
    """Adds the sequence of Bash statements to fetch and install halyard."""
    options = self.options
    script.append('curl -s -O {url}'.format(url=options.halyard_install_script))
    install_params = ['-y']
    if options.halyard_config_bucket:
      install_params.extend(['--config-bucket', options.halyard_config_bucket])
    if options.halyard_bucket_base_url:
      install_params.extend(['--halyard-bucket-base-url',
                             options.halyard_bucket_base_url])
    if options.halyard_version:
      install_params.extend(['--version', options.halyard_version])
    if self.hal_user:
      install_params.extend(['--user', self.hal_user])
    if options.spinnaker_repository:
      install_params.extend(
          ['--spinnaker-repository', options.spinnaker_repository])
    if options.spinnaker_registry:
      install_params.extend(
          ['--spinnaker-registry', options.spinnaker_registry])

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
      if options.deploy_distributed_platform == 'kubernetes_v2':
        script.append('hal -q --log=info config deploy edit --location {namespace}'
            .format(namespace=self.options.deploy_k8s_v2_namespace))

    script.append('hal -q --log=info config deploy edit {args}'
                  .format(args=' '.join(type_args)))

  def add_post_deploy_statements(self, script):
    """Add any statements following "hal deploy apply"."""
    pass


class KubernetesValidateBomDeployer(BaseValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Google Cloud Platform.

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """
  def __init__(self, options, metrics, **kwargs):
    super(KubernetesValidateBomDeployer, self).__init__(
        options, metrics, **kwargs)

  @classmethod
  def init_platform_argument_parser(cls, parser, defaults):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for the free function init_argument_parser().
    """
    add_parser_argument(
        parser, 'deploy_k8s_namespace', defaults, 'spinnaker',
        help='Namespace for the account Spinnaker is deployed into.')

  @classmethod
  def validate_options_helper(cls, options):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for make_deployer().
    """
    if options.deploy_distributed_platform != 'kubernetes':
      return

    if not options.k8s_account_name:
      raise_and_log_error(
          ConfigError('--deploy_distributed_platform="kubernetes" requires'
                      ' a --k8s_account_name be configured.'))

    if hasattr(options, "injected_deploy_spinnaker_account"):
      raise_and_log_error(
          UnexpectedError('deploy_spinnaker_account was already set to "{0}"'
                          .format(options.injected_deploy_spinnaker_account)))
    options.injected_deploy_spinnaker_account = options.k8s_account_name

  def __get_pod_name(self, k8s_namespace, service):
    """Determine the pod name for the deployed service."""
    options = self.options
    flags = ' --namespace {namespace} --logtostderr=false'.format(
        namespace=k8s_namespace)
    kubectl_command = 'kubectl {context} get pods {flags}'.format(
        context=('--context {0}'.format(options.k8s_account_context)
                 if options.k8s_account_context
                 else ''),
        flags=flags)

    retcode, stdout = run_subprocess(
        '{command}'
        ' | gawk -F "[[:space:]]+" "/{service}-v/ {{print \\$1}}"'
        ' | tail -1'.format(
            command=kubectl_command, service=service),
        shell=True)
    pod = stdout.strip()
    if not pod:
      message = 'There is no pod for "{service}" in {namespace}'.format(
          service=service, namespace=k8s_namespace)
      raise_and_log_error(ConfigError(message, cause='NoPod'))

    if retcode != 0:
      message = 'Could not find pod for "{service}".: {error}'.format(
          service=service,
          error=stdout.strip())
      raise_and_log_error(ExecutionError(message, program='kubectl'))
    else:
      logging.debug('pod "%s" -> %s', service, stdout)

    return stdout.strip()

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
    """Retrieve log file for the given service's pod.

    Args:
      service: [string] The service's log to get
      log_dir: [string] The directory name to write the logs into.
    """
    if service == 'monitoring':
      # monitoring is in a sidecar of each service
      return

    options = self.options
    k8s_namespace = options.deploy_k8s_namespace
    service_pod = self.__get_pod_name(k8s_namespace, service)

    containers = ['spin-' + service]
    if options.monitoring_install_which:
      containers.append('spin-monitoring-daemon')

    for container in containers:
      if container == 'spin-monitoring-daemon':
        path = os.path.join(log_dir, service + '_monitoring.log')
      else:
        path = os.path.join(log_dir, service + '.log')
      retcode, stdout = run_subprocess(
          'kubectl -n {namespace} -c {container} {context} logs {pod}'
          .format(namespace=k8s_namespace,
                  container=container,
                  context=('--context {0}'.format(options.k8s_account_context)
                           if options.k8s_account_context
                           else ''),
                  pod=service_pod),
          shell=True)
      write_data_to_secure_path(stdout, path)


class KubernetesV2ValidateBomDeployer(BaseValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Google Cloud Platform.

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """
  def __init__(self, options, metrics, **kwargs):
    super(KubernetesV2ValidateBomDeployer, self).__init__(
        options, metrics, **kwargs)

  @classmethod
  def init_platform_argument_parser(cls, parser, defaults):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for the free function init_argument_parser().
    """
    add_parser_argument(
        parser, 'deploy_k8s_v2_namespace', defaults, 'spinnaker',
        help='Namespace for the account Spinnaker is deployed into.')

  @classmethod
  def validate_options_helper(cls, options):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for make_deployer().
    """
    if options.deploy_distributed_platform != 'kubernetes_v2':
      return

    if not options.k8s_v2_account_name:
      raise_and_log_error(
          ConfigError('--deploy_distributed_platform="kubernetes_v2" requires'
                      ' a --k8s_v2_account_name be configured.'))

    if hasattr(options, "injected_deploy_spinnaker_account"):
      raise_and_log_error(
          UnexpectedError('deploy_spinnaker_account was already set to "{0}"'
                          .format(options.injected_deploy_spinnaker_account)))
    options.injected_deploy_spinnaker_account = options.k8s_v2_account_name

  def __get_pod_name(self, k8s_v2_namespace, service):
    """Determine the pod name for the deployed service."""
    options = self.options
    flags = ' --namespace {namespace} --logtostderr=false'.format(
        namespace=k8s_v2_namespace)
    kubectl_command = 'kubectl {context} get pods {flags}'.format(
        context=('--context {0}'.format(options.k8s_v2_account_context)
                 if options.k8s_v2_account_context
                 else ''),
        flags=flags)

    retcode, stdout = run_subprocess(
        '{command}'
        ' | gawk -F "[[:space:]]+" "/{service}/ {{print \\$1}}"'
        ' | tail -1'.format(
            command=kubectl_command, service=service),
        shell=True)
    pod = stdout.strip()
    if not pod:
      message = 'There is no pod for "{service}" in {namespace}'.format(
          service=service, namespace=k8s_v2_namespace)
      raise_and_log_error(ConfigError(message, cause='NoPod'))

    if retcode != 0:
      message = 'Could not find pod for "{service}".: {error}'.format(
          service=service,
          error=stdout.strip())
      raise_and_log_error(ExecutionError(message, program='kubectl'))
    else:
      logging.debug('pod "%s" -> %s', service, stdout)

    return stdout.strip()

  def do_make_port_forward_command(self, service, local_port, remote_port):
    """Implements interface."""
    options = self.options
    k8s_v2_namespace = options.deploy_k8s_v2_namespace
    service_pod = self.__get_pod_name(k8s_v2_namespace, service)

    return [
        'kubectl', '--namespace', k8s_v2_namespace,
        'port-forward', service_pod,
        '{local}:{remote}'.format(local=local_port, remote=remote_port)
    ]

  def do_deploy(self, script, files_to_upload):
    """Implements the BaseBomValidateDeployer interface."""
    # This is not yet supported in this script.
    # To deploy spinnaker to kubernetes, you need to go through
    # a halyard VM deployment. Halyard itself can be deployed to K8s.
    # This script doesnt.
    super(KubernetesV2ValidateBomDeployer, self).do_deploy(
        script, files_to_upload)

  def do_undeploy(self):
    """Implements the BaseBomValidateDeployer interface."""
    super(KubernetesV2ValidateBomDeployer, self).do_undeploy()
    # kubectl delete namespace spinnaker

  def do_fetch_service_log_file(self, service, log_dir):
    """Retrieve log file for the given service's pod.

    Args:
      service: [string] The service's log to get
      log_dir: [string] The directory name to write the logs into.
    """
    if service == 'monitoring':
      # monitoring is in a sidecar of each service
      return

    options = self.options
    k8s_v2_namespace = options.deploy_k8s_v2_namespace
    service_pod = self.__get_pod_name(k8s_v2_namespace, service)

    containers = [service]
    if options.monitoring_install_which:
      containers.append('monitoring-daemon')

    for container in containers:
      if container == 'monitoring-daemon':
        path = os.path.join(log_dir, service + '_monitoring.log')
      else:
        path = os.path.join(log_dir, service + '.log')
      retcode, stdout = run_subprocess(
          'kubectl -n {namespace} -c {container} {context} logs {pod}'
          .format(namespace=k8s_v2_namespace,
                  container=container,
                  context=('--context {0}'.format(options.k8s_v2_account_context)
                           if options.k8s_v2_account_context
                           else ''),
                  pod=service_pod),
          shell=True)
      write_data_to_secure_path(stdout, path)


class GenericVmValidateBomDeployer(BaseValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Generic VM

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """

  @property
  def instance_ip(self):
    """The underlying IP address for the deployed instance."""
    if not self.__instance_ip:
      self.__instance_ip = self.do_determine_instance_ip()
    return self.__instance_ip

  def set_instance_ip(self, value):
    """Sets the underlying IP address for the deployed instance."""
    self.__instance_ip = value

  @property
  def ssh_key_path(self):
    """Returns the path to the ssh key for the deployment VM."""
    return self.__ssh_key_path

  @ssh_key_path.setter
  def ssh_key_path(self, path):
    """Sets the path to the ssh key to use."""
    self.__ssh_key_path = path

  def __init__(self, options, metrics, **kwargs):
    super(GenericVmValidateBomDeployer, self).__init__(
        options, metrics, **kwargs)
    self.__instance_ip = None
    self.__ssh_key_path = os.path.join(os.environ['HOME'], '.ssh',
                                       '{0}_empty_key'.format(self.hal_user))

  def do_make_port_forward_command(self, service, local_port, remote_port):
    """Implements interface."""
    return [
        'ssh', '-i', self.__ssh_key_path,
        '-o', 'StrictHostKeyChecking=no',
        '-o', 'UserKnownHostsFile=/dev/null',
        '{user}@{ip}'.format(user=self.hal_user, ip=self.instance_ip),
        '-L', '{local_port}:localhost:{remote_port}'.format(
            local_port=local_port, remote_port=remote_port),
        '-N']

  def do_determine_instance_ip(self):
    """Hook for determining the ip address of the hal instance."""
    raise NotImplementedError(self.__class__.__name__)

  def do_create_vm(self, options):
    """Hook for concrete deployer to craete the VM."""
    raise NotImplementedError(self.__class__.__name__)

  def __upload_files_helper(self, files_to_upload):
    copy_files = (
        'scp'
        ' -i {ssh_key_path}'
        ' -o StrictHostKeyChecking=no'
        ' -o UserKnownHostsFile=/dev/null'
        ' {files}'
        ' {user}@{ip}:~'
        .format(ssh_key_path=self.__ssh_key_path,
                files=' '.join(files_to_upload),
                user=self.hal_user,
                ip=self.instance_ip))
    logging.info('Copying deployment and configuration files')

    # pylint: disable=unused-variable
    for retry in range(0, 10):
      returncode, _ = run_subprocess(copy_files)
      if returncode == 0:
        break
      time.sleep(2)

    if returncode != 0:
      check_subprocess(copy_files)

  def __wait_for_ssh_helper(self):
    logging.info('Waiting for ssh %s@%s...', self.hal_user, self.instance_ip)
    end_time = time.time() + 30
    while time.time() < end_time:
      retcode, _ = run_subprocess(
          'ssh'
          ' -i {ssh_key}'
          ' -o StrictHostKeyChecking=no'
          ' -o UserKnownHostsFile=/dev/null'
          ' {user}@{ip}'
          ' "exit 0"'
          .format(user=self.hal_user,
                  ip=self.instance_ip,
                  ssh_key=self.__ssh_key_path))
      if retcode == 0:
        logging.info('%s is ready', self.instance_ip)
        break
      time.sleep(1)

  def attempt_install(self, script_path, retry):
    """Attempt to the install script on the remote instance.

    Bintray is flaky making this not uncommon to fail intermittently.
    Therefore, it is intended that this function may be called multiple
    times on the same instance.
    """
    attempt_decorator = '+%d' % retry if retry > 0 else ''
    logging.info('Configuring deployment%s',
                 ' retry=%d' % retry if retry else '')
    logfile = os.path.join(
        self.options.output_dir,
        'install_spinnaker-%d%s.log' % (os.getpid(), attempt_decorator))
    try:
      command = (
          'ssh'
          ' -i {ssh_key}'
          ' -o StrictHostKeyChecking=no'
          ' -o UserKnownHostsFile=/dev/null'
          ' {user}@{ip}'
          ' bash -l -c ./{script_name}'
          .format(user=self.hal_user,
                  ip=self.instance_ip,
                  ssh_key=self.__ssh_key_path,
                  script_name=os.path.basename(script_path)))
      check_subprocesses_to_logfile('install spinnaker', logfile, [command])
    except ExecutionError as error:
      scan_logs_for_install_errors(logfile)
      return ExecutionError('Halyard deployment failed: %s' % error.message,
                            program='install')
    except Exception as ex:
      return UnexpectedError(ex.message)

    return None

  def do_deploy(self, script, files_to_upload):
    """Implements the BaseBomValidateDeployer interface."""
    options = self.options
    ensure_empty_ssh_key(self.__ssh_key_path, self.hal_user)

    script_parts = []
    for path in files_to_upload:
      filename = os.path.basename(path)
      script_parts.append('sudo chmod 600 {file}'.format(file=filename))
      script_parts.append('sudo chown {user}:{user} {file}'
                          .format(user=self.hal_user, file=filename))

    script_parts.extend(script)
    script_path = write_script_to_path(script_parts, path=None)
    files_to_upload.add(script_path)

    try:
      self.do_create_vm(options)
      self.__upload_files_helper(files_to_upload)
      self.__wait_for_ssh_helper()
    except Exception as ex:
      raise_and_log_error(
          ExecutionError('Caught %s provisioning vm' % ex.message,
                         program='provisionVm'))
    finally:
      shutil.copyfile(script_path,
                      os.path.join(options.output_dir, 'install-script.sh'))
      os.remove(script_path)
      files_to_upload.remove(script_path)  # in case we need to retry

    error = None
    max_retries = 10
    install_labels = {}
    for retry in range(0, max_retries):
      error = self.metrics.track_and_time_call(
          'InstallSpinnaker',
          install_labels,
          self.metrics.determine_outcome_labels_from_error_result,
          self.attempt_install, script_path, retry)
      if not error:
        break

      logging.warning('Encountered an error during install: %s', error.message)
      if retry < (max_retries - 1):
        # Re-upload the files because script may have moved them around
        # so re-running the script wont find them anymore.
        self.__upload_files_helper(files_to_upload)
        logging.debug('Re-uploading install files...')

        # Clear halyard history
        clear_halyard_command = (
          'ssh'
          ' -i {ssh_key}'
          ' -o StrictHostKeyChecking=no'
          ' -o UserKnownHostsFile=/dev/null'
          ' {user}@{ip}'
          ' "hal deploy clean || true; echo "Y" | sudo ~/.hal/uninstall.sh || true;"'
          .format(user=self.hal_user,
                  ip=self.instance_ip,
                  ssh_key=self.__ssh_key_path))
        run_subprocess(clear_halyard_command)

        logging.debug('Waiting a minute before retrying...')
        time.sleep(60)

    if error:
      raise_and_log_error(error)

  def do_fetch_service_log_file(self, service, log_dir):
    """Implements the BaseBomValidateDeployer interface."""
    write_data_to_secure_path('', os.path.join(log_dir, service + '.log'))
    retcode, stdout = run_subprocess(
        'ssh'
        ' -i {ssh_key}'
        ' -o StrictHostKeyChecking=no'
        ' -o UserKnownHostsFile=/dev/null'
        ' {user}@{ip}'
        ' "if [[ -f /var/log/spinnaker/{service_dir}/{service_name}.log ]];'
        '  then cat /var/log/spinnaker/{service_dir}/{service_name}.log;'
        '  else command -v journalctl >/dev/null && journalctl -u {service_name}; fi"'
        .format(user=self.hal_user,
                ip=self.instance_ip,
                ssh_key=self.ssh_key_path,
                service_dir=service,
                service_name=service))
    if retcode != 0:
      logging.warning('Failed obtaining %s.log: %s', service, stdout)
    write_to_path(stdout, os.path.join(log_dir, service + '.log'))


class AwsValidateBomDeployer(GenericVmValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Amazon EC2

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """

  @classmethod
  def init_platform_argument_parser(cls, parser, defaults):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for the free function init_argument_parser().
    """
    add_parser_argument(
        parser, 'deploy_aws_name', defaults, None,
        help='Value for name to tag instance with.')
    add_parser_argument(
        parser, 'deploy_aws_pem_path', defaults, None,
        help='Path to the EC2 PEM file.')
    add_parser_argument(
        parser, 'deploy_aws_security_group', defaults, None,
        help='Name of EC2 security group.')

    # Make this instead default to a search for the current image.
    # https://cloud-images.ubuntu.com/locator/ec2/
    add_parser_argument(
        # 14.04 east-1 hvm:ebs
        parser, 'deploy_aws_ami', defaults, 'ami-0b542c1d',
        help='Image ID to run.')
    add_parser_argument(
        parser, 'deploy_aws_region', defaults, 'us-east-1',
        help='Region to deploy aws instance into.'
             ' Need an aws profile with this name')

  @classmethod
  def validate_options_helper(cls, options):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for make_deployer().
    """
    if not options.deploy_aws_name:
      return
    if not options.deploy_aws_pem_path:
      raise_and_log_error(ConfigError('--deploy_aws_pem_path not specified.'))
    if not os.path.exists(options.deploy_aws_pem_path):
      raise_and_log_error(
          ConfigError('File "{path}" does not exist.'
                      .format(path=options.deploy_aws_pem_path)))
    if not options.deploy_aws_security_group:
      raise_and_log_error(
          ConfigError('--deploy_aws_security_group not specified.'))

    if options.deploy_deploy:
      retcode, stdout = run_subprocess(
          'aws ec2 describe-instances'
          ' --profile {region}'
          ' --filters "Name=tag:Name,Values={name}'
          ',Name=instance-state-name,Values=running"'
          .format(region=options.deploy_aws_region,
                  name=options.deploy_aws_name))
      if retcode != 0:
        raise_and_log_error(
            ExecutionError('Could not probe AWS: {0}'.format(stdout),
                           program='aws'))
      reservations = json.JSONDecoder().decode(stdout).get('Reservations')
      # For some reason aws is ignoring our filter, so check again just to be
      # sure the reservations returned are the ones we asked for.
      for reservation in reservations or []:
        for tags in reservation.get('Tags', []):
          if (tags.get('Key') == 'Name'
              and tags.get('Value') == options.deploy_aws_name):
            raise_and_log_error(
                ConfigError(
                    'Running "{name}" already exists: {info}'
                    .format(name=options.deploy_aws_name, info=reservation),
                    cause='VmExists'))
        logging.warning('aws returned another instance - ignore: %s',
                        reservation)

  def __init__(self, options, metrics, **kwargs):
    super(AwsValidateBomDeployer, self).__init__(options, metrics, **kwargs)
    self.__instance_id = None
    self.ssh_key_path = options.deploy_aws_pem_path

  def do_determine_instance_ip(self):
    """Implements GenericVmValidateBomDeployer interface."""
    options = self.options
    retcode, stdout = run_subprocess(
        'aws ec2 describe-instances'
        ' --profile {region}'
        ' --output json'
        ' --filters "Name=tag:Name,Values={name}'
        ',Name=instance-state-name,Values=running"'
        .format(region=options.deploy_aws_region,
                name=options.deploy_aws_name))
    if retcode != 0:
      raise_and_log_error(
          ExecutionError('Could not determine public IP: {0}'.format(stdout),
                         program='aws'))
    found = json.JSONDecoder().decode(stdout).get('Reservations')
    if not found:
      raise_and_log_error(
          ResponseError(
              '"{0}" is not running'.format(options.deploy_aws_name),
              server='ec2'))

    return found[0]['Instances'][0]['PublicIpAddress']

  def do_create_vm(self, options):
    """Implements GenericVmValidateBomDeployer interface."""
    pem_basename = os.path.basename(options.deploy_aws_pem_path)
    key_pair_name = os.path.splitext(pem_basename)[0]
    logging.info('Creating "%s" with key-pair "%s"',
                 options.deploy_aws_name, key_pair_name)

    response = check_subprocess(
        'aws ec2 run-instances'
        ' --profile {region}'
        ' --output json'
        ' --count 1'
        ' --image-id {ami}'
        ' --instance-type {type}'
        ' --key-name {key_pair_name}'
        ' --security-group-ids {sg}'
        .format(region=options.deploy_aws_region,
                ami=options.deploy_aws_ami,
                type='t2.xlarge',  # 4 core x 16G
                key_pair_name=key_pair_name,
                sg=options.deploy_aws_security_group))
    doc = json.JSONDecoder().decode(response)
    self.__instance_id = doc["Instances"][0]["InstanceId"]
    logging.info('Created instance id=%s to tag as "%s"',
                 self.__instance_id, options.deploy_aws_name)

    # It's slow to start up and sometimes there is a race condition
    # in which describe-instances doesnt know about our id even though
    # create-tags did, or create-tags doesnt know abut the new id.
    time.sleep(5)
    end_time = time.time() + 10*60
    did_tag = False
    while time.time() < end_time:
      if not did_tag:
        tag_retcode, _ = run_subprocess(
            'aws ec2 create-tags'
            ' --region {region}'
            ' --resources {instance_id}'
            ' --tags "Key=Name,Value={name}"'
            .format(region=options.deploy_aws_region,
                    instance_id=self.__instance_id,
                    name=options.deploy_aws_name))
        did_tag = tag_retcode == 0
      if self.__is_ready():
        return
      time.sleep(5)
    raise_and_log_error(
        TimeoutError('Giving up waiting for deployment.', cause='ec2'))

  def __is_ready(self):
    retcode, stdout = run_subprocess(
        'aws ec2 describe-instances'
        ' --profile {region}'
        ' --output json'
        ' --instance-ids {id}'
        ' --query "Reservations[*].Instances[*]"'
        .format(region=self.options.deploy_aws_region,
                id=self.__instance_id))
    if retcode != 0:
      logging.warning('Could not determine public IP: %s', stdout)
      return False

    # result is an array of reservations of ararys of instances.
    # but we only expect one, so fish out the first instance info
    info = json.JSONDecoder().decode(stdout)[0][0]
    state = info.get('State', {}).get('Name')
    if state in ['pending', 'initializing']:
      logging.info('Waiting for %s to finish initializing (state=%s)',
                   self.__instance_id, state)
      return False

    if state in ['shutting-down', 'terminated']:
      raise_and_log_error(ResponseError('VM failed: {0}'.format(info),
                                        server='ec2'))

    logging.info('%s is in state %s', self.__instance_id, state)
    self.set_instance_ip(info.get('PublicIpAddress'))
    # attempt to ssh into it so we know we're accepting connections when
    # we return. It takes time to start
    logging.info('Checking if it is ready for ssh...')
    retcode, stdout = run_subprocess(
        'ssh'
        ' -i {ssh_key}'
        ' -o StrictHostKeyChecking=no'
        ' -o UserKnownHostsFile=/dev/null'
        ' {user}@{ip}'
        ' "exit 0"'
        .format(user=self.hal_user,
                ip=self.instance_ip,
                ssh_key=self.ssh_key_path))
    if retcode == 0:
      logging.info('%s is ready', self.instance_ip)
      return True

    # Sometimes ssh accepts but authentication still fails
    # for a while. If this is the case, then try again
    # though the whole loop to distinguish VM going away.
    logging.info('%s\nNot yet ready...', stdout.strip())
    return False

  def do_undeploy(self):
    """Implements the BaseBomValidateDeployer interface."""
    options = self.options
    logging.info('Terminating "%s"', options.deploy_aws_name)

    if self.__instance_id:
      all_ids = [self.__instance_id]
    else:
      lookup_response = check_subprocess(
          'aws ec2 describe-instances'
          ' --profile {region}'
          ' --filters "Name=tag:Name,Values={name}'
          ',Name=instance-state-name,Values=running"'
          .format(region=options.deploy_aws_region,
                  name=options.deploy_aws_name))
      exists = json.JSONDecoder().decode(lookup_response).get('Reservations')
      if not exists:
        logging.warning('"%s" is not running', options.deploy_aws_name)
        return
      all_ids = []
      for reservation in exists:
        all_ids.extend([instance['InstanceId']
                        for instance in reservation['Instances']])

    for instance_id in all_ids:
      logging.info('Terminating "%s" instanceId=%s',
                   options.deploy_aws_name, instance_id)
      retcode, _ = run_subprocess(
          'aws ec2 terminate-instances'
          '  --profile {region}'
          '  --instance-ids {id}'
          .format(region=options.deploy_aws_region, id=instance_id))
      if retcode != 0:
        logging.warning('Failed to delete "%s" instanceId=%s',
                        options.deploy_aws_name, instance_id)


class AzureValidateBomDeployer(GenericVmValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Microsoft Azure

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """

  @classmethod
  def init_platform_argument_parser(cls, parser, defaults):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for the free function init_argument_parser().
    """
    add_parser_argument(
        parser, 'deploy_azure_location', defaults, 'eastus',
        help='Azure region to deploy to if --deploy_hal_platform is "azure".')
    add_parser_argument(
        parser, 'deploy_azure_resource_group', defaults, None,
        help='Azure resource group to deploy to'
             ' if --deploy_hal_platform is "azure".')
    add_parser_argument(
        parser, 'deploy_azure_name', defaults, None,
        help='Azure VM name to deploy to if --deploy_hal_platform is "azure".')
    add_parser_argument(
        parser, 'deploy_azure_image',
        defaults, 'Canonical:UbuntuServer:14.04.5-LTS:latest',
        help='Azure image to deploy.')

  @classmethod
  def validate_options_helper(cls, options):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for make_deployer().
    """
    if not options.deploy_azure_resource_group:
      raise_and_log_error(
          ConfigError('--deploy_azure_resource_group not specified.'))
    if not options.deploy_azure_name:
      raise_and_log_error(
          ConfigError('--deploy_azure_name not specified.'))

    if options.deploy_deploy:
      retcode, _ = run_subprocess(
          'az vm show --resource-group {rg} --vm-name {name}'
          .format(rg=options.deploy_azure_resource_group,
                  name=options.deploy_azure_name))

      if retcode == 0:
        raise_and_log_error(UnexpectedError(
            '"{name}" already exists in resource-group={rg}'
            .format(name=options.deploy_azure_name,
                    rg=options.deploy_azure_resource_group)))

  def do_create_vm(self, options):
    """Implements GenericVmValidateBomDeployer interface."""
    logging.info('Creating "%s" in resource-group "%s"',
                 options.deploy_azure_name,
                 options.deploy_azure_resource_group)

    response = check_subprocess(
        'az vm create'
        ' --name {name}'
        ' --resource-group {rg}'
        ' --location {location}'
        ' --image {image}'
        ' --use-unmanaged-disk'
        ' --storage-sku Standard_LRS'
        ' --size Standard_D12_v2_Promo'
        ' --ssh-key-value {ssh_key_path}.pub'
        .format(name=options.deploy_azure_name,
                rg=options.deploy_azure_resource_group,
                location=options.deploy_azure_location,
                image=options.deploy_azure_image,
                ssh_key_path=self.ssh_key_path))
    self.set_instance_ip(json.JSONDecoder().decode(
        response)['publicIpAddress'])

  def do_undeploy(self):
    """Implements the BaseBomValidateDeployer interface."""
    options = self.options
    if options.deploy_spinnaker_type == 'distributed':
      run_subprocess(
          'ssh'
          ' -i {ssh_key}'
          ' -o StrictHostKeyChecking=no'
          ' -o UserKnownHostsFile=/dev/null'
          ' {user}@{ip} sudo hal -q --log=info deploy clean'
          .format(user=self.hal_user,
                  ip=self.instance_ip,
                  ssh_key=self.ssh_key_path))
    check_subprocess(
        'az vm delete -y'
        ' --name {name}'
        ' --resource-group {rg}'
        .format(name=options.deploy_azure_name,
                rg=options.deploy_azure_resource_group))

  def do_determine_instance_ip(self):
    """Implements GenericVmValidateBomDeployer interface."""
    options = self.options
    retcode, stdout = run_subprocess(
        'az vm list-ip-addresses --name {name} --resource-group {group}'.format(
            name=options.deploy_azure_name,
            group=options.deploy_azure_resource_group))
    if retcode != 0:
      raise_and_log_error(
          ExecutionError('Could not determine public IP: {0}'.format(stdout),
                         program='az'))
    found = json.JSONDecoder().decode(stdout)[0].get('virtualMachine')
    if not found:
      raise_and_log_error(
          ResponseError(
              '"{0}" is not running'.format(options.deploy_azure_name),
              server='az'))
    return found['network']['publicIpAddresses'][0]['ipAddress']


class GoogleValidateBomDeployer(GenericVmValidateBomDeployer):
  """Concrete deployer used to deploy Hal onto Google Cloud Platform.

  This class is not intended to be constructed directly. Instead see the
  free function make_deployer() in this module.
  """

  def do_determine_instance_ip(self):
    """Implements GenericVmValidateBomDeployer interface."""
    options = self.options

    # Note: this used to dup_stderr_to_stdout=False with an older API
    # presumably this wont return stderr anymore or it will corrupt the json.
    response = check_subprocess(
        'gcloud compute instances describe'
        ' --format json'
        ' --account {gcloud_account}'
        ' --project {project} --zone {zone} {instance}'
        .format(gcloud_account=options.deploy_hal_google_service_account,
                project=options.deploy_google_project,
                zone=options.deploy_google_zone,
                instance=options.deploy_google_instance))
    nic = json.JSONDecoder().decode(response)['networkInterfaces'][0]

    use_internal_ip = options.deploy_google_use_internal_ip
    if use_internal_ip:
      return nic['networkIP']
    return nic['accessConfigs'][0]['natIP']

  def __init__(self, options, metrics, **kwargs):
    super(GoogleValidateBomDeployer, self).__init__(options, metrics, **kwargs)

  @classmethod
  def init_platform_argument_parser(cls, parser, defaults):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for the free function init_argument_parser().
    """
    add_parser_argument(
        parser, 'deploy_google_project', defaults, None,
        help='Google project to deploy to if --deploy_hal_platform is "gce".')
    add_parser_argument(
        parser, 'deploy_google_zone', defaults, 'us-central1-f',
        help='Google zone to deploy to if --deploy_hal_platform is "gce".')
    add_parser_argument(
        parser, 'deploy_google_instance', defaults, None,
        help='Google instance to deploy to if --deploy_hal_platform is "gce".')
    add_parser_argument(
        parser, 'deploy_google_machine_type', defaults, 'n1-standard-4',
        help='Google machine type if --deploy_hal_platform is "gce".')
    add_parser_argument(
        parser, 'deploy_google_image_family', defaults, 'ubuntu-1404-lts',
        help='Google image family to deploy if --deploy_hal_platform is "gce".')
    add_parser_argument(
        parser, 'deploy_google_image_project', defaults, 'ubuntu-os-cloud',
        help='Project containing image from --deploy_google_image_family.')
    add_parser_argument(
        parser, 'deploy_google_network', defaults, 'default',
        help='The GCP Network to deploy spinnaker into.')

    add_parser_argument(
        parser, 'deploy_google_use_internal_ip', defaults, True, type=bool,
        help='Force the internal IP to connect to the deployed instance.'
        ' This is only valid when talking within the same project.')
    parser.add_argument(
        '--deploy_google_use_external_ip',
        dest='deploy_google_use_internal_ip', action='store_false',
        help='DEPRECATED: Use --deploy_google_use_internal_ip=false')

    add_parser_argument(
        parser, 'deploy_google_tags',
        defaults, 'spinnaker-validation-instance',
        help='A comma-delimited list of GCP network tags to tag'
             ' the deployed instances with.')

    add_parser_argument(
        parser, 'deploy_hal_google_service_account', defaults, None,
        help='When deploying to gce, this is the service account to use'
             ' for configuring halyard.')

  @classmethod
  def validate_options_helper(cls, options):
    """Adds custom configuration parameters to argument parser.

    This is a helper function for make_deployer().
    """
    if not options.deploy_google_project:
      raise_and_log_error(
          ConfigError('--deploy_google_project not specified.'))
    if not options.deploy_google_instance:
      raise_and_log_error(
          ConfigError('--deploy_google_instance not specified.'))
    if not options.deploy_hal_google_service_account:
      raise_and_log_error(
          ConfigError('--deploy_hal_google_service_account not specified.'))

    if options.deploy_deploy:
      retcode, _ = run_subprocess(
          'gcloud compute instances describe'
          ' --account {gcloud_account}'
          ' --project {project} --zone {zone} {instance}'
          .format(gcloud_account=options.deploy_hal_google_service_account,
                  project=options.deploy_google_project,
                  zone=options.deploy_google_zone,
                  instance=options.deploy_google_instance))

      if retcode == 0:
        raise_and_log_error(ConfigError(
            '"{instance}" already exists in project={project} zone={zone}'
            .format(instance=options.deploy_google_instance,
                    project=options.deploy_google_project,
                    zone=options.deploy_google_zone),
            cause='VmExists'))

  def do_create_vm(self, options):
    """Implements the BaseBomValidateDeployer interface."""
    logging.info('Creating "%s" in project "%s"',
                 options.deploy_google_instance,
                 options.deploy_google_project)
    with open(self.ssh_key_path + '.pub', 'r') as f:
      ssh_key = f.read().strip()
    if ssh_key.startswith('ssh-rsa'):
      ssh_key = self.hal_user + ':' + ssh_key

    check_subprocess(
        'gcloud compute instances create'
        ' --account {gcloud_account}'
        ' --machine-type {machine_type}'
        ' --image-family {image_family}'
        ' --image-project {image_project}'
        ' --metadata block-project-ssh-keys=TRUE,ssh-keys="{ssh_key}"'
        ' --project {project} --zone {zone}'
        ' --network {network}'
        ' --tags {network_tags}'
        ' --scopes {scopes}'
        ' {instance}'
        .format(gcloud_account=options.deploy_hal_google_service_account,
                machine_type=options.deploy_google_machine_type,
                image_family=options.deploy_google_image_family,
                image_project=options.deploy_google_image_project,
                project=options.deploy_google_project,
                zone=options.deploy_google_zone,
                scopes='compute-rw,storage-full,logging-write,monitoring',
                network=options.deploy_google_network,
                network_tags=options.deploy_google_tags,
                ssh_key=ssh_key,
                instance=options.deploy_google_instance))

  def do_undeploy(self):
    """Implements the BaseBomValidateDeployer interface."""
    options = self.options
    if options.deploy_spinnaker_type == 'distributed':
      run_subprocess(
          'ssh'
          ' -i {ssh_key}'
          ' -o StrictHostKeyChecking=no'
          ' -o UserKnownHostsFile=/dev/null'
          ' {user}@{ip} sudo hal -q --log=info deploy clean'
          .format(user=self.hal_user,
                  ip=self.instance_ip,
                  ssh_key=self.ssh_key_path))

    check_subprocess(
        'gcloud -q compute instances delete'
        ' --account {gcloud_account}'
        ' --project {project} --zone {zone} {instance}'
        .format(gcloud_account=options.deploy_hal_google_service_account,
                project=options.deploy_google_project,
                zone=options.deploy_google_zone,
                instance=options.deploy_google_instance))


def make_deployer(options, metrics):
  """Public interface to instantiate the desired Deployer.

  Args:
    options: [Namespace] from an argument parser given to init_argument_parser
  """
  if options.deploy_hal_platform == 'gce':
    hal_klass = GoogleValidateBomDeployer
  elif options.deploy_hal_platform == 'ec2':
    hal_klass = AwsValidateBomDeployer
  elif options.deploy_hal_platform == 'azure':
    hal_klass = AzureValidateBomDeployer
  else:
    raise_and_log_error(ConfigError(
        'Invalid --deploy_hal_platform=%s' % options.deploy_hal_platform))

  if options.deploy_spinnaker_type not in SUPPORTED_DEPLOYMENT_TYPES:
    raise_and_log_error(ConfigError(
        'Invalid --deploy_spinnaker_type "{0}". Must be one of {1}'
        .format(options.deploy_spinnaker_type, SUPPORTED_DEPLOYMENT_TYPES)))

  # This is the class for accessing the Spinnaker deployment if other than Hal.
  spin_klass = None

  if options.deploy_spinnaker_type == 'distributed':
    if (options.deploy_distributed_platform
        not in SUPPORTED_DISTRIBUTED_PLATFORMS):
      raise_and_log_error(ConfigError(
          'A "distributed" deployment requires --deploy_distributed_platform'))
    if options.deploy_distributed_platform == 'kubernetes':
      spin_klass = KubernetesValidateBomDeployer
    elif options.deploy_distributed_platform == 'kubernetes_v2':
      spin_klass = KubernetesV2ValidateBomDeployer
    else:
      raise_and_log_error(ConfigError(
          'Unknown --deploy_distributed_platform.'
          ' This must be the value of one of the following parameters: {0}'
          .format(SUPPORTED_DISTRIBUTED_PLATFORMS)))

  hal_klass.validate_options_helper(options)
  if spin_klass:
    spin_klass.validate_options_helper(options)

  return hal_klass(options, metrics, runtime_class=spin_klass)


def determine_deployment_platform(options):
  """Helper function to determine the deployment platform being tested.

  This is used for instrumentation purposes.
  """
  platform = options.deploy_hal_platform
  if options.deploy_spinnaker_type == 'distributed':
    if platform == 'gce':
      platform = 'gke'
    else:
      platform += '+k8s'
  return platform


def init_argument_parser(parser, defaults):
  """Initialize the argument parser with deployment and configuration params.

  Args:
    parser: [ArgumentParser] The argument parser to add the parameters to.
  """
  # pylint: disable=line-too-long
  add_parser_argument(
      parser, 'halyard_install_script', defaults,
      'https://raw.githubusercontent.com/spinnaker/halyard/master/install/debian/InstallHalyard.sh',
      help='The URL to the InstallHalyard.sh script.')

  add_parser_argument(
      parser, 'halyard_version', defaults, None,
      help='If provided, the specific version of halyard to use.')

  add_parser_argument(
      parser, 'halyard_bucket_base_url', defaults, None,
      help='The base URL for the bucket containing the halyard jar files'
           ' to override, if any.')

  add_parser_argument(
      parser, 'halyard_config_bucket', defaults, None,
      help='The global halyard configuration bucket to override, if any.')

  add_parser_argument(
      parser, 'halyard_config_bucket_credentials', defaults, None,
      help='If specified, give these credentials to halyard'
           ' in order to access the global halyard GCS bucket.')

  add_parser_argument(
      parser, 'spinnaker_repository',
      defaults, 'https://dl.bintray.com/spinnaker-releases/debians',
      help='The location of the spinnaker debian repository.')

  add_parser_argument(
      parser, 'spinnaker_registry', defaults, 'gcr.io/spinnaker-marketplace',
      help='The location of the spinnaker container registry.')

  add_parser_argument(
      parser, 'deploy_spinnaker_type', defaults, None,
      choices=SUPPORTED_DEPLOYMENT_TYPES,
      help='The type of spinnaker deployment to create.')

  add_parser_argument(
      parser, 'deploy_hal_platform', defaults, None,
      choices=['gce', 'ec2', 'azure'],
      help='Platform to deploy Halyard onto.'
           ' Halyard will then deploy Spinnaker.')

  add_parser_argument(
      parser, 'deploy_hal_user', defaults, os.environ.get('LOGNAME'),
      help='User name on deployed hal_platform for deploying hal.'
           ' This is used to scp and ssh from this machine.')

  add_parser_argument(
      parser, 'deploy_distributed_platform', defaults, 'kubernetes',
      choices=SUPPORTED_DISTRIBUTED_PLATFORMS,
      help='The platform to deploy spinnaker to when'
           ' --deploy_spinnaker_type=distributed')

  add_parser_argument(
      parser, 'deploy_version', defaults, 'master-latest-unvalidated',
      help='Spinnaker version to deploy. The default is "master-latest-unverified".')

  add_parser_argument(
      parser, 'deploy_deploy', defaults, True, type=bool,
      help='Actually perform the deployment.'
           ' This is for facilitating debugging with this script.')

  add_parser_argument(
      parser, 'deploy_undeploy', defaults, True, type=bool,
      help='Actually perform the undeployment.'
           ' This is for facilitating debugging with this script.')

  add_parser_argument(
      parser, 'deploy_always_collect_logs', defaults, False, type=bool,
      help='Always collect logs.'
           'By default logs are only collected when deploy_undeploy is True.')

  AwsValidateBomDeployer.init_platform_argument_parser(parser, defaults)
  AzureValidateBomDeployer.init_platform_argument_parser(parser, defaults)
  GoogleValidateBomDeployer.init_platform_argument_parser(parser, defaults)
  KubernetesValidateBomDeployer.init_platform_argument_parser(parser, defaults)
  KubernetesV2ValidateBomDeployer.init_platform_argument_parser(parser, defaults)
