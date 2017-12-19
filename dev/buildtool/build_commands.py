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

import datetime
import logging
import os
import shlex
import shutil
import subprocess
import yaml

from buildtool.command import (
    RepositoryCommandFactory,
    RepositoryCommandProcessor)

from buildtool.source_code_manager import (
    SPINNAKER_BOM_REPOSITORIES,
    SPINNAKER_HALYARD_REPOSITORIES)

from buildtool.util import (
    check_subprocess,
    timedelta_string,
    timestring,
    check_subprocesses_to_logfile,
    write_to_path)


class GradleCommandFactory(RepositoryCommandFactory):
  """Base class for build commands using Gradle."""

  @staticmethod
  def add_bom_parser_args(parser, defaults):
    """Adds publishing arguments of interest to the BOM commands as well."""
    if hasattr(parser, 'added_gradle'):
      return
    parser.added_gradle = True

    GradleCommandFactory.add_argument(
        parser, 'build_bintray_repository', defaults, None,
        help='bintray repository to publish debians into.')

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(GradleCommandFactory, self)._do_init_argparser(parser, defaults)
    self.add_bom_parser_args(parser, defaults)

    # The gradle jobs in the foreach_repository seem to hang when using
    # pools but not threadpools. I think this is a deadlock related to
    # job forks. It only hangs after all the jobs completed (cycling through
    # the reuse of workers). Forcing the pool to not reuse works seems to
    # have fixed the problem. However I'm leaving this flag in as a
    # break glass if needed should this crop up again.
    #
    # In general using Processes are more resource-efficient than threads
    # because of the python GIL. The gradle jobs are so CPU heavy that
    # they cannot run that concurrently anyway so this might not matter
    # much where builds are local.
    self.add_argument(
        parser, 'use_threadpool', defaults, False, action='store_true',
        help='Use ThreadPool rather than Pool as a workaround for'
        ' gradle hanging.')
    self.add_argument(
        parser, 'max_local_builds', defaults, None, type=int,
        help='Maximum build concurrency.')
    self.add_argument(
        parser, 'run_unit_tests', defaults, False, action='store_true',
        help='Run unit tests during build for all components other than Deck.')
    self.add_argument(
        parser, 'build_jar_repository', defaults, None,
        help='bintray repository to publish jar files into.')
    self.add_argument(
        parser, 'maven_custom_init_file', defaults,
        os.path.join(os.path.dirname(__file__), '..', 'maven-init.gradle'),
        help='Path to a gradle init file to add to the debian builds.'
        ' Used to specify any custom behavior in the gradle builds.'
        ' Argument is a file path relative to the directory this script is'
        ' executed in.'
        ' The default value assumes we run this script from the parent'
        ' directory of spinnaker/spinnaker.')
    self.add_argument(
        parser, 'force_clean_gradle_cache', defaults, False,
        help='Force a fresh new component-specific gradle cache for'
        ' each component build')
    self.add_argument(
        parser, 'gradle_cache_path', defaults,
        '{home}/.gradle'.format(home=os.environ['HOME'])
        if os.environ.get('HOME') else None,
        help='Path to a gradle cache directory to use for the builds.')
    self.add_argument(
        parser, 'extra_gradle_args', defaults, None,
        help='Additional arguments to pass to gradle (comma-delimited).')


class GradleCommandProcessor(RepositoryCommandProcessor):
  """Base class for build commands using Gradle."""
  # pylint: disable=abstract-method

  def __init__(self, factory, options, upstream_boms, **kwargs):
    self.__upstream_boms = upstream_boms
    self.__nebula_repository_version = {}

    # This is to cut down on logging noise so we only emmit one
    # message on the test against freshness rather than on every access.
    self.__logged_is_newer = {}
    super(GradleCommandProcessor, self).__init__(
        factory, options, use_threadpool=options.use_threadpool, **kwargs)

  def _get_nebula_repository_version(self, name):
    """Get the GitSummary for the named repo."""
    if not self.__nebula_repository_version.get(name):
      summary_path = os.path.join(
          self.options.scratch_dir, name,
          '{name}-summary.yml'.format(name=name))
      if not os.path.exists(summary_path):
        logging.debug('Forcing a repository update because there is no %s',
                      summary_path)
        self._get_nebula_repository_path(name, force_new=True)

      with open(summary_path, 'r') as stream:
        data = yaml.load(stream)
        self.__nebula_repository_version[name] = data['version']
    return self.__nebula_repository_version[name]

  def _do_determine_source_repositories(self):
    """Implements RepositoryCommand interface."""
    local_repository = {
        name: os.path.join(self.options.root_path, name)
        for name in self.__upstream_boms.keys()
    }
    return {name: self.git.determine_remote_git_repository(git_dir)
            for name, git_dir in local_repository.items()}

  def __make_gradle_args(self, name):
    """Returns commandline arguments to pass to gradle."""
    options = self.options
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']
    if not bintray_key:
      raise ValueError('Expected BINTRAY_KEY set.')
    if not bintray_user:
      raise ValueError('Expected BINTRAY_USER set.')

    parts = (options.build_bintray_repository or '').split('/')
    if len(parts) != 2:
      raise ValueError(
          'Expected --build_bintray_repository to be in the form'
          ' <owner>/<repo>')
    org, package_repo = parts[0], parts[1]

    jar_repo = options.build_jar_repository
    if not jar_repo or len(jar_repo.split('/')) != 1:
      raise ValueError('Expected --build_jar_repository in the form <repo>'
                       ' using the implied owner from'
                       ' --build_bintray_repository')

    build_number = options.build_number
    extra_args = [
        '--stacktrace',
        '-Prelease.useLastTag=true',
        '-PbintrayPackageBuildNumber={number}'.format(number=build_number),
        '-PbintrayOrg="{org}"'.format(org=org),
        '-PbintrayPackageRepo="{repo}"'.format(repo=package_repo),
        '-PbintrayJarRepo="{jarRepo}"'.format(jarRepo=jar_repo),
        '-PbintrayKey="{key}"'.format(key=bintray_key),
        '-PbintrayUser="{user}"'.format(user=bintray_user)
    ]

    if options.maven_custom_init_file:
      extra_args.append('-I {}'.format(options.maven_custom_init_file))

    gradle_cache_path = options.gradle_cache_path
    if not gradle_cache_path:
      gradle_cache_path = os.path.join(options.scratch_dir, 'gradle_cache')

    if gradle_cache_path:
      extra_args.append(
          '--gradle-user-home={}'.format(options.gradle_cache_path))

    if (not options.run_unit_tests
        or (name == 'deck' and not 'CHROME_BIN' in os.environ)):
      extra_args.append('-x test')

    if not options.run_unit_tests and name == 'orca':
      extra_args.append('-x junitPlatformTest')
#     extra_args.append('-x generateHtmlTestReports')

    if name == 'halyard':
      extra_args.append('-PbintrayPackageDebDistribution=trusty-nightly')
    else:
      extra_args.append('-PbintrayPackageDebDistribution=trusty,xenial')

    extra_args.extend(shlex.split(options.extra_gradle_args or ''))
    return extra_args

  def __is_newer_repo(self, test_path, ref_path, log):
    """Determine if one path was modified more recently than another."""
    test_git = os.path.join(test_path, '.git')
    ref_git = os.path.join(ref_path, '.git')
    test_time = os.path.getmtime(test_git)
    ref_time = os.path.getmtime(ref_git)
    if test_time > ref_time:
      if log:
        test_datetime = datetime.datetime.fromtimestamp(test_time)
        ref_datetime = datetime.datetime.fromtimestamp(ref_time)
        logging.debug(
            '%s is still current over %s (%s > %s by %s)',
            test_git, ref_git,
            timestring(now=test_datetime),
            timestring(now=ref_datetime),
            timedelta_string(test_datetime - ref_datetime))
      return True

    test_datetime = datetime.datetime.fromtimestamp(test_time)
    ref_datetime = datetime.datetime.fromtimestamp(ref_time)
    logging.debug(
        '%s is older than %s (%s < %s by %s)',
        test_git, ref_git,
        timestring(now=test_datetime),
        timestring(now=ref_datetime),
        timedelta_string(ref_datetime - test_datetime))
    return False

  def _get_nebula_repository_path(self, name, force_new=False):
    """Return the path to the repository for nebula to build.

    If the repository does not exist then one will be cloned from the
    source repository.

    Nebula gets confused by our tags because it expects the Netflix ones.
    Since we dont understand how to change nebula and dont want to invest
    the time into it, we're just going to remove all the tags and add the
    one we want nebula to use so it isnt confused.

    Rather than modifying the original repository, we'll do all this in a
    new one. The bonus is that we can commit and tag the repo so that
    gradle/nebula is happy but without modifying the original repo at all
    until we are ready to later.

    Args:
      name: [string] The name of the repository
      force_new: [bool] If true, clear any existing repository
    """
    git_dir = self.source_code_manager.get_local_repository_path(name)
    original_repo_path = os.path.abspath(git_dir)

    options = self.options
    nebula_repo_path = os.path.join(options.scratch_dir, 'build', name)

    if os.path.exists(nebula_repo_path):
      if not force_new:
        logged_is_newer = self.__logged_is_newer.get(nebula_repo_path, False)
        if self.__is_newer_repo(nebula_repo_path, original_repo_path,
                                not logged_is_newer):
          if not logged_is_newer:
            logging.info('Reusing existing %s', nebula_repo_path)
            self.__logged_is_newer[nebula_repo_path] = True
          return nebula_repo_path

      self.__logged_is_newer[nebula_repo_path] = False
      logging.info('Removing old nebula repo %s', nebula_repo_path)
      shutil.rmtree(nebula_repo_path, ignore_errors=True)

    logging.debug('Determining version and build tag of %s',
                  original_repo_path)
    summary = self.git.collect_repository_summary(original_repo_path)
    build_tag = '{version}-{build}'.format(
        version=summary.version, build=options.build_number)

    logging.info('Cloning %s to nebula repo %s', name, nebula_repo_path)
    self.git.clone_repository_to_path(original_repo_path, nebula_repo_path)

    # Nebula complains if the git tag version doesn't match the branch's
    # version, i.e. in patch releases. As a workaround, we checkout the
    # HEAD commit of the current branch in a 'detached HEAD' state so
    # Nebula doesn't complain about our branch version and git tag
    # version not matching.
    self.git.reinit_local_repository_with_tag(
        nebula_repo_path, build_tag,
        'Snapshot from {path} to satisfy nebula.\n\n'
        'commit: {commit}\ntag: {tag}\nversion: {version}'
        .format(path=original_repo_path,
                commit=summary.commit_id,
                tag=summary.tag,
                version=summary.version))
    self.__nebula_repository_version[name] = summary.version
    summary_path = os.path.join(
        options.scratch_dir, name, '{name}-summary.yml'.format(name=name))
    write_to_path(summary.to_yaml(with_commit_messages=False), summary_path)
    return nebula_repo_path

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    name = repository.name
    options = self.options

    gradle_root = self._get_nebula_repository_path(name)
    extra_args = self.__make_gradle_args(name)

    target = 'candidate'
    cmd = './gradlew {extra} {target}'.format(
        extra=' '.join(extra_args), target=target)

    logdir = os.path.join(options.scratch_dir, name)
    logfile = os.path.join(logdir, '{name}-debian-build.log'.format(name=name))
    check_subprocesses_to_logfile(
        '{name} gradle build'.format(name=name), logfile,
        [cmd], cwd=gradle_root)

    return gradle_root


class BuildHalyardCommand(GradleCommandProcessor):
  """Implements the build_halyard command."""

  HALYARD_VERSIONS_BASENAME = 'nightly-version-commits.yml'

  def __init__(self, factory, options, upstream_repositories=None, **kwargs):
    upstream_repositories = (upstream_repositories
                             or SPINNAKER_HALYARD_REPOSITORIES)
    self.__build_version = None  # recorded after build

    # NOTE(ewiseblatt): 20171213
    # I think we need this, though am confused by the release/all
    # also building debians. Our parent class should publish to bintray
    # whereas the release/all does not.
    self.__build_halyard_to_bintray = True

    super(BuildHalyardCommand, self).__init__(
        factory, options, upstream_repositories,
        max_threads=options.max_local_builds, **kwargs)

  def publish_halyard_version_commits(self):
    """Publish the halyard build to the bucket.

    This also writes the built version to
        <scratch>/halyard/last_version_commit.yml
    so callers can know what version was written.
    """
    options = self.options
    versions_url = options.halyard_version_commits_url
    if not versions_url:
      versions_url = '{base}/{filename}'.format(
          base=options.halyard_bucket_base_url,
          filename=self.HALYARD_VERSIONS_BASENAME)
    named_scratch_dir = os.path.join(options.scratch_dir, 'halyard')
    summary_path = os.path.join(named_scratch_dir, 'halyard-summary.yml')

    with open(summary_path, 'r') as stream:
      summary_info = yaml.load(stream)

    # This is only because we need a file to gsutil cp
    # We already need gsutil so its easier to just use it again here.
    tmp_path = os.path.join(named_scratch_dir, self.HALYARD_VERSIONS_BASENAME)

    logging.debug('Fetching existing halyard build versions')
    try:
      contents = check_subprocess(
          'gsutil cat {url}'.format(url=versions_url))
      contents += '\n'
    except subprocess.CalledProcessError as error:
      output = error.output
      if output.find('No URLs matched') < 0:
        raise
      contents = ''
      logging.warning('%s did not exist. Creating a new one.', versions_url)

    new_entry = '{version}: {commit}\n'.format(
        version=self.__build_version, commit=summary_info['commit_id'])

    logging.info('Updating %s with %s', versions_url, new_entry)
    if contents and contents[-1] != '\n':
      contents += '\n'
    contents = contents + new_entry
    with open(tmp_path, 'w') as stream:
      stream.write(contents)
    check_subprocess('gsutil cp {path} {url}'.format(
        path=tmp_path, url=versions_url))

    last_version_commit_path = os.path.join(
        named_scratch_dir, 'last_version_commit.yml')
    with open(last_version_commit_path, 'w') as stream:
      stream.write(new_entry)

  def build_all_halyard_deployments(self, name):
    """Helper function for building halyard."""
    options = self.options

    nebula_repo_path = self._get_nebula_repository_path(name)
    raw_version = self._get_nebula_repository_version(name)
    self.__build_version = '{version}-{build}'.format(
        version=raw_version, build=options.build_number)

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

    logfile = os.path.join(options.scratch_dir, name, 'jar-build.log')
    check_subprocesses_to_logfile(
        '{name} build'.format(name='halyard'), logfile,
        [cmd], cwd=nebula_repo_path, env=env)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    if self.__build_halyard_to_bintray:
      # I think this needs to be first, but am not sure.
      super(BuildHalyardCommand, self)._do_repository(repository)

    if repository.name == 'halyard':
      self.build_all_halyard_deployments(repository.name)
      self.publish_halyard_version_commits()


class BuildHalyardFactory(GradleCommandFactory):
  """Implements the build_halyard command."""

  def __init__(self):
    super(BuildHalyardFactory, self).__init__(
        'build_halyard', BuildHalyardCommand,
        'Build halyard from the local git repository.')

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildHalyardFactory, self)._do_init_argparser(parser, defaults)

    self.add_argument(
        parser, 'halyard_version_commits_url', defaults, None,
        help='URL to file containing version and git commit for successful'
             ' nightly builds. By default this will be'
             ' "{filename}" in the'
             ' --halyard_bucket_base_url.'.format(
                 filename=BuildHalyardCommand.HALYARD_VERSIONS_BASENAME))
    self.add_argument(
        parser, 'halyard_bucket_base_url',
        defaults, 'gs://spinnaker-artifacts/halyard',
        help='Base Google Cloud Storage URL for writing halyard builds.')
    self.add_argument(
        parser, 'halyard_docker_image_base',
        defaults, 'gcr.io/spinnaker-marketplace/halyard',
        help='Base Docker image name for writing halyard builds.')



class BuildDebianCommand(GradleCommandProcessor):
  """Implements the build_debians command."""

  def __init__(self, factory, options, upstream_repositories=None, **kwargs):
    upstream_repositories = upstream_repositories or SPINNAKER_BOM_REPOSITORIES
    super(BuildDebianCommand, self).__init__(
        factory, options, upstream_repositories,
        max_threads=options.max_local_builds, **kwargs)


class BuildDebianFactory(GradleCommandFactory):
  """Implements the build_debians command."""

  def __init__(self):
    super(BuildDebianFactory, self).__init__(
        'build_debians', BuildDebianCommand,
        'Build one or more debians from the local git repository.')

  @staticmethod
  def add_bom_parser_args(parser, defaults):
    """Adds publishing arguments of interest to the BOM commands as well."""
    if hasattr(parser, 'added_debians'):
      return
    parser.added_debians = True

    GradleCommandFactory.add_bom_parser_args(parser, defaults)
    BuildDebianFactory.add_argument(
        parser, 'build_google_image_project', defaults,
        'marketplace-spinnaker-release',
        help='GCE project we publish HA Spinnaker component images to.')

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildDebianFactory, self)._do_init_argparser(parser, defaults)
    self.add_bom_parser_args(parser, defaults)


class BuildContainerCommand(GradleCommandProcessor):
  """Implements the build_bom_containers command."""

  def __init__(self, factory, options, upstream_repositories=None, **kwargs):
    if not upstream_repositories:
      raise ValueError(
          'No upstream repositories provided by %s' % factory.name)
    super(BuildContainerCommand, self).__init__(
        factory, options, upstream_repositories, **kwargs)

  def determine_gradle_root(self, repository):
    """Determine source directory root for gradle to run in."""
    name = repository.name
    return self._get_nebula_repository_path(name)

  def _do_repository(self, repository):
    """Implements RepositoryCommandProcessor interface."""
    options = self.options
    builder_methods = {
        'docker': self.__build_with_docker,
        'gcb': self.__build_with_gcb
    }
    builder_methods[options.container_builder](repository)

  def __build_with_docker(self, repository):
    logging.warning('DOCKER builds are still under development')
    name = repository.name
    version = self._get_nebula_repository_version(name)
    docker_tag = '{reg}/{name}:{version}'.format(
        reg=self.options.build_docker_registry,
        name=name,
        version=version)

    cmds = [
        'docker build -f Dockerfile -t {docker_tag} .'.format(
            docker_tag=docker_tag),
        'docker push {docker_tag}'.format(docker_tag=docker_tag)
    ]

    gradle_root = self.determine_gradle_root(repository)
    logfile = os.path.join(self.options.scratch_dir, name,
                           '{name}-docker-build.log'.format(name=name))
    check_subprocesses_to_logfile(
        '{name} docker build'.format(name=name), logfile,
        cmds, cwd=gradle_root)

  def __build_with_gcb(self, repository):
    name = repository.name
    nebula_dir = self._get_nebula_repository_path(name)

    version = self._get_nebula_repository_version(name)
    gcb_config = self.__derive_gcb_config(name, version)
    if gcb_config is None:
      logging.info('Skipping GCB for %s because there is config for it',
                   name)
      return

    options = self.options
    log_flags = '--log-http' if options.log_level == 'debug' else ''
    name_scratch_dir = os.path.join(options.scratch_dir, name)

    # Use an absoluate path here because we're going to
    # pass this to the gcloud command, which will be running
    # in a different directory so relative paths wont hold.
    config_path = os.path.abspath(os.path.join(
        name_scratch_dir, '{name}-gcb.yml'.format(name=name)))
    write_to_path(gcb_config, config_path)

    # Local .gradle dir stomps on GCB's .gradle directory when the gradle
    # wrapper is installed, so we need to delete the local one.
    # The .gradle dir is transient and will be recreated on the next gradle
    # build, so this is OK.
    #
    # This can still be shared among components as long as the
    # scratch directory remains around.
    if options.force_clean_gradle_cache:
      # If we're going to delete existing ones, then keep each component
      # separate so they dont stomp on one another
      gradle_cache = os.path.abspath(os.path.join(nebula_dir, '.gradle'))
    else:
      # Otherwise allow all the components to share a common gradle directory
      gradle_cache = os.path.abspath(
          os.path.join(options.scratch_dir, '.gradle'))

    if options.force_clean_gradle_cache and os.path.isdir(gradle_cache):
      shutil.rmtree(gradle_cache)

    # Note this command assumes a cwd of nebula_dir
    cmd = ('gcloud container builds submit {log_flags}'
           ' --account={account} --project={project}'
           ' --config="{config_path}" .'
           .format(log_flags=log_flags,
                   account=options.gcb_service_account,
                   project=options.gcb_project,
                   config_path=config_path))

    logfile = os.path.join(
        name_scratch_dir, '{name}-gcb-build.log'.format(name=name))
    check_subprocesses_to_logfile(
        '{name} container build'.format(name=name), logfile,
        [cmd], cwd=nebula_dir)

  def __make_gradle_gcb_step(self, name, env_vars_list):
    if name == 'deck':
      gradle_cmd = './gradlew build -PskipTests'
    else:
      gradle_cmd = './gradlew {name}-web:installDist -x test'.format(
          name=name)

    return {
        'args': ['bash', '-c', gradle_cmd],
        'env': env_vars_list,
        'name': self.options.build_container_base_image
    }

  def __derive_gcb_config(self, name, version):
    """Helper function for repository_main."""
    orig_name = name
    options = self.options
    is_spinnaker_monitoring = name == 'spinnaker-monitoring'
    if is_spinnaker_monitoring:
      name = 'monitoring-daemon'
      dirname = 'spinnaker-monitoring-daemon'
      dockerfile = 'Dockerfile'
    else:
      dirname = '.'
      dockerfile = 'Dockerfile.slim'

    dockerfile_path = os.path.join(
        self._get_nebula_repository_path(orig_name),
        dirname,
        dockerfile)
    if not os.path.exists(dockerfile_path):
      logging.warning('No GCB config for %s because there is no %s',
                      orig_name, dockerfile)
      return None

    env_vars_list = [env
                     for env in options.build_container_env_vars.split(',')
                     if env]


    versioned_image = '{reg}/{repo}:{tag}'.format(
        reg=options.build_docker_registry, repo=name, tag=version)
    has_gradle_step = not is_spinnaker_monitoring
    steps = ([self.__make_gradle_gcb_step(name, env_vars_list)]
             if has_gradle_step
             else [])
    steps.append({
        'args': ['build', '-t', versioned_image, '-f', dockerfile, '.'],
        'dir': dirname,
        'env': env_vars_list,
        'name': 'gcr.io/cloud-builders/docker'
    })

    config = {
        'images': [versioned_image],
        'timeout': '3600s',
        'steps': steps
    }

    return yaml.dump(config, default_flow_style=True)


class BuildContainerFactory(GradleCommandFactory):
  """Implements the build_containers command."""

  @staticmethod
  def add_bom_parser_args(parser, defaults):
    """Adds publishing arguments of interest to the BOM commands as well."""
    if hasattr(parser, 'added_container'):
      return
    parser.added_container = True
    GradleCommandFactory.add_bom_parser_args(parser, defaults)

    BuildContainerFactory.add_argument(
        parser, 'build_docker_registry', defaults, None,
        help='Docker registry to push the container images to.')

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildContainerFactory, self)._do_init_argparser(parser, defaults)

    self.add_bom_parser_args(parser, defaults)
    self.add_argument(
        parser, 'build_container_env_vars', defaults,
        'GRADLE_USER_HOME=/gradle_cache/.gradle',
        help='Comma-separated list of environment variable bindings'
        ' to set when performing container builds.')
    self.add_argument(
        parser, 'container_builder', defaults, 'gcb',
        choices=['docker', 'gcb', 'gcb-trigger'],
        help='Type of builder to use.')
    self.add_argument(
        parser, 'gcb_project', defaults, None,
        help='The GCP project ID to publish containers to when'
        ' using Google Container Builder.')
    self.add_argument(
        parser, 'gcb_service_account', defaults, None,
        help='Google Service Account when using the GCP Container Builder.')
    self.add_argument(
        parser, 'gcb_service_account_credentials_path', defaults, None,
        help='Path to JSON credentials for the --gcb_service_account.')
    self.add_argument(
        parser, 'build_container_base_image', defaults,
        'spinnakerrelease/gradle_cache',
        help='Base image to start from in the container builds.')


def add_bom_parser_args(parser, defaults):
  """Adds parser arguments pertaining to publishing boms."""
  BuildContainerFactory.add_bom_parser_args(parser, defaults)
  BuildDebianFactory.add_bom_parser_args(parser, defaults)


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  build_bom_containers_factory = BuildContainerFactory(
      'build_bom_containers', BuildContainerCommand,
      'Build one or more service containers from the local git repository.',
      upstream_repositories=SPINNAKER_BOM_REPOSITORIES)

  build_hal_containers_factory = BuildContainerFactory(
      'build_halyard_containers', BuildContainerCommand,
      'Build one or more service containers from the local git repository.',
      upstream_repositories=SPINNAKER_HALYARD_REPOSITORIES)

  build_bom_containers_factory.register(registry, subparsers, defaults)
  build_hal_containers_factory.register(registry, subparsers, defaults)
  BuildDebianFactory().register(registry, subparsers, defaults)
  BuildHalyardFactory().register(registry, subparsers, defaults)
