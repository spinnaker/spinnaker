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

"""Implements HTTP Server."""

import BaseHTTPServer

def build_html_document(body, title=None):
  """Produces the HTML document wrapper for a text/html response."""
  title_html = '<h1>{0}</h1>\n'.format(title) if title else ''
  html = ['<html>', '<head>',
          '<title>{title}</title>'.format(title=title),
          '<style>',
"""
  body { font-size:10pt }
  table { font-size:10pt;border-width:none;
          border-spacing:0px;border-color:#F8F8F8;border-style:solid }
  th, td { padding:2px;vertical-align:top;
           border-width:1px;border-color:#F8F8F8;border-style:solid; }
  th { font-weight:bold;text-align:left;font-family:times }
  th { color:#666666 }
  a, a.toggle:link, a.toggle:visited {
      background-color:#FFFFFF;color:#000099 }
  a.toggle:hover, a.toggle:active {
      color:#FFFFFF;background-color:#000099 }
"""
          '</style>'
          '</head>', '<body>',
          title_html,
          body,
          '</body>', '</html>']
  return '\n'.join(html)


class DelegatingRequestHandler(BaseHTTPServer.BaseHTTPRequestHandler):
  """An HttpServer request handler that delegates to our CommandHandler."""

  def respond(self, code, headers, body=None):
    """Send response to the HTTP request."""
    self.send_response(code)
    for key, value in headers.items():
      self.send_header(key, value)
    self.end_headers()
    if body:
      self.wfile.write(body)

  def decode_request(self, request):
    """Extract the URL components from the request."""
    parameters = {}
    path, ignore, query = request.partition('?')
    if not query:
      return request, parameters, None
    query, ignore, fragment = query.partition('#')

    for part in query.split('&'):
      key, ignore, value = part.partition('=')
      parameters[key] = value

    return path, parameters, fragment

  def do_HEAD(self):
    """Implements BaseHTTPRequestHandler."""
    # pylint: disable=invalid-name
    self.respond(200, {'Content-Type': 'text/html'})

  def do_GET(self):
    """Implements BaseHTTPRequestHandler."""
    # pylint: disable=invalid-name
    path, parameters, fragment = self.decode_request(self.path)
    offset = len(path)
    handler = None
    while handler is None and offset >= 0:
      handler = HttpServer.PATH_HANDLERS.get(path[0:offset])
      if handler is None:
        offset = path.rfind('/', 0, offset)

    if handler is None:
      self.respond(404, {'Content-Type': 'text/html'}, "Unknown")
    else:
      handler(self, path, parameters, fragment)


class StdoutRequestHandler(DelegatingRequestHandler):
  def __init__(self):
    pass

  def respond(self, code, headers, body=None):
    if code >= 300:
      print 'ERROR CODE {0}:\n'.format(code)
    print body


class HttpServer(BaseHTTPServer.HTTPServer):
  PATH_HANDLERS = {}

  def __init__(self, port, handlers=None):
    BaseHTTPServer.HTTPServer.__init__(
        self, ('localhost', port), DelegatingRequestHandler)
    HttpServer.PATH_HANDLERS.update(handlers or {})
