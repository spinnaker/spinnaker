#!/usr/bin/env bash
set -e

# Usage:
# ./pull.sh [repo1] [repo2] [repo...] [-r|--ref REF] [-o|--ours]
# -r|--ref: Specify a ref to use for all remotes (default: master)
# -o|--ours: Keep our side of any conflicts.
#            This should only be used to reintegrate a tree that has already been caught up by other means.
#            Always keeping the "ours" side isn't safe in situations where genuine conflicts can still be present.

# Examples:

# Pull from Gate mirror from ref "master"
# ./pull.sh gate

# Pull from Clouddriver and Orca mirrors from ref "release-1.27.x"
# ./pull.sh clouddriver orca -r release-1.27.x

# Some repos do not have release branches
MAIN_ONLY_PULLS=(deck-kayenta spinnaker-gradle-project)

# Setup arguments
REF='master'
REPOS=()
STRATEGY_OPT=''
while [[ $# -gt 0 ]]; do
  key="$1"

  case $key in
    -r|--ref)
      REF="$2"
      shift
      shift
      ;;
    -o|--ours)
      STRATEGY_OPT="-X ours"
      shift
      ;;
    *) # Collect all positional arguments as remote repo names
      REPOS+=("$1")
      shift
      ;;
  esac
done

# Add in default repos if user did not provide any
if [ ${#REPOS[@]} -eq 0 ]; then
  REPOS=(clouddriver deck deck-kayenta echo fiat front50 gate halyard igor kayenta kork orca rosco spin spinnaker-gradle-project)
fi

function pull() {
  local repo="$1"
  local ref="$REF"

  # Some OSS repos do not have release branches - exclude them
  # shellcheck disable=SC2076
  if [[ $ref != 'master' ]] && [[ $ref != 'main' ]] && [[ " ${MAIN_ONLY_PULLS[*]} " =~ " ${repo} " ]]; then
    echo "Not merging $repo - marked for main-only pulls"
    return 0
  fi

  local prefix="$repo"
  local remote="github.com:spinnaker/$repo.git"

  # Export data for the editor script
  export GIT_SUBTREE="$prefix"
  export GIT_SUBTREE_REMOTE="$remote"

  echo "Merging into $prefix from $remote ($ref)..."

  git fetch "git@$remote" "$ref"
  if ! git merge --edit --strategy subtree -X subtree=${prefix} ${STRATEGY_OPT} FETCH_HEAD;
  then
    # Write the finished message to a file if the merge fails
    ./subtree_pull_editor.sh ./SUBTREE_MERGE_MSG
    echo "Merging $prefix failed - resolve conflicts and run 'git commit -a -F SUBTREE_MERGE_MSG', then re-run this"
    exit 1
  else
    rm -f ./SUBTREE_MERGE_MSG
  fi
}

export GIT_EDITOR='./subtree_pull_editor.sh'
for repo in "${REPOS[@]}"; do
  pull "$repo"
done
