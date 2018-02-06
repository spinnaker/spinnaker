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

"""Implements build command for buildtool."""

import copy
import datetime
import logging
import os
import shutil
import textwrap
import yaml

from buildtool import (
    DEFAULT_BUILD_NUMBER,
    SPINNAKER_GITHUB_IO_REPOSITORY_NAME,
    SPINNAKER_HALYARD_REPOSITORY_NAME,

    BranchSourceCodeManager,
    CommandProcessor,
    CommandFactory,
    GitRunner,
    GradleCommandFactory,
    GradleCommandProcessor,
    GradleRunner,
    HalRunner,

    SpinnakerSourceCodeManager,

    run_subprocess,
    check_subprocess,
    check_subprocesses_to_logfile,
    raise_and_log_error,
    write_to_path,
    ConfigError,
    ExecutionError)


def build_halyard_docs(command, repository):
  """Builds Halyard's CLI and updates documentation in its repo."""
  cli_dir = os.path.join(repository.git_dir, 'halyard-cli')

  # Before, we were doing this first:
  # check_run_quick('git -C halyard rev-parse HEAD'
  #                 ' | xargs git -C halyard checkout ;')
  # however now the repository should already be at the desired commit.
  logging.debug('Building Halyard CLI and docs.')
  logfile = command.get_logfile_path('build-docs')
  check_subprocesses_to_logfile(
      'Build halyard docs', logfile, ['make'], cwd=cli_dir)


class BuildHalyardCommand(GradleCommandProcessor):
  """Implements the build_halyard command."""
  # pylint: disable=too-few-public-methods

  HALYARD_VERSIONS_BASENAME = 'nightly-version-commits.yml'

  def __init__(self, factory, options, **kwargs):
    options_copy = copy.copy(options)
    options_copy.bom_path = None
    options_copy.bom_version = None
    self.__build_version = None  # recorded after build
    self.__versions_url = options.halyard_version_commits_url
    if not self.__versions_url:
      self.__versions_url = '{base}/{filename}'.format(
          base=options.halyard_bucket_base_url,
          filename=self.HALYARD_VERSIONS_BASENAME)
    super(BuildHalyardCommand, self).__init__(
        factory, options_copy,
        source_repository_names=[SPINNAKER_HALYARD_REPOSITORY_NAME],
        **kwargs)

  def publish_halyard_version_commits(self, repository):
    """Publish the halyard build to the bucket.

    This also writes the built version to
        <output_dir>/halyard/last_version_commit.yml
    so callers can know what version was written.

    NOTE(ewiseblatt): 20180110 Halyard's policies should be revisited here.
    Although this is a "Publish" it is not a release. It is publishing
    the 'nightly' build which isnt really nightly just 'last-build',
    which could even be on an older branch than latest.
    """
    summary_info = self.source_code_manager.lookup_source_info(repository)

    # This is only because we need a file to gsutil cp
    # We already need gsutil so its easier to just use it again here.
    output_dir = self.get_output_dir()
    tmp_path = os.path.join(output_dir, self.HALYARD_VERSIONS_BASENAME)

    contents = self.load_halyard_version_commits()
    new_entry = '{version}: {commit}\n'.format(
        version=self.__build_version, commit=summary_info.summary.commit_id)

    logging.info('Updating %s with %s', self.__versions_url, new_entry)
    if contents and contents[-1] != '\n':
      contents += '\n'
    contents = contents + new_entry
    with open(tmp_path, 'w') as stream:
      stream.write(contents)
    check_subprocess('gsutil cp {path} {url}'.format(
        path=tmp_path, url=self.__versions_url))
    self.__emit_last_commit_entry(new_entry)

  def __emit_last_commit_entry(self, entry):
    last_version_commit_path = os.path.join(
        self.get_output_dir(), 'last_version_commit.yml')
    write_to_path(entry, last_version_commit_path)

  def build_all_halyard_deployments(self, repository):
    """Helper function for building halyard."""
    options = self.options

    git_dir = repository.git_dir
    source_info = self.source_code_manager.lookup_source_info(repository)
    self.__build_version = source_info.to_build_version()

    cmd = './release/all.sh {version} nightly'.format(
        version=self.__build_version)
    env = dict(os.environ)
    logging.info(
        'Preparing the environment variables for release/all.sh:\n'
        '    PUBLISH_HALYARD_DOCKER_IMAGE_BASE=%s\n'
        '    PUBLISH_HALYARD_BUCKET_BASE_URL=%s',
        options.halyard_docker_image_base,
        options.halyard_bucket_base_url)
    env['PUBLISH_HALYARD_DOCKER_IMAGE_BASE'] = options.halyard_docker_image_base
    env['PUBLISH_HALYARD_BUCKET_BASE_URL'] = options.halyard_bucket_base_url

    logfile = self.get_logfile_path('jar-build')
    check_subprocesses_to_logfile(
        '{name} build'.format(name='halyard'), logfile,
        [cmd], cwd=git_dir, env=env)

  def load_halyard_version_commits(self):
    logging.debug('Fetching existing halyard build versions')
    retcode, stdout = run_subprocess('gsutil cat ' + self.__versions_url)
    if not retcode:
      contents = stdout + '\n'
    else:
      if stdout.find('No URLs matched') < 0:
        raise_and_log_error(
            ExecutionError('No URLs matched', program='gsutil'),
            'Could not fetch "%s": %s' % (self.__versions_url, stdout))
      contents = ''
      logging.warning(
          '%s did not exist. Creating a new one.', self.__versions_url)
    return contents

  def find_commit_version_entry(self, repository):
    logging.debug('Looking for existing halyard version for this commit.')
    commit_id = self.git.query_local_repository_commit_id(repository.git_dir)
    commits = self.load_halyard_version_commits().split('\n')
    commits.reverse()
    postfix = ' ' + commit_id
    for line in commits:
      if line.endswith(postfix):
        return line
    return None

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    if self.options.skip_existing:
      entry = self.find_commit_version_entry(repository)
      if entry:
        logging.info('Found existing halyard version "%s"', entry)
        labels = {'repository': repository.name, 'artifact': 'halyard'}
        self.metrics.inc_counter('ReuseArtifact', labels,
                                 'Kept existing desired artifact build.')
        self.__emit_last_commit_entry(entry)
        return

    if self.gradle.consider_debian_on_bintray(
        repository, build_number=self.options.build_number):
      return

    args = self.gradle.get_common_args()
    if not self.options.run_unit_tests:
      args.append('-x test')

    args.extend(self.gradle.get_debian_args('trusty-nightly,xenial-nightly'))
    self.gradle.check_run(args, self, repository, 'candidate', 'debian-build')

    ## Tags above were written back. Nebula chokes on branches with that.
    self.gradle.prepare_local_git_for_nebula(repository.git_dir, repository)
    build_halyard_docs(self, repository)
    self.build_all_halyard_deployments(repository)
    self.publish_halyard_version_commits(repository)


class BuildHalyardFactory(GradleCommandFactory):
  """Implements the build_halyard command."""
  # pylint: disable=too-few-public-methods

  def __init__(self):
    super(BuildHalyardFactory, self).__init__(
        'build_halyard', BuildHalyardCommand,
        'Build halyard from the local git repository.',
        BranchSourceCodeManager)

  def init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildHalyardFactory, self).init_argparser(parser, defaults)

    self.add_argument(
        parser, 'halyard_version_commits_url', defaults, None,
        help='URL to file containing version and git commit for successful'
             ' nightly builds. By default this will be'
             ' "{filename}" in the'
             ' --halyard_bucket_base_url.'.format(
                 filename=BuildHalyardCommand.HALYARD_VERSIONS_BASENAME))
    self.add_argument(
        parser, 'build_number', defaults, DEFAULT_BUILD_NUMBER,
        help='The build number is used when generating halyard.')
    self.add_argument(
        parser, 'halyard_bucket_base_url',
        defaults, None,
        help='Base Google Cloud Storage URL for writing halyard builds.')
    self.add_argument(
        parser, 'halyard_docker_image_base',
        defaults, None,
        help='Base Docker image name for writing halyard builds.')


class PublishHalyardCommandFactory(CommandFactory):
  def __init__(self):
    super(PublishHalyardCommandFactory, self).__init__(
        'publish_halyard', PublishHalyardCommand,
        'Publish a new halyard release.')

  def init_argparser(self, parser, defaults):
    super(PublishHalyardCommandFactory, self).init_argparser(
        parser, defaults)
    GradleCommandFactory.add_bom_parser_args(parser, defaults)
    SpinnakerSourceCodeManager.add_parser_args(parser, defaults)
    GradleRunner.add_parser_args(parser, defaults)
    GitRunner.add_publishing_parser_args(parser, defaults)
    HalRunner.add_parser_args(parser, defaults)

    self.add_argument(
        parser, 'build_number', defaults, DEFAULT_BUILD_NUMBER,
        help='Publishing halyard requires a rebuild. This is the build number'
             ' to use when rebuilding halyard.')

    self.add_argument(
        parser, 'halyard_version', defaults, None, required=True,
        help='The semantic version of the release to publish.')

    self.add_argument(
        parser, 'halyard_version_commits_url', defaults, None,
        help='URL to file containing version and git commit for successful'
             ' nightly builds. By default this will be'
             ' "{filename}" in the'
             ' --halyard_bucket_base_url.'.format(
                 filename=BuildHalyardCommand.HALYARD_VERSIONS_BASENAME))
    self.add_argument(
        parser, 'halyard_bucket_base_url',
        defaults, None,
        help='Base Google Cloud Storage URL for writing halyard builds.')

    self.add_argument(parser, 'docs_repo_owner', defaults, None,
                      help='Owner of the docs repo if one was'
                      ' specified. The default is --github_owner.')
    self.add_argument(
        parser, 'skip_existing', defaults, False, type=bool,
        help='Skip builds if the desired version already exists on bintray.')

    self.add_argument(
        parser, 'delete_existing', defaults, None, type=bool,
        help='Delete pre-existing desired versions if from bintray.')


class PublishHalyardCommand(CommandProcessor):
  """Publish halyard version to the public repository."""

  def __init__(self, factory, options, **kwargs):
    options_copy = copy.copy(options)
    options_copy.bom_path = None
    options_copy.bom_version = None
    options_copy.git_branch = 'master'
    # Overrides later if --git_allow_publish_master_branch is false
    super(PublishHalyardCommand, self).__init__(factory, options_copy, **kwargs)

    self.__scm = BranchSourceCodeManager(options_copy, self.get_input_dir())
    self.__hal = HalRunner(options_copy)
    self.__gradle = GradleRunner(options_copy, self.__scm, self.metrics)
    self.__halyard_repo_md_path = os.path.join('docs', 'commands.md')

    dash = self.options.halyard_version.find('-')
    semver_str = self.options.halyard_version[0:dash]
    semver_parts = semver_str.split('.')
    if len(semver_parts) != 3:
      raise_and_log_error(
          ConfigError('Expected --halyard_version in the form X.Y.Z-N'))
    self.__release_branch = 'release-{maj}.{min}.x'.format(
        maj=semver_parts[0], min=semver_parts[1])
    self.__release_tag = 'version-' + semver_str
    self.__release_version = semver_str

  def determine_commit(self, repository):
    """Determine the commit_id that we want to publish."""
    if repository.name != 'halyard':
      raise_and_log_error(
          ConfigError('Unexpected repository "%s"' % repository.name))

    options = self.options
    versions_url = options.halyard_version_commits_url
    if not versions_url:
      versions_url = '{base}/{filename}'.format(
          base=options.halyard_bucket_base_url,
          filename=BuildHalyardCommand.HALYARD_VERSIONS_BASENAME)

    if os.path.exists(versions_url):
      logging.debug('Loading halyard version info from file %s', versions_url)
      with open(versions_url, 'r') as stream:
        version_data = stream.read()
    else:
      logging.debug('Loading halyard version info from bucket %s', versions_url)
      version_data = check_subprocess(
          'gsutil cat {url}'.format(url=versions_url))

    commit = yaml.load(version_data).get(options.halyard_version)
    if commit is None:
      raise_and_log_error(
          ConfigError('Unknown halyard version "{version}" in "{url}"'.format(
              version=options.halyard_version, url=versions_url)))

  def _prepare_repository(self):
    """Prepare a local repository to build for release.

    Were rebuilding it only to have nebula give a new distribution tag.
    However we will also use the repository to tag and branch the release
    into github so want to at least clone the repo regardless.
    """
    logging.debug('Preparing repository for publishing a halyard release.')
    repository = self.__scm.make_repository_spec(
        SPINNAKER_HALYARD_REPOSITORY_NAME)
    commit = self.determine_commit(repository)
    git_dir = repository.git_dir
    if os.path.exists(git_dir):
      logging.info('Deleting existing %s to build fresh.', git_dir)
      shutil.rmtree(git_dir)
    git = self.__scm.git
    git.clone_repository_to_path(repository, commit=commit)
    self.__scm.refresh_source_info(repository, self.options.build_number)
    return repository

  def _build_release(self, repository):
    """Rebuild the actual release.

    We dont necessarily need to rebuild here. We just need to push as
    debian to the "-stable".
    """
    # Ideally we would just modify the existing bintray version to add
    # trusty-stable to the distributions, however it does not appear possible
    # to patch the debian attributes of a bintray version, only the
    # version metadata. Therefore, we'll rebuild it.
    # Alternatively we could download the existing and push a new one,
    # however I dont see how to get at the existing debian metadata and
    # dont want to ommit something

    git_dir = repository.git_dir
    summary = self.__scm.git.collect_repository_summary(git_dir)

    args = self.__gradle.get_common_args()
    args.extend(self.__gradle.get_debian_args('trusty-stable,xenial-stable'))
    build_number = self.options.build_number
    if not self.__gradle.consider_debian_on_bintray(
        repository, build_number=build_number):
      self.__gradle.check_run(
          args, self, repository, 'candidate', 'build-release',
          version=self.__release_version, build_number=build_number,
          gradle_dir=git_dir)

    info_path = os.path.join(self.get_output_dir(), 'halyard_info.yml')
    logging.debug('Writing build information to %s', info_path)
    write_to_path(summary.to_yaml(), info_path)

  def write_target_docs(self, source_repository, target_repository):
    source_path = os.path.join(source_repository.git_dir,
                               self.__halyard_repo_md_path)
    target_rel_path = os.path.join('reference', 'halyard', 'commands.md')
    target_path = os.path.join(target_repository.git_dir, target_rel_path)
    now = datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')
    logging.debug('Writing documentation into %s', target_path)
    header = textwrap.dedent(
        """\
        ---
        layout: single
        title: "Commands"
        sidebar:
          nav: reference
        ---
        Published: {now}
        """.format(now=now))
    with open(source_path, 'r') as source:
      body = source.read()
    with open(target_path, 'w') as stream:
      stream.write(header)
      stream.write(body)
    return target_rel_path

  def push_docs(self, repository):
    base_branch = 'master'
    target_repository = self.__scm.make_repository_spec(
        SPINNAKER_GITHUB_IO_REPOSITORY_NAME)
    self.__scm.ensure_git_path(target_repository)
    target_rel_path = self.write_target_docs(repository, target_repository)

    if self.options.git_allow_publish_master_branch:
      head_branch = 'master'
      branch_flag = ''
    else:
      head_branch = self.__release_version + '-haldocs'
      branch_flag = '-b'
    logging.debug('Commiting changes into local repository "%s" branch=%s',
                  target_repository.git_dir, head_branch)

    git_dir = target_repository.git_dir
    message = 'docs(halyard): ' + self.__release_version
    local_git_commands = [
        # These commands are accomodating to a branch already existing
        # because the branch is on the version, not build. A rejected
        # build for some reason that is re-tried will have the same version
        # so the branch may already exist from the earlier attempt.
        'checkout ' + base_branch,
        'checkout {flag} {branch}'.format(
            flag=branch_flag, branch=head_branch),
        'add ' + target_rel_path,
        'commit -m "{msg}" {path}'.format(msg=message, path=target_rel_path),
    ]

    logging.debug('Commiting changes into local repository "%s" branch=%s',
                  target_repository.git_dir, head_branch)
    git = self.__scm.git
    git.check_run_sequence(git_dir, local_git_commands)

    logging.info('Pushing halyard docs to %s branch="%s"',
                 target_repository.origin, head_branch)
    git.push_branch_to_origin(target_repository.git_dir, branch=head_branch)

  def _do_command(self):
    """Implements CommandProcessor interface."""
    repository = self._prepare_repository()
    self._build_release(repository)
    build_halyard_docs(self, repository)
    self.push_docs(repository)
    self.push_tag_and_branch(repository)
    self.__hal.publish_halyard_release(self.__release_version)

  def push_tag_and_branch(self, repository):
    """Pushes a stable branch and git version tag to the origin repository."""
    git_dir = repository.git_dir
    git = self.__scm.git

    release_url = repository.origin
    logging.info('Pushing branch=%s and tag=%s to %s',
                 self.__release_tag, self.__release_branch, release_url)
    git.check_run_sequence(
        git_dir,
        [
            'checkout -b ' + self.__release_branch,
            'remote add release ' + release_url,
            'push release ' + self.__release_branch,
            'tag ' + self.__release_tag,
            'push release ' + self.__release_tag
        ])


def register_commands(registry, subparsers, defaults):
  BuildHalyardFactory().register(registry, subparsers, defaults)
  PublishHalyardCommandFactory().register(registry, subparsers, defaults)
