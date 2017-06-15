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
import datetime
import os
import sys
import yaml

from annotate_source import Annotator
from spinnaker.run import run_quick

DEPENDENCIES = 'dependencies'
SERVICES = 'services'
VERSION = 'version'
COMMIT = 'commit'

ARTIFACT_SOURCES = 'artifactSources'
DEBIAN_REPOSITORY = 'debianRepository'
DOCKER_REGISTRY = 'dockerRegistry'
GOOGLE_IMAGE_PROJECT = 'googleImageProject'
GIT_PREFIX = 'gitPrefix'

BUG_FIXES = 'Bug Fixes'
FEATURES = 'Features'
BREAKING_CHANGES = 'Breaking Changes'
CONFIG_CHANGES = 'Configuration Changes'

# Dependency versions.
CONSUL_VERSION = '0.7.5'
REDIS_VERSION = '2:2.8.4-2'
VAULT_VERSION = '0.7.0'

GOOGLE_CONTAINER_BUILDER_SERVICE_BASE_CONFIG = {
  'steps': [
    {
      'name': 'java:8',
      'env': ['GRADLE_USER_HOME=cache'],
      'args': []
    },
    {
      'name': 'gcr.io/cloud-builders/docker',
      'args': []
    }
  ],
  'images': [],
  'timeout': '3600s'
}

GOOGLE_CONTAINER_BUILDER_MONITORING_BASE_CONFIG = {
  'steps': [
    {
      'name': 'gcr.io/cloud-builders/docker',
      'dir': 'spinnaker-monitoring-daemon',
      'args': []
    }
  ],
  'images': [],
  'timeout': '3600s'
}

class BomGenerator(Annotator):
  """Provides facilities for generating the Bill of Materials file for the
  Spinnaker product release.

  This assumes Halyard (https://github.com/spinnaker/halyard) is installed on
  the machine this script runs on.
  """

  COMPONENTS = [
    'clouddriver',
    'deck',
    'echo',
    'front50',
    'gate',
    'igor',
    'orca',
    'rosco',
    'fiat',
    'spinnaker-monitoring',
    'spinnaker'
  ]

  def __init__(self, options):
    self.__options = options
    self.__base_dir = options.base_dir
    self.__docker_registry = options.docker_registry
    self.__bom_file = ''
    self.__component_versions = {}
    self.__changelog_start_hashes = {} # Hashes to start from when generating changelogs.
    self.__toplevel_version = ''
    self.__changelog_output = options.changelog_output
    self.__alias = options.bom_alias
    self.__google_image_project = options.google_image_project
    self.__git_prefix = options.git_prefix
    self.__halyard_version = {'halyard': None}
    super(BomGenerator, self).__init__(options)

  @property
  def base_dir(self):
    return self.__base_dir

  @classmethod
  def init_argument_parser(cls, parser):
    """Initialize command-line arguments."""
    parser.add_argument('--base_dir', default='.', required=True,
                        help="Base directory containing the component's git repositories as subdirectories.")
    parser.add_argument('--bom_alias', default='',
                        help="Alias to rename the 'real' BOM as. This also sets the Spinnaker version as the alias.")
    parser.add_argument('--changelog_output', default='',
                        help="Output file to write the changelog to.")
    parser.add_argument('--container_builder', default='gcb',
                        help="Type of builder to use. Currently, the supported options are {'gcb', 'docker'}.")
    parser.add_argument('--docker_registry', default='',
                        help="Docker registry to push the container images to.")
    parser.add_argument('--google_image_project', default='marketplace-spinnaker-release',
                        help="GCE project we publish HA Spinnaker component images to.")
    parser.add_argument('--git_prefix', default='https://github.com/spinnaker',
                        help="Prefix to the component source URIs.")
    super(BomGenerator, cls).init_argument_parser(parser)

  def __version_from_tag(self, comp):
    """Determine the component version from the 'version-X.Y.Z' git tag.

    Args:
      comp [string]: Spinnaker component name.

    Returns:
      [string] Component version with build number and without 'version-'.
    """
    version_bump = dict(self.__component_versions.items() + self.__halyard_version.items())[comp]
    next_tag_with_build = '{0}-{1}'.format(version_bump.version_str,
                                           self.build_number)
    first_dash_idx = next_tag_with_build.index('-')
    return next_tag_with_build[first_dash_idx + 1:]

  def write_container_builder_gcr_config(self):
    """Write a configuration file for producing Container Images with Google Container Builder for each microservice.
    """
    for comp in self.__component_versions:
      if comp == 'spinnaker-monitoring':
        config = dict(GOOGLE_CONTAINER_BUILDER_MONITORING_BASE_CONFIG)
        version = self.__version_from_tag(comp)
        versioned_image = '{reg}/monitoring-daemon:{tag}'.format(reg=self.__docker_registry,
                                                                 tag=version)
        config['steps'][0]['args'] = ['build', '-t', versioned_image, '-f', 'Dockerfile', '.']
        config['images'] = [versioned_image]
        config_file = '{0}-gcb.yml'.format(comp)
        with open(config_file, 'w') as cfg:
          yaml.dump(config, cfg, default_flow_style=True)
      elif comp == 'spinnaker':
        pass
      else:
        config = dict(GOOGLE_CONTAINER_BUILDER_SERVICE_BASE_CONFIG)
        gradle_version = self.__version_from_tag(comp)

        # Gradle complains if the git tag version doesn't match the branch's version, i.e.
        # in patch releases. As a workaround, we checkout the HEAD commit of the
        # current branch in a 'detached HEAD' state so Gradle doesn't complain about
        # our branch version and git tag version not matching.
        gradle_cmd = 'git rev-parse HEAD | xargs git checkout ;'
        if comp == 'deck':
          gradle_cmd += ' ./gradlew build -PskipTests'
        else:
          gradle_cmd += ' ./gradlew {0}-web:installDist -x test'.format(comp)
        config['steps'][0]['args'] = ['bash', '-c', gradle_cmd]
        versioned_image = '{reg}/{repo}:{tag}'.format(reg=self.__docker_registry,
                                                      repo=comp,
                                                      tag=gradle_version)
        config['steps'][1]['args'] = ['build', '-t', versioned_image, '-f', 'Dockerfile.slim', '.']
        config['images'] = [versioned_image]
        config_file = '{0}-gcb.yml'.format(comp)
        with open(config_file, 'w') as cfg:
            yaml.dump(config, cfg, default_flow_style=True)

  def write_docker_version_files(self):
    """Write a file containing the full tag for each microservice for Docker.
    """
    for comp in self.__component_versions:
      if comp == 'spinnaker':
        pass
      gradle_version = self.__version_from_tag(comp)
      docker_tag = '{reg}/{comp}:{tag}'.format(reg=self.__docker_registry,
                                               comp=comp,
                                               tag=gradle_version)

      config_file = '{0}-docker.yml'.format(comp)
      with open(config_file, 'w') as cfg:
        cfg.write(docker_tag)

  def write_rpm_version_files(self):
    """Write a file containing the version number for each microservice. Used
    by build_release.py for rpm builds.
    """
    self.determine_and_tag_halyard()
    for comp in dict(self.__component_versions.items() + self.__halyard_version.items()):
      if comp == 'spinnaker':
        pass
      gradle_version = self.__version_from_tag(comp)
      version_file = '{0}-rpm-version.txt'.format(comp)
      with open(version_file, 'w') as ver:
        ver.write(gradle_version)

  def generate_changelog(self):
    """Generate a release changelog and write it to a file.

    The changelog contains a section per microservice that describes the
    changes made since the last Spinnaker release. It also contains the
    version information as well.
    """
    changelog = []
    for comp in sorted(self.__changelog_start_hashes.keys()):
      if comp == 'spinnaker':
        continue
      hash = self.__changelog_start_hashes.get(comp, None)
      version = self.__version_from_tag(comp)

      # Generate the changelog for the component.
      print 'Generating changelog for {comp}...'.format(comp=comp)
      # Assumes the remote repository is aliased as 'origin'.
      component_url = run_quick('git -C {path} config --get remote.origin.url'
                                .format(path=comp)).stdout.strip()
      if component_url.endswith('.git'):
        component_url = component_url.replace('.git', '')
      result = run_quick('cd {comp}; clog -r {url} -f {hash} --setversion {version}; cd ..'
                         .format(comp=comp, url=component_url, hash=hash, version=version))
      if result.returncode != 0:
        print "Changelog generation failed for {0} with \n{1}\n exiting...".format(comp, result.stdout)
        exit(result.returncode)
      # Capitalize
      if (BUG_FIXES in result.stdout
          or FEATURES in result.stdout
          or BREAKING_CHANGES in result.stdout
          or CONFIG_CHANGES in result.stdout):
        comp_cap = comp[0].upper() + comp[1:]
        changelog.append('# {0}\n{1}'.format(comp_cap, result.stdout))
    print 'Writing changelog...'
    # Write the changelog with the toplevel version without the build number.
    # This is ok since the changelog is only published if the toplevel version is released.
    changelog_file = self.__changelog_output or '{0}-changelog.md'.format(self.__toplevel_version)
    with open(changelog_file, 'w') as clog:
      clog.write('\n'.join(changelog))

  def write_bom(self):
    output_yaml = {SERVICES: {}, DEPENDENCIES: {}, ARTIFACT_SOURCES: {}}

    # Dependencies
    output_yaml[DEPENDENCIES]['consul'] = {VERSION: CONSUL_VERSION}
    output_yaml[DEPENDENCIES]['redis'] = {VERSION: REDIS_VERSION}
    output_yaml[DEPENDENCIES]['vault'] = {VERSION: VAULT_VERSION}

    # Artifact sources
    if not self.__options.bintray_repo:
      raise ValueError('No Debian repository provided.')

    output_yaml[ARTIFACT_SOURCES][DEBIAN_REPOSITORY] = 'https://dl.bintray.com/' + self.__options.bintray_repo
    output_yaml[ARTIFACT_SOURCES][DOCKER_REGISTRY] = self.__docker_registry
    output_yaml[ARTIFACT_SOURCES][GOOGLE_IMAGE_PROJECT] = self.__google_image_project
    output_yaml[ARTIFACT_SOURCES][GIT_PREFIX] = self.__git_prefix

    for comp in self.__component_versions:
      version_bump = self.__component_versions[comp]

      if version_bump.major == True:
        breaking_change = True
      elif version_bump.minor == True:
        feature = True

      gradle_version = self.__version_from_tag(comp)
      version_entry = {COMMIT: version_bump.commit_hash, VERSION: gradle_version}
      if comp == 'spinnaker-monitoring':
        # Add two entries for both components of spinnaker-monitoring
        output_yaml[SERVICES]['monitoring-third-party'] = dict(version_entry)
        output_yaml[SERVICES]['monitoring-daemon'] = dict(version_entry)
      else:
        output_yaml[SERVICES][comp] = version_entry

    timestamp = '{:%Y-%m-%d}'.format(datetime.datetime.utcnow())
    self.__toplevel_version = '{0}-{1}'.format(self.branch, timestamp)
    toplevel_with_build = '{0}-{1}'.format(self.__toplevel_version, self.build_number)
    output_yaml[VERSION] = toplevel_with_build
    self.__bom_file = '{0}.yml'.format(toplevel_with_build)
    self.write_bom_file(self.__bom_file, output_yaml)
    if self.__alias:
      output_yaml[VERSION] = self.__alias
      self.write_bom_file(self.__alias + '.yml', output_yaml)

  def publish_boms(self):
    """Pushes the generated BOMs to a public GCS bucket for Halyard to use.
    """
    self.publish_bom(self.__bom_file)
    if self.__alias:
      self.publish_bom(self.__alias + '.yml')

  def write_bom_file(self, filename, output_yaml):
    """Helper function to write the calculated BOM to files.

    Args:
      filename [string]: Name of the file to write to.
      output_yaml [dict]: Dictionary containing BOM information.
    """
    with open(filename, 'w') as output_file:
      output_yaml['timestamp'] = '{:%Y-%m-%d %H:%M:%S}'.format(datetime.datetime.utcnow())
      yaml.dump(output_yaml, output_file, default_flow_style=False)
      print 'Wrote BOM to {0}.'.format(filename)

  def publish_bom(self, bom_path):
    """Publishes the BOM using Halyard.

    Assumes that Halyard is installed and correctly configured on the current
    machine.
    """
    result = run_quick('hal admin publish bom --color false --bom-path {0}'
                       .format(bom_path))
    if result.returncode != 0:
      print "'hal admin publish bom' command failed with: \n{0}\nexiting...".format(result.stdout)
      exit(result.returncode)

  def __publish_config(self, component, profile_path):
    """Publishes the yaml configuration consumed by Halyard for the component.

    Args:
      component [string]: Name of the Spinnaker component.
      profile_path [string]: Path to component's yaml configuration file.
    """
    for profile in os.listdir(profile_path):
      full_profile = os.path.join(profile_path, profile)
      if os.path.isfile(full_profile):
        # Don't zip with `tar` if the profile is a file.
        self.__hal_upload_profile(component, self.__bom_file, full_profile)
      elif os.path.isdir(full_profile):
        tar_path = '{0}.tar.gz'.format(full_profile)
        result = run_quick('tar -cvf {0} -C {1} $(ls {1})'.format(tar_path, full_profile))
        if result.returncode != 0:
          print "Creating a tar archive of '{0}/*' failed with \n{1}\nexiting...".format(full_profile, result.stdout)
          exit(result.returncode)
        self.__hal_upload_profile(component, self.__bom_file, tar_path)
      else:
        print 'Listed profile was neither a file nor a directory. Ignoring...'
        continue

  def __hal_upload_profile(self, component, bom_file, profile_path):
    result = run_quick(
      'hal admin publish profile {0} --color false --bom-path {1} --profile-path {2}'
      .format(component, bom_file, profile_path)
    )
    if result.returncode != 0:
      print "'hal admin publish profile' command failed with: \n{0}\nexiting...".format(result.stdout)
      exit(result.returncode)

  def publish_microservice_configs(self):
    for comp in self.COMPONENTS:
      if comp == 'spinnaker-monitoring':
        daemon_path = '{0}-daemon'.format(comp)
        config_path = os.path.join(comp, daemon_path, 'halconfig')
        self.__publish_config('monitoring-daemon', config_path)
      elif comp == 'spinnaker':
        pass
      else:
        config_path = os.path.join(comp, 'halconfig')
        self.__publish_config(comp, config_path)

  def determine_and_tag_versions(self):
    for comp in self.COMPONENTS:
      self.path = os.path.join(self.__base_dir, comp)
      self.parse_git_tree()
      self.__changelog_start_hashes[comp] = self.current_version.hash
      version_bump = self.tag_head()
      self.__component_versions[comp] = version_bump
      self.delete_unwanted_tags()

  def determine_and_tag_halyard(self):
    """This serves only to generate an rpm version file
    for halyard
    """
    comp = 'halyard'
    self.path = os.path.join(self.__base_dir, comp)
    self.parse_git_tree()
    self.__changelog_start_hashes[comp] = self.current_version.hash
    version_bump = self.tag_head()
    self.__halyard_version[comp] = version_bump
    self.delete_unwanted_tags()

  @classmethod
  def main(cls):
    parser = argparse.ArgumentParser()
    cls.init_argument_parser(parser)
    options = parser.parse_args()
    if options.container_builder not in ['gcb', 'docker']:
      raise ValueError(
          'Invalid container_builder="{0}"'.format(options.container_builder))

    bom_generator = cls(options)
    bom_generator.determine_and_tag_versions()
    bom_generator.write_rpm_version_files()
    if options.container_builder == 'gcb':
      bom_generator.write_container_builder_gcr_config()
    elif options.container_builder == 'docker':
      bom_generator.write_docker_version_files()
    else:
      raise NotImplementedError('container_builder="{0}"'.format(
          options.container_builder))

    bom_generator.write_bom()
    bom_generator.publish_boms()
    bom_generator.publish_microservice_configs()
    bom_generator.generate_changelog()

if __name__ == '__main__':
  sys.exit(BomGenerator.main())
