#!/usr/bin/python
#
# Copyright 2015 Google Inc. All Rights Reserved.
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

import fcntl
import os
import subprocess
import sys
import urllib2

def __collect_from_stream(stream, buffer, echo_stream):
    collected = []
    try:
      while True:
        got = os.read(stream.fileno(), 1)
        if not got:
          break
        collected.append(got)
        if echo_stream:
          echo_stream.write(got)
          echo_stream.flush()
    except OSError:
      pass

    # Chunk together all the data we just received.
    buffer.append(''.join(collected))
    return len(collected)


def run(command, echo=True, input=None):
  if echo:
    print command

  sys.stdout.flush()
  stdin = subprocess.PIPE if input else None
  process = subprocess.Popen(
      command,
      stdout=subprocess.PIPE, stderr=subprocess.PIPE, stdin=stdin,
      shell=True, close_fds=True)

  # Setup for nonblocking reads
  fl = fcntl.fcntl(process.stdout, fcntl.F_GETFL)
  fcntl.fcntl(process.stdout, fcntl.F_SETFL, fl | os.O_NONBLOCK)
  fl = fcntl.fcntl(process.stderr, fcntl.F_GETFL)
  fcntl.fcntl(process.stderr, fcntl.F_SETFL, fl | os.O_NONBLOCK)

  if stdin:
      process.stdin.write(input)
      process.stdin.close()

  out = []
  err = []
  echo_out = sys.stdout if echo else None
  echo_err = sys.stderr if echo else None
  while (__collect_from_stream(process.stdout, out, echo_out)
         or  __collect_from_stream(process.stderr, err, echo_err)
         or process.poll() is None):
      pass

  # Get any trailing data from termination race condition
  __collect_from_stream(process.stdout, out, echo_out)
  __collect_from_stream(process.stderr, err, echo_err)

  return process.returncode, ''.join(out), ''.join(err)


def run_or_die(command, echo=True, input=None):
  code, stdout, stderr = run(command, echo=echo, input=input)
  if code != 0:
    sys.stderr.write('FAILED with exit code {code}\n'.format(code=code))
    raise SystemExit('FAILED {command} with exit code {code}\n{err}'.format(
        command=command, code=code, err=stderr))

  return stdout, stderr


def run_or_die_no_result(command, echo=True):
  stdout = None if echo else subprocess.PIPE
  stderr = None if echo else subprocess.PIPE
  p = subprocess.Popen(command, shell=True, close_fds=True,
                         stdout=stdout, stderr=stderr)
  stdout, stderr = p.communicate()
  if p.returncode:
     error = 'FAILED with exit code {code}\nCommand was {command}'.format(
                 code=p.returncode, command=command)
     sys.stderr.write(error + '\n')
     raise SystemExit('FAILED')


def fetch(url, google=False):
    request = urllib2.Request(url)
    if google:
      request.add_header('Metadata-Flavor', 'Google')
    try:
      response = urllib2.urlopen(request)
      return response.getcode(), response.read()
    except urllib2.HTTPError as e:
      return e.code, str(e.reason)
    except urllib2.URLError as e:
      return -1, str(e.reason)


def fetch_or_die(url, google=False):
    retcode, content = fetch(url, google)
    if retcode != 200:
        sys.stderr.write('{code}: {url}\n{result}\n'.format(
            code=retcode, url=url, result=content))
        raise SystemExit('FAILED')
    return content


