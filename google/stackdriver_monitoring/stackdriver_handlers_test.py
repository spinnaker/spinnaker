import mock
from mock import patch
from mock import Mock

import collections
import unittest

import command_processor
import stackdriver_handlers
from stackdriver_service import StackdriverMetricsService


class FakeBatch(object):
  def __init__(self, responses):
    self.payloads = []
    self.__callbacks = []
    self.__responses = responses
    pass

  def add(self, payload, callback, request_id):
    self.payloads.append(payload)
    self.__callbacks.append((callback, request_id))

  def execute(self):
    for index, good_bad in enumerate(self.__responses):
      self.__callbacks[index][0](self.__callbacks[index][1], *good_bad)
    if len(self.__responses) != len(self.__callbacks):
      raise ValueError('{0} responses != {1} callbacks'.format(
          len(self.__responses), len(self.__callbacks)))


class UpsertHandlerTest(unittest.TestCase):
  def setUp(self):
    options = {'project': 'PROJECT',
               'source_path': 'IgnoreThis',
               'output_path': None}
    mockStub = mock.create_autospec(['projects', 'new_batch_http_request'])
    mockStub.new_batch_http_request = Mock()
    self.fake_batch_list = []

    stackdriver = StackdriverMetricsService(lambda: mockStub, options)
    self.upsertHandler = stackdriver_handlers.UpsertCustomDescriptorsHandler(
        None, options, None)

    self.mockProjects = mock.create_autospec(['metricDescriptors'])
    self.mockMetricDescriptors = mock.create_autospec(
        ['create', 'delete', 'list'])

    self.mockStub = mockStub
    self.mockStub.projects = Mock(return_value=self.mockProjects)
    self.mockProjects.metricDescriptors = Mock(
        return_value=self.mockMetricDescriptors)

    self.mockCreateDescriptor = Mock(spec=['execute'])
    self.mockDeleteDescriptor = Mock(spec=['execute'])
    self.mockListDescriptors = Mock(spec=['execute'])

    self.mockMetricDescriptors.create = Mock(
        return_value=self.mockCreateDescriptor)
    self.mockMetricDescriptors.delete = Mock(
        return_value=self.mockDeleteDescriptor)
    self.mockMetricDescriptors.list = Mock(
        return_value=self.mockListDescriptors)
    self.mockMetricDescriptors.list_next = Mock(return_value=None)

    self.options = options
    self.project = options['project']
    self.stackdriver = stackdriver

  def do_upsert_handler(
        self, have_descriptors, want_descriptors, batch_responses=None):
    for batch_response in batch_responses or []:
      self.fake_batch_list.append(FakeBatch(batch_response))
      self.mockStub.new_batch_http_request.side_effect = self.fake_batch_list

    params = {'source_path': 'IgnoreThis', 'project': self.options['project']}
    execute = Mock(return_value={'metricDescriptors': have_descriptors})
    self.mockListDescriptors.execute = execute
    self.upsertHandler.process_commandline_request(params, want_descriptors)

  @patch('stackdriver_service.make_service')
  def test_keep_existing(self, mockMakeService):
    mockMakeService.side_effect = [self.stackdriver]
    typeA = 'custom.googleapis.com/spinnaker/rosco/a'
    typeB = 'custom.googleapis.com/spinnaker/rosco/b'
    descriptors = [
        {'type': typeA,
         'name': 'projects/test-project/metricDescriptors/' + typeA},
        {'type': typeB,
         'name': 'projects/test-project/metricDescriptors/' + typeB},
    ]

    self.do_upsert_handler(descriptors, descriptors)
    self.assertEquals(0, self.mockStub.new_batch_http_request.call_count)
    self.assertEquals(0, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(0, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.list.call_count)
    self.assertEquals(1, self.mockListDescriptors.execute.call_count)
    self.mockMetricDescriptors.list.assert_called_once_with(
        name='projects/{0}'.format(self.project))

  @patch('stackdriver_service.make_service')
  def test_create_new(self, mockMakeService):
    mockMakeService.side_effect = [self.stackdriver]
    typeA = 'custom.googleapis.com/spinnaker/rosco/a'
    typeB = 'custom.googleapis.com/spinnaker/rosco/b'
    typeC = 'custom.googleapis.com/spinnaker/rosco/c'
    descriptors = [
        {'type': typeA,
         'name': 'projects/test-project/metricDescriptors/' + typeA},
        {'type': typeB,
         'name': 'projects/test-project/metricDescriptors/' + typeB},
        {'type': typeC,
         'name': 'projects/test-project/metricDescriptors/' + typeC},
    ]

    self.do_upsert_handler(descriptors[1:2], descriptors,
                           batch_responses=[[('okA', None), ('okC', None)]])

    self.assertEquals(1, self.mockStub.new_batch_http_request.call_count)
    self.assertEquals(2, len(self.fake_batch_list[0].payloads))
    self.assertEquals(0, self.mockCreateDescriptor.execute.call_count)
    self.assertEquals(2, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(0, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.list.call_count)
    self.assertEquals(1, self.mockListDescriptors.execute.call_count)
    self.mockMetricDescriptors.list.assert_called_once_with(
        name='projects/{0}'.format(self.project))
    self.mockMetricDescriptors.create.assert_has_calls(
        [mock.call(name='projects/{0}'.format(self.project),
                   body=descriptors[0]),
         mock.call(name='projects/{0}'.format(self.project),
                   body=descriptors[2])])

  @patch('stackdriver_service.make_service')
  def test_update_existing(self, mockMakeService):
    mockMakeService.side_effect = [self.stackdriver]
    typeA = 'custom.googleapis.com/spinnaker/rosco/a'
    typeB = 'custom.googleapis.com/spinnaker/rosco/b'
    typeC = 'custom.googleapis.com/spinnaker/rosco/c'
    descriptors = [
        {'type': typeA,
         'name': 'projects/test-project/metricDescriptors/' + typeA},
        {'type': typeB,
         'name': 'projects/test-project/metricDescriptors/' + typeB},
        {'type': typeC,
         'field': 123,
         'name': 'projects/test-project/metricDescriptors/' + typeC},
    ]
    updated_descriptors = [
        {'type': typeA,
         'field': 'ADDED',
         'name': 'projects/test-project/metricDescriptors/' + typeA},
        {'type': typeC,
         'field': 321,
         'name': 'projects/test-project/metricDescriptors/' + typeC},
    ]

    delete_responses = [('deleteA', None), ('deleteC', None)]
    create_responses = [('createA', None), ('createC', None)]
    self.do_upsert_handler(
        descriptors, updated_descriptors,
        batch_responses=[delete_responses, create_responses])

    self.assertEquals(2, self.mockStub.new_batch_http_request.call_count)
    self.assertEquals(2, len(self.fake_batch_list[0].payloads))
    self.assertEquals(2, len(self.fake_batch_list[1].payloads))
    self.assertEquals(0, self.mockCreateDescriptor.execute.call_count)
    self.assertEquals(0, self.mockDeleteDescriptor.execute.call_count)
    self.assertEquals(2, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(2, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.list.call_count)
    self.assertEquals(1, self.mockListDescriptors.execute.call_count)
    self.mockMetricDescriptors.list.assert_called_once_with(
        name='projects/{0}'.format(self.project))
    self.mockMetricDescriptors.delete.assert_has_calls(
        [mock.call(name=descriptors[0]['name']),
         mock.call(name=descriptors[2]['name'])])
    self.mockMetricDescriptors.create.assert_has_calls(
        [mock.call(name='projects/{0}'.format(self.project),
                   body=updated_descriptors[0]),
         mock.call(name='projects/{0}'.format(self.project),
                   body=updated_descriptors[1])])

  @patch('stackdriver_service.make_service')
  def test_update_failure_and_restore(self, mockMakeService):
    mockMakeService.side_effect = [self.stackdriver]
    typeA = 'custom.googleapis.com/spinnaker/rosco/a'
    typeB = 'custom.googleapis.com/spinnaker/rosco/b'
    typeC = 'custom.googleapis.com/spinnaker/rosco/c'
    descriptors = [
        {'type': typeA,
         'name': 'projects/test-project/metricDescriptors/' + typeA},
        {'type': typeB,
         'name': 'projects/test-project/metricDescriptors/' + typeB},
        {'type': typeC,
         'field': 123,
         'name': 'projects/test-project/metricDescriptors/' + typeC},
    ]
    updated_descriptors = [
        {'type': typeA,
         'field': 'GOOD',
         'name': 'projects/test-project/metricDescriptors/' + typeA},
        {'type': typeB,
         'field': 'BAD',
         'name': 'projects/test-project/metricDescriptors/' + typeB},
        {'type': typeC,
         'field': 'GOOD',
         'name': 'projects/test-project/metricDescriptors/' + typeC},
    ]

    delete_responses = [('deleteA', None), ('deleteB', None), ('deleteC', None)]
    create_responses = [('createA', None), (None, 'failedB'), ('createC', None)]
    restore_responses = [('restoredB', None)]
    self.do_upsert_handler(
        descriptors, updated_descriptors,
        batch_responses=[delete_responses, create_responses, restore_responses])

    self.assertEquals(3, self.mockStub.new_batch_http_request.call_count)
    self.assertEquals(3, len(self.fake_batch_list[0].payloads))
    self.assertEquals(3, len(self.fake_batch_list[1].payloads))
    self.assertEquals(1, len(self.fake_batch_list[2].payloads))
    self.assertEquals(0, self.mockCreateDescriptor.execute.call_count)
    self.assertEquals(0, self.mockDeleteDescriptor.execute.call_count)
    self.assertEquals(4, self.mockMetricDescriptors.create.call_count)
    self.assertEquals(3, self.mockMetricDescriptors.delete.call_count)
    self.assertEquals(1, self.mockMetricDescriptors.list.call_count)
    self.assertEquals(1, self.mockListDescriptors.execute.call_count)
    self.mockMetricDescriptors.list.assert_called_once_with(
        name='projects/{0}'.format(self.project))
    self.mockMetricDescriptors.delete.assert_has_calls(
        [mock.call(name=descriptors[0]['name']),
         mock.call(name=descriptors[1]['name']),
         mock.call(name=descriptors[2]['name'])])
    self.mockMetricDescriptors.create.assert_has_calls(
        [mock.call(name='projects/{0}'.format(self.project),
                   body=updated_descriptors[0]),
         mock.call(name='projects/{0}'.format(self.project),
                   body=updated_descriptors[1]),
         mock.call(name='projects/{0}'.format(self.project),
                   body=updated_descriptors[2]),
         mock.call(name='projects/{0}'.format(self.project),
                   body=descriptors[1])])

if __name__ == '__main__':
  # pylint: disable=invalid-name
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(UpsertHandlerTest)
  unittest.TextTestRunner(verbosity=2).run(suite)

