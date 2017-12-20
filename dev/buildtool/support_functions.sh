#!/bin/bash
# Copyright 2017 Google Inc. All Rights Reserved.
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


# Support functions for the various flow scripts.

# The path to the buildtool program we're going to run.
BUILDTOOL=$(dirname $0)/../buildtool.sh
BUILDTOOL_ARGS=

# An associative array of jobs we are running asynchronously.
# This is keyed by the command name, with the program PID as the value.
# When programs complete, they are removed from this list
declare -A COMMAND_TO_PID

# The accumulated failed commands.
BAD_COMMANDS=()

# The accumulated successful commands.
GOOD_COMMANDS=()

# The accumulated skipped commands.
SKIPPED_COMMANDS=()


########################################################
# Determine the path for a log file for a command.
#   Args:
#       command: The name of the command being logged.
#   Returns:
#       The path to the logfile
########################################################
function command_log_path() {
  local command_name=$1
  echo "./command_logs/${command_name}.log"
}


########################################################
# Start a command if its guard has no value
#    Args:
#      guard: The name of the variable to check
#      command: The command to run
#      ...: Additional args passed through to start_command
########################################################
function start_command_unless() {
  local guard=$1
  local value=${!1}
  shift
  local command=$1

  if [[ "$value" != "" ]]; then
      echo "Skipping '$command' because $guard=$value"
      SKIPPED_COMMANDS=(${SKIPPED_COMMANDS[@]} $command)
      return
  fi
  start_command $@
}


########################################################
# Start a command in an asynchronous subshell.
#   Args:
#       command: The buildtool command name to run.
#       extra_args: Additional command arguments.
#
#   Globals:
#       This adds the command and pid into COMMAND_TO_PID
########################################################
function start_command() {
  local command=$1
  local extra_args=

  if [[ $# -gt 1 ]]; then
    extra_args=$2
  fi

  local logfile=$(command_log_path $command)
  mkdir -p command_logs
  echo "$(date): Start $command"
  $BUILDTOOL $BUILDTOOL_ARGS $command $extra_args >& $logfile &
  COMMAND_TO_PID[$command]=$!
  echo "$(date): Started $command as ${COMMAND_TO_PID[$command]} to $logfile"
}


########################################################
# Wait for all the commands in COMMAND_TO_PID to complete
#   Args:
#     description: identifier for logging only.
#
#   Globals:
#     Removes from COMMAND_TO_PID
#     Adds command names to GOOD_COMMANDS and BAD_COMMANDS
########################################################
function wait_for_commands() {
  local description="$1"

  local fail_on_error
  if [[ "$-" == *e* ]]; then
    fail_on_error=0
    set +e
  else
    fail_on_error=1
  fi

  local last_count=0
  while true; do
    if [[ ${#COMMAND_TO_PID[@]} -eq 0 ]]; then
      break
    elif [[ ${#COMMAND_TO_PID[@]} -eq $last_count ]]; then
      echo -n "."
    else
      last_count=${#COMMAND_TO_PID[@]}
      echo "$(date): Still $last_count remaining ${description}:"
      for command in "${!COMMAND_TO_PID[@]}"; do
        echo "  ${COMMAND_TO_PID[$command]}  $command   $(command_log_path $command)"
      done
      echo -n "$(date): Waiting on ${description}..."
    fi
    sleep 5

    for command in "${!COMMAND_TO_PID[@]}"; do
      if ! kill -s 0 ${COMMAND_TO_PID[$command]} >& /dev/null; then
          wait ${COMMAND_TO_PID[$command]}
          retcode=$?
          if [[ ${#COMMAND_TO_PID[@]} -eq $last_count ]]; then
            echo ""  # Terminate line of waiting '.'
          fi
          if [[ $retcode -eq 0 ]]; then
            echo "$(date): $command finished OK"
            GOOD_COMMANDS=(${GOOD_COMMANDS[@]} $command)
          else
            >&2 echo "$(date): $command FAILED.\n"
            >&2 tail -10 $(command_log_path $command)
            >&2 echo "    See $(command_log_path $command) for details"
            >&2 echo ""
            BAD_COMMANDS=(${BAD_COMMANDS[@]} $command)
          fi
          unset COMMAND_TO_PID[$command]
      fi
    done
  done

  if [[ $fail_on_error -eq 0 ]]; then
    set -e
  fi
}


##################################################
# Wrapper around wait_for_commands exists on failure
#
#     See wait_for_commands_or_die
##################################################
function wait_for_commands_or_die() {
  local description="$1"
  wait_for_commands "$description"

  echo ""
  echo ""
  echo "$(date): Finished $description"

  if [[ ${#SKIPPED_COMMANDS[@]} -gt 0 ]]; then
    for command in "${SKIPPED_COMMANDS[@]}"; do
       echo "   SKIP: $command"
    done
  fi

  if [[ ${#GOOD_COMMANDS[@]} -gt 0 ]]; then
    for command in "${GOOD_COMMANDS[@]}"; do
       echo "   OK: $command   $(command_log_path $command)"
    done
  fi

  if [[ ${#BAD_COMMANDS[@]} -gt 0 ]]; then
    for command in "${BAD_COMMANDS[@]}"; do
       echo "   FAIL: $command   $(command_log_path $command)"
    done
    exit -1
  fi
}
