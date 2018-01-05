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

import textwrap
import unittest
from buildtool.build_commands import GradleMetricsUpdater
import buildtool.metrics
import buildtool.git

class SimpleNamespace(object):
  def __init__(self):
    self.metric_name_scope = 'unittest'
    self.monitoring_flush_frequency = -1
    self.monitoring_system = 'file'
    self.monitoring_enabled = True

REPOSITORY = buildtool.git.RemoteGitRepository('testRepo', '/path/to/repo', None)


BINTRAY_ERROR_OUTPUT = textwrap.dedent("""\
     :echo-web:publishBuildDeb
     
     FAILURE: Build completed with 9 failures.
     
     1: Task failed with an exception.
     -----------
     * What went wrong:
     Execution failed for task ':echo-core:bintrayUpload'.
     > Could not upload to 'https://api.bintray.com/content/spinnaker-releases/ewiseblatt-maven/echo/1.542.0/com/netflix/spinnaker/echo/echo-core/1.542.0/echo-core-1.542.0.jar': HTTP/1.1 409 Conflict [message:Unable to upload files: An artifact with the path 'com/netflix/spinnaker/echo/echo-core/1.542.0/echo-core-1.542.0.jar' already exists]
     
     * Try:
     Run with --info or --debug option to get more log output.
""")


class TestGradleMetricsUpdater(unittest.TestCase):
  def setUp(self):
      self.metrics = buildtool.metrics.MetricsManager.singleton()

  def test_ok(self):
    updater = GradleMetricsUpdater(self.metrics, REPOSITORY, 'TestWhatThing')
    counter = updater(0, BINTRAY_ERROR_OUTPUT)
    self.assertEquals(1, counter.count)
    self.assertEquals('GradleOutcome', counter.family.name)
    self.assertEquals({
        'repository': 'testRepo',
        'context': 'TestWhatThing',
        'success': True,
        'failed_task': '',
        'failed_by': '',
        'failed_reason': ''
    }, counter.labels)

  def test_bintray_error(self):
    updater = GradleMetricsUpdater(self.metrics, REPOSITORY, 'TestWhatThing')
    counter = updater(-1, BINTRAY_ERROR_OUTPUT)
    self.assertEquals(1, counter.count)
    self.assertEquals('GradleOutcome', counter.family.name)
    self.assertEquals({
        'repository': 'testRepo',
        'context': 'TestWhatThing',
        'success': False,
        'failed_task': ':echo-core:bintrayUpload',
        'failed_by': 'bintray',
        'failed_reason': '409'
    }, counter.labels)


if __name__ == '__main__':
  import logging
  logging.basicConfig(
      format='%(levelname).1s %(asctime)s.%(msecs)03d %(message)s',
      datefmt='%H:%M:%S',
      level=logging.DEBUG)

  buildtool.metrics.MetricsManager.startup_metrics(SimpleNamespace())
  unittest.main(verbosity=2)
