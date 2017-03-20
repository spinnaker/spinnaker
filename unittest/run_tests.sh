#!/bin/bash

cd `dirname $0`
TEST_DIR=`pwd`

# Some tests need to be in the build directory
# so run them all from there.
cd $TEST_DIR/../..

passed_tests=()
failed_tests=()

for test in `cd $TEST_DIR; ls *_test.py`; do
  echo "Running $test"
  PYTHONPATH=$TEST_DIR/../pylib:$TEST_DIR/../dev python $TEST_DIR/$test
  
  if [[ $? -eq 0 ]]; then
      passed_tests+=("$test")
  else
      failed_tests+=("$test")
  fi
done

if [[ ${#failed_tests[@]} -eq 0 ]]; then
  echo "PASSED ALL ${#passed_tests[@]} TESTS"
  exit 0
fi

>&2 echo "FAILED ${#failed_tests[@]} TESTS"
for test in ${failed_tests[@]}; do
    >&2 echo "FAILED: $test"
done
exit -1

