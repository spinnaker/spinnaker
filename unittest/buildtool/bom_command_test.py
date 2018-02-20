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
import datetime
import os
import tempfile
import textwrap
import unittest
from mock import patch

import yaml

from buildtool import (
    DEFAULT_BUILD_NUMBER,
    BranchSourceCodeManager,
    GitRepositorySpec,
    RepositorySummary,
    SourceInfo)
import buildtool

import buildtool.__main__ as bomtool_main
import buildtool.bom_commands
from buildtool.bom_commands import (
    BomBuilder, BuildBomCommand)


from test_util import (
    ALL_STANDARD_TEST_BOM_REPO_NAMES,
    PATCH_BRANCH,
    PATCH_VERSION_NUMBER,
    NORMAL_REPO,
    NORMAL_SERVICE,
    OUTLIER_REPO,
    OUTLIER_SERVICE,
    BaseGitRepoTestFixture,
    init_runtime)


def load_default_bom_dependencies():
  path = os.path.join(os.path.dirname(__file__),
                      '../../dev/buildtool/bom_dependencies.yml')
  with open(path, 'r') as stream:
    return yaml.load(stream.read())

def make_default_options(options):
  options.git_branch = 'OptionBranch'
  options.github_owner = 'test-user'
  options.bom_dependencies_path = None
  options.build_number = 'OptionBuildNumber'
  options.bintray_org = 'test-bintray-org'
  options.bintray_debian_repository = 'test-debian-repo'
  options.docker_registry = 'test-docker-registry'
  options.publish_gce_image_project = 'test-image-project-name'
  return options


class TestBuildBomCommand(BaseGitRepoTestFixture):
  def setUp(self):
    super(TestBuildBomCommand, self).setUp()
    self.parser = argparse.ArgumentParser()
    self.subparsers = self.parser.add_subparsers()

  def make_test_options(self):
    options = super(TestBuildBomCommand, self).make_test_options()
    return make_default_options(options)

  def test_default_bom_options(self):
    registry = {}
    buildtool.bom_commands.register_commands(registry, self.subparsers, {})
    self.assertTrue('build_bom' in registry)
    self.assertTrue('publish_bom' in registry)

    options = self.parser.parse_args(['build_bom'])
    option_dict = vars(options)
    self.assertEquals(DEFAULT_BUILD_NUMBER, options.build_number)

    for key in ['bom_path', 'github_owner']:
      self.assertIsNone(option_dict[key])

  def test_bom_option_default_overrides(self):
    defaults = {'not_used': False}
    defaults.update(vars(self.options))

    registry = {}
    buildtool.bom_commands.register_commands(
        registry, self.subparsers, defaults)
    parsed_options = self.parser.parse_args(['build_bom'])
    parsed_option_dict = vars(parsed_options)

    self.assertTrue('not_used' not in parsed_option_dict)
    for key, value in defaults.items():
      if key in ['not_used', 'command', 'input_dir', 'output_dir']:
        continue
      self.assertEquals(value, parsed_option_dict[key])

  def test_bom_command(self):
    """Make sure when we run "build_bom" we actually get what we meant."""
    defaults = vars(make_default_options(self.options))
    defaults.update({'bom_path': 'MY PATH',
                     'github_owner': 'TestOwner',
                     'input_dir': 'TestInputRoot'})
    defaults.update({'bintray_org': 'TestBintrayOrg',
                     'bintray_debian_repository': 'TestDebianRepo',
                     'docker_registry': 'TestDockerRegistry',
                     'publish_gce_image_project': 'TestGceProject'})
    del defaults['github_filesystem_root']
    parser = argparse.ArgumentParser()
    registry = bomtool_main.make_registry([buildtool.bom_commands],
                                          parser, defaults)
    bomtool_main.add_standard_parser_args(parser, defaults)
    options = parser.parse_args(['build_bom'])

    prefix = 'http://test-domain.com/test-owner'

    make_fake = self.patch_method

    # When asked to filter the normal bom repos to determine source_repositories
    # we'll return our own fake repository as if we configured the original
    # command for it. This will also make it easier to test just the one
    # repo rather than all, and that there are no assumptions.
    mock_filter = make_fake(BuildBomCommand, 'filter_repositories')
    test_repository = GitRepositorySpec('clouddriver', commit_id='CommitA',
                                        origin=prefix + '/TestRepoA')
    mock_filter.return_value = [test_repository]

    # When the base command ensures the local repository exists, we'll
    # intercept that call and do nothing rather than the git checkouts, etc.
    make_fake(BranchSourceCodeManager, 'ensure_local_repository')
    mock_remote = self.patch_function('buildtool.branch_scm.GitRunner'
                                      '.query_remote_repository_commit_id')
    mock_remote.return_value = 'CommitA'

    # When the base command asks for the repository metadata, we'll return
    # this hardcoded info, then look for it later in the generated om.
    mock_refresh = make_fake(BranchSourceCodeManager, 'refresh_source_info')
    summary = RepositorySummary('CommitA', 'TagA', '9.8.7', '44.55.66', [])
    source_info = SourceInfo('MyBuildNumber', summary)
    mock_refresh.return_value = source_info

    # When asked to write the bom out, do nothing.
    # We'll verify the bom later when looking at the mock call sequencing.
    mock_write = self.patch_function('buildtool.bom_commands.write_to_path')

    mock_now = self.patch_function('buildtool.bom_commands.now')
    mock_now.return_value = datetime.datetime(2018, 1, 2, 3, 4, 5)

    factory = registry['build_bom']
    command = factory.make_command(options)
    command()

    # Verify source repositories were filtered
    self.assertEquals([test_repository], command.source_repositories)

    # Verify that the filter was called with the original bom repos,
    # and these repos were coming from the configured github_owner's repo.
    bom_repo_list = [
        GitRepositorySpec(
            name,
            git_dir=os.path.join('TestInputRoot', 'build_bom', name),
            origin='https://%s/TestOwner/%s' % (options.github_hostname, name),
            upstream='https://github.com/spinnaker/' + name)
        for name in sorted(['clouddriver', 'deck', 'echo', 'fiat', 'front50',
                            'gate', 'igor', 'orca', 'rosco', 'spinnaker',
                            'spinnaker-monitoring'])
    ]
    mock_remote.assert_called_once_with(test_repository.origin,
                                        options.git_branch)
    mock_filter.assert_called_once_with(bom_repo_list)
    mock_refresh.assert_called_once_with(test_repository, 'OptionBuildNumber')
    bom_text, bom_path = mock_write.call_args_list[0][0]

    self.assertEquals(bom_path, 'MY PATH')
    bom = yaml.load(bom_text)

    golden_text = textwrap.dedent("""\
        artifactSources:
          debianRepository: https://dl.bintray.com/TestBintrayOrg/TestDebianRepo
          dockerRegistry: TestDockerRegistry
          gitPrefix: http://test-domain.com/test-owner
          googleImageProject: TestGceProject
        dependencies:
        services:
          clouddriver:
            commit: CommitA
            version: 9.8.7-MyBuildNumber
        timestamp: '2018-01-02 03:04:05'
        version: OptionBranch-OptionBuildNumber
    """)
    golden_bom = yaml.load(golden_text)
    golden_bom['dependencies'] = load_default_bom_dependencies()

    for key, value in golden_bom.items():
      self.assertEquals(value, bom[key])


class TestBomBuilder(BaseGitRepoTestFixture):
  def make_test_options(self):
    options = super(TestBomBuilder, self).make_test_options()
    return make_default_options(options)

  def setUp(self):
    super(TestBomBuilder, self).setUp()
    self.test_root = os.path.join(self.base_temp_dir, self._testMethodName)
    self.scm = BranchSourceCodeManager(self.options, self.test_root)

  def test_default_build(self):
    builder = BomBuilder(self.options, self.scm)
    bom = builder.build()
    self.assertEquals(
        bom['dependencies'], load_default_bom_dependencies())

    # There are no services because we never added any.
    # Although the builder takes an SCM, you still need to explicitly add repos.
    self.assertEquals({}, bom['services'])

  def test_inject_dependencies(self):
    dependencies = {
        'DependencyA': {'version': 'vA'},
        'DependencyB': {'version': 'vB'}
    }
    fd, path = tempfile.mkstemp(prefix='bomdeps')
    os.close(fd)
    with open(path, 'w') as stream:
      yaml.dump(dependencies, stream)

    options = self.options
    options.bom_dependencies_path = path

    try:
      builder = BomBuilder(options, self.scm)
      bom = builder.build()
    finally:
      os.remove(path)

    self.assertEquals(dependencies, bom['dependencies'])
    self.assertEquals({}, bom['services'])

  def test_build(self):
    test_root = self.test_root
    options = self.options
    options.git_branch = PATCH_BRANCH
    options.github_owner = 'default'
    options.github_disable_upstream_push = True

    scm = BranchSourceCodeManager(options, test_root)
    golden_bom = dict(self.golden_bom)
    builder = BomBuilder.new_from_bom(options, scm, golden_bom)
    source_repositories = [scm.make_repository_spec(name)
                           for name in ALL_STANDARD_TEST_BOM_REPO_NAMES]
    for repository in source_repositories:
      scm.ensure_git_path(repository)
      summary = scm.git.collect_repository_summary(repository.git_dir)
      source_info = SourceInfo('SourceInfoBuildNumber', summary)
      builder.add_repository(repository, source_info)

    with patch('buildtool.bom_commands.now') as mock_now:
      mock_now.return_value = datetime.datetime(2018, 1, 2, 3, 4, 5)
      bom = builder.build()

    golden_bom['version'] = 'patch-OptionBuildNumber'
    golden_bom['timestamp'] = '2018-01-02 03:04:05'
    golden_bom['services'][NORMAL_SERVICE]['version'] = (
        PATCH_VERSION_NUMBER + '-SourceInfoBuildNumber')
    golden_bom['services'][OUTLIER_SERVICE]['version'] = (
        PATCH_VERSION_NUMBER + '-SourceInfoBuildNumber')
    golden_bom['services']['monitoring-third-party']['version'] = (
        PATCH_VERSION_NUMBER + '-SourceInfoBuildNumber')
    
    golden_bom['artifactSources'] = {
      'debianRepository': 'https://dl.bintray.com/%s/%s' % (
          options.bintray_org, options.bintray_debian_repository),
      'dockerRegistry': options.docker_registry,
      'googleImageProject': options.publish_gce_image_project,
      'gitPrefix': os.path.dirname(self.repo_commit_map[NORMAL_REPO]['ORIGIN'])
    }

    for key, value in bom['services'].items():
      self.assertEquals(value, golden_bom['services'][key])
    for key, value in bom.items():
      self.assertEquals(value, golden_bom[key])
    self.assertEquals(golden_bom, bom)

  def test_rebuild(self):
    test_root = self.test_root
    options = self.options
    options.git_branch = 'master'
    options.github_owner = 'default'
    options.github_disable_upstream_push = True
    options.build_number = 'UpdatedBuildNumber'

    scm = BranchSourceCodeManager(options, test_root)
    builder = BomBuilder.new_from_bom(options, scm, self.golden_bom)

    repository = scm.make_repository_spec(OUTLIER_REPO)
    scm.ensure_git_path(repository)
    scm.git.check_run(repository.git_dir, 'checkout ' + PATCH_BRANCH)
    summary = scm.git.collect_repository_summary(repository.git_dir)
    source_info = SourceInfo('SourceInfoBuildNumber', summary)
    builder.add_repository(repository, source_info)

    with patch('buildtool.bom_commands.now') as mock_now:
      mock_now.return_value = datetime.datetime(2018, 1, 2, 3, 4, 5)
      bom = builder.build()

    updated_service = bom['services'][OUTLIER_SERVICE]
    self.assertEquals(updated_service, {
        'commit': self.repo_commit_map[OUTLIER_REPO][PATCH_BRANCH],
        'version': PATCH_VERSION_NUMBER + '-SourceInfoBuildNumber'
        })

    # The bom should be the same as before, but with new timestamp/version
    # and our service updated. And the artifactSources to our configs.
    updated_bom = dict(self.golden_bom)
    updated_bom['timestamp'] = '2018-01-02 03:04:05'
    updated_bom['version'] = 'master-UpdatedBuildNumber'
    updated_bom['services'][OUTLIER_SERVICE] = updated_service
    updated_bom['artifactSources'] = {
        'debianRepository': 'https://dl.bintray.com/%s/%s' % (
            options.bintray_org, options.bintray_debian_repository),
        'dockerRegistry': options.docker_registry,
        'googleImageProject': options.publish_gce_image_project,
        'gitPrefix': self.golden_bom['artifactSources']['gitPrefix']
    }
    for key, value in updated_bom.items():
      self.assertEquals(value, bom[key])
    self.assertEquals(updated_bom, bom)

  def test_determine_most_common_prefix(self):
    options = self.options
    builder = BomBuilder(options, self.scm)
    self.assertIsNone(builder.determine_most_common_prefix())

    prefix = ['http://github.com/one', '/local/source/path/two']

    # Test two vs one in from different repo prefix
    # run the test twice changing the ordering the desired prefix is visible.
    for which in [0, 1]:
      repository = GitRepositorySpec(
          'RepoOne', origin=prefix[0] + '/RepoOne',
          commit_id='RepoOneCommit')
      summary = RepositorySummary('RepoOneCommit', 'RepoOneTag',
                                  '1.2.3', '1.2.2', [])
      source_info = SourceInfo('BuildOne', summary)
      builder.add_repository(repository, source_info)
      self.assertEquals(prefix[0], builder.determine_most_common_prefix())

      repository = GitRepositorySpec(
          'RepoTwo', origin=prefix[which] + '/RepoTwo',
          commit_id='RepoTwoCommit')
      summary = RepositorySummary('RepoTwoCommit', 'RepoTwoTag',
                                  '2.2.3', '2.2.3', [])
      source_info = SourceInfo('BuildTwo', summary)
      builder.add_repository(repository, source_info)

      repository = GitRepositorySpec(
          'RepoThree', origin=prefix[1] + '/RepoThree',
          commit_id='RepoThreeCommit')
      summary = RepositorySummary('RepoThreeCommit', 'RepoThreeTag',
                                  '3.2.0', '2.2.1', [])
      source_info = SourceInfo('BuildThree', summary)
      builder.add_repository(repository, source_info)
      self.assertEquals(prefix[which], builder.determine_most_common_prefix())


if __name__ == '__main__':
  init_runtime()
  unittest.main(verbosity=2)
