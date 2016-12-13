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

"""Tests for complexity in spectator_handlers."""

# pylint: disable=missing-docstring


import json
import re
import unittest
from StringIO import StringIO

from mock import patch
from mock import Mock

import command_processor
import http_server
import spectator_client
import spectator_client_test as sample_data
from spectator_client_test import args_to_options
import spectator_handlers

# pylint: disable=invalid-name
# pylint: disable=missing-docstring


def TABLE(row_html_list):
  return '<table>{0}</table>'.format(''.join(row_html_list))

def TR(*column_lists):
  columns = []
  for column_list in column_lists:
    for column in column_list:
      columns.append(column)
  return '<tr>{0}</tr>'.format(''.join(columns))

def TH(names):
  return ['<th>{0}</th>'.format(name) for name in names]

def TD(cells, rowspan=None):
  attributes = ''
  if rowspan is not None:
    attributes += ' rowspan={0}'.format(rowspan)
  return ['<td{0}>{1}</td>'.format(attributes, cell) for cell in cells]


class MetricCollectorHandlersTest(unittest.TestCase):
  def setUp(self):
    self.options = args_to_options(
        ['--host=spectator_hostname',
         '--service_hosts=',
         '--clouddriver=spectator_hostname',
         '--gate=spectator_hostname'])
    command_processor.set_global_options(self.options)

    self.spectator = spectator_client.SpectatorClient(self.options)

    self.mock_clouddriver_response = (
      StringIO(sample_data.CLOUDDRIVER_RESPONSE_TEXT))
    self.mock_gate_response = StringIO(sample_data.GATE_RESPONSE_TEXT)

    self.mock_request = Mock()
    self.mock_request.respond = Mock()

  @patch('spectator_client.urllib2.urlopen')
  def test_dump_handler_default(self, mock_urlopen):
    expected_by_service = {
        'clouddriver': [sample_data.CLOUDDRIVER_RESPONSE_OBJ],
        'gate': [sample_data.GATE_RESPONSE_OBJ]
    }
    expected_by_service['clouddriver'][0].update({
      '__host': 'spectator_hostname',
      '__port': 7002
      })
    expected_by_service['gate'][0].update({
      '__host': 'spectator_hostname',
      '__port': 8084
      })

    mock_urlopen.side_effect = [self.mock_gate_response,
                                self.mock_clouddriver_response]

    dump = spectator_handlers.DumpMetricsHandler(None, None, None)

    params = dict(self.options)
    params['services'] = 'clouddriver,gate'
    dump.process_web_request(self.mock_request, '/dump', params, '')
    called_with = self.mock_request.respond.call_args[0]
    self.assertEqual(200, called_with[0])
    self.assertEqual({'ContentType': 'application/json'}, called_with[1])
    doc = json.JSONDecoder(encoding='utf-8').decode(called_with[2])
    self.assertEqual(expected_by_service, doc)

  def test_explore_to_service_tag_one(self):
    klass = spectator_handlers.ExploreCustomDescriptorsHandler
    type_map = spectator_client.SpectatorClient.service_map_to_type_map(
        {'clouddriver': [sample_data.CLOUDDRIVER_RESPONSE_OBJ]})
    service_tag_map, services = klass.to_service_tag_map(type_map)
    expect = {
        'jvm.buffer.memoryUsed': {
            'clouddriver' : [{'id': 'mapped'}, {'id': 'direct'}],
         },
        'jvm.gc.maxDataSize': {
            'clouddriver' : [{None: None}]
        },
        'tasks': {
            'clouddriver' : [{'success': 'true'}]
        }
    }
    self.assertEqual(expect, service_tag_map)
    self.assertEqual(set(['clouddriver']), services)

  def test_explore_to_service_tag_map_two(self):
    klass = spectator_handlers.ExploreCustomDescriptorsHandler
    type_map = spectator_client.SpectatorClient.service_map_to_type_map(
        {'clouddriver': [sample_data.CLOUDDRIVER_RESPONSE_OBJ]})
    spectator_client.SpectatorClient.ingest_metrics(
        'gate', sample_data.GATE_RESPONSE_OBJ, type_map)
    usage, services = klass.to_service_tag_map(type_map)
    expect = {
        'controller.invocations': {
            'gate' : [{'controller': 'PipelineController',
                       'method': 'savePipeline'}]
        },
        'jvm.buffer.memoryUsed': {
            'clouddriver' : [{'id': 'mapped'}, {'id': 'direct'}],
            'gate' : [{'id': 'mapped'}, {'id': 'direct'}],
         },
        'jvm.gc.maxDataSize': {
            'clouddriver' : [{None: None}],
            'gate' : [{None: None}],
        },
        'tasks': {
            'clouddriver' : [{'success': 'true'}]
        }
    }

    self.assertEqual(set(['clouddriver', 'gate']), services)
    self.assertEqual(expect, usage)

  def test_to_tag_service_map(self):
    klass = spectator_handlers.ExploreCustomDescriptorsHandler
    service_tag_map = {
        'A': [{'x': 'X', 'y': 'Y'}, {'x': '1', 'y': '2'}],
        'B': [{'x': 'X', 'z': 'Z'}, {'x': 'b', 'z': '3'}]}
    columns = {'A': 0, 'B': 1}
    expect = {'x': [set(['X', '1']), set(['X', 'b'])],
              'y': [set(['Y', '2']), set()],
              'z': [set(), set(['Z', '3'])]}

    inverse_map = klass.to_tag_service_map(columns, service_tag_map)
    self.assertEqual(expect, inverse_map)

  @patch('spectator_client.urllib2.urlopen')
  def test_explore_custom_descriptors_default(self, mock_urlopen):
    klass = spectator_handlers.ExploreCustomDescriptorsHandler
    explore = klass(None, None, None)

    mock_urlopen.side_effect = [self.mock_gate_response,
                                self.mock_clouddriver_response]

    params = dict(self.options)
    params['services'] = 'clouddriver'

    explore.process_web_request(self.mock_request, '/explore', params, '')
    called_with = self.mock_request.respond.call_args[0]
    self.assertEqual(200, called_with[0])
    self.assertEqual({'ContentType': 'text/html'}, called_with[1])
    html = minimize_html(called_with[2])

    service_link = '<A href="/show?services={0}">{0}</A>'.format
    name_link = '<A href="/show?meterNameRegex={0}">{0}</A>'.format
    tag_link = '<A href="/explore?tagNameRegex={0}">{0}</A>'.format
    value_link = '<A href="/explore?tagValueRegex={0}">{0}</A>'.format

    expect_body = TABLE([
        TR(TH(['Metric',
               'Label',
               service_link('clouddriver'),
               service_link('gate')])),
        TR(TD([name_link('controller.invocations')], rowspan=2),
           TD([tag_link('controller'),
               '',
               value_link('PipelineController')])),
        TR(TD([tag_link('method'),
               '',
               value_link('savePipeline')])),
        TR(TD([name_link('jvm.buffer.memoryUsed'),
               tag_link('id'),
               ', '.join([value_link('direct'), value_link('mapped')]),
               ', '.join([value_link('direct'), value_link('mapped')])])),
        TR(TD([name_link('jvm.gc.maxDataSize'),
               '',
               'n/a',
               'n/a'])),
        TR(TD([name_link('tasks'),
               tag_link('success'),
               value_link('true'),
               ''])),
    ])
    expect = minimize_html(
        http_server.build_html_document(expect_body, 'Metric Usage'))
    self.assertEqual(expect, html)


def minimize_html(html):
  html = html.replace('\n', '')
  return re.sub(r' border=1', '', html)

if __name__ == '__main__':
  # pylint: disable=invalid-name
  loader = unittest.TestLoader()
  suite = loader.loadTestsFromTestCase(MetricCollectorHandlersTest)
  unittest.TextTestRunner(verbosity=2).run(suite)
