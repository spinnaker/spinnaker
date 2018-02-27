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

"""Helper module for running gradle commands."""

import base64
import logging
import os
import re
import urllib2

from buildtool import (
    RepositoryCommandFactory,
    RepositoryCommandProcessor,
    GitRunner,

    add_parser_argument,
    check_subprocesses_to_logfile,
    raise_and_log_error,
    ConfigError,
    ResponseError)


class GradleMetricsUpdater(object):
  """Collect gradle metrics, focusing on characterizing failures."""

  def __init__(
      self, metrics_repository, spinnaker_repository, gradle_context_name):
    self.__metrics = metrics_repository
    self.__name = spinnaker_repository.name
    self.__context = gradle_context_name

  def __call__(self, retcode, output):
    """Update metrics considering the final return code and process output."""
    labels = self.determine_labels(retcode, output)
    return self.__metrics.inc_counter('GradleOutcome', labels)

  def __extract_task_failure(self, output):
    """Extract the task failure, if any."""
    summary_match = re.search(
        "Execution failed for task '(.+)'.*\n", output, re.MULTILINE)
    if not summary_match:
      return None, None

    failed_task = summary_match.group(1)
    remainder = output[summary_match.end(0):]
    first_line = remainder[:remainder.find('\n')]
    logging.debug('Instrumenting failure after stdout[%d..%d]: first_line="%s"',
                  summary_match.start(0), summary_match.end(0), first_line)
    return failed_task, first_line

  def extract_failure_summary(self, retcode, output):
    """Determine the top level failure message from gradle."""
    if not retcode:
      return None, None
    task, first_line = self.__extract_task_failure(output)
    if task is not None:
      return task, first_line
    errno_match = re.search("error=", output)
    if errno_match:
      return 'unknown-task', errno_match.group(0)
    return 'unknown-task', 'unknown-error'

  def determine_labels(self, retcode, output):
    """Determine label bindings for this outcome."""
    labels = {
        'repository': self.__name,
        'context': self.__context,
        'success': retcode == 0,
        'failed_task': '',
        'failed_by': '',
        'failed_reason': ''
    }
    if retcode == 0:
      return labels

    failed_task, summary_line = self.extract_failure_summary(retcode, output)
    self.update_failure_cause(labels, failed_task, summary_line)

    return labels

  def __update_http_failure_cause(self, labels, summary_line):
    """Figure out the labels from the failure summary line."""
    # This is an example of an error (wrapped from one to multiple lines
    # for readability here. The ...jar was a long path to the bintray jar.
    #
    # > Could not upload to \
    # 'https://api.bintray.com/content/...jar': HTTP/1.1 409 Conflict \
    # [message:Unable to upload files: An artifact with the path \
    # 'com/netflix/spinnaker/echo/echo-core/1.542.0/echo-core-1.542.0.jar' \
    # already exists]

    summary_match = re.match(
        r"[> ]*(.*?)'(.+?)': HTTP/[0-9\.]+ ([0-9]{3}) ([\w]+)(.*)",
        summary_line)
    if not summary_match:
      return False

    # abstract = summary_match.group(1)
    thing = summary_match.group(2)
    abstract_http = summary_match.group(3)
    # abstract_category = summary_match.group(4)
    if thing.find('bintray.com') >= 0:
      logging.debug('Counting another bintray failure.')
      labels['failed_by'] = 'bintray'
    labels['failed_reason'] = abstract_http
    return True

  def __update_error_failure_cause(self, labels, summary_line):
    errno_match = re.match(
        r".*error='(.+?)' \(errno=(\d+)\).*",
        summary_line)
    if errno_match:
      cause = errno_match.group(1)
      errno = errno_match.group(2)
      logging.debug('FAILED errno=%s cause="%s"'
                    '\nSUMMARY=%s', errno, cause, summary_line)
      labels['failed_by'] = 'other_runtime'
      labels['failed_reason'] = cause

    return False

  def update_failure_cause(self, labels, failed_task, summary_line):
    """Figure out the labels from the failure summary line."""

    labels['failed_task'] = failed_task
    labels['failed_by'] = 'unknown'
    labels['failed_reason'] = 'unknown'
    if self.__update_http_failure_cause(labels, summary_line):
      return
    if self.__update_error_failure_cause(labels, summary_line):
      return
    logging.error('Cannot interpret cause of failure:\n%s', summary_line)
    return



class GradleRunner(object):
  """Helper module for running gradle."""

  @staticmethod
  def add_parser_args(parser, defaults):
    """Add parser arguments for gradle."""
    if hasattr(parser, 'added_gradle_runner'):
      return
    parser.added_gradle_runner = True

    add_parser_argument(
        parser, 'bintray_jar_repository', defaults, None,
        help='bintray repository in the bintray_org to publish jar files into.')
    add_parser_argument(
        parser, 'gradle_cache_path', defaults,
        '{home}/.gradle'.format(home=os.environ['HOME'])
        if os.environ.get('HOME') else None,
        help='Path to a gradle cache directory to use for the builds.')

    add_parser_argument(
        parser, 'maven_custom_init_file', defaults,
        os.path.join(os.path.dirname(__file__), '..', 'maven-init.gradle'),
        help='Path to a gradle init file to add to the debian builds.'
        ' Used to specify any custom behavior in the gradle builds.'
        ' Argument is a file path relative to the directory this script is'
        ' executed in.'
        ' The default value assumes we run this script from the parent'
        ' directory of spinnaker/spinnaker.')

  @property
  def source_code_manager(self):
    """Return bound source code manager."""
    return self.__scm

  def __init__(self, options, scm, metrics):
    self.__options = options
    self.__metrics = metrics
    self.__git = GitRunner(options)
    self.__scm = scm

  def __to_bintray_url(self, repo, package_name, repository,
                       build_version=None, build_number=None):
    """Return the url for the desired versioned repository in bintray repo."""
    if not build_version:
      source_info = self.__scm.lookup_source_info(repository)
      build_number = build_number or source_info.build_number
      build_version = '%s-%s' % (source_info.summary.version, build_number)

    bintray_path = (
        'packages/{subject}/{repo}/{package}/versions/{version}'.format(
            subject=self.__options.bintray_org,
            package=package_name, repo=repo, version=build_version))
    return 'https://api.bintray.com/' + bintray_path

  def __add_bintray_auth_header(self, request):
    """Adds bintray authentication header to the request."""
    user = os.environ['BINTRAY_USER']
    password = os.environ['BINTRAY_KEY']
    encoded_auth = base64.encodestring('{user}:{password}'.format(
        user=user, password=password))[:-1]  # strip eoln
    request.add_header('Authorization', 'Basic ' + encoded_auth)

  def bintray_repo_has_version(self, repo, package_name, repository,
                               build_version=None,
                               build_number=None):
    """See if the given bintray repository has the package version to build."""
    try:
      bintray_url = self.__to_bintray_url(repo, package_name, repository,
                                          build_version=build_version,
                                          build_number=build_number)
      logging.debug('Checking for %s', bintray_url)
      request = urllib2.Request(url=bintray_url)
      self.__add_bintray_auth_header(request)
      urllib2.urlopen(request)
      return True
    except urllib2.HTTPError as ex:
      if ex.code == 404:
        return False
      raise_and_log_error(
          ResponseError('Bintray failure: {}'.format(ex),
                        server='bintray.check'),
          'Failed on url=%s: %s' % (bintray_url, ex.message))
    except Exception as ex:
      raise


  def consider_debian_on_bintray(self, repository,
                                 build_version=None,
                                 build_number=None):
    """Check whether desired version already exists on bintray."""
    options = self.__options
    exists = []
    missing = []

    # technically we publish to both maven and debian repos.
    # we can be in a state where we are in one but not the other.
    # let's not worry about this for now.
    for bintray_repo in [options.bintray_debian_repository]:#,
#                         options.bintray_jar_repository]:
      package_name = repository.name
      if bintray_repo == options.bintray_debian_repository:
        if package_name == 'spinnaker-monitoring':
          package_name = 'spinnaker-monitoring-daemon'
        elif not package_name.startswith('spinnaker'):
          package_name = 'spinnaker-' + package_name
      if self.bintray_repo_has_version(
          bintray_repo, package_name, repository,
          build_version=build_version,
          build_number=build_number):
        exists.append(bintray_repo)
      else:
        missing.append(bintray_repo)

    if exists:
      if options.skip_existing:
        if missing:
          raise_and_log_error(
              ConfigError('Have {name} version for {exists} but not {missing}'
                          .format(name=repository.name,
                                  exists=exists[0], missing=missing[0])))
        logging.info('Already have %s -- skipping build', repository.name)
        labels = {'repository': repository.name, 'artifact': 'debian'}
        self.__metrics.inc_counter('ReuseArtifact', labels)
        return True

      if options.delete_existing:
        for repo in exists:
          self.bintray_repo_delete_version(repo, package_name, repository,
                                           build_version=build_version)
      else:
        raise_and_log_error(
            ConfigError('Already have debian for {name}'.format(
                name=repository.name)))
    return False

  def bintray_repo_delete_version(self, repo, package_name, repository,
                                  build_version=None):
    """Delete the given bintray repository version if it exsts."""
    try:
      bintray_url = self.__to_bintray_url(repo, package_name, repository,
                                          build_version=build_version)
      logging.debug('Checking for %s', bintray_url)
      request = urllib2.Request(url=bintray_url)
      request.get_method = lambda: 'DELETE'
      self.__add_bintray_auth_header(request)

      labels = {
          'repo': repo,
          'repository': repository.name,
          'artifact': 'debian'
      }
      self.__metrics.count_call(
          'DeleteArtifact', labels, urllib2.urlopen, request)
      return True
    except urllib2.HTTPError as ex:
      if ex.code == 404:
        return True
      raise_and_log_error(
          ResponseError('Bintray failure: {}'.format(ex),
                        server='bintray.delete'),
          'Failed on url=%s: %s' % (bintray_url, ex.message))

  def get_common_args(self):
    """Return standard gradle args."""
    options = self.__options
    args = [
        '--stacktrace',
        '--info',
        '-Prelease.useLastTag=true',
    ]

    if options.maven_custom_init_file:
      # Note, this was only debians, not for rpms before.
      args.append('-I {}'.format(options.maven_custom_init_file))

    return args

  def get_debian_args(self, distribution):
    """Return the debian args for the given distribution name."""
    bintray_key = os.environ['BINTRAY_KEY']
    bintray_user = os.environ['BINTRAY_USER']
    options = self.__options
    bintray_org = options.bintray_org
    jar_repo = options.bintray_jar_repository
    debian_repo = options.bintray_debian_repository

    args = [
        '-PbintrayOrg="{org}"'.format(org=bintray_org),
        '-PbintrayPackageRepo="{repo}"'.format(repo=debian_repo),
        '-PbintrayJarRepo="{jarRepo}"'.format(jarRepo=jar_repo),
        '-PbintrayKey="{key}"'.format(key=bintray_key),
        '-PbintrayUser="{user}"'.format(user=bintray_user),
        '-PbintrayPackageDebDistribution={distribution}'.format(
            distribution=distribution)
    ]

    return args

  def check_run(self, args, command_processor, repository, target, context,
                gradle_dir=None, **kwargs):
    """Run the gradle command on the given repository."""
    gradle_dir = gradle_dir or repository.git_dir
    version = kwargs.pop('version', None)
    build_number = kwargs.pop('build_number', None)
    build_number = self.prepare_local_git_for_nebula(
        gradle_dir, repository, version=version, build_number=build_number)

    full_args = list(args)
    full_args.append('-PbintrayPackageBuildNumber=%s' % build_number)

    name = repository.name
    logfile = command_processor.get_logfile_path(name + '-' + context)
    cmd = './gradlew {args} {target}'.format(
        args=' '.join(full_args), target=target)

    labels = {
        'repository': repository.name,
        'context': context,
        'target': target
    }
    self.__metrics.time_call(
        'GradleBuild', labels, self.__metrics.default_determine_outcome_labels,
        check_subprocesses_to_logfile,
        name + ' gradle ' + context, logfile, [cmd], cwd=gradle_dir,
        postprocess_hook=GradleMetricsUpdater(self.__metrics,
                                              repository, target))

  def prepare_local_git_for_nebula(
      self, gradle_dir, repository, version=None, build_number=None):
    """Tag the repository with the version we want to build.

    Args:
      version: optional version to tag with. If not provided then infer it.
      gradle_dir: The dir to prepare if supplied
    """
    git_dir = gradle_dir or repository.git_dir
    self.__scm.ensure_local_repository(repository)
    self.__git.remove_all_non_version_tags(repository, git_dir=git_dir)

    if not build_number:
      build_number = self.__scm.determine_build_number(repository)
    if not version:
      build_version = self.__scm.get_repository_service_build_version(
          repository)
    else:
      build_version = '%s-%s' % (version, build_number)

    logging.debug('Tagging repository %s with "%s" for nebula',
                  git_dir, build_version)
    self.__git.tag_head(git_dir, build_version)
    return build_number


class GradleCommandFactory(RepositoryCommandFactory):
  """Base class for build commands using Gradle."""
  # pylint: disable=too-few-public-methods

  @staticmethod
  def add_bom_parser_args(parser, defaults):
    """Adds publishing arguments of interest to the BOM commands as well."""
    if hasattr(parser, 'added_gradle_bom'):
      return
    parser.added_gradle_bom = True

    GradleCommandFactory.add_argument(
        parser, 'bintray_org', defaults, None,
        help='The bintray organization for the bintray_*_repositories.')

    GradleCommandFactory.add_argument(
        parser, 'bintray_debian_repository', defaults, None,
        help='Repository in the --bintray_org to publish debians to.')


  def init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(GradleCommandFactory, self).init_argparser(parser, defaults)
    GradleRunner.add_parser_args(parser, defaults)
    self.add_bom_parser_args(parser, defaults)

    self.add_argument(
        parser, 'skip_existing', defaults, False, type=bool,
        help='Skip builds if the desired version already exists on bintray.')

    self.add_argument(
        parser, 'delete_existing', defaults, None, type=bool,
        help='Delete pre-existing desired versions if from bintray.')

    self.add_argument(
        parser, 'max_local_builds', defaults, None, type=int,
        help='Maximum build concurrency.')

    self.add_argument(
        parser, 'run_unit_tests', defaults, False, type=bool,
        help='Run unit tests during build for all components other than Deck.')


class GradleCommandProcessor(RepositoryCommandProcessor):
  """Base class for build commands using Gradle."""
  # pylint: disable=too-few-public-methods
  # pylint: disable=abstract-method

  @property
  def gradle(self):
    """Return bound gradle runner."""
    return self.__gradle

  def __init__(self, factory, options, **kwargs):
    super(GradleCommandProcessor, self).__init__(factory, options, **kwargs)
    self.__gradle = GradleRunner(options, self.source_code_manager,
                                 self.metrics)
