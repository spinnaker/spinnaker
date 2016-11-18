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


def accepts_html(request):
  if not hasattr(request, 'headers'):
    return False
  accept = request.headers.get('accept', None)
  if not accept:
    return None
  return 'text/html' in accept.split(',')


def millis_to_time(millis):
  return datetime.fromtimestamp(millis / 1000).isoformat('T') + 'Z'


def params_to_query(params):
  query_list = ['{0}={1}'.format(key, value) for key, value in params.items()]
  return '?{0}'.format('&'.join(query_list)) if query_list else ''


class BaseHandler(object):
  def __init__(self, options, registry):
    self.__registry = registry

  def __call__(self, request, path, params, fragment):
    query = params_to_query(params)
    rows = [(entry.url_path, entry.description) for entry in self.__registry]
    row_html = [('<tr>'
                 '<td><A href="{path}{params}">{path}</A></td>'
                 '<td>{info}</td>'
                 '</tr>'.format(path=row[0], params=query, info=row[1]))
                for row in rows]

    html_body = '<table>\n{0}\n</table>'.format('\n'.join(row_html))
    html_doc = request.build_html_document(
        html_body, title='Spinnaker Metrics Administration')
    request.respond(200, {'ContentType': 'application/html'}, html_doc)


class DumpMetricsHandler(object):
  def __init__(self, options, spectator):
    self.__service_list = options.get('services', ['all'])
    self.__spectator = spectator

  def __call__(self, request, path, params, fragment):
    services_text = params.get('services', None)
    service_list = (services_text.split(',')
                    if services_text else self.__service_list)

    by = params.get('by', 'service')
    if by == 'service':
      data_map = self.__spectator.scan_by_service(service_list, params=params)
    else:
      data_map = self.__spectator.scan_by_type(service_list, params=params)
    body = json.JSONEncoder(indent=2).encode(data_map)
    request.respond(200, {'ContentType': 'application/json'}, body)


class ExploreCustomDescriptorsHandler(object):
  """Show all the current descriptors in use, and who is using them."""
  def __init__(self, options, spectator):
    self.__spectator = spectator
    self.__service_list = options.get('services', ['all'])

  def __call__(self, request, path, params, fragment):
    services_text = params.get('services', None)
    service_list = (services_text.split(',')
                    if services_text else self.__service_list)

    type_map = self.__spectator.scan_by_type(service_list, params=params)
    service_tag_map, active_services = self.to_service_tag_map(type_map)
    html = self.to_html(type_map, service_tag_map, active_services, params)
    html_doc = request.build_html_document(html, title='Metric Usage')
    request.respond(200, {'ContentType': 'text/html'}, html_doc)

  @staticmethod
  def to_service_tag_map(type_map):
    service_tag_map = {}
    active_services = set()
    for key, entry in sorted(type_map.items()):
      # pylint: disable=bad-indentation
      for service, value in sorted(entry.items()):
        active_services.add(service)
        tagged_data = value.get('values')
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
      header_html.append('<th>{0}</th>'.format(service_name))
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
      metric_url = '/show{0}'.format(params_to_query(query_params))
      row_html.append(
          '<td{row_span}><A href="{url}">{type_name}</A></td>'.format(
              row_span=row_span, url=metric_url, type_name=type_name))

      for label_name, service_values in tag_service_map.items():
        if label_name is None:
          label_name = ''
        row_html.append('<td>{0}</td>'.format(label_name))
        for value_set in service_values:
          if value_set == set([None]):
            value_set = ['n/a']
          row_html.append('<td>{0}</td>'.format(', '.join(sorted(value_set))))
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


class ShowCurrentMetricsHandler(object):
  """Show all the current metric values."""
  def __init__(self, options, spectator):
    self.__spectator = spectator
    self.__service_list = options.get('services', ['all'])

  def __call__(self, request, path, params, fragment):
    services_text = params.get('services', None)
    service_list = (services_text.split(',')
                    if services_text else self.__service_list)

    if accepts_html(request):
      content_type = 'text/html'
      by_service = self.service_map_to_html
      by_type = self.type_map_to_html
    else:
      content_type = 'text/plain'
      by_service = self.service_map_to_text
      by_type = self.type_map_to_text

    by = params.get('by', 'service')
    if by == 'service':
      service_map = self.__spectator.scan_by_service(
          service_list, params=params)
      content_data = by_service(service_map, params=params)
    else:
      type_map = self.__spectator.scan_by_type(service_list, params=params)
      content_data = by_type(type_map, params=params)

    if content_type == 'text/html':
      body = request.build_html_document(content_data, title='Current Metrics')
    else:
      body = content_data
    request.respond(200, {'ContentType': content_type}, body)

  def all_tagged_values(self, value_list):
    all_values = []
    for data in value_list:
      all_points = []
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
    if len(data_points) == 1:
      point = data_points[0]
      return '{time} {value}'.format(
          time=millis_to_time(point['t']), value=point['v'])

      text = []
      for point in data_points:
        text.append('{time}  {value}'.format(
            time=millis_to_time(point['t']),
            value=point['v']))
      return ', '.join(text)

  def service_map_to_text(self, service_map, params=None):
    lines = []
    for service, entry in sorted(service_map.items()):
      metrics = entry.get('metrics', {})
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
    return '\n\n'.join(lines)

  def service_map_to_html(self, service_map, params=None):
    column_headers_html = ('<tr><th>Service</th><th>Key</th><th>Tags</th>'
                           '<th>Timestamp</th><th>Value</th></tr>')
    result = ['<table>',
              '<tr><th>Service</th><th>Metric</th>'
              '<th>Timestamp</th><th>Values</th><th>Labels</th></tr>']
    for service, entry in sorted(service_map.items()):
      metrics = entry.get('metrics', {})
      for key, value in metrics.items():
          # pylint: disable=bad-indentation
          tagged_values = self.all_tagged_values(value.get('values'))
          service_url = '/show{0}'.format(
              params_to_query({'services': service}))
          metric_url = '/show{0}'.format(
              params_to_query({'meterNameRegex': key}))
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
    result.append('</table>')
    return '\n'.join(result)

  def type_map_to_text(self, type_map, params=None):
    lines = []
    for key, entry in sorted(type_map.items()):
      tag_to_service_values = {}
      for service, value in sorted(entry.items()):
        tagged_values = self.all_tagged_values(value.get('values'))
        for tag_value in tagged_values:
          text_key = ', '.join([str(tag) for tag in tag_value[0]])
          tag_to_service_values[text_key] = (service, tag_value[1])

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

    for key, entry in sorted(type_map.items()):
      tag_to_service_values = {}
      for service, value in sorted(entry.items()):
        tagged_values = self.all_tagged_values(value.get('values'))
        for tag_value in tagged_values:
          html_key = '<br/>'.join([tag.as_html() for tag in tag_value[0]])
          tag_to_service_values[html_key] = (service, tag_value[1])

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
