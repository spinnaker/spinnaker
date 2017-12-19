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

# pylint: disable=missing-docstring

import datetime
import os
import shutil
import tempfile
import unittest

import dateutil.parser

from buildtool.git import (
    GitRunner,
    CommitMessage,
    RemoteGitRepository,
    RepositorySummary,
    SemanticVersion
)

from buildtool.util import (
    check_subprocess,
    check_subprocess_sequence
)
TAG_VERSION_PATTERN = r'^version-[0-9]+\.[0-9]+\.[0-9]+$'

VERSION_BASE = 'version-0.1.0'
VERSION_A = 'version-0.4.0'
VERSION_B = 'version-0.5.0'
BRANCH_A = 'branch-a'
BRANCH_B = 'branch-b'
BRANCH_BASE = 'baseline'
UPSTREAM_USER = 'unittest'
TEST_REPO_NAME = 'test_repository'

class TestGitRunner(unittest.TestCase):
  @classmethod
  def run_git(cls, command):
    return check_subprocess(
        'git -C "{dir}" {command}'.format(dir=cls.git_dir, command=command))

  @classmethod
  def setUpClass(cls):
    cls.git = GitRunner()
    cls.base_temp_dir = tempfile.mkdtemp(prefix='git_test')
    cls.git_dir = os.path.join(cls.base_temp_dir, UPSTREAM_USER, TEST_REPO_NAME)
    os.makedirs(cls.git_dir)

    git_dir = cls.git_dir
    gitify = lambda args: 'git -C "{dir}" {args}'.format(dir=git_dir, args=args)
    check_subprocess_sequence([
        gitify('init'),
        'touch "{dir}/base_file"'.format(dir=git_dir),
        gitify('add "{dir}/base_file"'.format(dir=git_dir)),
        gitify('commit -a -m "feat(test): added file"'),
        gitify('tag {base_version} HEAD'.format(base_version=VERSION_BASE)),
        gitify('checkout -b {base_branch}'.format(base_branch=BRANCH_BASE)),
        gitify('checkout -b {a_branch}'.format(a_branch=BRANCH_A)),
        'touch "{dir}/a_file"'.format(dir=git_dir),
        gitify('add "{dir}/a_file"'.format(dir=git_dir)),
        gitify('commit -a -m "feat(test): added a_file"'),
        gitify('tag {a_version} HEAD'.format(a_version=VERSION_A)),
        gitify('checkout -b {b_branch}'.format(b_branch=BRANCH_B)),
        'touch "{dir}/b_file"'.format(dir=git_dir),
        gitify('add "{dir}/b_file"'.format(dir=git_dir)),
        gitify('commit -a -m "feat(test): added b_file"'),
        gitify('tag {b_version} HEAD'.format(b_version=VERSION_B))])

  @classmethod
  def tearDownClass(cls):
    shutil.rmtree(cls.base_temp_dir)

  def setUp(self):
    self.run_git('checkout master'.format(dir=self.git_dir))

  def test_query_local_repository_branch(self):
    initial_branch = self.git.query_local_repository_branch(self.git_dir)
    self.assertEqual('master', initial_branch)

    self.run_git('checkout -b branch_test')
    final_branch = self.git.query_local_repository_branch(self.git_dir)
    self.assertEqual('branch_test', final_branch)

  def test_determine_tag_at_head(self):
    # pylint: disable=too-many-locals
    git = self.git
    all_tags = git.query_tag_commits(self.git_dir, TAG_VERSION_PATTERN)
    test_method = git.query_local_repository_commits_to_existing_tag_from_id

    tests = [(None, VERSION_BASE),
             (BRANCH_A, VERSION_A),
             (BRANCH_B, VERSION_B)]
    for branch, version in tests:
      if branch is not None:
        self.run_git('checkout ' + branch)
        commit_id = git.query_local_repository_commit_id(self.git_dir)
        tag, messages = test_method(self.git_dir, commit_id, all_tags)
        self.assertEquals([], messages)
        self.assertEquals(version, tag)

  def test_same_repo(self):
    variants = [
        'http://github.com/user/spinnaker',
        'http://github.com/user/spinnaker.git',
        'https://github.com/user/spinnaker',
        'https://github.com/user/spinnaker.git',
        'git@github.com:user/spinnaker.git',
        'git@github.com:user/spinnaker.git'
    ]
    for url in variants:
      self.assertTrue(GitRunner.same_repo(variants[0], url))

  def test_different_repo(self):
    variants = [
        'http://github.com/user/spinnaker',
        'http://github.com/path/user/spinnaker',
        'http://github.com/user/spinnaker/path',
        'http://github.com/user/spinnaker.github',
        'http://github/user/spinnaker',
        'http://mydomain.com/user/spinnaker',
        'path/user/spinnaker'
    ]
    for url in variants[1:]:
      self.assertFalse(GitRunner.same_repo(variants[0], url))

  def test_determine_tag_at_patch(self):
    git = self.git
    test_method = git.query_local_repository_commits_to_existing_tag_from_id

    tests = [(BRANCH_A, VERSION_A),
             (BRANCH_B, VERSION_B)]
    for branch, version in tests:
      new_version = str(version)
      new_version = new_version[:-1] + '1'
      self.run_git('checkout ' + branch)
      self.run_git('checkout -b {branch}-patch'.format(branch=branch))
      pending_messages = []
      for change in ['first', 'second']:
        new_path = os.path.join(self.git_dir, change + '_file')
        check_subprocess('touch "{path}"'.format(path=new_path))
        self.run_git('add "{path}"'.format(path=new_path))
        message = 'fix(test): Made {change} change for testing.'.format(
            change=change)
        self.run_git('commit -a -m "{message}"'.format(message=message))
        pending_messages.append(' '*4 + message)

      commit_id = git.query_local_repository_commit_id(self.git_dir)

      # The pending change shows up for the old tag (and are most recent first)
      all_tags = git.query_tag_commits(self.git_dir, TAG_VERSION_PATTERN)
      tag, messages = test_method(self.git_dir, commit_id, all_tags)
      self.assertEquals(version, tag)
      self.assertEquals(len(pending_messages), len(messages))
      self.assertEquals(sorted(pending_messages, reverse=True),
                        [m.message for m in messages])

      # When we re-tag at this change,
      # the new tag shows up without pending change.
      self.run_git('tag {version} HEAD'.format(version=new_version))
      all_tags = git.query_tag_commits(self.git_dir, TAG_VERSION_PATTERN)

      tag, messages = test_method(self.git_dir, commit_id, all_tags)
      self.assertEquals(new_version, tag)
      self.assertEquals([], messages)

  def test_clone_upstream(self):
    git = self.git
    test_parent = os.path.join(self.base_temp_dir, 'test_clone_upstream')
    os.makedirs(test_parent)

    test_dir = os.path.join(test_parent, TEST_REPO_NAME)
    git.clone_repository_to_path(self.git_dir, test_dir)
    self.assertTrue(os.path.exists(os.path.join(test_dir, 'base_file')))

    want_tags = git.query_tag_commits(self.git_dir, TAG_VERSION_PATTERN)
    have_tags = git.query_tag_commits(test_dir, TAG_VERSION_PATTERN)
    self.assertEquals(want_tags, have_tags)

    got = check_subprocess('git -C "{dir}" remote -v'.format(dir=test_dir))
    # Disable pushes to the origni
    # No upstream since origin is upstream
    self.assertEquals(
        '\n'.join([
            'origin\t{origin} (fetch)'.format(origin=self.git_dir),
            'origin\tdisabled (push)'
            ]),
        got)

    reference = git.determine_remote_git_repository(test_dir)
    self.assertEquals(
        RemoteGitRepository.make_from_url(self.git_dir),
        reference)

  def test_clone_origin(self):
    git = self.git

    # Make the origin we're going to test the clone against
    # This is intentionally different from upstream so that
    # we can confirm that upstream is also setup properly.
    origin_user = 'origin_user'
    origin_basedir = os.path.join(self.base_temp_dir, origin_user)
    os.makedirs(origin_basedir)
    check_subprocess(
        'git -C "{origin_dir}" clone "{upstream}"'.format(
            origin_dir=origin_basedir, upstream=self.git_dir))

    test_parent = os.path.join(self.base_temp_dir, 'test_clone_origin')
    os.makedirs(test_parent)

    test_dir = os.path.join(test_parent, TEST_REPO_NAME)
    origin_dir = os.path.join(origin_basedir, TEST_REPO_NAME)
    self.git.clone_repository_to_path(
        origin_dir, test_dir, upstream_url=self.git_dir)

    want_tags = git.query_tag_commits(self.git_dir, TAG_VERSION_PATTERN)
    have_tags = git.query_tag_commits(test_dir, TAG_VERSION_PATTERN)
    self.assertEquals(want_tags, have_tags)

    got = check_subprocess('git -C "{dir}" remote -v'.format(dir=test_dir))

    # Upstream repo is configured for pulls, but not for pushes.
    self.assertEquals(
        '\n'.join([
            'origin\t{origin} (fetch)'.format(origin=origin_dir),
            'origin\t{origin} (push)'.format(origin=origin_dir),
            'upstream\t{upstream} (fetch)'.format(upstream=self.git_dir),
            'upstream\tdisabled (push)'
            ]),
        got)

    reference = git.determine_remote_git_repository(test_dir)
    self.assertEquals(
        RemoteGitRepository.make_from_url(
            origin_dir,
            upstream_ref=RemoteGitRepository.make_from_url(self.git_dir)),
        reference)

  def test_clone_branch(self):
    test_parent = os.path.join(self.base_temp_dir, 'test_clone_branch')
    os.makedirs(test_parent)

    test_dir = os.path.join(test_parent, TEST_REPO_NAME)
    self.git.clone_repository_to_path(
        self.git_dir, test_dir, branch=BRANCH_A)
    self.assertEquals(BRANCH_A,
                      self.git.query_local_repository_branch(test_dir))

  def test_branch_not_found_exception(self):
    test_parent = os.path.join(self.base_temp_dir, 'test_bad_branch')
    os.makedirs(test_parent)
    test_dir = os.path.join(test_parent, TEST_REPO_NAME)
    self.assertFalse(os.path.exists(test_dir))

    branch = 'Bogus'
    regexp = r"Branches \['{branch}'\] do not exist in {url}\.".format(
        branch=branch, url=self.git_dir)

    with self.assertRaisesRegexp(Exception, regexp):
      self.git.clone_repository_to_path(
          self.git_dir, test_dir, branch=branch)
    self.assertFalse(os.path.exists(test_dir))

  def test_clone_failure(self):
    test_dir = os.path.join(
        self.base_temp_dir, 'clone_failure', TEST_REPO_NAME)
    os.makedirs(test_dir)
    with open(os.path.join(test_dir, 'something'), 'w') as f:
      f.write('not empty')

    regexp = '.* clone .*'
    with self.assertRaisesRegexp(Exception, regexp):
      self.git.clone_repository_to_path(
          self.git_dir, test_dir, branch='master')

  def test_default_branch(self):
    test_parent = os.path.join(self.base_temp_dir, 'test_default_branch')
    os.makedirs(test_parent)
    test_dir = os.path.join(test_parent, TEST_REPO_NAME)

    self.git.clone_repository_to_path(
        self.git_dir, test_dir, branch='Bogus', default_branch=BRANCH_B)
    self.assertEquals(BRANCH_B,
                      self.git.query_local_repository_branch(test_dir))


class TestSemanticVersion(unittest.TestCase):
  def test_semver_make_valid(self):
    tests = [('simple-1.0.0', SemanticVersion('simple', 1, 0, 0)),
             ('another-10.11.12', SemanticVersion('another', 10, 11, 12))]
    for tag, expect in tests:
      semver = SemanticVersion.make(tag)
      self.assertEquals(semver, expect)
      self.assertEquals(tag, semver.to_tag())
      self.assertEquals(tag[tag.rfind('-') + 1:], semver.to_version())

  def test_semver_next(self):
    semver = SemanticVersion('A', 1, 2, 3)
    tests = [
        (SemanticVersion.TAG_INDEX, SemanticVersion('B', 1, 2, 3), None),
        (None, SemanticVersion('A', 1, 2, 3), None),

        (SemanticVersion.MAJOR_INDEX,
         SemanticVersion('A', 2, 2, 3),
         SemanticVersion('A', 2, 0, 0)),  # next major index to semver

        (SemanticVersion.MINOR_INDEX,
         SemanticVersion('A', 1, 3, 3),
         SemanticVersion('A', 1, 3, 0)),  # next minor index to semver

        (SemanticVersion.PATCH_INDEX,
         SemanticVersion('A', 1, 2, 4),
         SemanticVersion('A', 1, 2, 4)),  # next patch index to semver
    ]
    for expect_index, test, next_semver in tests:
      self.assertEquals(expect_index, semver.most_significant_diff_index(test))
      self.assertEquals(expect_index, test.most_significant_diff_index(semver))
      if expect_index is not None and expect_index > SemanticVersion.TAG_INDEX:
        self.assertEquals(next_semver, semver.next(expect_index))


class TestCommitMessage(unittest.TestCase):
  PATCH_BRANCH = 'patch_branch'
  MINOR_BRANCH = 'minor_branch'
  MAJOR_BRANCH = 'major_branch'
  MERGED_BRANCH = 'merged_branch'

  @classmethod
  def run_git(cls, command):
    return check_subprocess(
        'git -C "{dir}" {command}'.format(dir=cls.git_dir, command=command))

  @classmethod
  def setUpClass(cls):
    cls.git = GitRunner()
    cls.base_temp_dir = tempfile.mkdtemp(prefix='git_test')
    cls.git_dir = os.path.join(cls.base_temp_dir, 'commit_message_test')
    os.makedirs(cls.git_dir)

    git_dir = cls.git_dir
    gitify = lambda args: 'git -C "{dir}" {args}'.format(dir=git_dir, args=args)
    check_subprocess_sequence([
        gitify('init'),
        'touch "{dir}/base_file"'.format(dir=git_dir),
        gitify('add "{dir}/base_file"'.format(dir=git_dir)),
        gitify('commit -a -m "feat(test): added file"'),
        gitify('tag {base_version} HEAD'.format(base_version=VERSION_BASE)),
        gitify('checkout -b {patch_branch}'.format(
            patch_branch=cls.PATCH_BRANCH)),
        'touch "{dir}/patch_file"'.format(dir=git_dir),
        gitify('add "{dir}/patch_file"'.format(dir=git_dir)),
        gitify('commit -a -m "fix(testA): added patch_file"'),
        gitify('checkout -b {minor_branch}'.format(
            minor_branch=cls.MINOR_BRANCH)),
        'touch "{dir}/minor_file"'.format(dir=git_dir),
        gitify('add "{dir}/minor_file"'.format(dir=git_dir)),
        gitify('commit -a -m "chore(testB): added minor_file"'),
        gitify('checkout -b {major_branch}'.format(
            major_branch=cls.MAJOR_BRANCH)),
        'touch "{dir}/major_file"'.format(dir=git_dir),
        gitify('add "{dir}/major_file"'.format(dir=git_dir)),
        gitify('commit -a -m'
               ' "feat(testC): added major_file\n'
               '\nInterestingly enough, this is a BREAKING CHANGE.'
               '"'),
        gitify('checkout -b {merged_branch}'.format(
            merged_branch=cls.MERGED_BRANCH)),
        gitify('reset --hard HEAD~3'),
        gitify('merge --squash HEAD@{1}')
    ])
    env = dict(os.environ)
    if os.path.exists('/bin/true'):
      env['EDITOR'] = '/bin/true'
    elif os.path.exists('/usr/bin/true'):
      env['EDITOR'] = '/usr/bin/true'
    else:
      raise NotImplementedError('platform not supported for this test')
    check_subprocess('git -C "{dir}" commit'.format(dir=git_dir), env=env)

  @classmethod
  def tearDownClass(cls):
    shutil.rmtree(cls.base_temp_dir)

  def setUp(self):
    self.run_git('checkout master'.format(dir=self.git_dir))

  def test_merged_branch(self):
    git = self.git
    all_tags = git.query_tag_commits(self.git_dir, TAG_VERSION_PATTERN)
    self.run_git('checkout {branch}'.format(branch=self.MERGED_BRANCH))
    commit_id = git.query_local_repository_commit_id(self.git_dir)
    tag, messages = git.query_local_repository_commits_to_existing_tag_from_id(
        self.git_dir, commit_id, all_tags)
    self.assertEqual(1, len(messages))
    normalized_messages = CommitMessage.normalize_message_list(messages)
    self.assertEqual(3, len(normalized_messages))

    expected_messages = [
        (' '*4 + 'feat(testC): added major_file\n'
         '\n' + ' '*4 + 'Interestingly enough, this is a BREAKING CHANGE.'),
        ' '*4 + 'chore(testB): added minor_file',
        ' '*4 + 'fix(testA): added patch_file'
    ]

    prototype = messages[0]
    for index, msg in enumerate(normalized_messages):
      self.assertEqual(prototype.author, msg.author)
      self.assertEqual(expected_messages[index], msg.message)

      # The dates might be off by one sec because of roundoff with
      # the sequential commits. Typically the times will be the same
      # but we check for the drift explicitly so the assertion failure
      # would make more sense should it be neither.
      prototype_date = dateutil.parser.parse(prototype.date)
      msg_date = dateutil.parser.parse(msg.date)
      one_sec = datetime.timedelta(0, 1)
      if msg_date + one_sec != prototype_date:
        self.assertEqual(prototype.date, msg.date)

  def test_message_analysis(self):
    # pylint: disable=line-too-long
    git = self.git
    all_tags = git.query_tag_commits(self.git_dir, TAG_VERSION_PATTERN)

    tests = [(self.MAJOR_BRANCH, SemanticVersion.MAJOR_INDEX),
             (self.MINOR_BRANCH, SemanticVersion.MINOR_INDEX),
             (self.PATCH_BRANCH, SemanticVersion.PATCH_INDEX)]
    for branch, expect in tests:
      self.run_git('checkout {branch}'.format(branch=branch))
      commit_id = git.query_local_repository_commit_id(self.git_dir)
      tag, messages = git.query_local_repository_commits_to_existing_tag_from_id(
          self.git_dir, commit_id, all_tags)
      self.assertEquals(
          expect,
          CommitMessage.determine_semver_implication_on_list(
              messages,
              major_regexs=CommitMessage.DEFAULT_MAJOR_REGEXS,
              minor_regexs=CommitMessage.DEFAULT_MINOR_REGEXS,
              patch_regexs=CommitMessage.DEFAULT_PATCH_REGEXS))
      self.assertEquals(
          expect,
          CommitMessage.determine_semver_implication_on_list(
              sorted(messages, reverse=True),
              major_regexs=CommitMessage.DEFAULT_MAJOR_REGEXS,
              minor_regexs=CommitMessage.DEFAULT_MINOR_REGEXS,
              patch_regexs=CommitMessage.DEFAULT_PATCH_REGEXS))


class TestRepositorySummary(unittest.TestCase):
  def test_to_yaml(self):
    summary = RepositorySummary(
        'abcd1234', 'mytag-987', '0.0.1',
        [CommitMessage('commit-abc', 'author', 'date', 'commit message')])

    expect = """commit_id: {id}
tag: {tag}
version: {version}
""".format(id=summary.commit_id, tag=summary.tag, version=summary.version)
    self.assertEquals(expect, summary.to_yaml(with_commit_messages=False))


if __name__ == '__main__':
  import logging
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  unittest.main(verbosity=2)
