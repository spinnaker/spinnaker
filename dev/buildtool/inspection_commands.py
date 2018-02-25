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

"""Implements inspection commands for buildtool.

   1) buildtool.sh collect_bom_versions
   2) buildtool.sh collect_artifact_versions
   3) buildtool.sh audit_artifact_versions

   This will produce files of things to prune.
   The should be reviewed. Those remove those that you wish to keep.
   Then to remove each of the artifacts:

      for url in $(cat prune_jars.txt); do
         curl -s -u$BINTRAY_USER:$BINTRAY_KEY -X DELETE $url &
      done
      wait

      for url in $(cat prune_debians.txt); do
         curl -s -u$BINTRAY_USER:$BINTRAY_KEY -X DELETE $url &
      done
      wait

      for url in $(cat prune_containers.txt); do
         gcloud -q container images delete $url --force-delete-tags &
      done
      wait

      for image_name in $(cat prune_images.txt); do
         gcloud -q compute images --project $PROJECT delete $image_name &
      done

      for url in $(cat prune_boms.txt); do
         gsutil rm $url
      done
"""

from threading import current_thread
from multiprocessing.pool import ThreadPool

import base64
import json
import logging
import os
import re
import urllib2
import yaml

from buildtool import (
    CommandFactory,
    CommandProcessor,
    SemanticVersion,
    check_options_set,
    check_path_exists,
    check_subprocess,
    maybe_log_exception,
    raise_and_log_error,
    write_to_path,
    ConfigError,
    UnexpectedError,
    ResponseError)


def my_unicode_representer(self, data):
  return self.represent_str(data.encode('utf-8'))

yaml.representer.Representer.add_representer(unicode, my_unicode_representer)
yaml.Dumper.ignore_aliases = lambda *args: True


class CollectBomVersions(CommandProcessor):
  """Determine which artifact versions are in use by which boms.

  Ultimately this produces an inverse map of boms. Whereas a bom
  maps to a collection of services and their build information, this
  produces a map of service build information and which boms they apepar in.
  Historically boms were unique builds, however this wasnt supposed to be
  the case, and is no longer the case.

  The map is further partitioned into two files where one contains
  released boms and the other unreleased boms. Unreleased boms are not
  necessarily obsolete.

  Emits files:
     bom_list.txt: A list of all the boms, released and unreleased
     bad_boms.txt: A list of malformed boms with what makes it malformed.
     all_bom_sevice_map.yml: The inverse service version mapping of all the boms
     released_bom_service_map.yml: The subset of all_bom_service_map for boms
        that were released.
     unreleased_bom_service_map.yml: The subset of all_bom_service_map for
        service versions that only appear in unreleased boms.
     nonstandard_boms.txt: A list of boms whose artifactSources do not match
       the values specified via options. Unspecified options match anything.
     config.yml: The configuration values used to determine standard compliance.
  """

  RELEASED_VERSION_MATCHER = re.compile(r'^\d+(?:\.\d+){2}$')

  @staticmethod
  def url_to_bom_name(url):
    """Given a url to a bom, return the name of the bom."""
    name = url
    dash = name.rfind('/')
    if dash >= 0:
      name = name[dash + 1:]
    return os.path.splitext(name)[0]

  def __init__(self, factory, options, **kwargs):
    if options.bintray_org is None != options.bintray_debian_repository is None:
      raise_and_log_error(
          ConfigError('Either neither or both "bintray_org"'
                      ' and "bintray_debian_repository" should be specified'))
    self.__bad_files = {}
    self.__non_standard_boms = []

    # We're going to have a bunch of threads each writing into different keys
    # in order to deconflict with one another lockless. Then we'll aggregate
    # it all together when we're done processing for a single aggregate result.
    self.__per_thread_result_map = {}

    self.__expect_docker_registry = options.docker_registry
    self.__expect_debian_repository = (
        'https://dl.bintray.com/%s/%s' % (options.bintray_org,
                                          options.bintray_debian_repository)
        if options.bintray_org
        else None)

    super(CollectBomVersions, self).__init__(
        factory, options, **kwargs)

  def load_bom_from_url(self, url):
    """Returns the bom specification dict from a gcs url."""
    logging.debug('Loading %s', url)
    try:
      text = check_subprocess('gsutil cat ' + url)
      return yaml.load(text)
    except Exception as ex:
      self.__bad_files[self.url_to_bom_name(url)] = ex.message
      maybe_log_exception('load_from_from_url', ex,
                          action_msg='Skipping %s' % url)
      return None

  def extract_bom_info(self, bom):
    """Return a minimal dict identifying this BOM.

    This also includes non-standard config specified by this BOM.
    """
    info = {
        'bom_version': bom['version'],
        'bom_timestamp': bom.get('timestamp', 'NotRecorded')
    }
    artifact_sources = bom.get('artifactSources')
    if artifact_sources is None:
      logging.warning('%s does not have artifactSources', bom['version'])
      return info

    def add_if_nonstandard(name, expect):
      if artifact_sources[name] != expect:
        logging.warning('%s has nonstandard %s = %s',
                        bom['version'], name, artifact_sources[name])
        info[name] = artifact_sources[name]

    add_if_nonstandard('dockerRegistry', self.__expect_docker_registry)
    add_if_nonstandard('debianRepository', self.__expect_debian_repository)
    if len(info) > 2:
      self.__non_standard_boms.append(info)

    return info

  def analyze_bom(self, bom):
    """Analyzes one bom and breaks it down into this threads result_map.

    Boms are processed within a single thread, but multiple boms can be
    processed in different threads.
    """
    tid = current_thread().name
    thread_service_map = self.__per_thread_result_map.get(tid, {})
    self.__per_thread_result_map[tid] = thread_service_map

    bom_info = self.extract_bom_info(bom)
    for name, entry in bom['services'].items():
      if name == 'defaultArtifact':
        continue
      build_version = entry['version']
      parts = build_version.split('-', 1)
      if len(parts) == 1:
        version = parts[0]
        buildnum = 'NotRecorded'
      else:
        version, buildnum = parts

      commit = entry.get('commit', 'NotRecorded')
      service_record = thread_service_map.get(name)
      if service_record is None:
        service_record = {}
        thread_service_map[name] = service_record
      version_map = service_record.get(version)
      if version_map is None:
        version_map = {}
        service_record[version] = version_map
      commit_map = version_map.get(commit)
      if commit_map is None:
        commit_map = {}
        version_map[commit] = commit_map
      build_list = commit_map.get(buildnum)
      if build_list is None:
        build_list = []
        commit_map[buildnum] = build_list

      build_list.append(bom_info)

  def ingest_bom(self, line):
    """Function to ingest a single bom into the result map."""
    bom = self.load_bom_from_url(line)
    if not bom:
      return
    try:
      if bom['version'] + '.yml' != line[line.rfind('/') + 1:]:
        message = 'BOM version "%s" != filename "%s"' % (bom['version'], line)
        self.__bad_files[self.url_to_bom_name(line.strip())] = message
        logging.warning(message)
        raise_and_log_error(UnexpectedError(message))
      self.analyze_bom(bom)
    except Exception as ex:
      self.__bad_files[self.url_to_bom_name(line.strip())] = ex.message
      maybe_log_exception('analyze_bom', ex,
                          action_msg='Skipping %s' % line)

  def join_result_maps(self):
    """Join the individual thread result maps into a single one.

    This assumes a single threaded environment.
    """
    def join_buildnums(commit_buildnums, result_buildnums):
      for buildnum, info_list in commit_buildnums.items():
        result_info_list = result_buildnums.get(buildnum)
        if result_info_list is None:
          result_info_list = []
          result_buildnums[buildnum] = result_info_list
        result_info_list.extend(info_list)
        result_info_list.sort(key=lambda info: info['bom_timestamp'])

    def join_commits(commit_map, result_commits):
      for commit, commit_buildnums in commit_map.items():
        result_buildnums = result_commits.get(commit)
        if result_buildnums is None:
          result_buildnums = {}
          result_commits[commit] = result_buildnums
        join_buildnums(commit_buildnums, result_buildnums)

    def join_versions(version_map, result_versions):
      for version, commit_map in version_map.items():
        result_commits = result_versions.get(version)
        if result_commits is None:
          result_commits = {}
          result_versions[version] = result_commits
        join_commits(commit_map, result_commits)

    def join_results(thread_results, result_map):
      for name, version_map in thread_results.items():
        result_versions = result_map.get(name)
        if result_versions is None:
          result_versions = {}
          result_map[name] = result_versions
        join_versions(version_map, result_versions)

    result_map = {}
    for thread_results in self.__per_thread_result_map.values():
      join_results(thread_results, result_map)
    return result_map

  def ingest_bom_list(self, bom_list):
    """Ingest each of the boms."""
    max_threads = 1 if self.options.one_at_a_time else 64
    pool = ThreadPool(min(max_threads, len(bom_list)))
    pool.map(self.ingest_bom, bom_list)
    pool.close()
    pool.join()
    return self.join_result_maps()

  def list_bom_urls(self, gcs_dir_url_prefix):
    """Get a list of all the bom versions that exist."""
    result = check_subprocess('gsutil ls ' + gcs_dir_url_prefix)
    return [line for line in result.split('\n')
            if line.startswith(gcs_dir_url_prefix) and line.endswith('.yml')]

  def _do_command(self):
    """Reads the list of boms, then concurrently processes them.

    Ultimately it will write out the analysis into bom_service_map.yml
    """
    options = self.options
    url_prefix = 'gs://%s/bom/' % options.halyard_bom_bucket
    if options.version_name_prefix:
      url_prefix += options.version_name_prefix
    logging.debug('Listing BOM urls')
    results = self.list_bom_urls(url_prefix)
    write_to_path('\n'.join(sorted(results)),
                  os.path.join(self.get_output_dir(), 'bom_list.txt'))
    result_map = self.ingest_bom_list(results)

    path = os.path.join(self.get_output_dir(), 'all_bom_service_map.yml')
    logging.info('Writing bom analysis to %s', path)
    write_to_path(yaml.dump(result_map, default_flow_style=False), path)

    partition_names = ['released', 'unreleased']
    partitions = self.partition_service_map(result_map)
    for index, data in enumerate(partitions):
      path = os.path.join(self.get_output_dir(),
                          partition_names[index] + '_bom_service_map.yml')
      logging.info('Writing bom analysis to %s', path)
      write_to_path(yaml.dump(data, default_flow_style=False), path)

    if self.__bad_files:
      path = os.path.join(self.get_output_dir(), 'bad_boms.txt')
      logging.warning('Writing %d bad URLs to %s', len(self.__bad_files), path)
      write_to_path(yaml.dump(self.__bad_files, default_flow_style=False), path)

    if self.__non_standard_boms:
      path = os.path.join(self.get_output_dir(), 'nonstandard_boms.txt')
      logging.warning('Writing %d nonstandard boms to %s',
                      len(self.__non_standard_boms), path)
      write_to_path(
          yaml.dump(self.__non_standard_boms, default_flow_style=False), path)

    config = {
        'halyard_bom_bucket': options.halyard_bom_bucket
    }
    path = os.path.join(self.get_output_dir(), 'config.yml')
    logging.info('Writing to %s', path)
    write_to_path(yaml.dump(config, default_flow_style=False), path)

  def partition_service_map(self, result_map):
    def partition_info_list(info_list):
      released = []
      unreleased = []
      for info in info_list:
        if self.RELEASED_VERSION_MATCHER.match(info['bom_version']):
          released.append(info)
        else:
          unreleased.append(info)

      if released:
        # If we released this somewhere, then it isnt unreleased.
        unreleased = []

      return released, unreleased

    def partition_buildnum_map(buildnum_map):
      released = {}
      unreleased = {}
      for buildnum, info_list in buildnum_map.items():
        results = partition_info_list(info_list)
        if results[0]:
          released[buildnum] = results[0]
        if results[1]:
          unreleased[buildnum] = results[1]
      return released, unreleased

    def partition_commit_map(commit_map):
      released = {}
      unreleased = {}
      for commit, buildnum_map in commit_map.items():
        results = partition_buildnum_map(buildnum_map)
        if results[0]:
          released[commit] = results[0]
        if results[1]:
          unreleased[commit] = results[1]
      return released, unreleased

    def partition_version_map(version_map):
      released = {}
      unreleased = {}
      for version, commit_map in version_map.items():
        results = partition_commit_map(commit_map)
        if results[0]:
          released[version] = results[0]
        if results[1]:
          unreleased[version] = results[1]
      if not released:
        released = None
      if not unreleased:
        unreleased = None
      return released, unreleased

    released = {}
    unreleased = {}
    for name, version_map in result_map.items():
      released[name], unreleased[name] = partition_version_map(version_map)

    return released, unreleased


class CollectBomVersionsFactory(CommandFactory):
  def __init__(self, **kwargs):
    super(CollectBomVersionsFactory, self).__init__(
        'collect_bom_versions', CollectBomVersions,
        'Find information about bom versions.', **kwargs)

  def init_argparser(self, parser, defaults):
    super(CollectBomVersionsFactory, self).init_argparser(parser, defaults)
    self.add_argument(parser, 'version_name_prefix', defaults, None,
                      help='Prefix for bom version to collect.')
    self.add_argument(
        parser, 'halyard_bom_bucket', defaults, 'halconfig',
        help='The bucket managing halyard BOMs and config profiles.')
    self.add_argument(
        parser, 'docker_registry', defaults, None,
        help='The expected docker registry in boms.')
    self.add_argument(
        parser, 'bintray_org', defaults, None,
        help='The expected bintray organization in boms.')
    self.add_argument(
        parser, 'bintray_debian_repository', defaults, None,
        help='The expected bintray debian repository in boms.')


class CollectArtifactVersions(CommandProcessor):
  """Locate all the existing spinnaker build artifacts.

  Ultimately this produces files mapping all the existing artifact
  builds for each service of a given type. It also looks for consistency
  between the bintray jar and debian builds.

  Emits files:
     <debian_repository>__versions.yml: All the debian build versions
     <jar_repository>__versions.yml: All the jar build versions
     <docker_registry>__versions.yml: All the container build versions
     missing_jars.yml: Bintray debian versions without a corresponding jar
     missing_debians.yml: Bintray jar versions witout a corresponding debian
     config.yml: The configuration values used to collect the artifacts
  """

  def __init__(self, factory, options, **kwargs):
    super(CollectArtifactVersions, self).__init__(
        factory, options, **kwargs)

    check_options_set(options,
                      ['docker_registry', 'bintray_org',
                       'bintray_jar_repository', 'bintray_debian_repository'])
    user = os.environ.get('BINTRAY_USER')
    password = os.environ.get('BINTRAY_KEY')
    if user and password:
      encoded_auth = base64.encodestring('{user}:{password}'.format(
          user=user, password=password))[:-1]  # strip eoln
      self.__basic_auth = 'Basic ' + encoded_auth
    else:
      self.__basic_auth = None

  def fetch_bintray_url(self, bintray_url):
    request = urllib2.Request(bintray_url)
    if self.__basic_auth:
      request.add_header('Authorization', self.__basic_auth)
    try:
      response = urllib2.urlopen(request)
      headers = response.info()
      payload = response.read()
      content = json.JSONDecoder(encoding='utf-8').decode(payload)
    except urllib2.HTTPError as ex:
      raise_and_log_error(
          ResponseError('Bintray failure: {}'.format(ex),
                        server='bintray.api'),
          'Failed on url=%s: %s' % (bintray_url, ex.message))
    except Exception as ex:
      raise
    return headers, content

  def list_bintray_packages(self, subject_repo):
    path = 'repos/%s/packages' % subject_repo
    base_url = 'https://api.bintray.com/' + path
    result = []
    while True:
      url = base_url + '?start_pos=%d' % len(result)
      headers, content = self.fetch_bintray_url(url)
      # logging.debug('Bintray responded with headers\n%s', headers)
      total = headers.get('X-RangeLimit-Total', 0)
      result.extend(['%s/%s' % (subject_repo, entry['name'])
                     for entry in content])
      if len(result) >= total:
        break
    return result

  def query_bintray_package_versions(self, package_path):
    path = 'packages/' + package_path
    url = 'https://api.bintray.com/' + path
    headers, content = self.fetch_bintray_url(url)
    # logging.debug('Bintray responded with headers\n%s', headers)
    package_name = package_path[package_path.rfind('/') + 1:]
    return (package_name, content['versions'])

  def difference(self, versions, target):
    missing = []
    for version in versions:
      if not version in target:
        missing.append(version)
    return missing

  def find_missing_jar_versions(self, jar_map, debian_map):
    missing_jars = {}
    prefix = 'spinnaker-'
    for package, versions in debian_map.items():
      if package.startswith(prefix):
        key = package[len(prefix):]
      if not key in jar_map:
        key = package
        if key == 'spinnaker-monitoring-daemon':
          key = 'spinnaker-monitoring'
        if not key in jar_map:
          if key == 'spinnaker-monitoring-third-party':
            continue
          continue
      missing = self.difference(versions, jar_map.get(key))
      if missing:
        missing_jars[key] = missing

    return missing_jars

  def find_missing_debian_versions(self, jar_map, debian_map):
    missing_debians = {}
    for package, versions in jar_map.items():
      key = 'spinnaker-' + package
      if not key in debian_map:
        key = package
        if not key in debian_map:
          if key == 'spinnaker-monitoring':
            key = 'spinnaker-monitoring-daemon'
          else:
            raise ValueError('Unknown DEBIAN "%s"' % package)

      missing = self.difference(versions, debian_map.get(key))
      if missing:
        missing_debians[key] = missing

    return missing_debians

  def collect_bintray_versions(self, pool):
    options = self.options
    repos = [('jar', options.bintray_jar_repository),
             ('debian', options.bintray_debian_repository)]
    results = []
    for repo_type, bintray_repo in repos:
      subject_repo = '%s/%s' % (options.bintray_org, bintray_repo)
      packages = self.list_bintray_packages(subject_repo)
      package_versions = pool.map(self.query_bintray_package_versions, packages)

      package_map = {}
      for name, versions in package_versions:
        package_map[name] = versions
      results.append(package_map)

      path = os.path.join(
          self.get_output_dir(),
          '%s__%s_versions.yml' % (bintray_repo, repo_type))
      logging.info('Writing %s versions to %s', bintray_repo, path)
      write_to_path(yaml.dump(package_map,
                              allow_unicode=True,
                              default_flow_style=False), path)
    return results[0], results[1]

  def query_gcr_image_versions(self, image):
    options = self.options
    command_parts = ['gcloud',
                     '--format=json',
                     'container images list-tags',
                     image, '--limit 10000']
    if options.gcb_service_account:
      command_parts.extend(['--account', options.gcb_service_account])
    response = check_subprocess(' '.join(command_parts))
    result = []
    for version in json.JSONDecoder(encoding='utf-8').decode(response):
      result.extend(version['tags'])
    return (image[image.rfind('/') + 1:], result)

  def collect_gcb_versions(self, pool):
    options = self.options
    logging.debug('Collecting GCB versions from %s', options.docker_registry)
    command_parts = ['gcloud',
                     '--format=json',
                     'container images list',
                     '--repository', options.docker_registry]
    if options.gcb_service_account:
      logging.debug('Using account %s', options.gcb_service_account)
      command_parts.extend(['--account', options.gcb_service_account])

    response = check_subprocess(' '.join(command_parts))
    images = [entry['name']
              for entry in json.JSONDecoder(encoding='utf-8').decode(response)]
    image_versions = pool.map(self.query_gcr_image_versions, images)

    image_map = {}
    for name, versions in image_versions:
      image_map[name] = versions

    path = os.path.join(
        self.get_output_dir(),
        options.docker_registry.replace('/', '__') + '__gcb_versions.yml')
    logging.info('Writing %s versions to %s', options.docker_registry, path)
    write_to_path(yaml.dump(image_map,
                            allow_unicode=True,
                            default_flow_style=False), path)
    return image_map

  def collect_gce_image_versions(self):
    options = self.options
    project = options.publish_gce_image_project
    logging.debug('Collecting GCE image versions from %s', project)
    command_parts = ['gcloud', '--format=json',
                     'compute images list', '--project', project,
                     '--filter spinnaker-']
    if options.build_gce_service_account:
      logging.debug('Using account %s', options.build_gce_service_account)
      command_parts.extend(['--account', options.build_gce_service_account])

    response = check_subprocess(' '.join(command_parts))
    images = [entry['name']
              for entry in json.JSONDecoder(encoding='utf-8').decode(response)]
    image_map = {}
    for name in images:
      parts = name.split('-', 2)
      if len(parts) != 3:
        logging.warning('Skipping malformed %s', name)
        continue
      _, module, build_version = parts
      parts = build_version.split('-')
      if len(parts) != 4:
        logging.warning('Skipping malformed %s', name)
        continue
      version_list = image_map.get(module, [])
      version_list.append('{}.{}.{}-{}'.format(*parts))
      image_map[module] = version_list

    path = os.path.join(
        self.get_output_dir(), project + '__gce_image_versions.yml')
    logging.info('Writing gce image versions to %s', path)
    write_to_path(yaml.dump(image_map,
                            allow_unicode=True,
                            default_flow_style=False), path)
    return image_map

  def _do_command(self):
    pool = ThreadPool(16)
    bintray_jars, bintray_debians = self.collect_bintray_versions(pool)
    self.collect_gcb_versions(pool)
    self.collect_gce_image_versions()
    pool.close()
    pool.join()

    missing_jars = self.find_missing_jar_versions(
        bintray_jars, bintray_debians)
    missing_debians = self.find_missing_debian_versions(
        bintray_jars, bintray_debians)

    options = self.options
    for which in [(options.bintray_jar_repository, missing_jars),
                  (options.bintray_debian_repository, missing_debians)]:
      if not which[1]:
        logging.info('%s is all accounted for.', which[0])
        continue
      path = os.path.join(self.get_output_dir(), 'missing_%s.yml' % which[0])
      logging.info('Writing to %s', path)
      write_to_path(
          yaml.dump(which[1], allow_unicode=True, default_flow_style=False),
          path)

    config = {
        'bintray_org': options.bintray_org,
        'bintray_jar_repository': options.bintray_jar_repository,
        'bintray_debian_repository': options.bintray_debian_repository,
        'docker_registry': options.docker_registry,
        'googleImageProject': options.publish_gce_image_project
    }
    path = os.path.join(self.get_output_dir(), 'config.yml')
    logging.info('Writing to %s', path)
    write_to_path(yaml.dump(config, default_flow_style=False), path)


class CollectArtifactVersionsFactory(CommandFactory):
  def __init__(self, **kwargs):
    super(CollectArtifactVersionsFactory, self).__init__(
        'collect_artifact_versions', CollectArtifactVersions,
        'Find information about artifact jar/debian versions.', **kwargs)

  def init_argparser(self, parser, defaults):
    super(CollectArtifactVersionsFactory, self).init_argparser(
        parser, defaults)
    self.add_argument(
        parser, 'bintray_org', defaults, None,
        help='bintray organization for the jar and debian repositories.')
    self.add_argument(
        parser, 'bintray_jar_repository', defaults, None,
        help='bintray repository in the bintray_org containing published jars.')
    self.add_argument(
        parser, 'bintray_debian_repository', defaults, None,
        help='bintray repository in the bintray_org containing debians.')
    self.add_argument(
        parser, 'version_name_prefix', defaults, None,
        help='Prefix for bintray versions to collect.')
    self.add_argument(
        parser, 'gcb_service_account', defaults, None,
        help='The service account to use when checking gcr images.')
    self.add_argument(
        parser, 'docker_registry', defaults, None,
        help='The GCB service account query image versions from.')
    self.add_argument(
        parser, 'build_gce_service_account', defaults, None,
        help='The service account to use with the gce project.')
    self.add_argument(
        parser, 'publish_gce_image_project', defaults, None,
        help='The GCE project ot collect images from.')


class AuditArtifactVersions(CommandProcessor):
  """Given the collected BOMs and artifacts, separate good from bad.

  Ultimately this determines which existing artifacts are in use and which are
  not referenced by a bom. It also verifies the integrity of the boms with
  regard to the existence of the artifacts they specify. It will emit files
  that suggest which specific boms and artifacts can be deleted. The artifacts
  in use by the boms suggested for pruning are not included in the prune list.
  They will be nominated in the next round.

  Emits files:
     audit_confirmed_boms.yml: All the boms that have been verified intact.
     audit_found_<type>.yml: All the artifacts of <type> that were referenced
         by a bom.
     audit_missing_<type>.yml: All the artifacts of <type> that were not
         referenced by a bom.
     audit_unused_<type>.yml: All the artifacts of <type> that were referenced
         by a bom but not found to actually exist. These are for documentation.
         The audit_invalid_boms.yml file is more useful.
     audit_invalid_boms.yml: All the boms whose integrity is suspect along with
         the explanation as to why. Usually they are missing artifacts, but
         there could be other reasons.
     prune_<type>.txt The list of URLs that should be safe to delete for the
         given <type> from a strict referential integrity standpoint. There
         could be unanticipated uses of these artifacts.
  """

  def __init_bintray_versions_helper(self, base_path):
    artifact_data_dir = os.path.join(base_path, 'collect_artifact_versions')
    debian_paths = []
    jar_paths = []
    gcr_paths = []
    image_paths = []
    for filename in os.listdir(artifact_data_dir):
      path = os.path.join(artifact_data_dir, filename)
      if filename.endswith('__gcb_versions.yml'):
        gcr_paths.append(path)
      elif filename.endswith('__jar_versions.yml'):
        jar_paths.append(path)
      elif filename.endswith('__debian_versions.yml'):
        debian_paths.append(path)
      elif filename.endswith('__gce_image_versions.yml'):
        image_paths.append(path)

    for name, found in [('jar', jar_paths), ('debian', debian_paths),
                        ('gce image', image_paths), ('gcr image', gcr_paths)]:
      if len(found) != 1:
        raise_and_log_error(
            ConfigError(
                'Expected 1 %s version files in "%s": %s' % (
                    name, artifact_data_dir, found)))

    logging.debug('Loading container image versions from "%s"', gcr_paths[0])
    with open(gcr_paths[0], 'r') as stream:
      self.__container_versions = yaml.load(stream.read())
    with open(jar_paths[0], 'r') as stream:
      self.__jar_versions = yaml.load(stream.read())
    with open(debian_paths[0], 'r') as stream:
      self.__debian_versions = yaml.load(stream.read())
    with open(image_paths[0], 'r') as stream:
      self.__gce_image_versions = yaml.load(stream.read())

  def __extract_all_bom_versions(self, bom_map):
    result = set([])
    for versions in bom_map.values():
      if not versions:
        continue
      for commits in versions.values():
        for buildnum in commits.values():
          for info_list in buildnum.values():
            for info in info_list:
              result.add(info['bom_version'])
    return result

  def __init__(self, factory, options, **kwargs):
    super(AuditArtifactVersions, self).__init__(factory, options, **kwargs)
    base_path = os.path.dirname(self.get_output_dir())
    self.__init_bintray_versions_helper(base_path)

    bom_data_dir = os.path.join(base_path, 'collect_bom_versions')
    path = os.path.join(bom_data_dir, 'released_bom_service_map.yml')
    check_path_exists(path, 'released bom analysis')
    with open(path, 'r') as stream:
      self.__released_boms = yaml.load(stream.read())

    path = os.path.join(bom_data_dir, 'unreleased_bom_service_map.yml')
    check_path_exists(path, 'unreleased bom analysis')
    with open(path, 'r') as stream:
      self.__unreleased_boms = yaml.load(stream.read())

    self.__only_bad_and_invalid_boms = False
    self.__all_bom_versions = self.__extract_all_bom_versions(
        self.__released_boms)
    self.__all_bom_versions.update(
        self.__extract_all_bom_versions(self.__unreleased_boms))

    self.__missing_debians = {}
    self.__missing_jars = {}
    self.__missing_containers = {}
    self.__missing_images = {}
    self.__found_debians = {}
    self.__found_jars = {}
    self.__found_containers = {}
    self.__found_images = {}
    self.__unused_jars = {}
    self.__unused_debians = {}
    self.__unused_containers = {}
    self.__unused_gce_images = {}
    self.__invalid_boms = {}
    self.__confirmed_boms = set([])
    self.__prune_boms = []
    self.__prune_jars = {}
    self.__prune_debians = {}
    self.__prune_containers = {}
    self.__prune_gce_images = {}
    self.__invalid_versions = {}

  def audit_artifacts(self):
    self.audit_bom_services(self.__released_boms, 'released')
    self.audit_bom_services(self.__unreleased_boms, 'unreleased')
    self.audit_package(
        'jar', self.__jar_versions, self.__unused_jars)
    self.audit_package(
        'debian', self.__debian_versions, self.__unused_debians)
    self.audit_package(
        'container', self.__container_versions, self.__unused_containers)
    self.audit_package(
        'image',
        self.__gce_image_versions, self.__unused_gce_images)

    def maybe_write_log(what, data):
      if not data:
        return
      path = os.path.join(self.get_output_dir(), 'audit_' + what + '.yml')
      logging.info('Writing %s', path)
      write_to_path(
          yaml.dump(data, allow_unicode=True, default_flow_style=False),
          path)

    confirmed_boms = self.__all_bom_versions - set(self.__invalid_boms.keys())
    maybe_write_log('missing_debians', self.__missing_debians)
    maybe_write_log('missing_jars', self.__missing_jars)
    maybe_write_log('missing_containers', self.__missing_containers)
    maybe_write_log('missing_images', self.__missing_images)
    maybe_write_log('found_debians', self.__found_debians)
    maybe_write_log('found_jars', self.__found_jars)
    maybe_write_log('found_containers', self.__found_containers)
    maybe_write_log('found_images', self.__found_images)
    maybe_write_log('unused_debians', self.__unused_debians)
    maybe_write_log('unused_jars', self.__unused_jars)
    maybe_write_log('unused_containers', self.__unused_containers)
    maybe_write_log('unused_images', self.__unused_gce_images)
    maybe_write_log('invalid_boms', self.__invalid_boms)
    maybe_write_log('confirmed_boms', sorted(list(confirmed_boms)))
    maybe_write_log('invalid_versions', self.__invalid_versions)

  def most_recent_version(self, name, versions):
    """Find the most recent version built."""
    if not versions:
      return None
    raw_versions = set([version.split('-')[0] for version in versions])
    sem_vers = []
    for text in raw_versions:
      try:
        sem_vers.append(SemanticVersion.make('version-' + text))
      except Exception as ex:
        bad_list = self.__invalid_versions.get(name, [])
        bad_list.append(text)
        self.__invalid_versions[name] = bad_list
        logging.error('Ignoring invalid %s version "%s": %s', name, text, ex)
    return sorted(sem_vers, cmp=SemanticVersion.compare)[-1].to_version()

  def test_buildnum(self, buildver):
    dash = buildver.rfind('-')
    if dash < 0:
      return True
    buildnum = buildver[dash + 1:]
    return buildnum < self.options.prune_min_buildnum_prefix

  def determine_bom_candidates(self):
    path = os.path.join(os.path.dirname(self.get_output_dir()),
                        'collect_bom_versions', 'bom_list.txt')
    candidates = []
    with open(path, 'r') as stream:
      for line in stream.read().split('\n'):
        if line.endswith('-latest-unvalidated.yml'):
          continue
        bom = CollectBomVersions.url_to_bom_name(line)
        if not CollectBomVersions.RELEASED_VERSION_MATCHER.match(bom):
          candidates.append(line)

    return candidates

  def determine_prunings(self):
    def filter_from_candidates(newest_version, candidate_version_list):
      if self.options.prune_keep_latest_version:
        prune_version = lambda ver: not ver.startswith(newest_version)
      else:
        prune_version = lambda ver: True

      if self.options.prune_min_buildnum_prefix:
        prune_buildnum = self.test_buildnum
      else:
        prune_buildnum = lambda ver: True

      return [candidate for candidate in candidate_version_list
              if prune_version(candidate) and prune_buildnum(candidate)]

    self.__prune_boms = [name for name in self.determine_bom_candidates()
                         if self.test_buildnum(name)]

    service_list = set(self.__found_debians.keys())
    service_list.update(set(self.__found_containers.keys()))
    for name in service_list:
      skip_versions = self.__invalid_versions.get(name, [])
      for unused_map, prune_map in [
          (self.__unused_jars, self.__prune_jars),
          (self.__unused_debians, self.__prune_debians),
          (self.__unused_gce_images, self.__prune_gce_images),
          (self.__unused_containers, self.__prune_containers)]:
        unused_list = unused_map.get(name, None)
        if unused_list is None:
          unused_list = unused_map.get('spinnaker-' + name, [])

        newest_version = self.most_recent_version(name, unused_list)
        candidates = filter_from_candidates(newest_version, unused_list)

        # We're going to keep malformed versions. These are rare so
        # we'll leave it to manual cleanup.
        pruned = [version
                  for version in candidates if not version in skip_versions]
        if pruned:
          prune_map[name] = sorted(pruned)

  def suggest_prunings(self):
    path = os.path.join(os.path.dirname(self.get_output_dir()),
                        'collect_bom_versions', 'config.yml')
    with open(path, 'r') as stream:
      bom_config = yaml.load(stream.read())
    path = os.path.join(os.path.dirname(self.get_output_dir()),
                        'collect_artifact_versions', 'config.yml')
    with open(path, 'r') as stream:
      art_config = yaml.load(stream.read())

    urls = []
    if self.__prune_boms:
      path = os.path.join(self.get_output_dir(), 'prune_boms.txt')
      logging.info('Writing to %s', path)
      write_to_path('\n'.join(sorted(self.__prune_boms)), path)

    jar_repo_path = 'packages/%s/%s' % (
        art_config['bintray_org'], art_config['bintray_jar_repository'])
    debian_repo_path = 'packages/%s/%s' % (
        art_config['bintray_org'], art_config['bintray_debian_repository'])
    artifact_prefix_func = {
        'jar': lambda name: 'https://api.bintray.com/%s/%s/versions/' % (
            jar_repo_path, name),
        'debian': lambda name: 'https://api.bintray.com/%s/%s/versions/' % (
            debian_repo_path,
            name if name == 'spinnaker' else 'spinnaker-' + name),
        'container': lambda name: '%s/%s:' % (
            art_config['docker_registry'], name),
        'image': lambda name: 'spinnaker-%s-' % name
    }
    artifact_version_func = {
        'jar': lambda version: version,
        'debian': lambda version: version,
        'container': lambda version: version,
        'image': lambda version: version.replace('.', '-')
    }
    for art_type, art_map in [('jar', self.__prune_jars),
                              ('debian', self.__prune_debians),
                              ('container', self.__prune_containers),
                              ('image', self.__prune_gce_images)]:
      urls = []
      for service, art_list in art_map.items():
        prefix = artifact_prefix_func[art_type](service)
        version_func = artifact_version_func[art_type]
        urls.extend([prefix + version_func(version) for version in art_list])
      if urls:
        path = os.path.join(self.get_output_dir(), 'prune_%ss.txt' % art_type)
        logging.info('Writing to %s', path)
        write_to_path('\n'.join(sorted(urls)), path)

  def _do_command(self):
    self.audit_artifacts()
    self.determine_prunings()
    self.suggest_prunings()

  def audit_container(self, service, build_version, entries):
    if service in ['spinnaker', 'monitoring-third-party']:
      return True  # not applicable

    if service in self.__container_versions:
      versions = self.__container_versions[service]
    elif service in ['monitoring-daemon']:
      versions = self.__container_versions.get('monitoring-daemon', [])
    else:
      versions = []

    if build_version in versions:
      holder = self.__found_containers.get(service, {})
      holder[build_version] = entries
      self.__found_containers[service] = holder
      return True

    holder = self.__missing_containers.get(service, {})
    holder[build_version] = entries
    self.__missing_containers[service] = holder
    logging.warning('Missing %s container %s', service, build_version)
    return False

  def audit_image(self, service, build_version, entries):
    if service in ['spinnaker',
                   'monitoring-third-party', 'monitoring-daemon']:
      return True  # not applicable

    versions = self.__gce_image_versions.get(service, [])
    if build_version in versions:
      holder = self.__found_images.get(service, {})
      holder[build_version] = entries
      self.__found_images[service] = holder
      return True

    holder = self.__missing_images.get(service, {})
    holder[build_version] = entries
    self.__missing_images[service] = holder
    logging.warning('Missing %s gce image %s', service, build_version)
    return False

  def audit_jar(self, service, build_version, entries):
    if service in self.__jar_versions:
      versions = self.__jar_versions[service]
    elif service in ['monitoring-daemon', 'monitoring-third-party']:
      versions = self.__jar_versions.get('spinnaker-monitoring', [])
    else:
      versions = []

    if build_version in versions:
      holder = self.__found_jars.get(service, {})
      holder[build_version] = entries
      self.__found_jars[service] = holder
      return True

    holder = self.__missing_jars.get(service, {})
    holder[build_version] = entries
    self.__missing_jars[service] = holder
    logging.warning('Missing %s jar %s', service, build_version)
    return False

  def audit_debian(self, service, build_version, info_list):
    versions = []
    if service in self.__debian_versions:
      key = service
      versions = self.__debian_versions[service]
    else:
      key = 'spinnaker-' + service
      if key in self.__debian_versions:
        versions = self.__debian_versions[key]

    if build_version in versions:
      holder = self.__found_debians.get(service, {})
      holder[build_version] = info_list
      self.__found_debians[service] = holder
      return True

    holder = self.__missing_debians.get(key, {})
    holder[build_version] = info_list
    self.__missing_debians[key] = holder
    logging.warning('Missing %s debian %s', key, build_version)
    return False

  def package_in_bom_map(self, service, version, buildnum, service_map):
    version_map = service_map.get(service)
    if version_map is None:
      return False
    commit_map = version_map.get(version)
    if commit_map is None:
      return False
    for _, buildnums in commit_map.items():
      if buildnum in buildnums:
        return True
    return False

  def audit_package_helper(self, package, version, buildnum, which):
    if package in self.__released_boms or package in self.__unreleased_boms:
      name = package
    elif package.startswith('spinnaker-'):
      name = package[package.find('-') + 1:]
    else:
      return False

    is_released = self.package_in_bom_map(
        name, version, buildnum, self.__released_boms)
    is_unreleased = self.package_in_bom_map(
        name, version, buildnum, self.__unreleased_boms)
    if is_released or is_unreleased:
      return True

    data_list = which.get(package, [])
    if buildnum:
      data_list.append('%s-%s' % (version, buildnum))
    else:
      data_list.append(version)
    which[package] = data_list
    return False

  def audit_package(self, kind, packages, which):
    logging.info('Auditing %s packages', kind)
    for package, versions in packages.items():
      if package == 'halyard':
        logging.warning('Skipping halyard.')
        continue
      for build_version in versions:
        parts = build_version.split('-', 1)
        if len(parts) == 1:
          logging.warning('Unexpected %s version %s', package, build_version)
          continue
        version, buildnum = parts
        self.audit_package_helper(package, version, buildnum, which)

  def audit_bom_services(self, bom_services, title):
    def add_invalid_boms(jar_ok, deb_ok, container_ok, image_ok,
                         service, version_buildnum, info_list, invalid_boms):
      if jar_ok and deb_ok and container_ok and image_ok:
        return

      kind_checks = [(jar_ok, 'jars'), (deb_ok, 'debs'),
                     (container_ok, 'containers'), (image_ok, 'images')]
      for info in info_list:
        bom_version = info['bom_version']
        bom_record = invalid_boms.get(bom_version, {})
        for is_ok, kind in kind_checks:
          if not is_ok:
            problems = bom_record.get(kind, {})
            problems[service] = version_buildnum
            bom_record[kind] = problems
        invalid_boms[bom_version] = bom_record

    def audit_service(service, versions):
      for version, commits in versions.items():
        for _, buildnums in commits.items():
          for buildnum, info_list in buildnums.items():
            version_buildnum = '%s-%s' % (version, buildnum)
            jar_ok = self.audit_jar(service, version_buildnum, info_list)
            deb_ok = self.audit_debian(service, version_buildnum, info_list)
            gcr_ok = self.audit_container(service, version_buildnum, info_list)
            image_ok = self.audit_image(service, version_buildnum, info_list)
            add_invalid_boms(jar_ok, deb_ok, gcr_ok, image_ok,
                             service, version_buildnum,
                             info_list, self.__invalid_boms)

    logging.debug('Auditing %s BOMs', title)
    for service, versions in bom_services.items():
      if not versions:
        logging.debug('No versions for %s', service)
        continue
      audit_service(service, versions)


class AuditArtifactVersionsFactory(CommandFactory):
  def __init__(self, **kwargs):
    super(AuditArtifactVersionsFactory, self).__init__(
        'audit_artifact_versions', AuditArtifactVersions,
        'Audit artifact versions in BOMs and vice-versa', **kwargs)

  def init_argparser(self, parser, defaults):
    super(AuditArtifactVersionsFactory, self).init_argparser(parser, defaults)
    self.add_argument(
        parser, 'prune_min_buildnum_prefix', defaults, None,
        help='Only suggest pruning artifacts with a smaller build number.'
        ' This is actually just a string, not a number so is a string compare.')
    self.add_argument(
        parser, 'prune_keep_latest_version', defaults, False, type=bool,
        help='If true, suggest only artifacts whose version is not the most'
             ' recent version among the boms surveyed.')

def register_commands(registry, subparsers, defaults):
  CollectBomVersionsFactory().register(registry, subparsers, defaults)
  CollectArtifactVersionsFactory().register(registry, subparsers, defaults)
  AuditArtifactVersionsFactory().register(registry, subparsers, defaults)
