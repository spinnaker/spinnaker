#!/bin/bash
cd `dirname $0`
MODULES=$*;

TUPLES=""
for MODULE in $MODULES ; do
  # hard coding known deps for now because the package.json files don't yet contain the package's dependencies
  case "$MODULE" in
      amazon) DEPS="core" ;;
      appengine) DEPS="core" ;;
      cloudfoundry) DEPS="core" ;;
      core) DEPS="core" ;;
      docker) DEPS="core" ;;
      ecs) DEPS="amazon core" ;;
      google) DEPS="core" ;;
      kubernetes) DEPS="core" ;;
      openstack) DEPS="core" ;;
      oracle) DEPS="core" ;;
      titus) DEPS="amazon docker core" ;;
      *)
        echo "Unknown module: ${MODULE}"
        exit -1;
        ;;
  esac

  for DEP in $DEPS ; do
    TUPLES="${TUPLES} ${DEP} ${MODULE}"
  done
done

echo $TUPLES | tsort
