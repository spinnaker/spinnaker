# Copyright 2016 Google Inc. All Rights Reserved.
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

import mock
from mock import Mock

import collections
import copy
import unittest
from googleapiclient.errors import HttpError

from stackdriver_service import StackdriverMetricsService


ResponseStatus = collections.namedtuple('ResponseStatus',
                                        ['status', 'reason'])
StackdriverResponse = collections.namedtuple(
    'StackdriverResponse', ['resp', 'content'])

class StackdriverMetricsServiceTest(unittest.TestCase):
  def setUp(self):
    project = 'test-project'
    instance = 'test-instance'
    options = {'project': project, 'zone': 'us-central1-f',
               'instance_id': instance,
               'fix_stackdriver_labels_unsafe': True}

    self.mockStub = mock.create_autospec(['projects'])
    self.mockProjects = mock.create_autospec(
        ['metricDescriptors', 'timeSeries'])
    self.mockMetricDescriptors = mock.create_autospec(
        ['create', 'delete', 'get'])
    self.mockTimeSeries = mock.create_autospec(['create'])
    self.mockStub.projects = Mock(return_value=self.mockProjects)
    self.mockProjects.metricDescriptors = Mock(
        return_value=self.mockMetricDescriptors)
    self.mockProjects.timeSeries = Mock(return_value=self.mockTimeSeries)

    # pylint: disable=invalid-name
    self.mockCreateTimeSeries = Mock(spec=['execute'])
    self.mockCreateDescriptor = Mock(spec=['execute'])
    self.mockGetDescriptor = Mock(spec=['execute'])
    self.mockDeleteDescriptor = Mock(spec=['execute'])
    self.mockMetricDescriptors.create = Mock(
        return_value=self.mockCreateDescriptor)
    self.mockMetricDescriptors.delete = Mock(
        return_value=self.mockDeleteDescriptor)
    self.mockMetricDescriptors.get = Mock(
        return_value=self.mockGetDescriptor)
    self.mockTimeSeries.create = Mock(return_value=self.mockCreateTimeSeries)
    self.service = StackdriverMetricsService(lambda: self.mockStub, options)

  def test_find_problematic_elements(self):
    content = """{
  "error": {
    "code": 400,
    "message": "Field timeSeries[1].metric.labels[2] had an invalid value of \\"application\\": Unrecognized metric label.",
    "errors": [
      {
        "message": "Field timeSeries[1].metric.labels[2] had an invalid value of \\"application\\": Unrecognized metric label.",
        "domain": "global",
        "reason": "badRequest"
      }
    ],
    "status": "INVALID_ARGUMENT"
  }
}"""

    Error = collections.namedtuple('Error', ['content'])
    error = Error(content)

    all_ts = [
        {'metric': {'type': 'typeA', 'name': 'nameA'}},
        {'metric': {'type': 'FOUND-TYPE', 'name': 'nameFound'}},
        {'metric': {'type': 'typeC', 'name': 'nameC'}},
        ]

    found = self.service.find_problematic_elements(error, all_ts)
    self.assertEquals([(self.service.add_label_and_retry,
                        'application',
                        'FOUND-TYPE', all_ts[1])], found)

  def test_add_label_and_retry_no_descriptor(self):
    timeseries = 'OPAQUE'

    status = ResponseStatus(404, 'Not Found')
    self.mockMetricDescriptors.get.side_effect = HttpError(
        status, 'Not Found')

    self.service.add_label_and_retry('NewLabel', 'ExistingType', timeseries)
    self.assertEquals(0, self.mockStub.projects.list.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.get.call_count)
    self.assertEquals(0, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(0, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(0, self.mockTimeSeries.create.call_count)

  def test_add_label_already_present(self):
    timeseries = 'OPAQUE'

    original_descriptor = {'type': 'TYPE',
                           'labels': [{'key': 'TestLabel'}]}
    self.mockGetDescriptor.execute.side_effect = [original_descriptor]

    self.service.add_label_and_retry('TestLabel', 'ExistingType', timeseries)
    self.assertEquals(0, self.mockStub.projects.list.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.get.call_count)
    self.assertEquals(0, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(0, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(1, self.mockTimeSeries.create.call_count)

  def test_add_label_and_retry_cannot_delete(self):
    timeseries = 'OPAQUE'

    original_descriptor = {'labels': [{'key': 'TestLabel'}],
                           'type': 'TYPE'
                           }
    new_descriptor = dict(copy.deepcopy(original_descriptor))
    new_descriptor['labels'].append({'key': 'NewLabel'})

    self.mockGetDescriptor.execute.side_effect = [
        original_descriptor, new_descriptor]
    self.mockDeleteDescriptor.execute.side_effect = HttpError(
        ResponseStatus(404, 'Not Found'), 'Not Found')
    self.mockCreateDescriptor.execute.side_effect = [new_descriptor]

    self.service.add_label_and_retry('NewLabel', 'ExistingType', timeseries)
    self.assertEquals(0, self.mockStub.projects.list.call_count)
    self.assertEquals(2, self.mockMetricDescriptors.get.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(1, self.mockTimeSeries.create.call_count)

  def test_add_label_and_retry_cannot_create(self):
    timeseries = 'OPAQUE'

    original_descriptor = {'labels': [{'key': 'TestLabel'}],
                           'type': 'TYPE'
                           }

    self.mockGetDescriptor.execute.side_effect = [original_descriptor]
    self.mockDeleteDescriptor.execute.side_effect = ResponseStatus(
        200, 'ok')
    self.mockCreateDescriptor.execute.side_effect = HttpError(
        ResponseStatus(404, 'Not Found'), 'Not Found')

    self.service.add_label_and_retry('NewLabel', 'ExistingType', timeseries)
    self.assertEquals(0, self.mockStub.projects.list.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.get.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(0, self.mockTimeSeries.create.call_count)

if __name__ == '__main__':
  # pylint: disable=invalid-name
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(StackdriverMetricsServiceTest)
  unittest.TextTestRunner(verbosity=2).run(suite)

