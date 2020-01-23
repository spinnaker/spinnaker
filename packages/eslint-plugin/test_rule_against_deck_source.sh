#!/bin/sh
if [[ -z "$1" ]] ; then
  echo "please provide the rule to run against ../../app:\n";
  ls `dirname $0`/rules | sed -e 's/\.js$//'
else
  RULE={\"$1\":2}
  shift;
  echo "node ${NODE_OPTS} ../../node_modules/.bin/eslint --no-eslintrc -c test.eslintrc --rulesdir rules --rule $RULE ../../app $*"
  node ${NODE_OPTS} ../../node_modules/.bin/eslint --no-eslintrc -c test.eslintrc --rulesdir rules --rule $RULE ../../app $*
fi
