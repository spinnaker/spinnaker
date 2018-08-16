#!/usr/bin/env python

from random import randrange
from flask import Flask
from prometheus_client import start_http_server, Counter
import os

app = Flask('kayenta-tester')
c = Counter('requests', 'Number of requests served, by http code', ['http_code'])

@app.route('/')
def hello():
    if randrange(1, 100) > int(os.environ['SUCCESS_RATE']):
        c.labels(http_code = '500').inc()
        return "Internal Server Error\n", 500
    else:
        c.labels(http_code = '200').inc()
        return "Hello World!\n"

start_http_server(8000)
app.run(host = '0.0.0.0', port = 8080)
