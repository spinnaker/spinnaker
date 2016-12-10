#!/usr/bin/env python

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


"""Tool to help support consuming Spinnaker metrics."""

import argparse
import logging
import logging.config
import os

import command_processor
import datadog_handlers
import server_handlers
import stackdriver_handlers
import spectator_handlers


def init_logging(options):
  """Initialize logging within this tool."""
  log_file = options['log_basename'] + '.log'
  log_dir = options['log_dir']

  log_config = {
    'version':1,
    'disable_existing_loggers':True,
    'formatters': {
      'timestamped':{
        'format':'%(asctime)s %(message)s',
        'datefmt':'%H:%M:%S'
      }
    },
    'handlers':{
      'console':{
        'level':'WARNING',
        'class':'logging.StreamHandler',
        'formatter':'timestamped'
      },
      'file':{
        'level':'DEBUG',
        'class':'logging.FileHandler',
        'formatter':'timestamped',
        'filename': os.path.join(log_dir, log_file),
        'mode':'w'
      },
    },
    'loggers':{
       '': {
         'level':'DEBUG',
         'handlers':['console', 'file']
       },
    }
  }
  logging.config.dictConfig(log_config)


def add_global_args(parser):
  """Add global parser options that are independent of the command."""
  parser.add_argument('--log_basename', default='spinnaker_metric_tool')
  parser.add_argument('--log_dir', default='.')


def main():
  """The main program sets up the commands then delegates to one of them."""
  all_command_handlers = []
  parser = argparse.ArgumentParser(
      description='Helper tool to interact with Spinnaker deployment metrics.')
  add_global_args(parser)

  subparsers = parser.add_subparsers(title='commands', dest='command')
  server_handlers.add_handlers(all_command_handlers, subparsers)
  spectator_handlers.add_handlers(all_command_handlers, subparsers)
  stackdriver_handlers.add_handlers(all_command_handlers, subparsers)
  datadog_handlers.add_handlers(all_command_handlers, subparsers)

  opts = parser.parse_args()
  options = vars(opts)

  init_logging(options)

  command_processor.process_command(
      options['command'], options, all_command_handlers)

if __name__ == '__main__':
  main()
