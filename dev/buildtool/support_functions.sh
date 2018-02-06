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
export BUILD_NUMBER=${BUILD_NUMBER:-$(date +'%Y%m%d%H%M%S')}

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

# The original PID for this job is used to decorate
# the command logfiles
JOBPID=$BASHPID

PARENT_INVOCATION_ID=


########################################################
# echo's the date in YYYY-MM-DD HH:MM:SS format
########################################################
function timestamp() {
  echo $(date +'%Y-%m-%d %H:%M:%S')
}


########################################################
# Determine the path for a log file for a command.
#   Args:
#       command: The name of the command being logged.
#   Returns:
#       The path to the logfile
########################################################
function command_log_path() {
  local command_name=$1
  local logs_dir='./command_logs'
  if [[ "$LOGS_DIR" != "" ]]; then
      logs_dir="$LOGS_DIR";
  fi
  mkdir -p $logs_dir
  echo "${logs_dir}/${command_name}-command-$JOBPID.log"
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
  shift

  if [[ "$value" != "" ]]; then
      echo "Skipping '$command' because $guard=$value"
      SKIPPED_COMMANDS=(${SKIPPED_COMMANDS[@]} $command)
      return
  fi

  start_command $command $@
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
  shift
  local extra_args=

  if [[ $# -gt 1 ]]; then
    extra_args="$@"
  fi

  local logfile=$(command_log_path $command)
  mkdir -p $(dirname logfile)
  echo "$(timestamp): Start $command"

  # Create traceable id to relate metrics later if needed.
  local program=$(echo $(basename $0 | cut -f 1 -d .))
  local invocation_id="$program-$JOBPID"
  local parent_id
  if [[ $PARENT_INVOCATION_ID == "" ]]; then
      parent_id="$(date +'%y%m%d')-${invocation_id}"
  else
      parent_id="${PARENT_INVOCATION_ID}+${invocation_id}"
  fi
  local args="--parent_invocation_id=$parent_id $BUILDTOOL_ARGS"

  echo "$(timestamp): $BUILDTOOL $args $command ${extra_args[@]}" \
      &> $logfile
  $BUILDTOOL $args $command ${extra_args[@]} \
      &>> $logfile &
  COMMAND_TO_PID[$command]=$!
  echo "$(timestamp): Started $command as pid=${COMMAND_TO_PID[$command]} to $logfile"
}


##############################################################
# Figure out how long the longest outstanding command name is
#
#    This is so we can pad the table showing them.
##############################################################
function _determine_max_command_length() {
  local list="$1"
  local max=0
  for command in "${list}[@]"; do
    local len=${#command}
    if [[ $len -gt $max ]]; then
      max=$len
    fi
  done
  echo "$max"
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
  local command_format="%$(_determine_max_command_length ${!COMMAND_TO_PID[@]})s"
  while true; do
    if [[ ${#COMMAND_TO_PID[@]} -eq 0 ]]; then
      break
    elif [[ ${#COMMAND_TO_PID[@]} -eq $last_count ]]; then
      echo -n "."
    else
      last_count=${#COMMAND_TO_PID[@]}
      echo "$(timestamp): Still $last_count remaining ${description}:"
      for command in "${!COMMAND_TO_PID[@]}"; do
        printf "  ${COMMAND_TO_PID[$command]}  $command_format   $(command_log_path $command)\n" "$command"
      done
      echo -n "$(timestamp): Waiting on ${description}..."
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
            echo "$(timestamp): $command finished OK"
            GOOD_COMMANDS=(${GOOD_COMMANDS[@]} $command)
          else
            >&2 echo "$(timestamp): $command FAILED with output:\n"
            >&2 echo "$(cat $(command_log_path $command) | sed 's/^/   >>>   /g')"
            >&2 echo "See $(command_log_path $command) for details"
            BAD_COMMANDS=(${BAD_COMMANDS[@]} $command)
            mkdir -p errors
            cp $(command_log_path $command) "errors"
          fi
          unset COMMAND_TO_PID[$command]

          if [[ "$OUTPUT_DIR" != "" ]]; then
            mkdir -p "$OUTPUT_DIR/$command"
            cp $(command_log_path $command) "$OUTPUT_DIR/$command"
          fi
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
  echo "$(timestamp): Finished $description"

  local command_format="%$(_determine_max_command_length ${SKIPPED_COMMANDS[@]})s"
  if [[ ${#SKIPPED_COMMANDS[@]} -gt 0 ]]; then
    for command in "${SKIPPED_COMMANDS[@]}"; do
       printf "   SKIP: $command_format\n" "$command"
    done
  fi

  command_format="%$(_determine_max_command_length ${GOOD_COMMANDS[@]})s"
  if [[ ${#GOOD_COMMANDS[@]} -gt 0 ]]; then
    for command in "${GOOD_COMMANDS[@]}"; do
       printf "     OK: $command_format   $(command_log_path $command)\n" "$command"
    done
  fi

  command_format="%$(_determine_max_command_length ${BAD_COMMANDS[@]})s"
  if [[ ${#BAD_COMMANDS[@]} -gt 0 ]]; then
    for command in "${BAD_COMMANDS[@]}"; do
       printf "   FAIL: $command_format   $(command_log_path $command)\n" "$command"
    done
    exit -1
  fi
}
