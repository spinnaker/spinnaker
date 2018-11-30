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

"""Implements apidocs commands for buildtool."""

import copy
import logging
import os
import shutil
import time

try:
  from urllib2 import urlopen
  from urllib2 import URLError
except ImportError:
  from urllib.request import urlopen
  from urllib.error import URLError

from buildtool import (
    SPINNAKER_GITHUB_IO_REPOSITORY_NAME,

    RepositoryCommandFactory,
    RepositoryCommandProcessor,
    CommandFactory,
    CommandProcessor,

    BomSourceCodeManager,
    BranchSourceCodeManager,
    GitRunner,

    check_options_set,
    check_path_exists,
    check_subprocess,
    maybe_log_exception,
    run_subprocess,
    start_subprocess,
    log_timestring,
    wait_subprocess,
    unused_port,
    ensure_dir_exists,
    raise_and_log_error,
    TimeoutError,
    UnexpectedError)


SWAGGER_URL_PATHS = {
    'gate': 'v2/api-docs'
}

BUILD_DOCS_COMMAND = 'build_apidocs'


def make_options_with_fallback(options):
  """A hack for now, using git_fallback_branch to support spinnaker.github.io

  That repo does not use the release branches, rather master.
  So if creating a release, it will fallback to master for that repo.
  """
  options_copy = copy.copy(options)
  options_copy.git_fallback_branch = 'master'
  return options_copy


class BuildApiDocsCommand(RepositoryCommandProcessor):
  """Implements build_api_docs command."""

  def __init__(self, factory, options, **kwargs):
    super(BuildApiDocsCommand, self).__init__(
        factory, options, source_repository_names=['gate'], **kwargs)
    self._check_args()
    self.__templates_directory = None

  def _check_args(self):
    options = self.options
    check_options_set(options, ['swagger_codegen_cli_jar_path'])
    check_path_exists(options.swagger_codegen_cli_jar_path,
                      why='options.swagger_codegen_cli_jar_path')

  def _ensure_templates_directory(self):
    """Initialize the templates_directory, pulling the repo if needed."""
    scm = BranchSourceCodeManager(
        make_options_with_fallback(self.options),
        self.get_input_dir())
    repository = scm.make_repository_spec(SPINNAKER_GITHUB_IO_REPOSITORY_NAME)

    # These documents arent tied to version control, especially since they are
    # published to a different repository.
    scm.ensure_git_path(repository)

    self.__templates_directory = os.path.join(
        repository.git_dir, '_api_templates')

  def _do_command(self):
    """Wraps base method so we can start/stop redis if needed."""
    self._ensure_templates_directory()
    redis_name = 'redis-server'
    start_redis = run_subprocess('sudo service %s start' % redis_name)[0] != 0
    logging.debug('redis-server %s running.',
                  'IS NOT' if start_redis else 'IS')

    try:
      if start_redis:
        check_subprocess('sudo service %s start' % redis_name)
      super(BuildApiDocsCommand, self)._do_command()
    finally:
      if start_redis:
        # pylint: disable=broad-except
        try:
          check_subprocess('sudo service %s stop' % redis_name)
        except Exception as ex:
          maybe_log_exception(
              self.name, ex,
              'Ignoring exception while stopping temporary ' + redis_name)

  def wait_for_url(self, url, timeout_secs):
    """Wait for url to be ready or timeout."""
    logging.info('Waiting for %s', url)
    for _ in range(timeout_secs):
      try:
        code = urlopen(url).getcode()
        if code >= 200 and code < 300:
          logging.info('%s is ready', url)
          return
      except URLError:
        time.sleep(1)

    raise_and_log_error(
        TimeoutError('%s not ready' % url),
        '%s not ready after %s secs' % (url, timeout_secs))

  def build_swagger_docs(self, repository, docs_url):
    """Build the API from the swagger endpoint."""
    if repository.name != 'gate':
      raise_and_log_error(
          UnexpectedError('Repo "%s" != "gate"' % repository.name))

    docs_dir = self.get_output_dir()
    ensure_dir_exists(docs_dir)
    docs_path = os.path.join(docs_dir, 'docs.json')

    logging.info('Generating swagger docs for %s', repository.name)
    check_subprocess('curl -s {url} -o {docs_path}'
                     .format(url=docs_url, docs_path=docs_path))
    check_subprocess(
        'java -jar {jar_path} generate -i {docs_path} -l html2'
        ' -o {output_dir} -t {templates_directory}'
        .format(jar_path=self.options.swagger_codegen_cli_jar_path,
                docs_path=docs_path, output_dir=docs_dir,
                templates_directory=self.__templates_directory))
    logging.info('Writing docs to directory %s', docs_dir)

  def _do_repository(self, repository):
    """Implements CommandProcessor interface."""
    docs_url_path = SWAGGER_URL_PATHS[repository.name]
    env = dict(os.environ)
    port = unused_port()
    env['SERVER_PORT'] = str(port)
    base_url = 'http://localhost:' + str(port)

    gate_logfile = self.get_logfile_path(repository.name + '-apidocs-server')
    logging.info('Starting up prototype %s so we can extract docs from it.'
                 ' We will log this instance to %s',
                 repository.name, gate_logfile)
    boot_run_cmd = './gradlew'  # default will run
    ensure_dir_exists(os.path.dirname(gate_logfile))
    gate_logstream = open(gate_logfile, 'w')
    process = start_subprocess(
        boot_run_cmd, stream=gate_logstream, stdout=gate_logstream,
        cwd=repository.git_dir, env=env)

    max_wait_secs = self.options.max_wait_secs_startup
    # pylint: disable=broad-except
    try:
      logging.info('Waiting up to %s secs for %s to be ready on port %d',
                   max_wait_secs, repository.name, port)
      self.wait_for_url(base_url + '/health', max_wait_secs)
      self.build_swagger_docs(repository, base_url + '/' + docs_url_path)
    finally:
      try:
        gate_logstream.flush()
        gate_logstream.write(
            '\n' + log_timestring()
            + ' ***** buildtool is killing subprocess  *****\n')
        logging.info('Killing %s subprocess %s now that we are done with it',
                     repository.name, process.pid)
        process.kill()
        wait_subprocess(process)
        gate_logstream.close()
      except Exception as ex:
        maybe_log_exception(
            self.name, ex,
            'Ignoring exception while stopping {name} subprocess {pid}.'
            .format(name=repository.name, pid=process.pid))


class BuildApiDocsFactory(RepositoryCommandFactory):
  """Creates instances of BuildApiDocsCommand."""

  def __init__(self):
    super(BuildApiDocsFactory, self).__init__(
        BUILD_DOCS_COMMAND, BuildApiDocsCommand,
        'Build the Spinnaker API REST documentation.',
        BomSourceCodeManager)

  def init_argparser(self, parser, defaults):
    """Implements CommandFactory interface."""
    # For the spinnaker.github.io repository
    BranchSourceCodeManager.add_parser_args(parser, defaults)

    self.add_argument(
        parser, 'max_wait_secs_startup', defaults, 210, type=int,
        help='Number of seconds to wait for gate server to startup'
             ' before giving up and timing out.')
    self.add_argument(
        parser, 'swagger_codegen_cli_jar_path', defaults, None,
        help='The location of the swagger-codegen-cli jarfile.'
        ' The file can be obtained from'
        ' http://central.maven.org/maven2/io/swagger/swagger-codegen-cli'
        '/2.2.3/swagger-codegen-cli-2.2.3.jar')

    super(BuildApiDocsFactory, self).init_argparser(parser, defaults)


class PublishApiDocsCommand(CommandProcessor):
  """Publish built docs back up to the origin repository.

  This uses git_runner to checkout the SPINNAKER_GITHUB_IO_REPOSITORY
  that we'll be publishing docs to. It assumes that the documentation has
  already been built so that it could have been reviewed.
  """

  def __init__(self, factory, options, **kwargs):
    super(PublishApiDocsCommand, self).__init__(
        factory, options, **kwargs)
    docs_dir = self.get_output_dir(command=BUILD_DOCS_COMMAND)
    self.__html_path = os.path.join(os.path.abspath(docs_dir), 'index.html')
    self._check_args()

    self.__scm = BranchSourceCodeManager(
        make_options_with_fallback(self.options),
        self.get_input_dir())

    self.__docs_repository = self.__scm.make_repository_spec(
        SPINNAKER_GITHUB_IO_REPOSITORY_NAME)

  def _check_args(self):
    check_path_exists(self.__html_path,
                      why='output from running "build_apidocs"')
    check_options_set(self.options, ['spinnaker_version'])

  def _do_command(self):
    """Implements CommandProcessor interface."""
    self.__scm.ensure_git_path(self.__docs_repository, branch='master')
    base_branch = 'master'
    version = self.options.spinnaker_version
    if self.options.git_allow_publish_master_branch:
      head_branch = 'master'
      branch_flag = ''
    else:
      head_branch = version + '-apidocs'
      branch_flag = '-b'

    files_added = self._prepare_local_repository_files()
    git_dir = self.__docs_repository.git_dir
    message = 'docs(api): API Documentation for Spinnaker ' + version
    local_git_commands = [
        # These commands are accomodating to a branch already existing
        # because the branch is on the version, not build. A rejected
        # build for some reason that is re-tried will have the same version
        # so the branch may already exist from the earlier attempt.
        'checkout ' + base_branch,
        'checkout {flag} {branch}'.format(
            flag=branch_flag, branch=head_branch),
        'add ' + ' '.join([os.path.abspath(path) for path in files_added]),
    ]
    logging.debug('Commiting changes into local repository "%s" branch=%s',
                  self.__docs_repository.git_dir, head_branch)
    git = self.__scm.git
    git.check_run_sequence(git_dir, local_git_commands)
    git.check_commit_or_no_changes(git_dir, '-m "{msg}"'.format(msg=message))

    logging.info('Pushing branch="%s" into "%s"',
                 head_branch, self.__docs_repository.origin)
    git.push_branch_to_origin(git_dir, branch=head_branch, force=True)

  def _prepare_local_repository_files(self):
    """Implements CommandProcessor interface."""

    # And putting them into the SPINNAKER_GITHUB_IO_REPOSITORY_NAME
    #
    # NOTE(ewiseblatt): 20171218
    # This is the current scheme, however I think this should read
    # os.path.join(git_dir, spinnaker_minor_branch, docs_path_in_repo)
    # where "spinnaker_minor_branch" is the version with the <PATCH>
    # replaced with 'x'
    target_path = os.path.join(
        self.__docs_repository.git_dir, 'reference', 'api', 'docs.html')

    logging.debug('Copying %s to %s', self.__html_path, target_path)
    shutil.copy2(self.__html_path, target_path)

    return [target_path]


class PublishApiDocsFactory(CommandFactory):
  """Factory for PublishApidDocCommand"""

  def __init__(self, **kwargs):
    super(PublishApiDocsFactory, self).__init__(
        'publish_apidocs', PublishApiDocsCommand,
        'Publish Spinnaker REST API documentation to spinnaker.github.io.',
        **kwargs)

  def init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(PublishApiDocsFactory, self).init_argparser(
        parser, defaults)
    GitRunner.add_parser_args(parser, defaults)
    GitRunner.add_publishing_parser_args(parser, defaults)
    self.add_argument(
        parser, 'git_branch', defaults, None,
        help='The branch to checkout in ' + SPINNAKER_GITHUB_IO_REPOSITORY_NAME)
    self.add_argument(
        parser, 'spinnaker_version', defaults, None,
        help='The version of spinnaker this documentation is for.')


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  BuildApiDocsFactory().register(registry, subparsers, defaults)
  PublishApiDocsFactory().register(registry, subparsers, defaults)
