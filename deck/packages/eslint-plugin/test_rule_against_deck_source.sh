#!/bin/sh
RULESDIR=`dirname $0`/rules;
RULESDIRTEMP=$RULESDIR/temp
if [[ -z "$1" ]] ; then
  echo "please provide the rule to run against ../../app and ../../packages:\n";
  ls $RULESDIR | grep -v '\.spec\.' | sed -e 's/\.js$//'
else
  RULEFILE=$(basename $(ls $RULESDIR/$1.[tj]s));
  RULECONFIG={\"$1\":2}
  echo RULE=$RULE
  echo RULECONFIG=$RULECONFIG
  mkdir -p $RULESDIRTEMP
  ln -s ../$RULEFILE $RULESDIRTEMP
  shift;
  echo "node ${NODE_OPTS} ../../node_modules/.bin/eslint --ext js,ts,jsx,tsx --no-eslintrc -c test.eslintrc --rulesdir $RULESDIRTEMP --rule $RULECONFIG ../../app ../../packages $*"
  node ${NODE_OPTS} ../../node_modules/.bin/eslint --ext js,ts,jsx,tsx --no-eslintrc -c test.eslintrc --rulesdir $RULESDIRTEMP --rule $RULECONFIG ../../app ../../packages $*
  rm -rf $RULESDIRTEMP
fi
