#!/usr/bin/python
#
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

import argparse
import sys
import unittest

from annotate_source import Annotator, CommitTag, VersionBump

global OPTIONS # argparse Namespace

class SourceAnnotatorTest(unittest.TestCase):

  PREV_VERSION = CommitTag('z refs/tag/version-1.0.0')

  def test_patch_version(self):
    expect = VersionBump('version-1.0.1', 'a', patch=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'z'
    ]
    commit_msgs = [
      'a\n\nfix(stuff): I fixed some more stuff.',
      'z\n\nfix(stuff): I fixed some stuff.'
    ]
    result = annotator.bump_semver(self.PREV_VERSION, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

  def test_minor_version(self):
    expect = VersionBump('version-1.1.0', 'a', minor=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'b',
      'z'
    ]
    commit_msgs = [
      'a\n\nfix(stuff): I fixed some more stuff.',
      'b\n\nfeat(stuff): I added a sweet new feature.',
      'z\n\nfix(stuff): I fixed some stuff.'
    ]
    result = annotator.bump_semver(self.PREV_VERSION, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

  def test_minor_two_digit_version(self):
    expect = VersionBump('version-1.10.0', 'a', minor=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'b',
      'z'
    ]
    commit_msgs = [
      'a\n\nfix(stuff): I fixed some more stuff.',
      'b\n\nfeat(stuff): I added a sweet new feature.',
      'z\n\nfix(stuff): I fixed some stuff.'
    ]
    prev_version = CommitTag('z refs/tag/version-1.9.0')
    result = annotator.bump_semver(prev_version, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

  def test_minor_reset_patch(self):
    expect = VersionBump('version-1.10.0', 'a', minor=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'b',
      'z'
    ]
    commit_msgs = [
      'a\n\nfix(stuff): I fixed some more stuff.',
      'b\n\nfeat(stuff): I added a sweet new feature.',
      'z\n\nfix(stuff): I fixed some stuff.'
    ]
    prev_version = CommitTag('z refs/tag/version-1.9.4')
    result = annotator.bump_semver(prev_version, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

  def test_major_version(self):
    expect = VersionBump('version-2.0.0', 'a', major=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'b',
      'z'
    ]
    commit_msgs = [
      'a\n\nfix(stuff): I fixed some more stuff.',
      'b\n\nfeat(stuff): I added a sweet new feature.\n\nBREAKING CHANGE: This breaks stuff really bad.',
      'z\n\nfix(stuff): I fixed some stuff.'
    ]
    result = annotator.bump_semver(self.PREV_VERSION, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

  def test_major_reset_version(self):
    expect = VersionBump('version-2.0.0', 'a', major=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'b',
      'z'
    ]
    commit_msgs = [
      'a\n\nfix(stuff): I fixed some more stuff.',
      'b\n\nfeat(stuff): I added a sweet new feature.\n\nBREAKING CHANGE: This breaks stuff really bad.',
      'z\n\nfix(stuff): I fixed some stuff.'
    ]
    prev_version = CommitTag('z refs/tag/version-1.9.4')
    result = annotator.bump_semver(prev_version, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

  def test_major_reset_version(self):
    expect = VersionBump('version-2.0.0', 'a', major=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'b',
      'z'
    ]
    commit_msgs = [
      'a\n\nfix(stuff): I fixed some more stuff.',
      'b\n\nfeat(stuff): I added a sweet new feature.\n\nBREAKING CHANGE: This breaks stuff really bad.',
      'z\n\nfix(stuff): I fixed some stuff.'
    ]
    prev_version = CommitTag('z refs/tag/version-1.9.0')
    result = annotator.bump_semver(prev_version, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

  def test_default_msgs(self):
    expect = VersionBump('version-1.0.1', 'a', patch=True)
    annotator = Annotator(OPTIONS)
    commit_hashes = [
      'a',
      'z'
    ]
    commit_msgs = [
      'a\n\nI fixed some more stuff.',
      'z\n\nI fixed some stuff.'
    ]
    result = annotator.bump_semver(self.PREV_VERSION, commit_hashes, commit_msgs)
    self.assertEqual(expect, result)

if __name__ == '__main__':
  parser = argparse.ArgumentParser()
  Annotator.init_argument_parser(parser)
  OPTIONS = parser.parse_args()

  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(SourceAnnotatorTest)
  got = unittest.TextTestRunner(verbosity=2).run(suite)
  sys.exit(len(got.errors) + len(got.failures))
