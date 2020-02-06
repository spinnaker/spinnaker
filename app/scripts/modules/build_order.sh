#!/bin/bash
cd `dirname $0`
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
      core) echo "core";;
      docker) echo "core" ;;
      ecs) echo "amazon core" ;;
      google) echo "core" ;;
      huaweicloud) echo "core" ;;
      kubernetes) echo "core" ;;
      oracle) echo "core" ;;
      titus) echo "amazon docker core" ;;
      *)
        echo "Unknown module: ${MODULE}"
        exit -1;
        ;;
  esac
}


# Given a list of modules, prints each module and its dependencies
AllDeps () {
  for MODULE in $* ; do
    echo $MODULE
    ModuleDeps $MODULE
  done
}

# Given a list of modules, prints a sorted list of those modules and all their dependencies
UniqueDeps () {
  AllDeps $* | tr ' ' $'\n' | sort | uniq;
}

# For a single module, print tuples of (one dep / the module) ... for each of its dependencies
ModuleTuples () {
  MODULE=$1
  shift
  for DEP in $* ; do 
    echo $DEP $MODULE
  done
}

# For a list of modules, print all the dependency tuples
AllTuples () {
  for MODULE in $* ; do
    ModuleTuples $MODULE $(ModuleDeps $MODULE)
  done
}

# Pipe all the dependency tuples through tsort, which orders the dependecy
AllTuples $(UniqueDeps $MODULES) | tsort
