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

import argparse
import json
import sys
import unittest
from StringIO import StringIO
import mock
from mock import patch
from mock import Mock

import spectator_client

# pylint: disable=missing-docstring
# pylint: disable=invalid-name


def args_to_options(args):
  parser = argparse.ArgumentParser()
  spectator_client.SpectatorClient.add_standard_parser_arguments(parser)
  old_argv = sys.argv
  try:
    sys.argv = ['foo']
    sys.argv.extend(args)
    options = vars(parser.parse_args())
  finally:
    sys.argv = old_argv
  return options
    


TEST_HOST = 'test_hostname'

CLOUDDRIVER_RESPONSE_OBJ = {
  'applicationName': 'clouddriver',
  'metrics' : {
    'jvm.buffer.memoryUsed' : {
      'kind': 'AggrMeter',
      'values': [{
          'tags': [{'key': 'id', 'value': 'mapped'}],
          'values': [{'t': 1471917869670, 'v': 0.0}]
       }, {
          'tags': [{'key': 'id', 'value': 'direct'}],
          'values': [{'t': 1471917869671, 'v': 81920.0}]
       }]
    },
    'jvm.gc.maxDataSize' : {
      'kind': 'AggrMeter',
      'values': [{
        'tags': [],
        'values': [{'t': 1471917869672, 'v': 12345.0}]
      }]
    },
    'tasks': {
      'kind': 'Counter',
      'values': [{
        'tags': [{'key': 'success', 'value': 'true'}],
        'values': [{'t': 1471917869673, 'v': 24.0}]
      }]
    }
  }
}


CLOUDDRIVER_RESPONSE_TEXT = json.JSONEncoder(encoding='utf-8').encode(
    CLOUDDRIVER_RESPONSE_OBJ)


GATE_RESPONSE_OBJ = {
  'applicationName': 'gate',
  'metrics' : {
    'jvm.buffer.memoryUsed' : {
      'kind': 'AggrMeter',
      'values': [{
          'tags': [{'key': 'id', 'value': 'mapped'}],
          'values': [{'t': 1471917869677, 'v': 2.0}]
        }, {
          'tags': [{'key': 'id', 'value': 'direct'}],
          'values': [{'t': 1471917869678, 'v': 22222.0}]
        }]
    },
    'jvm.gc.maxDataSize': {
      'kind': 'AggrMeter',
      'values': [{
        'tags': [],
        'values': [{'t' : 1471917869679, 'v' : 54321.0}]
      }]
    },
    'controller.invocations': {
      'kind': 'Timer',
      'values': [{
        'tags': [{'key': 'controller', 'value': 'PipelineController'},
                 {'key': 'method', 'value': 'savePipeline'}],
        'values': [{'t' : 1471917869679, 'v' : 1.0}]
      }]
    }
  }
}

GATE_RESPONSE_TEXT = json.JSONEncoder(encoding='utf-8').encode(
    GATE_RESPONSE_OBJ)

class SpectatorClientTest(unittest.TestCase):
  def setUp(self):
    options = {'prototype_path': None, 'host': TEST_HOST}
    self.spectator = spectator_client.SpectatorClient(options)
    self.default_query_params = '?tagNameRegex=.%2B'  # tagNameRegex=.+

  def test_default_endpoints(self):
    options = args_to_options([])
    service_endpoints = spectator_client.determine_service_endpoints(options)
    self.assertEquals({'clouddriver': [('localhost', 7002)],
                       'echo': [('localhost', 8089)],
                       'fiat': [('localhost', 7003)],
                       'front50': [('localhost', 8080)],
                       'gate': [('localhost', 8084)],
                       'igor': [('localhost', 8088)],
                       'orca': [('localhost', 8083)],
                       'rosco': [('localhost', 8087)]},
                      service_endpoints)

  def test_multi_mutated_endpoints(self):
    options = args_to_options(
        ['--service_hosts=one,two,three',
         '--clouddriver=xyz',
         '--echo=', '--fiat=', '--front50=','--gate=', '--igor='])
    service_endpoints = spectator_client.determine_service_endpoints(options)
    self.assertEquals(
      {'clouddriver': [('xyz', 7002)],
       'orca': [('one', 8083), ('two', 8083), ('three', 8083)],
       'rosco': [('one', 8087), ('two', 8087), ('three', 8087)]},
      service_endpoints)

  def test_filtered_endpoints(self):
    options = args_to_options(
        ['--service_hosts=',
         '--clouddriver=abc:1234,xyz:4321',
         '--gate=even:2468,named:foo'])
    service_endpoints = spectator_client.determine_service_endpoints(options)
    self.assertEquals(
      {'clouddriver': [('abc', 1234), ('xyz', 4321)],
       'gate': [('even', 2468), ('named', 'foo')]},
      service_endpoints)

  @patch('spectator_client.urllib2.urlopen')
  def test_collect_metrics_no_params(self, mock_urlopen):
    port = 13246
    expect = {'bogus': 'Hello, World', '__host': TEST_HOST, '__port': port}

    text = json.JSONEncoder(encoding='utf-8').encode(expect)
    mock_http_response = StringIO(text)
    mock_urlopen.return_value = mock_http_response

    response = self.spectator.collect_metrics(TEST_HOST, port)
    mock_urlopen.assert_called_with(
        'http://{0}:{1}/spectator/metrics{2}'.format(
          TEST_HOST, port, self.default_query_params))
    self.assertEqual(expect, response)

  @patch('spectator_client.urllib2.urlopen')
  def test_collect_metrics_with_params(self, mock_urlopen):
    port = 13246
    expect = {'bogus': 'Hello, World', '__host': TEST_HOST, '__port': port}

    params = {'tagNameRegex': 'someName', 'tagValueRegex': 'Second+Part&Third'}
    encoded_params = {'tagNameRegex': 'someName',
                      'tagValueRegex': 'Second%2BPart%26Third'}

    expected_query = ''
    for key, value in params.items():
      expected_query += '{sep}{key}={value}'.format(
          sep='&' if expected_query else '?',
          key=key,
          value=encoded_params[key])

    text = json.JSONEncoder(encoding='utf-8').encode(expect)
    mock_http_response = StringIO(text)
    mock_urlopen.return_value = mock_http_response

    response = self.spectator.collect_metrics(TEST_HOST, port, params)
    mock_urlopen.assert_called_with(
        'http://{0}:{1}/spectator/metrics{2}'.format(
            TEST_HOST, port, expected_query))
    self.assertEqual(expect, response)

  @patch('spectator_client.urllib2.urlopen')
  def test_scan_by_service_one(self, mock_urlopen):
    expect = [{'bogus': 'Hello, World', '__host': TEST_HOST, '__port': 7002}]

    text = json.JSONEncoder(encoding='utf-8').encode(expect[0])
    mock_http_response = StringIO(text)
    mock_urlopen.return_value = mock_http_response

    response = self.spectator.scan_by_service(
        {'clouddriver': [(TEST_HOST, 7002)]})
    mock_urlopen.assert_called_with(
        'http://{0}:7002/spectator/metrics{1}'.format(
          TEST_HOST, self.default_query_params))
    self.assertEqual({'clouddriver': expect}, response)

  @patch('spectator_client.urllib2.urlopen')
  def test_scan_by_service_two(self, mock_urlopen):
    expect = [{'common': 123, 'a': 100, '__host': TEST_HOST, '__port': 7002},
              {'common': 456, 'b': 200, '__host': TEST_HOST, '__port': 7003}]

    text_a = json.JSONEncoder(encoding='utf-8').encode(expect[0])
    text_b = json.JSONEncoder(encoding='utf-8').encode(expect[1])
    mock_http_response_a = StringIO(text_a)
    mock_http_response_b = StringIO(text_b)
    mock_urlopen.side_effect = [mock_http_response_a, mock_http_response_b]

    response = self.spectator.scan_by_service(
        {'clouddriver': [(TEST_HOST, 7002), (TEST_HOST, 7003)]})
    calls = [mock.call('http://{0}:7002/spectator/metrics{1}'
                       .format(TEST_HOST, self.default_query_params)),
             mock.call('http://{0}:7003/spectator/metrics{1}'
                       .format(TEST_HOST, self.default_query_params))]
    mock_urlopen.assert_has_calls(calls)

    self.assertEqual({'clouddriver': expect}, response)

  @patch('spectator_client.urllib2.urlopen')
  def test_scan_by_service_list(self, mock_urlopen):
    expect_clouddriver = [{'cloud': 'Cloud Hello',
                           '__host': TEST_HOST, '__port': 7002}]
    expect_gate = [{'gateway': 'Gateway Hello',
                    '__host': TEST_HOST, '__port': 8084}]

    text = json.JSONEncoder(encoding='utf-8').encode(expect_clouddriver[0])
    mock_clouddriver_response = StringIO(text)
    text = json.JSONEncoder(encoding='utf-8').encode(expect_gate[0])
    mock_gate_response = StringIO(text)

    # The order is sensitive to the order we'll call in.
    # To get the call order, we'll let the dict tell us,
    # since that is the order we'll be calling internally.
    # Ideally this can be specified a different way, but I cant find how.
    mock_urlopen.side_effect = {'clouddriver': mock_clouddriver_response,
                                'gate': mock_gate_response}.values()

    response = self.spectator.scan_by_service(
        {'clouddriver': [(TEST_HOST, 7002)], 'gate': [(TEST_HOST, 8084)]})
    clouddriver_url = 'http://{0}:7002/spectator/metrics{1}'.format(
        TEST_HOST, self.default_query_params)
    gate_url = 'http://{0}:8084/spectator/metrics{1}'.format(
        TEST_HOST, self.default_query_params)

    # Order does not matter.
    self.assertEquals(sorted([mock.call(clouddriver_url), mock.call(gate_url)]),
                      sorted(mock_urlopen.call_args_list))

    self.assertEqual({'clouddriver': expect_clouddriver, 'gate': expect_gate},
                     response)

  def test_service_map_to_type_map_one(self):
    got = spectator_client.SpectatorClient.service_map_to_type_map(
      {'clouddriver': [CLOUDDRIVER_RESPONSE_OBJ]})
    expect = {}
    self.spectator.ingest_metrics(
        'clouddriver', CLOUDDRIVER_RESPONSE_OBJ, expect)
    self.assertEquals(expect, got)

  def test_service_map_to_type_map_two_different(self):
    got = spectator_client.SpectatorClient.service_map_to_type_map(
      {'clouddriver': [CLOUDDRIVER_RESPONSE_OBJ],
       'gate': [GATE_RESPONSE_OBJ]})
    expect = {}
    self.spectator.ingest_metrics(
        'clouddriver', CLOUDDRIVER_RESPONSE_OBJ, expect)
    self.spectator.ingest_metrics(
        'gate', GATE_RESPONSE_OBJ, expect)
    self.assertEquals(expect, got)

  def test_service_map_to_type_map_two_same(self):
    another = dict(CLOUDDRIVER_RESPONSE_OBJ)
    metric = another['metrics']['jvm.buffer.memoryUsed']['values'][0]
    metric['values'][0]['t'] = 12345
    got = spectator_client.SpectatorClient.service_map_to_type_map(
      {'clouddriver': [CLOUDDRIVER_RESPONSE_OBJ, another]})

    expect = {}
    self.spectator.ingest_metrics(
        'clouddriver', CLOUDDRIVER_RESPONSE_OBJ, expect)
    self.spectator.ingest_metrics(
        'clouddriver', another, expect)
    self.assertEquals(expect, got)

  @patch('spectator_client.urllib2.urlopen')
  def test_scan_by_type_base_case(self, mock_urlopen):
    expect = {}
    self.spectator.ingest_metrics(
        'clouddriver', CLOUDDRIVER_RESPONSE_OBJ, expect)

    mock_http_response = StringIO(CLOUDDRIVER_RESPONSE_TEXT)
    mock_urlopen.return_value = mock_http_response

    response = self.spectator.scan_by_type({'clouddriver': [(TEST_HOST, 7002)]})
    mock_urlopen.assert_called_with(
        'http://{0}:7002/spectator/metrics{1}'.format(
            TEST_HOST, self.default_query_params))

    self.assertEqual(expect, response)

  @patch('spectator_client.urllib2.urlopen')
  def test_scan_by_type_incremental_case(self, mock_urlopen):
    expect = {}
    self.spectator.ingest_metrics(
        'clouddriver', CLOUDDRIVER_RESPONSE_OBJ, expect)
    self.spectator.ingest_metrics(
        'gate', GATE_RESPONSE_OBJ, expect)

    mock_clouddriver_response = StringIO(CLOUDDRIVER_RESPONSE_TEXT)
    mock_gate_response = StringIO(GATE_RESPONSE_TEXT)

    # The order is sensitive to the order we'll call in.
    # To get the call order, we'll let the dict tell us,
    # since that is the order we'll be calling internally.
    # Ideally this can be specified a different way, but I cant find how.
    mock_urlopen.side_effect = {'clouddriver': mock_clouddriver_response,
                                'gate': mock_gate_response}.values()

    response = self.spectator.scan_by_type(
        {'clouddriver': [(TEST_HOST, 7002)], 'gate': [(TEST_HOST, 8084)]})
    clouddriver_url = 'http://{0}:7002/spectator/metrics{1}'.format(
        TEST_HOST, self.default_query_params)
    gate_url = 'http://{0}:8084/spectator/metrics{1}'.format(
        TEST_HOST, self.default_query_params)
    self.assertEquals(
        sorted([mock.call(clouddriver_url), mock.call(gate_url)]),
        sorted(mock_urlopen.call_args_list))
    self.assertEqual(expect, response)

  def test_ingest_metrics_base_case(self):
    result = {}
    self.spectator.ingest_metrics(
        'clouddriver', CLOUDDRIVER_RESPONSE_OBJ, result)

    expect = {
        key: {'clouddriver': [value]}
        for key, value in CLOUDDRIVER_RESPONSE_OBJ['metrics'].items()
        }

    self.assertEqual(expect, result)

  def test_ingest_metrics_incremental_case(self):
    result = {}
    self.spectator.ingest_metrics(
        'clouddriver', CLOUDDRIVER_RESPONSE_OBJ, result)
    self.spectator.ingest_metrics(
      'gate', GATE_RESPONSE_OBJ, result)

    expect = {key: {'clouddriver': [value]}
              for key, value
              in CLOUDDRIVER_RESPONSE_OBJ['metrics'].items()}
    for key, value in GATE_RESPONSE_OBJ['metrics'].items():
      if key in expect:
        expect[key]['gate'] = [value]
      else:
        expect[key] = {'gate': [value]}

    self.assertEqual(expect, result)

  def test_filter_name(self):
    prototype = {'metrics': {'tasks': {}}}
    expect = {'applicationName': 'clouddriver',
              'metrics': {
                  'tasks': CLOUDDRIVER_RESPONSE_OBJ['metrics']['tasks']}}
    got = self.spectator.filter_metrics(CLOUDDRIVER_RESPONSE_OBJ, prototype)
    self.assertEqual(expect, got)

  def test_filter_tag(self):
    prototype = {
      'metrics': {
        'jvm.buffer.memoryUsed': {
          'values': [{
            'tags': [{'key': 'id', 'value': 'direct'}]
            }]
          }
        }
      }

    metric = dict(CLOUDDRIVER_RESPONSE_OBJ['metrics']['jvm.buffer.memoryUsed'])
    metric['values'] = [metric['values'][1]]  # Keep just the one tag set value.
    expect = {'applicationName': 'clouddriver',
              'metrics': {'jvm.buffer.memoryUsed': metric}
             }
    got = self.spectator.filter_metrics(CLOUDDRIVER_RESPONSE_OBJ, prototype)
    self.assertEqual(expect, got)

if __name__ == '__main__':
  # pylint: disable=invalid-name
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(SpectatorClientTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
