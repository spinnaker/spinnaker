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

"""Implements generate_apidocs command for buildtool."""

import logging
import os
import time
import urllib2

from buildtool.source_commands import FetchSourceCommandFactory

from buildtool.command import (
    RepositoryCommandFactory,
    RepositoryCommandProcessor)

from buildtool.source_code_manager import (
    SPINNAKER_RUNNABLE_REPOSITORIES,
    SPINNAKER_GITHUB_IO_REPOSITORY)

from buildtool.util import (
    check_subprocess,
    ensure_dir_exists,
    maybe_log_exception,
    run_subprocess,
    start_subprocess,
    wait_subprocess,
    unused_port)


SWAGGER_URL_PATHS = {
    'gate': 'v2/api-docs'
}

class GenerateApiDocsCommand(RepositoryCommandProcessor):
  """Implements generate_api_docs command."""
  def __init__(self, factory, options, **kwargs):
    """Implements CommandProcessor interface."""
    super(GenerateApiDocsCommand, self).__init__(factory, options, **kwargs)
    self._check_args()
    self.__bom = None
    self.__templates_directory = None

  def _check_args(self):
    """Ensure required parameters were provided."""
    options = self.options
    if not options.swagger_codegen_cli_jar_path:
      raise ValueError('"swagger_codegen_cli_jar_path" was not provided.')
    if not os.path.exists(options.swagger_codegen_cli_jar_path):
      raise ValueError('swagger_codegen_cli_jar_path="{path}" not found.'
                       .format(path=options.swagger_codegen_cli_jar_path))
    have_bom_path = 1 if options.fetch_bom_path else 0
    have_bom_version = 1 if options.fetch_bom_version else 0
    if have_bom_path + have_bom_version > 1:
      raise ValueError(
          '{name} allows at most one of:'
          ' --fetch_bom_path, --fetch_bom_version'
          .format(name=self.name))

  def _do_preprocess(self):
    """Implements RepositoryCommandProcessor interface."""
    super(GenerateApiDocsCommand, self)._do_preprocess()
    options = self.options
    bom_path = options.fetch_bom_path
    bom_version = options.fetch_bom_version
    if bom_path:
      self.__bom = self.source_code_manager.bom_from_path(bom_path)
    elif bom_version:
      self.__bom = self.source_code_manager.bom_from_version(bom_version)

  def _ensure_templates_directory(self):
    """Initialize the templates_directory, pulling the repo if needed."""
    options = self.options
    github_io_repository_name = SPINNAKER_GITHUB_IO_REPOSITORY.name
    github_io_repository_url = options.githubio_repo_url
    scm = self.source_code_manager
    git_dir = scm.get_local_repository_path(github_io_repository_name)

    # This isnt tied to version control, especially since it is stored
    # in a different repository. Maybe this is/should be tagged with
    # the general spinnaker version. But there is a chicken/egg race
    # if we are strict about tagging across these repositories.
    if not os.path.exists(git_dir):
      scm.git.clone_repository_to_path(
          github_io_repository_url, git_dir,
          upstream_url=SPINNAKER_GITHUB_IO_REPOSITORY.url,
          branch=options.git_branch,
          default_branch='master')
    self.__templates_directory = os.path.join(git_dir, '_api_templates')

  def _do_command(self):
    """Implements CommandProcessor interface."""
    self._ensure_templates_directory()
    redis_service = 'redis-server'
    start_redis = run_subprocess('sudo service {redis} start'.format(
        redis=redis_service))[0] != 0
    logging.debug('redis-server %s running.',
                  'IS NOT' if start_redis else 'IS')

    try:
      if start_redis:
        check_subprocess(
            'sudo service {redis} start'.format(redis=redis_service))
      super(GenerateApiDocsCommand, self)._do_command()
    finally:
      if start_redis:
        # pylint: disable=broad-except
        try:
          check_subprocess(
              'sudo service {redis} stop'.format(redis=redis_service))
        except Exception as ex:
          maybe_log_exception(
              self.name, ex,
              'Ignoring exception while stopping temporary {name}.'.format(
                  name=redis_service))

  def wait_for_url(self, url, timeout_secs):
    """Wait for url to be ready or timeout."""
    logging.info('Waiting for %s', url)
    for _ in range(timeout_secs):
      try:
        code = urllib2.urlopen(url).getcode()
        if code >= 200 and code < 300:
          logging.info('%s is ready', url)
          return
      except urllib2.URLError:
        time.sleep(1)
    raise RuntimeError('{url} not ready after {time} secs'.format(
        url=url, time=timeout_secs))

  def generate_swagger_docs(self, repository, docs_url):
    """Generate the API from the swagger endpoint."""
    options = self.options
    named_scratch_dir = os.path.join(options.scratch_dir, repository.name)
    docs_dir = os.path.abspath(os.path.join(named_scratch_dir, 'apidocs'))
    docs_path = os.path.join(docs_dir, 'docs.json')
    ensure_dir_exists(docs_dir)

    logging.info('Generating swagger docs for %s', repository.name)
    check_subprocess('curl -s {url} -o {docs_path}'
                     .format(url=docs_url, docs_path=docs_path))
    check_subprocess(
        'java -jar {jar_path} generate -i {docs_path} -l html2'
        ' -o {scratch} -t {templates_directory}'
        .format(jar_path=options.swagger_codegen_cli_jar_path,
                docs_path=docs_path, scratch=docs_dir,
                templates_directory=self.__templates_directory))
    logging.info('Writing docs to directory %s', docs_dir)

  def _do_repository(self, repository):
    """Implements CommandProcessor interface."""
    docs_url_path = SWAGGER_URL_PATHS[repository.name]

    scm = self.source_code_manager
    git_dir = scm.get_local_repository_path(repository.name)
    if self.options.fetch_bom_path or self.options.fetch_bom_version:
      scm.pull_source_from_bom(repository.name, git_dir, self.__bom)

    env = dict(os.environ)
    port = unused_port()
    env['SERVER_PORT'] = str(port)
    base_url = 'http://localhost:{port}'.format(port=port)

    boot_run_cmd = './gradlew bootRun'
    process = start_subprocess(boot_run_cmd, cwd=git_dir, env=env)

    max_wait_secs = 45
    # pylint: disable=broad-except
    try:
      logging.info('Waiting for %s to be ready on port %d',
                   repository.name, port)
      self.wait_for_url(base_url + '/health', max_wait_secs)
      self.generate_swagger_docs(
          repository,
          '{base}/{path}'.format(base=base_url, path=docs_url_path))
    finally:
      try:
        logging.debug('Killing %s subprocess %s', repository.name, process.pid)
        process.kill()
        wait_subprocess(process)
      except Exception as ex:
        maybe_log_exception(
            self.name, ex,
            'Ignoring exception while stopping {name} subprocess {pid}.'
            .format(name=repository.name, pid=process.pid))


class GenerateApiDocsFactory(RepositoryCommandFactory):
  """Creates instances of GenerateApiDocsCommand."""

  def __init__(self):
    repositories = {
        'gate': SPINNAKER_RUNNABLE_REPOSITORIES['gate']
    }
    super(GenerateApiDocsFactory, self).__init__(
        'generate_api_docs', GenerateApiDocsCommand,
        'Generate the Spinnaker API REST documentation.',
        source_repositories=repositories)

  def _do_init_argparser(self, parser, defaults):
    """Implements CommandFactory interface."""
    self.add_argument(
        parser, 'githubio_repo_url', defaults,
        'https://github.com/spinnaker/spinnaker.github.io',
        help='The SSH URL of the repository containing the templates.')
    self.add_argument(
        parser, 'swagger_codegen_cli_jar_path', defaults, None,
        help='The location of the swagger-codegen-cli jarfile.'
        ' The file can be obtained from'
        ' http://central.maven.org/maven2/io/swagger/swagger-codegen-cli'
        '/2.2.3/swagger-codegen-cli-2.2.3.jar')

    FetchSourceCommandFactory.add_fetch_parser_args(parser, defaults)
    super(GenerateApiDocsFactory, self)._do_init_argparser(parser, defaults)


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  GenerateApiDocsFactory().register(registry, subparsers, defaults)
