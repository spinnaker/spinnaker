#!/bin/bash
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


# About this script:
# -----------------
# This script prepares a user environment to build spinnaker from source.
# It is intended to only be run one time. Typically on a newly provisioned
# GCE instance, but could be run on any linux machine.
#
# What it does:
# ------------
# This script will do the following:
#
#   * Create a $HOME/.git-credentials file if one does not already exist
#       You will be prompted for your github username and two-factor access
#       token.
#
#   * Creates a build/ directory as a subdirectory of $PWD.
#
#   * Clone each of the spinnaker subsystem github repositories into build/
#       If there is a .ssh/id_rsa file then it will clone the repositories
#       using ssh. Otherwise it will clone the repositories using https.
#
#       When a repository is cloned, an upstream remote will be added to
#       reference the authoritative repository for the given repository.
#       (e.g. the spinnaker github repository corresponding to your origin).
#
#       If the environment variable SPINNAKER_REPOSITORY_OWNER is set then
#       the repositories will be cloned from that github user. Otherwise it
#       will be cloned from the user in your .git-credentials. If the owner
#       is "default" then it will clone the authoritative repository for each
#       repository cloned (e.g. all the 'spinnaker' repositories).
#
#    * Print out next step instructions.
#
#
# Running the script:
# -------------------
# Rather than executing the script, you can source it to leave some side
# effects in the current shell. Mainly changing the directory and starting
# an ssh-agent if ssh (as opposed to https) is used to clone the github
# repositories. These are trivial effects that would be part of a normal
# future session after logout (e.g. changing directory and starting an
# ssh agent if one were desired), but is convienent to have here on first
# time usage and source. If the GITHUB_REPOSITORY_OWNER was used, it wont
# be needed once the repositories are cloned since the clone repositories
# know their origin.
#
#
# This is optional, if you want to use the spinnaker repositories
# rather than your own.
# GITHUB_REPOSITORY_OWNER="default"
# source spinnaker/google/dev/bootstrap_vm.sh


function git_clone() {
  local git_user="$1"
  local git_project="$2"
  local upstream_user="$3"

  if [[ "$git_user" == "default" ]]; then
      git_user="$upstream_user"
  fi

  # Use SSH if the rsa key was defined, otherwise use HTTPS.
  if [[ -f ~/.ssh/id_rsa ]]; then
    git clone git@github.com:$git_user/$git_project.git
  else
    git clone https://github.com/$git_user/$git_project.git
  fi

  if [[ "$upstream_user" ]]; then
    # Always use https for upstream.
    cd $git_project
    git remote add upstream https://github.com/$upstream_user/${git_project}.git
    cd ..
  fi
}


# This script can be used to populate a new development vm with the
# initial build environment and source code.
#
# It is put into /opt/spinnaker/install of development VMs
# (created with dev/create_dev.sh).

# If you do not have a .git-credentials file, you might want to create one.
# You were better off doing this on your original machine because then
# it would have been copied here (and to future VMs created by this script).
if [[ -f ~/.git-credentials ]]; then
  GITHUB_USER=$(sed 's/https:\/\/\([^:]\+\):.*@github.com/\1/' ~/.git-credentials)
else
  read -p 'Please enter your GitHub User ID: ' GITHUB_USER
  read -p 'Please enter your GitHub Acess Token: ' ACCESS_TOKEN
  cat <<EOF > ~/.git-credentials
https://$GITHUB_USER:$ACCESS_TOKEN@github.com
EOF
  chmod 600 ~/.git-credentials
fi

export GITHUB_USER

# If specified then use this as the user owning github repositories when
# cloning them. If the owner is "default" then use the default owner for the
# given repository. If this is not defined, then use GITHUB_USER which is
# intended to be the github user account for the user running this script.
GITHUB_REPOSITORY_OWNER=${GITHUB_REPOSITORY_OWNER:-"$GITHUB_USER"}


# Configure git to remember these credentials.
git config --global credential.helper store

# If you chose to copy the rsa keys (which is off by default for security),
# then start it up here to avoid lots of passphrase prompting.
# You'll need to run this again in future sessions in other shells.
if [[ -f ~/.ssh/id_rsa ]]; then
  eval "$(ssh-agent -s&)"; ssh-add ~/.ssh/id_rsa
fi

# This is a bootstrap pull of the development scripts.
git_clone "$GITHUB_REPOSITORY_OWNER" "spinnaker" "spinnaker"

# Pull the spinnaker source into a fresh build directory.
mkdir build
cd build
../spinnaker/google/dev/refresh_source.sh --github_user $GITHUB_REPOSITORY_OWNER

# Some dependencies of Deck rely on Bower to manage their dependencies. Bower
# annoyingly prompts the user to collect some stats, so this disables that.
echo "{\"interactive\":false}" > ~/.bowerrc


# If this script was run in a different shell then we
# dont have the environment variables we set, and arent in the build directory.
function print_invoke_instructions() {
cat <<EOF
To initate a build and run spinnaker:
  cd build
  ../spinnaker/google/dev/run_dev.sh
EOF
}


# If we sourced this script, we already have a bunch of stuff setup.
function print_source_instructions() {
cat <<EOF

To initate a build and run spinnaker:
  ../spinnaker/google/dev/run_dev.sh
EOF
}


function print_run_book_reference() {
cat <<EOF

For more help, see the Spinnaker Build & Run Book:
https://docs.google.com/document/d/1Q_ah8eG3Imyw-RWS1DSp_ItM2pyn56QEepCeaOAPaKA

EOF
}


# The /bogus prefix here is because eval seems to make $0 -bash,
# which basename thinks are flags. So since basename ignores the
# leading path, we'll just add a bogus one in.
if [[ "$(basename '/bogus/$0')" == "bootstrap_vm.sh" ]]; then
  print_invoke_instructions
else
  print_source_instructions
fi

print_run_book_reference

# Let path changes take effect in calling shell (if we source'd this)
exec bash -l
