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
import httplib
import json
import logging

from command_processor import CommandHandler
from command_processor import get_global_options
import http_server
import stackdriver_service
from stackdriver_service import StackdriverMetricsService


def accepts_content_type(request, content_type):
  """Determine if the request wants the given content_type as a response."""
  if not hasattr(request, 'headers'):
    return False
  accept = request.headers.get('accept', None)
  if not accept:
    return None
  return content_type in str(accept)


class BatchProcessor(object):
  """Helper class for managing events in batch."""
  def __init__(self, project, stackdriver, data_list,
               invocation_factory, get_name):
    """Constructor.

    Args:
      data_list: [object] The data to operate on.
    """
    self.__project = project
    self.__stackdriver = stackdriver
    self.__data_list = data_list
    self.__num_data = len(self.__data_list)
    self.__invocation_factory = invocation_factory
    self.__get_name = get_name

    self.batch_response = [None] * self.__num_data
    self.num_bad = 0  # number of unsuccessful responses.
    self.num_ok = 0   # number of successful responses.
    self.was_ok = [None] * self.__num_data

  def handle_batch_response(self, index_str, response, exception):
    """Record an individual response.

    Args:
      index_str: [string] The index in the batch response corresponds to
         the index in the original query so the mapping is 1:1.
      response: [HttpResponse] The response from the request, which succeeded.
      exception: [Exception] The exception from the request, which failed.
    """
    index = int(index_str)
    if exception:
      self.was_ok[index] = False
      self.num_bad += 1
      self.batch_response[index] = 'ERROR {0}'.format(
          cgi.escape(str(exception)))
      logging.error(exception)
    else:
      self.was_ok[index] = True
      self.num_ok += 1
      self.batch_response[index] = 'OK {0}'.format(
          cgi.escape(str(response)))

  def process(self):
    """Process all the data by sending one or more batches."""
    batch = self.__stackdriver.stub.new_batch_http_request()
    max_batch = 100
    count = 0

    for data in self.__data_list:
      invocation = self.__invocation_factory(data)
      batch.add(invocation, callback=self.handle_batch_response,
                request_id=str(count))
      count += 1
      if count % max_batch == 0:
        decorator = ('final batch'
                     if count == len(self.__data_list)
                     else 'batch')
        logging.info('Executing %s of %d', decorator, max_batch)
        batch.execute()
        batch = self.__stackdriver.stub.new_batch_http_request()

    if count % max_batch:
      logging.info('Executing final batch of %d', count % max_batch)
      batch.execute()

  def make_response(self, request, as_html, action, title):
    """Create a response for the caller to ultimately send."""
    if as_html:
      html_rows = [('<tr><td>{0}</td><td>{1}</td></tr>\n'
                    .format(self.__get_name(self.__data_list[i]),
                            self.batch_response[i]))
                   for i in range(self.__num_data)]
      html_body = '{0} {1} of {2}:\n<table>\n{3}\n</table>'.format(
          action, self.num_ok, self.__num_data, '\n'.join(html_rows))
      html_doc = http_server.build_html_document(
          html_body, title=title)
      return {'ContentType': 'text/html'}, html_doc

    text = ['{0}  {1}'.format(self.__get_name(self.__data_list[i]),
                              self.batch_response[i])
            for i in range(self.__num_data)]
    text.append('')
    text.append('{0} {1} of {2}'.format(action, self.num_ok, self.__num_data))
    return {'ContentType': 'text/plain'}, '\n'.join(text)


class BaseStackdriverCommandHandler(CommandHandler):
  """Base CommandHandler for Stackdriver commands."""

  def add_argparser(self, subparsers):
    """Implements CommandHandler."""
    parser = super(BaseStackdriverCommandHandler, self).add_argparser(
        subparsers)
    stackdriver_service.StackdriverMetricsService.add_parser_arguments(parser)
    return parser


class ListCustomDescriptorsHandler(BaseStackdriverCommandHandler):
  """Administrative handler to list all the known descriptors."""

  @staticmethod
  def compare_types(a, b):
    """Compare two metric types to sort them in order."""
    # pylint: disable=invalid-name
    a_root = a['type'][len(StackdriverMetricsService.CUSTOM_PREFIX):]
    b_root = b['type'][len(StackdriverMetricsService.CUSTOM_PREFIX):]
    return (-1 if a_root < b_root
            else 0 if a_root == b_root
            else 1)

  def __get_descriptor_list(self, options):
    stackdriver = stackdriver_service.make_service(options)
    project = options.get('project', None)
    type_map = stackdriver.fetch_all_custom_descriptors(project)
    descriptor_list = type_map.values()
    descriptor_list.sort(self.compare_types)
    return descriptor_list

  def process_commandline_request(self, options):
    descriptor_list = self.__get_descriptor_list(options)
    json_text = json.JSONEncoder(indent=2).encode(descriptor_list)
    self.output(options, json_text)

  def process_web_request(self, request, path, params, fragment):
    options = dict(get_global_options())
    options.update(params)
    descriptor_list = self.__get_descriptor_list(options)

    if accepts_content_type(request, 'text/html'):
      html = self.descriptors_to_html(descriptor_list)
      html_doc = http_server.build_html_document(
          html, title='Custom Descriptors')
      request.respond(200, {'ContentType': 'text/html'}, html_doc)
    elif accepts_content_type(request, 'application/json'):
      json_doc = json.JSONEncoder(indent=2).encode(descriptor_list)
      request.respond(200, {'ContentType': 'application/json'}, json_doc)
    else:
      text = self.descriptors_to_text(descriptor_list)
      request.respond(200, {'ContentType': 'text/plain'}, text)

  def collect_rows(self, descriptor_list):
    rows = []
    for elem in descriptor_list:
      type_name = elem['type'][len(StackdriverMetricsService.CUSTOM_PREFIX):]
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


class ClearCustomDescriptorsHandler(BaseStackdriverCommandHandler):
  """Administrative handler to clear all the known descriptors.

  This clears all the TimeSeries history as well.
  """

  def __do_clear(self, options):
    """Deletes exsiting custom metric descriptors."""
    project = options.get('project', None)
    stackdriver = stackdriver_service.make_service(options)

    type_map = stackdriver.fetch_all_custom_descriptors(project)
    delete_method = (stackdriver.stub.projects().metricDescriptors().delete)
    def delete_invocation(descriptor):
      name = descriptor['name']
      logging.info('batch DELETE %s', name)
      return delete_method(name=name)
    get_descriptor_name = lambda descriptor: descriptor['name']

    processor = BatchProcessor(
        project, stackdriver,
        type_map.values(), delete_invocation, get_descriptor_name)
    processor.process()
    return type_map, processor

  def process_commandline_request(self, options):
    """Implements CommandHandler."""
    type_map, processor = self.__do_clear(options)
    headers, body = processor.make_response(
        None, False,
        'Deleted', 'Cleared Time Series')
    self.output(options, body)

  def process_web_request(self, request, path, params, fragment):
    """Implements CommandHandler."""
    options = dict(get_global_options())
    options.update(params)
    type_map, processor = self.__do_clear(options)
    response_code = (httplib.OK if processor.num_ok == len(type_map)
                     else httplib.INTERNAL_SERVER_ERROR)
    headers, body = processor.make_response(
        request, accepts_content_type(request, 'text/html'),
        'Deleted', 'Cleared Time Series')
    request.respond(response_code, headers, body)


class UpsertCustomDescriptorsProcessor(object):
  """Administrative helper to update/create new descriptors."""
  def __init__(self, project, stackdriver):
    self.__project = project
    self.__stackdriver = stackdriver

  def __do_batch_create(self, project, create_list):
    """Create the new descriptors as a batch request."""
    create_method = (self.__stackdriver.stub.projects()
                     .metricDescriptors().create)

    def create_invocation(descriptor):
      name = descriptor['name']
      logging.info('batch CREATE %s', name)
      return create_method(
          name='projects/{0}'.format(project), body=descriptor)
    get_descriptor_name = lambda descriptor: descriptor['name']

    processor = BatchProcessor(
        project, self.__stackdriver,
        create_list, create_invocation, get_descriptor_name)
    processor.process()

    response_code = (httplib.OK if processor.num_ok == len(create_list)
                     else httplib.INTERNAL_SERVER_ERROR)
    headers, body = processor.make_response(
        None, False, 'Created', 'Added Descriptor')
    return response_code, headers, body

  def __do_batch_update_delete_helper(
      self, project, delete_list, success_list, failed_list, failed_errors):
    """Delete descriptors as a batch request.

    We need to delete descriptors in order to update them.

    Args:
      project: [string] The project to delete from.
      delete_list: [list of descriptor] The types to delete.
      success_list: [list of descriptor] The types that were actually deleted.
      failed_list: [list of descriptor] The types for which DELETE failed.
      failed_errors: [list of string] The error messages from the failures.
    """
    get_descriptor_name = lambda descriptor: descriptor['name']
    delete_method = (self.__stackdriver.stub.projects()
                     .metricDescriptors().delete)
    def delete_invocation(descriptor):
      """Helper method tocreate the delete request."""
      name = descriptor['name']
      logging.info('batch DELETE %s', name)
      return delete_method(name=name)

    delete_processor = BatchProcessor(
        project, self.__stackdriver,
        delete_list, delete_invocation, get_descriptor_name)
    delete_processor.process()

    for index, ok in enumerate(delete_processor.was_ok):
      if ok:
        success_list.append(delete_list[index])
      else:
        failed_list.append(delete_list[index])
        failed_errors.append(delete_processor.batch_response[index])

  def __do_batch_update_create_helper(
      self, project, create_list, success_list, failed_list, failed_errors):
    """Create descriptors as a batch request.

    We need to create new descriptors to update them.

    Args:
      project: [string] The project to create in.
      delete_list: [list of descriptor] The types to create.
      success_list: [list of descriptor] The types that were actually created.
      failed_list: [list of descriptor] The types for which PUT failed.
      failed_errors: [list of string] The error messages from the failures.
    """
    get_descriptor_name = lambda descriptor: descriptor['name']
    create_method = (self.__stackdriver.stub.projects()
                     .metricDescriptors().create)
    def create_invocation(descriptor):
      """Helper method to create the create request."""
      name = descriptor['name']
      logging.info('batch CREATE %s', name)
      return create_method(
          name='projects/{0}'.format(project), body=descriptor)

    create_processor = BatchProcessor(
        project, self.__stackdriver,
        create_list, create_invocation, get_descriptor_name)
    create_processor.process()

    for index, ok in enumerate(create_processor.was_ok):
      if ok:
        success_list.append(create_list[index])
      else:
        failed_list.append(create_list[index])
        failed_errors.append(create_processor.batch_response[index])

  def __do_batch_update(self, project, update_list, original_type_map):
    """Orchestrate updates of existing descriptors.

    Args:
      project: [string] The project we're updating in.
      update_list: [list of descriptors] The new descriptor definitions.
      original_type_map: [type to descriptor] The original definitions in
       case we need to restore them.
    """
    get_descriptor_name = lambda descriptor: descriptor['name']

    delete_errors = []
    create_errors = []
    restore_errors = []

    failed_list = []
    create_list = []
    success_list = []
    restore_list = []
    not_updated_list = []
    lost_list = []

    if update_list:
      self.__do_batch_update_delete_helper(
          project, update_list, create_list, failed_list, delete_errors)

    if create_list:
      self.__do_batch_update_create_helper(
        project, create_list, success_list, restore_list, create_errors)
      restore_list = [original_type_map[elem['type']] for elem in restore_list]

    if restore_list:
      # If we successfully restore, we left it in the original unupdated state.
      # If we failed to restore, then we've lost the descriptor entirely.
      self.__do_batch_update_create_helper(
        project, restore_list, not_updated_list, lost_list, restore_errors)

    response_code = (httplib.OK if len(failed_list) + len(create_errors) == 0
                     else httplib.INTERNAL_SERVER_ERROR)
    bodies = []
    for elem in success_list:
      bodies.append('Updated {0} to {1}'.format(elem['type'], elem))
    for index, elem in enumerate(failed_list):
      bodies.append('Failed to update {0} to {1}: {2}'.format(
        elem['type'], elem, delete_errors[index]))
    for index, elem in enumerate(restore_list):
      bodies.append('Failed to update {0} to {1}: {2}'.format(
        elem['type'], elem, create_errors[index]))
    for index, elem in enumerate(lost_list):
      bodies.append('Lost {0}. It used to be {1}: {2}'.format(
        elem['type'], elem, restore_errors[index]))

    return response_code, {'Content-Type': 'text/plain'}, '\n'.join(bodies)

  def upsert_descriptors(
      self, project, upsert_descriptors, type_map, output_method):
    create_list = []
    update_list = []
    for elem in upsert_descriptors:
      elem_type = elem.get('type', '')
      if not elem_type.startswith('custom.googleapis.com/spinnaker'):
        raise ValueError('Invalid Metric Descriptor:\n{0}\n'.format(elem))
      if elem_type in type_map:
        if elem != type_map[elem_type]:
          update_list.append(elem)
      else:
        create_list.append(elem)

    headers = {}
    response_code = httplib.OK
    response_body = []

    if create_list:
      create_response_code, create_headers, create_response_body = (
          self.__do_batch_create(project, create_list))
      response_code = max(response_code, create_response_code)
      response_body.append(create_response_body)
      headers.update(create_headers)

    if update_list:
      update_response_code, update_headers, update_response_body = (
          self.__do_batch_update(project, update_list, type_map))
      response_code = max(response_code, update_response_code)
      response_body.append(update_response_body)
      headers.update(update_headers)

    output_method({}, '\n'.join(response_body))


class UpsertCustomDescriptorsHandler(BaseStackdriverCommandHandler):
  """Administrative handler to update/create new descriptors."""

  def add_argparser(self, subparsers):
    """Implements CommandHandler."""
    parser = (super(UpsertCustomDescriptorsHandler, self)
              .add_argparser(subparsers))
    parser.add_argument('--source_path', required=True)
    return parser

  def load_descriptors(self, options):
    """Loads existing descriptors to be uploaded."""
    with open(options['source_path'], 'r') as f:
      return json.JSONDecoder().decode(f.read())

  def process_commandline_request(self, options, upsert_descriptors=None):
    """Implements CommandHandler."""
    if upsert_descriptors is None:
      upsert_descriptors = self.load_descriptors(options)

    stackdriver = stackdriver_service.make_service(options)
    processor = UpsertCustomDescriptorsProcessor(
        options['project'], stackdriver)
    project = options.get('project', None)
    type_map = stackdriver.fetch_all_custom_descriptors(project)

    processor.upsert_descriptors(
        project, upsert_descriptors, type_map, self.output)


def add_handlers(handler_list, subparsers):
  """Registers CommandHandlers for interacting with Stackdriver."""
  command_handlers = [
      ListCustomDescriptorsHandler(
          '/stackdriver/list_descriptors',
          'list_stackdriver',
          'Get the JSON of all the Stackdriver Custom Metric Descriptors.'),
      ClearCustomDescriptorsHandler(
          '/stackdriver/clear_descriptors',
          'clear_stackdriver',
          'Clear all the Stackdriver Custom Metrics'),
      UpsertCustomDescriptorsHandler(
          None,
          'upsert_stackdriver_descriptors',
          'Given a file of Stackdriver Custom Metric Desciptors,'
          ' update the existing ones and add the new ones.'
          ' WARNING: Historic time-series data may be lost on update.')
  ]
  for handler in command_handlers:
    handler.add_argparser(subparsers)
    handler_list.append(handler)
