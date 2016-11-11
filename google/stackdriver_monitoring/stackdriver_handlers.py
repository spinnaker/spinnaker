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

import cgi
import logging
from stackdriver_client import StackdriverClient


def accepts_html(request):
  if not hasattr(request, 'headers'):
    return False
  accept = request.headers.get('accept', None)
  if not accept:
    return None
  return 'text/html' in str(accept)


class ListCustomDescriptorsHandler(object):
  """Administrative handler to list all the known descriptors."""

  @staticmethod
  def compare_types(a, b):
    # pylint: disable=invalid-name
    a_root = a['type'][len(StackdriverClient.CUSTOM_PREFIX):]
    b_root = b['type'][len(StackdriverClient.CUSTOM_PREFIX):]
    return (-1 if a_root < b_root
            else 0 if a_root == b_root
            else 1)

  def __init__(self, options, stackdriver):
    self.__stackdriver = stackdriver
    self.__project = options.project

  def __call__(self, request, path, params, fragment):
    project = params.get('project', self.__project)
    type_map = self.__stackdriver.fetch_custom_descriptors(project)
    descriptor_list = type_map.values()
    descriptor_list.sort(self.compare_types)

    if accepts_html(request):
      html = self.descriptors_to_html(descriptor_list)
      html_doc = request.build_html_document(html, title='Custom Descriptors')
      request.respond(200, {'ContentType': 'text/html'}, html_doc)
    else:
      text = self.descriptors_to_text(descriptor_list)
      request.respond(200, {'ContentType': 'text/plain'}, text)

  def collect_rows(self, descriptor_list):
    rows = []
    for elem in descriptor_list:
      type_name = elem['type'][len(StackdriverClient.CUSTOM_PREFIX):]
      labels = elem.get('labels', [])
      label_names = [k['key'] for k in labels]
      rows.append((type_name, label_names))
    return rows

  def descriptors_to_html(self, descriptor_list):
    rows = self.collect_rows(descriptor_list)

    html = ['<table>', '<tr><th>Custom Type</th><th>Labels</th></tr>']
    html.extend(['<tr><td><b>{0}</b></td><td><code>{1}</code></td></tr>'
                 .format(row[0], ', '.join(row[1]))
                 for row in rows])
    html.append('</table>')
    html.append('<p>Found {0} Custom Metrics</p>'
                .format(len(descriptor_list)))
    return '\n'.join(html)

  def descriptors_to_text(self, descriptor_list):
    rows = self.collect_rows(descriptor_list)

    text = []
    for row in rows:
      text.append('{0}\n  Tags={1}'.format(row[0], ','.join(row[1])))
    text.append('Found {0} Custom Metrics'.format(len(descriptor_list)))
    return '\n\n'.join(text)


class ClearCustomDescriptorsHandler(object):
  """Administrative handler to clear all the known descriptors.

  This clears all the TimeSeries history as well.
  """
  def __init__(self, options, stackdriver):
    self.__stackdriver = stackdriver
    self.__project = options.project

  def __call__(self, request, path, params, fragment):
    project = params.get('project', self.__project)

    delete_method = (self.__stackdriver.service.projects()
                     .metricDescriptors().delete)

    type_map = self.__stackdriver.fetch_custom_descriptors(project)

    all_names = [descriptor['name'] for descriptor in type_map.values()]
    batch = self.__stackdriver.service.new_batch_http_request()
    max_batch = 100
    count = 0

    class BatchResponseHandler(object):
      def __init__(self, num):
        """Constructor.

        Args:
          num: [int] The number of responses we expect.
        """
        self.batch_response = [None] * num
        self.num_ok = 0   # number of successful responses.

      def handle_batch_response(self, index_str, good, bad):
        """Record an individual response.

        Args:
          index_str: [string] The index in the batch response corresponds to
             the index in the original query so the mapping is 1:1.
          good: [string] The response from the request, which succeeded.
          bad: [string] The response from the request, which failed.
        """
        index = int(index_str)
        if bad:
          self.batch_response[index] = 'ERROR {0}'.format(cgi.escape(str(bad)))
          logging.error(bad)
        else:
          self.num_ok += 1
          if not good:
            good = ''
          self.batch_response[index] = 'OK {0}'.format(cgi.escape(good))

    handler = BatchResponseHandler(len(all_names))
    for name in all_names:
      logging.info('batch DELETE %s', name)
      invocation = delete_method(name=name)
      batch.add(invocation, callback=handler.handle_batch_response,
                request_id=str(count))
      count += 1
      if count % max_batch == 0:
        logging.info('Executing batch of %d', max_batch)
        batch.execute()
        batch = self.__stackdriver.service.new_batch_http_request()

    if count % max_batch:
      logging.info('Executing final batch of %d', count % max_batch)
      batch.execute()

    response_code = 200 if handler.num_ok == len(all_names) else 500
    if accepts_html(request):
      html_rows = [('<tr><td>{0}</td><td>{1}</td></tr>'
                    .format(all_names[i], handler.batch_response[i]))
                   for i in range(len(all_names))]
      html_body = 'Deleted {0} of {1}:\n<table>\n{2}\n</table>'.format(
          handler.num_ok, len(all_names), '\n'.join(html_rows))
      html_doc = request.build_html_document(html_body, title='Cleared Time Series')
      request.respond(response_code, {'ContentType': 'text/html'}, html_doc)
    else:
      text = ['{0}  {1}'.format(all_names[i], handler.batch_response[i])
              for i in range(len(all_names))]
      text.append('')
      text.append('Deleted {0} of {1}'.format(
            handler.num_ok, len(all_names)))
      request.respond(response_code,
                      {'ContentType': 'text/plain'},
                      '\n'.join(text))

