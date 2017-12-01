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

"""Provides support functions for running shell commands."""

import collections
import fcntl
import os
import subprocess
import sys
import time


class RunResult(collections.namedtuple('RunResult',
                                       ['returncode', 'stdout', 'stderr'])):
  """Captures the result of running a subprocess.

  If output was not captured then stdout and stderr will be None.
  """
  pass


def __collect_from_stream(stream, buffer, echo_stream, observe_data):
  """Read all the input from a stream.

  Args:
    stream [File]: The file to read() from.
    buffer [list of string]: The buffer to append the collected data to.
       This will write a single chunk if any data was read.
    echo_stream [stream]: If not None, the File to write() for logging
       the stream.

  Returns:
    Number of additional bytes added to the buffer.
  """
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
  if collected:
      buffer.append(''.join(collected))
  if observe_data:
    observe_data(collected)
  return len(collected)


def run_and_monitor(command, echo=True, input=None,
                    observe_stdout=None, observe_stderr=None):
  """Run the provided command in a subprocess shell.

  Args:
    command [string]: The shell command to execute.
    echo [bool]: If True then echo the command and output to stdout.
    input [string]: If non-empty then feed this to stdin.

  Returns:
    RunResult with result code and output from running the command.
  """
  if echo:
    print command

  sys.stdout.flush()
  stdin = subprocess.PIPE if input else None
  process = subprocess.Popen(
      command,
      stdout=subprocess.PIPE, stderr=subprocess.PIPE, stdin=stdin,
      shell=True, close_fds=True)
  time.sleep(0)  # yield this thread

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
  while (__collect_from_stream(process.stdout, out, echo_out, observe_stdout)
         or  __collect_from_stream(process.stderr, err, echo_err, observe_stderr)
         or process.poll() is None):
      pass

  # Get any trailing data from termination race condition
  __collect_from_stream(process.stdout, out, echo_out, observe_stdout)
  __collect_from_stream(process.stderr, err, echo_err, observe_stderr)

  return RunResult(process.returncode, ''.join(out), ''.join(err))


def run_quick(command, echo=True, dup_stderr_to_stdout=True):
  """A more efficient form of run_and_monitor that doesnt monitor output.

  Args:
    command [string]: The shell command to run.
    echo [bool]: If True then echo the command and output to stdout.

  Returns:
    RunResult with result code and output from running the command.
       The content of stderr will be joined into stdout.
       stderr itself will be None.
  """
  stderr_target = subprocess.STDOUT if dup_stderr_to_stdout else subprocess.PIPE
  p = subprocess.Popen(command, shell=True, close_fds=True,
                       stdout=subprocess.PIPE, stderr=stderr_target)
  time.sleep(0)  # yield this thread
  stdout, stderr = p.communicate()
  if echo:
    print command
    print stdout
    if not dup_stderr_to_stdout:
      print stderr

  return RunResult(p.returncode, stdout, stderr)


def check_run_and_monitor(command, echo=True, input=None):
  """Runs the command in a subshell and throws an exception if it fails.

  Args:
    command [string]: The shell command to run.
    echo [bool]: If True then echo the command and output to stdout.
    input [string]: If non-empty then feed this to stdin.

  Returns:
    RunResult with result code and output from running the command.

  Raises:
    RuntimeError if command failed.
  """
  result = run_and_monitor(command, echo=echo, input=input)
  if result.returncode != 0:
    error = 'FAILED {command} with exit code {code}\n{err}'.format(
        command=command, code=result.returncode,
        err=result.stderr.strip() if not echo else '')
    sys.stderr.write(error + '\n')
    raise RuntimeError(error)

  return result


def check_run_quick(command, echo=True, dup_stderr_to_stdout=True):
  """A more efficient form of check_run_and_monitor that doesnt monitor output.

  Args:
    command [string]: The shell command to run.
    echo [bool]: If True then echo the command and output to stdout.
    dup_stderr_to_stdout [bool]: If True then also write stderr to stdout,
       otherwise keep the streams separate. Duping them interleaves the sequence
       into a single stream which is more human readable but might not be machine
       readable if expecting a response and a warning is emitted.

  Returns:
    RunResult with result code and output from running the command.
       The content of stderr will be joined into stdout.
       stderr itself will be None.

  Raises:
    RuntimeError if command failed.
  """
  result = run_quick(command, echo=echo, dup_stderr_to_stdout=dup_stderr_to_stdout)
  if result.returncode:
     msg = result.stdout if dup_stderr_to_stdout else result.stderr
     error = ('FAILED with exit code {code}'
              '\n\nCommand was {command}'
              '\n\nError was {msg}'.format(
                 code=result.returncode,
                command=command,
                msg=msg))
     raise RuntimeError(error)
  return result
