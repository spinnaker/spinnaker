#!/bin/bash
cd "$(dirname "$0")" || exit
MODULES=$*;

# Given a module, prints that module's dependencies
# hard coding known deps for now because the package.json
# files don't yet contain the package's dependencies
ModuleDeps () {
  case "$1" in
      amazon) echo "core" ;;
      appengine) echo "core" ;;
      azure) echo "core" ;;
      cloudfoundry) echo "core" ;;
      cloudrun) echo "core" ;;
      core) echo "presentation";;
      docker) echo "core" ;;
      ecs) echo "amazon docker core" ;;
      google) echo "core" ;;
      huaweicloud) echo "core" ;;
      kubernetes) echo "core" ;;
      oracle) echo "core" ;;
      presentation) echo "presentation";;
      titus) echo "amazon docker core" ;;
      tencentcloud) echo "core";;
      *)
        if [[ ! -d "../packages/$1" ]] ; then
          echo "Unknown module: $1"
          exit 1
        else
          echo $1;
        fi
        ;;
  esac
}

# Given a list of modules, prints each module and its dependencies
AllDeps () {
  for MODULE in "$@" ; do
    echo "$MODULE"
    ModuleDeps "$MODULE";
  done
}

# Given a list of modules, prints a sorted list of those modules and all their dependencies
UniqueDeps () {
  AllDeps "$@" | tr ' ' $'\n' | sort | uniq || exit $?;
}

# For a single module, print tuples of (one dep / the module) ... for each of its dependencies
ModuleTuples () {
  MODULE=$1
  shift
  for DEP in "$@" ; do
    echo "$DEP" "$MODULE"
  done
}

# For a list of modules, print all the dependency tuples
AllTuples () {
  for MODULE in "$@" ; do
    MODULE_DEPS=$(ModuleDeps "$MODULE")
    [[ $? -eq 0 ]] || (echo $MODULE_DEPS ; exit 1)
    ModuleTuples "$MODULE" $MODULE_DEPS
  done
}

try() {
  CODE=$?
  if [[ $CODE -ne 0 ]] ; then
    echo $*
    exit $CODE
  fi
}

# Validate inputs
for MODULE in $MODULES ; do
  try $(ModuleDeps "$MODULE")
done

# Pipe all the dependency tuples through tsort, which orders the dependecy
AllTuples $(UniqueDeps $MODULES) | tsort
