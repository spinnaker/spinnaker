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

"""Implements generate_changelog command for buildtool."""

import collections
import logging
import os
import re

import buildtool.source_commands

from buildtool.command import (
    RepositoryCommandFactory,
    RepositoryCommandProcessor)
from buildtool.git import (
    CommitMessage)
from buildtool.source_code_manager import (
    SPINNAKER_BOM_REPOSITORIES,
    SPINNAKER_HALYARD_REPOSITORIES)
from buildtool.util import (
    write_to_path)


class ChangelogRepositoryData(
    collections.namedtuple('ChangelogRepositoryData',
                           ['repository', 'summary', 'normalized_messages'])):
  """Captures the change information for a given repository."""

  def __cmp__(self, other):
    return self.repository.name.__cmp__(other.repository.name)

  def partition_commits(self):
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
        result[key] = workspace[key]
    return result


class ChangelogBuilder(object):
  """Knows how to create changelogs from git.RepositorySummary."""

  STRIP_ID_MATCHER = re.compile(r'^(.*?)\s*\(#\d+\)$')

  def __init__(self, **kwargs):
    """Constructor."""
    if kwargs:
      raise KeyError('Unrecognized arguments: {0}'.format(kwargs.keys()))
    self.__entries = []

  def clean_message(self, text):
    """Remove trailing "(#<id>)" from first line of message titles"""
    parts = text.split('\n', 1)
    if len(parts) == 1:
      first, rest = text, ''
    else:
      first, rest = parts
    match = self.STRIP_ID_MATCHER.match(first)
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

      report.extend(self.build_commits_by_type(entry))
      report.append('\n')
      report.extend(self.build_commits_by_sequence(entry))
      sep = '\n\n\n'

    return '\n'.join(report)

  def build_commits_by_type(self, entry):
    """Create a section that enumerates changes by partition type.

    Args:
      entry: [ChangelogRepositoryData] The repository to report on.

    Returns:
      list of changelog lines
    """

    report = []
    partitioned_commits = entry.partition_commits()
    report.append('### Changes by Type')
    if not partitioned_commits:
      report.append('  No Significant Changes.')
      return report

    one_liner = re.compile(r'\W*\w+\(([^\)]+)\)\s*:(.*)')
    base_url = entry.repository.url
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
    base_url = entry.repository.url
    for msg in entry.normalized_messages:
      clean_text = self.clean_message(msg.message)
      link = '[{short_hash}]({base_url}/commit/{full_hash})'.format(
          short_hash=msg.commit_id[:8], full_hash=msg.commit_id,
          base_url=base_url)
      level = msg.determine_semver_implication()
      report.append('**{level}** ({link})\n{detail}\n'.format(
          level=level_name[level], link=link, detail=clean_text))
    return report


class GenerateChangelogCommand(RepositoryCommandProcessor):
  """Implements the generate_changelog."""

  def _do_determine_source_repositories(self):
    """Implements RepositoryCommand interface."""
    upstream = SPINNAKER_BOM_REPOSITORIES
    options = self.options
    local_repository = {name: os.path.join(options.root_path, name)
                        for name in upstream.keys()}
    return {name: self.git.determine_remote_git_repository(git_dir)
            for name, git_dir in local_repository.items()}

  def _do_repository(self, repository):
    """Collect the summary for the given repository."""
    scm = self.source_code_manager
    git_dir = scm.get_local_repository_path(repository.name)
    return self.git.collect_repository_summary(git_dir)

  def _do_postprocess(self, result_dict):
    """Construct changelog from the collected summary, then write it out."""
    options = self.options
    summary_table = result_dict
    path = (options.changelog_path
            or os.path.join(options.scratch_dir, 'changelog.md'))

    builder = ChangelogBuilder()
    for name, summary in summary_table.items():
      builder.add_repository(self.source_repositories[name], summary)
    changelog_text = builder.build()
    write_to_path(changelog_text, path)
    logging.info('Wrote changelog to %s', path)


class GenerateChangelogCommandFactory(RepositoryCommandFactory):
  """Generates changelog files."""
  def __init__(self, **kwargs):
    super(GenerateChangelogCommandFactory, self).__init__(
        'generate_changelog', GenerateChangelogCommand,
        'Generate a git changelog and write it out to a file.',
        **kwargs)

  def _do_init_argparser(self, parser, defaults):
    """Adds command-specific arguments."""
    super(GenerateChangelogCommandFactory, self)._do_init_argparser(
        parser, defaults)
    buildtool.source_commands.FetchSourceCommandFactory.add_fetch_parser_args(
        parser, defaults)
    self.add_argument(
        parser, 'changelog_path', defaults, None,
        help='Write the changelog to the given path.'
             ' The default is <scratch_dir>/changelog.md')


def register_commands(registry, subparsers, defaults):
  """Registers all the commands for this module."""
  GenerateChangelogCommandFactory().register(registry, subparsers, defaults)
