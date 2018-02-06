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

"""Implements changelog commands for buildtool."""

import collections
import copy
import datetime
import logging
import os
import re
import textwrap


from buildtool import (
    SPINNAKER_GITHUB_IO_REPOSITORY_NAME,

    RepositoryCommandFactory,
    RepositoryCommandProcessor,

    BomSourceCodeManager,
    BranchSourceCodeManager,

    CommitMessage,
    GitRunner,

    UnexpectedError,
    check_kwargs_empty,
    check_options_set,
    check_path_exists,
    raise_and_log_error,
    write_to_path)


BUILD_CHANGELOG_COMMAND = 'build_changelog'
TITLE_LINE_MATCHER = re.compile(r'\W*\w+\(([^\)]+)\)\s*[:-]?(.*)')


def make_options_with_fallback(options):
  """A hack for now, using git_fallback_branch to support spinnaker.github.io

  That repo does not use the release branches, rather master.
  So if creating a release, it will fallback to master for that repo.
  """
  options_copy = copy.copy(options)
  options_copy.git_fallback_branch = 'master'
  return options_copy


class ChangelogRepositoryData(
    collections.namedtuple('ChangelogRepositoryData',
                           ['repository', 'summary', 'normalized_messages'])):
  """Captures the change information for a given repository."""

  def __cmp__(self, other):
    return self.repository.name.__cmp__(other.repository.name)

  def partition_commits(self, sort=True):
    """Partition the commit messages by the type of change.

    Returns an OrderedDict of the partition ordered by significance.
    The keys in the dictionary are the type of change.
    The values are a list of git.CommitMessage.
    """
    partition_types = [
        ('Breaking Changes',
         re.compile(r'^\s*'
                    r'(.*?BREAKING CHANGE.*)',
                    re.MULTILINE)),
        ('Features',
         re.compile(r'^\s*'
                    r'(?:\*\s+)?'
                    r'((?:feat|feature)[\(:].*)',
                    re.MULTILINE)),
        ('Configuration',
         re.compile(r'^\s*'
                    r'(?:\*\s+)?'
                    r'((?:config)[\(:].*)',
                    re.MULTILINE)),
        ('Fixes',
         re.compile(r'^\s*'
                    r'(?:\*\s+)?'
                    r'((?:bug|fix)[\(:].*)',
                    re.MULTILINE)),
        ('Other',
         re.compile(r'.*'))
    ]
    workspace = {}
    for msg in self.normalized_messages:
      text = msg.message
      match = None
      for section, regex in partition_types:
        match = regex.search(text)
        if match:
          if section not in workspace:
            workspace[section] = []
          workspace[section].append(msg)
          break

    result = collections.OrderedDict()
    for spec in partition_types:
      key = spec[0]
      if key in workspace:
        result[key] = (self._sort_partition(workspace[key])
                       if sort
                       else workspace[key])
    return result

  @staticmethod
  def _sort_partition(commit_messages):
    """sorting key function for CommitMessage.

    Returns the commit messages sorted by affected component while
    preserving original ordering with the component. The affected
    component is the <THING> for titles in the form TYPE(<THING>): <MESSAGE>
    """
    thing_dict = {}
    def get_thing_list(title_line):
      """Return bucket for title_line, adding new one if needed."""
      match = TITLE_LINE_MATCHER.match(title_line)
      thing = match.group(1) if match else None
      if not thing in thing_dict:
        thing_dict[thing] = []
      return thing_dict[thing]

    for message in commit_messages:
      title_line = message.message.split('\n')[0]
      get_thing_list(title_line).append(message)

    result = []
    for thing in sorted(thing_dict.keys()):
      result.extend(thing_dict[thing])
    return result


class ChangelogBuilder(object):
  """Knows how to create changelogs from git.RepositorySummary."""

  STRIP_GITHUB_ID_MATCHER = re.compile(r'^(.*?)\s*\(#\d+\)$')

  def __init__(self, **kwargs):
    self.__entries = []
    self.__with_partition = kwargs.pop('with_partition', True)
    self.__with_detail = kwargs.pop('with_detail', False)
    check_kwargs_empty(kwargs)
    self.__sort_partitions = True

  def clean_message(self, text):
    """Remove trailing "(#<id>)" from first line of message titles"""
    parts = text.split('\n', 1)
    if len(parts) == 1:
      first, rest = text, ''
    else:
      first, rest = parts
    match = self.STRIP_GITHUB_ID_MATCHER.match(first)
    if match:
      if rest:
        return '\n'.join([match.group(1), rest])
      return match.group(1)
    return text

  def add_repository(self, repository, summary):
    """Add repository changes into the builder."""
    message_list = summary.commit_messages
    normalized_messages = CommitMessage.normalize_message_list(message_list)
    self.__entries.append(ChangelogRepositoryData(
        repository, summary, normalized_messages))

  def build(self):
    """Construct changelog."""
    report = []

    sep = ''
    for entry in sorted(self.__entries):
      summary = entry.summary
      repository = entry.repository
      commit_messages = entry.normalized_messages
      name = repository.name

      report.append('{sep}## [{title}](#{name}) {version}'.format(
          sep=sep, title=name.capitalize(), name=name,
          version=summary.version))

      if not commit_messages:
        report.append('  No Changes')
        report.append('\n\n')
        continue

      if self.__with_partition:
        report.extend(self.build_commits_by_type(entry))
        report.append('\n')
      if self.__with_detail:
        report.extend(self.build_commits_by_sequence(entry))
        report.append('\n')
      sep = '\n\n'

    return '\n'.join(report)

  def build_commits_by_type(self, entry):
    """Create a section that enumerates changes by partition type.

    Args:
      entry: [ChangelogRepositoryData] The repository to report on.

    Returns:
      list of changelog lines
    """

    report = []
    partitioned_commits = entry.partition_commits(sort=self.__sort_partitions)
    report.append('### Changes by Type')
    if not partitioned_commits:
      report.append('  No Significant Changes.')
      return report

    one_liner = TITLE_LINE_MATCHER
    base_url = entry.repository.origin
    level_marker = '#' * 4
    for title, commit_messages in partitioned_commits.items():
      report.append('{level} {title}'.format(level=level_marker, title=title))
      for msg in commit_messages:
        first_line = msg.message.split('\n', 1)[0].strip()
        clean_text = self.clean_message(first_line)
        match = one_liner.match(clean_text)
        if match:
          text = '**{thing}:**  {message}'.format(
              thing=match.group(1), message=match.group(2))
        else:
          text = clean_text

        link = '[{short_hash}]({base_url}/commit/{full_hash})'.format(
            short_hash=msg.commit_id[:8], full_hash=msg.commit_id,
            base_url=base_url)
        report.append('* {text} ({link})'.format(text=text, link=link))
      report.append('')
    return report

  def build_commits_by_sequence(self, entry):
    """Create a section that enumerates all changes in order.

    Args:
      entry: [ChangelogRepositoryData] The repository to report on.

    Returns:
      list of changelog lines
    """
    level_name = [None, 'MAJOR', 'MINOR', 'PATCH']

    report = []
    report.append('### Changes by Sequence')
    base_url = entry.repository.origin
    for msg in entry.normalized_messages:
      clean_text = self.clean_message(msg.message)
      link = '[{short_hash}]({base_url}/commit/{full_hash})'.format(
          short_hash=msg.commit_id[:8], full_hash=msg.commit_id,
          base_url=base_url)
      level = msg.determine_semver_implication()
      report.append('**{level}** ({link})\n{detail}\n'.format(
          level=level_name[level], link=link, detail=clean_text))
    return report


class BuildChangelogCommand(RepositoryCommandProcessor):
  """Implements the build_changelog."""

  def __init__(self, factory, options, **kwargs):
    # Use own repository to avoid race conditions when commands are
    # running concurrently.
    options_copy = copy.copy(options)
    options_copy.github_disable_upstream_push = True
    super(BuildChangelogCommand, self).__init__(factory, options_copy, **kwargs)

  def _do_repository(self, repository):
    """Collect the summary for the given repository."""
    return self.git.collect_repository_summary(repository.git_dir)

  def _do_postprocess(self, result_dict):
    """Construct changelog from the collected summary, then write it out."""
    options = self.options
    path = os.path.join(self.get_output_dir(), 'changelog.md')

    builder = ChangelogBuilder(with_detail=options.include_changelog_details)
    repository_map = {repository.name: repository
                      for repository in self.source_repositories}
    for name, summary in result_dict.items():
      builder.add_repository(repository_map[name], summary)
    changelog_text = builder.build()
    write_to_path(changelog_text, path)
    logging.info('Wrote changelog to %s', path)


class BuildChangelogFactory(RepositoryCommandFactory):
  """Builds changelog files."""
  def __init__(self, **kwargs):
    super(BuildChangelogFactory, self).__init__(
        BUILD_CHANGELOG_COMMAND, BuildChangelogCommand,
        'Build a git changelog and write it out to a file.',
        BomSourceCodeManager, **kwargs)

  def init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(BuildChangelogFactory, self).init_argparser(
        parser, defaults)
    self.add_argument(
        parser, 'include_changelog_details', defaults, False,
        action='store_true',
        help='Include a "details" section with the full commit messages'
             ' in time sequence in the changelog.')


class PublishChangelogFactory(RepositoryCommandFactory):
  def __init__(self, **kwargs):
    super(PublishChangelogFactory, self).__init__(
        'publish_changelog', PublishChangelogCommand,
        'Publish Spinnaker version Changelog to spinnaker.github.io.',
        BranchSourceCodeManager, **kwargs)

  def init_argparser(self, parser, defaults):
    super(PublishChangelogFactory, self).init_argparser(
        parser, defaults)
    GitRunner.add_parser_args(parser, defaults)
    GitRunner.add_publishing_parser_args(parser, defaults)
    self.add_argument(
        parser, 'spinnaker_version', defaults, None,
        help='The version of spinnaker this documentation is for.')


class PublishChangelogCommand(RepositoryCommandProcessor):
  """Implements publish_changelog."""

  def __init__(self, factory, options, **kwargs):
    super(PublishChangelogCommand, self).__init__(
        factory, make_options_with_fallback(options),
        source_repository_names=[SPINNAKER_GITHUB_IO_REPOSITORY_NAME],
        **kwargs)
    check_options_set(options, ['spinnaker_version'])
    self.__markdown_path = os.path.join(
        self.get_output_dir(command=BUILD_CHANGELOG_COMMAND),
        'changelog.md')
    check_path_exists(self.__markdown_path,
                      why='output from "%s"' % BUILD_CHANGELOG_COMMAND)

  def _do_repository(self, repository):
    if repository.name != SPINNAKER_GITHUB_IO_REPOSITORY_NAME:
      raise_and_log_error(UnexpectedError('Got "%s"' % repository.name))

    base_branch = 'master'
    self.scm.ensure_git_path(repository, branch=base_branch)
    version = self.options.spinnaker_version
    if self.options.git_allow_publish_master_branch:
      branch_flag = ''
      head_branch = 'master'
    else:
      branch_flag = '-b'
      head_branch = version + '-changelog'

    files_added = self.prepare_local_repository_files(repository)
    git_dir = repository.git_dir
    message = 'doc(changelog): Spinnaker Version ' + version

    local_git_commands = [
        # These commands are accomodating to a branch already existing
        # because the branch is on the version, not build. A rejected
        # build for some reason that is re-tried will have the same version
        # so the branch may already exist from the earlier attempt.
        'checkout ' + base_branch,
        'checkout {flag} {branch}'.format(
            flag=branch_flag, branch=head_branch),
        'add ' + ' '.join([os.path.abspath(path) for path in files_added]),
        'commit -m "{msg}"'.format(msg=message),
    ]
    logging.debug('Commiting changes into local repository "%s" branch=%s',
                  repository.git_dir, head_branch)
    git = self.git
    git.check_run_sequence(git_dir, local_git_commands)

    logging.info('Pushing branch="%s" into "%s"',
                 head_branch, repository.origin)
    git.push_branch_to_origin(git_dir, branch=head_branch)

  def prepare_local_repository_files(self, repository):
    if repository.name != SPINNAKER_GITHUB_IO_REPOSITORY_NAME:
      raise_and_log_error(UnexpectedError('Got "%s"' % repository.name))

    with open(self.__markdown_path) as f:
      detail = f.read()

    # Use the original capture time
    utc = datetime.datetime.fromtimestamp(
        os.path.getmtime(self.__markdown_path))
    timestamp = '{:%Y-%m-%d %H:%M:%S %Z}'.format(utc)

    version = self.options.spinnaker_version
    changelog_filename = '{version}-changelog.md'.format(version=version)
    target_path = os.path.join(repository.git_dir,
                               '_changelogs', changelog_filename)
    major, minor, _ = version.split('.')
    logging.debug('Adding changelog file %s', target_path)
    with open(target_path, 'w') as f:
      # pylint: disable=trailing-whitespace
      header = textwrap.dedent(
          """\
          ---
          title: Version {version}
          date: {timestamp}
          tags: changelogs {major}.{minor}
          ---
          # Spinnaker {version}
          """.format(
              version=version,
              timestamp=timestamp,
              major=major, minor=minor))
      f.write(header)
      f.write(detail)

    return [target_path]


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  BuildChangelogFactory().register(registry, subparsers, defaults)
  PublishChangelogFactory().register(registry, subparsers, defaults)
