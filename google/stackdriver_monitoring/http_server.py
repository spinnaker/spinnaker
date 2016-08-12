import BaseHTTPServer


class DelegatingRequestHandler(BaseHTTPServer.BaseHTTPRequestHandler):
  def build_html_document(self, body, title=None):
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

  def respond(self, code, headers, body=None):
    self.send_response(code)
    for key, value in headers.items():
      self.send_header(key, value)
    self.end_headers()
    if body:
      self.wfile.write(body)

  def decode_request(self, request):
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
    self.respond(200, {'Content-Type': 'text/html'})

  def do_GET(self):
    path, parameters, fragment = self.decode_request(self.path)
    offset = len(path)
    handler = None
    while handler is None and offset >= 0:
      handler = HttpServer.PATH_HANDLERS.get(path[0:offset])
      if handler is None:
        offset = path.rfind('/', 0, offset)

    if handler is None:
      self.respond(404, {'Content-Type': 'text/html'}, "Unknown");
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
