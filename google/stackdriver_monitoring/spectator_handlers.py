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

from datetime import datetime
import json

import command_processor
import http_server
import spectator_client


def accepts_html(request):
  if not hasattr(request, 'headers'):
    return False
  accept = request.headers.get('accept', None)
  if not accept:
    return None
  return 'text/html' in accept.split(',')


def millis_to_time(millis):
  return datetime.fromtimestamp(millis / 1000).isoformat('T') + 'Z'


def strip_non_html_params(options):
  params = {}
  for key in ['tagNameRegex', 'tagValueRegex', 'metricNameRegex']:
    if key in options:
      params[key] = options[key]
  return params


class BaseSpectatorCommandHandler(command_processor.CommandHandler):
  def make_spectator_client(self, options):
    return spectator_client.SpectatorClient(options)

  def add_argparser(self, subparsers):
    parser = super(BaseSpectatorCommandHandler, self).add_argparser(subparsers)
    parser.add_argument('--by', default='service',
                        help='Organize by "service" or by "metric" name.')
    spectator_client.SpectatorClient.add_standard_parser_arguments(parser)
    return parser

  def _get_data_map(self, service_endpoints, options):
    restrict_services = options.get('services', None)
    if restrict_services:
      service_endpoints = {service: endpoints
                           for service, endpoints in service_endpoints.items()
                           if service in restrict_services.split(',')}
    spectator = self.make_spectator_client(options)

    by = options.get('by', 'service')
    if by == 'service':
      data_map = spectator.scan_by_service(service_endpoints, params=options)
    else:
      data_map = spectator.scan_by_type(service_endpoints, params=options)
    return data_map


class DumpMetricsHandler(BaseSpectatorCommandHandler):
  def process_commandline_request(self, options):
    service_endpoints = spectator_client.determine_service_endpoints(options)
    data_map = self._get_data_map(service_endpoints, options)
    json_text = json.JSONEncoder(indent=2).encode(data_map)
    self.output(options, json_text)

  def process_web_request(self, request, path, params, fragment):
    options = dict(command_processor.get_global_options())
    options.update(params)
    all_service_endpoints = spectator_client.determine_service_endpoints(
        options)
    param_services = params.get('services', 'all').split(',')
    if param_services == 'all':
      service_endpoints = all_service_endpoints
    else:
      service_endpoints = {key: value
                           for key, value in all_service_endpoints.items()
                           if key in all_service_endpoints.keys()}

    data_map = self._get_data_map(service_endpoints, options)
    body = json.JSONEncoder(indent=2).encode(data_map)
    request.respond(200, {'ContentType': 'application/json'}, body)


class ExploreCustomDescriptorsHandler(BaseSpectatorCommandHandler):
  """Show all the current descriptors in use, and who is using them."""

  def __get_type_and_tag_map_and_active_services(
        self, service_endpoints, options):
    service_endpoints = spectator_client.determine_service_endpoints(options)
    spectator = self.make_spectator_client(options)

    type_map = spectator.scan_by_type(service_endpoints, params=options)
    service_tag_map, active_services = self.to_service_tag_map(type_map)
    return type_map, service_tag_map, active_services

  def process_commandline_request(self, options):
    service_endpoints = spectator_client.determine_service_endpoints(options)
    type_map, service_tag_map, active_services = (
        self.__get_type_and_tag_map_and_active_services(
            service_endpoints, options))

    params = strip_non_html_params(options)
    html = self.to_html(type_map, service_tag_map, active_services, params)
    html_doc = http_server.build_html_document(
        html, title='Metric Usage')
    self.output(options, html_doc)

  def process_web_request(self, request, path, params, fragment):
    options = dict(command_processor.get_global_options())
    options.update(params)
    service_endpoints = spectator_client.determine_service_endpoints(options)

    type_map, service_tag_map, active_services = (
        self.__get_type_and_tag_map_and_active_services(
            service_endpoints, options))

    params = strip_non_html_params(options)
    html = self.to_html(type_map, service_tag_map, active_services, params)
    html_doc = http_server.build_html_document(
        html, title='Metric Usage')
    request.respond(200, {'ContentType': 'text/html'}, html_doc)

  @staticmethod
  def to_service_tag_map(type_map):
    service_tag_map = {}
    active_services = set()

    def process_endpoint_values_helper(key, service, values):
      if not isinstance(values, dict):
        return
      tagged_data = values.get('values', [])
      for tagged_point in tagged_data:
        tag_map = {tag['key']: tag['value']
                  for tag in tagged_point.get('tags')}
        if not tag_map:
          tag_map = {None: None}
        if key not in service_tag_map:
          service_tag_map[key] = {service: [tag_map]}
        else:
          service_map = service_tag_map[key]
          if service in service_map:
             service_map[service].append(tag_map)
          else:
             service_map[service] = [tag_map]

    for key, entry in sorted(type_map.items()):
      # pylint: disable=bad-indentation
      for service, value_list in sorted(entry.items()):
        active_services.add(service)
        for value in value_list:
          process_endpoint_values_helper(key, service, value)

    return service_tag_map, active_services

  @staticmethod
  def to_tag_service_map(columns, service_tag_map):
    tag_service_map = {}
    for service, tags in service_tag_map.items():
      service_index = columns[service]

      for tag_group in tags:
        for tag_name, tag_value in tag_group.items():
          if tag_name not in tag_service_map:
            tag_service_map[tag_name] = [set() for ignore in columns]
          tag_service_map[tag_name][service_index].add(tag_value)

    return tag_service_map

  def to_html(self, type_map, service_tag_map, active_services, params=None):
    header_html = ['<tr>', '<th>Metric</th>', '<th>Label</th>']
    columns = {}
    for service_name in sorted(active_services):
      columns[service_name] = len(columns)
      header_html.append('<th><A href="/show?services={0}">{0}</A></th>'.format(
          service_name))
    header_html.append('</tr>')

    html = ['<table border=1>']
    html.extend(header_html)

    for type_name, service_tag_map in sorted(service_tag_map.items()):
      tag_service_map = self.to_tag_service_map(columns, service_tag_map)
      num_labels = len(tag_service_map)

      row_html = ['<tr>']
      row_span = ' rowspan={0}'.format(num_labels) if num_labels > 1 else ''
      query_params = dict(params or {})
      query_params['meterNameRegex'] = type_name
      metric_url = '/show{0}'.format(self.params_to_query(query_params))
      row_html.append(
          '<td{row_span}><A href="{url}">{type_name}</A></td>'.format(
              row_span=row_span, url=metric_url, type_name=type_name))

      for label_name, service_values in tag_service_map.items():
        if label_name is None:
          row_html.append('<td></td>')
        else:
          row_html.append(
              '<td><A href="/explore?tagNameRegex={0}">{0}</A></td>'.format(
                  label_name))
        for value_set in service_values:
          if value_set == set([None]):
            row_html.append('<td>n/a</td>')
          else:
            row_html.append(
                '<td>{0}</td>'.format(', '.join(
                  ['<A href="/explore?tagValueRegex={v}">{v}</A>'.format(
                      v=value)
                   for value in sorted(value_set)])))
        row_html.append('</tr>')
        html.append(''.join(row_html))
        row_html = ['<tr>']  # prepare for next row if needed

    html.append('</table>')
    return '\n'.join(html)


class TagValue(object):
  def __init__(self, tag):
    self.key = tag['key']
    self.value = tag['value']

  def __hash__(self):
    return hash((self.key, self.value))

  def __eq__(self, value):
    return self.key == value.key and self.value == value.value

  def __repr__(self):
    return self.__str__()

  def __str__(self):
    return '{0}={1}'.format(self.key, self.value)

  def as_html(self):
    return '<code><b>{0}</b>={1}</code>'.format(self.key, self.value)


class ShowCurrentMetricsHandler(BaseSpectatorCommandHandler):
  """Show all the current metric values."""

  def process_commandline_request(self, options):
    service_endpoints = spectator_client.determine_service_endpoints(options)
    data_map = self._get_data_map(service_endpoints, options)
    by = options.get('by', 'service')
    if by == 'service':
      content_data = self.service_map_to_text(data_map, params=options)
    else:
      content_data = self.type_map_to_text(data_map, params=options)
    self.output(options, content_data)

  def process_web_request(self, request, path, params, fragment):
    options = dict(command_processor.get_global_options())
    options.update(params)
    service_endpoints = spectator_client.determine_service_endpoints(options)
    data_map = self._get_data_map(service_endpoints, options)

    if accepts_html(request):
      content_type = 'text/html'
      by_service = self.service_map_to_html
      by_type = self.type_map_to_html
    else:
      content_type = 'text/plain'
      by_service = self.service_map_to_text
      by_type = self.type_map_to_text

    by = options.get('by', 'service')
    if by == 'service':
      content_data = by_service(data_map, params=params)
    else:
      content_data = by_type(data_map, params=params)

    if content_type == 'text/html':
      body = http_server.build_html_document(
          content_data, title='Current Metrics')
    else:
      body = content_data
    request.respond(200, {'ContentType': content_type}, body)

  def all_tagged_values(self, value_list):
    all_values = []
    for data in value_list:
      tags = [TagValue(tag) for tag in data.get('tags', [])]
      all_values.append((tags, data['values']))
    return all_values

  def data_points_to_td(self, data_points):
    if len(data_points) == 1:
      point = data_points[0]
      return '<td>{time}</td><td>{value}</td>'.format(
          time=millis_to_time(point['t']), value=point['v'])

      td_html = '<td colspan=2><table>'
      for point in data_points:
        td_html += '<tr><td>{time}</td><td>{value}</td></tr>'.format(
            time=millis_to_time(point['t']),
            value=point['v'])
      td_html += '</tr></table></td>'
      return td_html

  def data_points_to_text(self, data_points):
    text = []
    for point in data_points:
      text.append('{time}  {value}'.format(
          time=millis_to_time(point['t']),
          value=point['v']))
    return ', '.join(text)

  def service_map_to_text(self, service_map, params=None):
    lines = []
    def process_metrics_helper(metrics):
      for key, value in metrics.items():
        tagged_values = self.all_tagged_values(value.get('values'))
        parts = ['Service "{0}"'.format(service)]
        parts.append('  {0}'.format(key))

        for one in tagged_values:
          tag_list = one[0]
          tag_text = ', '.join([str(elem) for elem in tag_list])
          time_values = self.data_points_to_text(one[1])
          parts.append('    Tags={0}'.format(tag_text))
          parts.append('    Values={0}'.format(time_values))
        lines.append('\n'.join(parts))

    for service, entry_list in sorted(service_map.items()):
      for entry in entry_list or []:
        process_metrics_helper(entry.get('metrics', {}))

    return '\n\n'.join(lines)

  def service_map_to_html(self, service_map, params=None):
    column_headers_html = ('<tr><th>Service</th><th>Key</th><th>Tags</th>'
                           '<th>Timestamp</th><th>Value</th></tr>')
    result = ['<table>',
              '<tr><th>Service</th><th>Metric</th>'
              '<th>Timestamp</th><th>Values</th><th>Labels</th></tr>']
    def process_metrics_helper(metrics):
      for key, value in metrics.items():
          # pylint: disable=bad-indentation
          tagged_values = self.all_tagged_values(value.get('values'))
          service_url = '/show{0}'.format(
              self.params_to_query({'services': service}))
          metric_url = '/show{0}'.format(
              self.params_to_query({'meterNameRegex': key}))
          html = (
              '<tr>'
              '<th rowspan={rowspan}><A href="{service_url}">{service}</A></th>'
              '<th rowspan={rowspan}><A href="{metric_url}">{key}</A></th>'
              .format(rowspan=len(tagged_values),
                      service_url=service_url,
                      service=service,
                      metric_url=metric_url,
                      key=key))
          for one in tagged_values:
            tag_list = one[0]
            tag_html = '<br/>'.join([elem.as_html() for elem in tag_list])
            time_value_td = self.data_points_to_td(one[1])
            html += '{time_value_td}<td>{tag_list}</td></tr>'.format(
                time_value_td=time_value_td, tag_list=tag_html)
            result.append(html)
            html = '<tr>'

    for service, entry_list in sorted(service_map.items()):
      for entry in entry_list or []:
        process_metrics_helper(entry.get('metrics', {}))
    result.append('</table>')
    return '\n'.join(result)

  def type_map_to_text(self, type_map, params=None):
    lines = []
    def process_values_helper(values):
      tagged_values = self.all_tagged_values(values)
      for tag_value in tagged_values:
        text_key = ', '.join([str(tag) for tag in tag_value[0]])
        tag_to_service_values[text_key] = (service, tag_value[1])

    for key, entry in sorted(type_map.items()):
      tag_to_service_values = {}
      for service, value_list in sorted(entry.items()):
        for value in value_list:
          process_values_helper(value.get('values'))

      parts = ['Metric "{0}"'.format(key)]
      for tags_text, values in sorted(tag_to_service_values.items()):
        parts.append('  Service "{0}"'.format(values[0]))
        parts.append('    Value: {0}'.format(
            self.data_points_to_text(values[1])))
        parts.append('    Tags: {0}'.format(tags_text))
      lines.append('\n'.join(parts))
    return '\n\n'.join(lines)

  def type_map_to_html(self, type_map, params=None):
    """Helper function to render descriptor usage into text."""

    column_headers_html = ('<tr><th>Key</th><th>Timestamp</th><th>Value</th>'
                           '<th>Service</th><th>Tags</th></tr>')
    row_html = []
    def process_values_helper(values):
      tagged_values = self.all_tagged_values(values)
      for tag_value in tagged_values:
        html_key = '<br/>'.join([tag.as_html() for tag in tag_value[0]])
        tag_to_service_values[html_key] = (service, tag_value[1])

    for key, entry in sorted(type_map.items()):
      tag_to_service_values = {}
      for service, value_list in sorted(entry.items()):
        for value in value_list or []:
          process_values_helper(value.get('values'))

      row_html.append('<tr><td rowspan={rowspan}><b>{key}</b></td>'.format(
          rowspan=len(tag_to_service_values), key=key))

      sep = ''
      for tags_html, values in sorted(tag_to_service_values.items()):
        time_value_td = self.data_points_to_td(values[1])
        row_html.append('{sep}{time_value_td}'
                        '<td><i>{service}</i></td><td>{tags}</td></tr>'
                        .format(sep=sep, time_value_td=time_value_td,
                                service=values[0], tags=tags_html))
        sep = '<tr>'

    return '<table>\n{header}\n{rows}\n</table>'.format(
        header=column_headers_html, rows='\n'.join(row_html))


def add_handlers(handler_list, subparsers):
  command_handlers = [
      ShowCurrentMetricsHandler(
          '/show', 'show', 'Show current metric JSON for all Spinnaker.'),
      DumpMetricsHandler(
          '/dump', 'dump',
          'Show current raw metric JSON from all the servers.'),
      ExploreCustomDescriptorsHandler(
          '/explore', 'explore',
          'Explore metric type usage across Spinnaker microservices.')
  ]
  for handler in command_handlers:
    handler.add_argparser(subparsers)
    handler_list.append(handler)
