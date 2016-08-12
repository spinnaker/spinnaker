from datetime import datetime
import cgi
import json
import logging
from stackdriver_client import StackdriverClient

def params_to_query(params):
  query_list = ['{0}={1}'.format(key, value) for key, value in params.items()]
  return '?{0}'.format('&'.join(query_list)) if query_list else ''

class BaseHandler(object):
  def __init__(self, options):
    pass

  def __call__(self, request, path, params, fragment):
    query = params_to_query(params)
    rows = [('/clear', 'Clear all Spinnaker Metrics.'),
            ('/dump', 'Show current raw metric JSON from all the servers.'),
            ('/list', 'List all the Stackdriver Custom Metric Descriptors.'),
            ('/explore', 'Explore Custom Metric Type usage across Spinnaker.'),
            ('/show', 'Show current metric JSON for all Spinnaker.')]
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
    self.__service_list = options.services
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

    html = self.descriptors_to_html(descriptor_list)
    html_doc = request.build_html_document(html, title='Custom Descriptors')
    request.respond(200, {'ContentType': 'text/html'}, html_doc)

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
        self.batch_response = [None] * num
        self.num_ok = 0

      def handle_batch_response(self, index_str, good, bad):
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

    html_rows = [('<tr><td>{0}</td><td>{1}</td></tr>'
                  .format(all_names[i], handler.batch_response[i]))
                 for i in range(len(all_names))]
    html_body = 'Deleted {0} of {1}:\n<table>\n{2}\n</table>'.format(
        handler.num_ok, len(all_names), '\n'.join(html_rows))
    html_doc = request.build_html_document(html_body, title='Cleared Time Series')
    response_code = 200 if handler.num_ok == len(all_names) else 500
    request.respond(response_code, {'ContentType': 'text/html'}, html_doc)


class ExploreCustomDescriptorsHandler(object):
  """Show all the current descriptors in use, and who is using them."""
  def __init__(self, options, spectator):
    self.__spectator = spectator
    self.__service_list = options.services or ['all']

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
      row_html.append('<td{row_span}><A href="{url}">{type_name}</A></td>'.format(
          row_span=row_span, url=metric_url, type_name=type_name))
      sep = ''
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
    self.__service_list = options.services or ['all']

  def __call__(self, request, path, params, fragment):
    services_text = params.get('services', None)
    service_list = (services_text.split(',')
                    if services_text else self.__service_list)

    by = params.get('by', 'service')
    if by == 'service':
      service_map = self.__spectator.scan_by_service(service_list, params=params)
      html = self.service_map_to_html(service_map, params=params)
    else:
      type_map = self.__spectator.scan_by_type(service_list, params=params)
      html = self.type_map_to_html(type_map, params=params)
    html_doc = request.build_html_document(html, title='Current Metrics')
    request.respond(200, {'ContentType': 'text/html'}, html_doc)

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
          time=StackdriverClient.millis_to_time(point['t']), value=point['v'])

      td_html = '<td colspan=2><table>'
      for point in data_points:
        td_html += '<tr><td>{time}</td><td>{value}</td></tr>'.format(
            time=StackdriverClient.millis_to_time(point['t']),
            value=point['v'])
      td_html += '</tr></table></td>'
      return td_html

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
          service_url = '/show{0}'.format(params_to_query({'services': service}))
          metric_url = '/show{0}'.format(params_to_query({'meterNameRegex': key}))
          html = ('<tr>'
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
