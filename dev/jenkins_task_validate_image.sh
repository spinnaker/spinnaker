#!/bin/bash
#
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

# This script configures and deploys Spinnaker on GCP and runs an integration
# test suite for validation that manages resources on AWS, GCP, and Kubernetes.
#
# The following environment variables are expected to be populated:
#
# IMAGE_TO_VALIDATE [String] - The name of the image to validate.
# IMAGE_PROJECT [String] - The GCE project containing the $IMAGE_TO_VALIDATE.
# If changing this, you will also need to make sure that the $TESTER_SERVICE_ACCOUNT has permission to View (or Edit) the project so that it can download the image.
# SPINNAKER_PROJECT [String] - Name of the project Spinnaker is running in.
# SPINNAKER_INSTANCE_NAME [String] - The name of the instance to deploy when testing. Must be unique.
# SPINNAKER_ZONE [String] - GCP zone Spinnaker is running in.

# OPT_FILE_METADATA [String] - Metadata entry specifying manage project credentials of the form: 'managed_project_credentials=$PROJECT_JSON_PATH'.
# GCS_BUCKET [String] - GCS bucket storing Spinnaker application config. Must be unique.
# PROJECT_JSON_PATH [String] - Path to service account credentials file for GCP project managed by Spinnaker.
# SPINNAKER_LOCAL_YML_PATH [String] - Path to spinnaker-local.yml used to configure Spinnaker microservices.

# DOCKER_REPOSITORY [String] - Name of the configured Docker repository in the Spinnaker instance.
# GCR_ACCOUNT [String] - Name of the GCR account to configure Spinnaker with. Must be unique.
# GCR_ACCOUNT_EMAIL [String] - Email corresponding to $GCR_ACCOUNT of form '$GCR_ACCOUNT@$SPINNAKER_PROJECT.iam.gserviceaccount.com'
# K8S_CLUSTER_NAME [String] - The name of the Kubernetes cluster to be tested against.
# K8S_CLUSTER_SIZE [Integer] - Size of Kubernetes cluster.
# KUBECONFIG_PATH [String] - Path to kubeconfig file used in the Spinnaker instance.

# AWS_ACCESS_KEY [String] - The value of the AWS access key.
# AWS_SECRET_KEY [String] - The value of the AWS secret key.

# NOTE: The JENKINS_* env variables are for the Jenkins instance the deployed
# Spinnaker instance is configured to test against. These do _not_ pertain to
# the Jenkins instance used to run this script.
################################################################################
# JENKINS_PASSWORD [String] - Password to Jenkins instance configured for Spinnaker instance.
# JENKINS_URL [String] - External URL for Jenkins instance configured in the Spinnaker instance. Must be firewalled so the VM running this script can make an HTTP request.
# JENKINS_USER [String] - User for Jenkins instance configured for Spinnaker instance.

# BASE_ARCHIVE_PATH [String] - Path to GCS bucket for build archival.
# HOME [String] - Home directory for the user running this script.
# PARALLELIZE_TESTS [Boolean] - Run each of the top-level test suites in parallel.
# REPORT_EVERY [Integer] - Period of print statements when waiting for servers (in seconds).
# TESTER_SERVICE_ACCOUNT [String] - Service account used for managing Spinnaker resources.

# In addition to the environment setup, this script _must_ be executed from the
# root directory of the Jenkins job for build artifact collection purposes.
#
# A minimal Jenkins job configuration to run this script:
#
# <set up env vars>
# rm -rf spinnaker/
# git clone https://github.com/spinnaker/spinnaker.git
# ./spinnaker/dev/jenkins_task_validate_image.sh


function cleanup_workspace() {
  # Remove old abandoned log files from previous runs.
  # This is so we dont accidentally associate the output with this run.
  rm -f *.journal *.html *.log logs.tz
  rm -rf server_logs
  rm -rf citest_logs
  rm -rf test_logs

  # Delete git sources
  rm -rf citest spinnaker
}


function prepare_citest() {
  # Clone the git repository since we dont have a pip install for citest.
  git clone http://github.com/google/citest.git

  # Install ffi package needed by pyopenssl
  # This is affecting the machine so only happens on the first run.
  # However we're leaving it here to facilitate migrating to new machines.
  sudo apt-get install libffi-dev

  # Install python depdendencies to virtualenv
  # (so we can reset it between tests)
  rm -rf ./testenv
  virtualenv ./testenv
  source ./testenv/bin/activate
  pushd citest; pip install -r ./requirements.txt; popd
  pushd spinnaker/testing/citest; pip install -r ./requirements.txt; popd

  # We're going to log citest into ./test_logs directory.
  mkdir -p test_logs
}


function prepare_stackdriver() {
  # 20161019 Attempt to delete all the existing stackdriver metrics for now.
  # This is so we can fix metrics descriptor definitions without carrying over
  # new ones. Eventually we'll need to deal with maintainence issues. For now
  # we assume we're just fixing pre-release bugs.
  echo "Deleting existing metric descriptors (this may lose persisted metrics)"
  spinnaker/google/stackdriver_monitoring/spinnaker_metric_tool.sh \
      --credential_path $PROJECT_JSON_PATH \
      --project $SPINNAKER_PROJECT \
      clear_stackdriver \
  || true
}


function snapshot_resources() {
  local snapshot_name="$1"

  echo "$(date) Grabbing ${snapshot_name} snapshot."

  # We need three different snapshots because there are two projects
  #   (1) project that we deploy into
  #   (2) project we manage
  #   (3) constrain ContainerEngine to a specific zone
  #       because ContainerEngine requires a specific zone, but we
  #       dont want to constrain in general for the others.

  # (1) This is the project we are going to deploy spinnaker into.
  #     We'll only snapshot "Compute" and "Storage" resources.
  #     We are snapshotting container resources separately because we'll
  #     constrain those further.
  #
  #     We are excluding operations because they are lengthy and we wont delete them.
  python citest/citest/gcp_testing/resource_snapshot.py \
      --credentials_path=$PROJECT_JSON_PATH \
      --bindings=project=$SPINNAKER_PROJECT,projectId=$SPINNAKER_PROJECT,bucket=$GCS_BUCKET \
      --output_path=test_logs/${snapshot_name}__compute_storage__${SPINNAKER_PROJECT}.snapshot-$BUILD_NUMBER \
      --exclude compute.*Operations \
      --list compute storage > /dev/null


  # (2) This is the project we are managing.
  #     We'll only snapshot "Compute" and "Storage", although we probably
  #     arent even using storage here.
  #     Normally we'd be managing Container resources here for kubernetes,
  #     however we are going to deploy kubernetes into the validate project
  #     because that's all the google_first_boot mechanism we are using for
  #     credentials supports.
  #
  #     We are excluding operations because they are lengthy and we wont delete them.
  python citest/citest/gcp_testing/resource_snapshot.py \
      --credentials_path=$PROJECT_JSON_PATH \
      --bindings=project=spinnaker-build,projectId=spinnaker-build,bucket=$GCS_BUCKET \
      --output_path=test_logs/${snapshot_name}__compute_storage__spinnaker-build.snapshot-$BUILD_NUMBER \
      --exclude compute.*Operations \
      --list compute storage > /dev/null

  # (3) This is for kubernetes containers constrained by zone.
  #     We're only creating containers in the deploy project so that's all
  #     that we'll snapshot
  python citest/citest/gcp_testing/resource_snapshot.py \
      --credentials_path=$PROJECT_JSON_PATH \
      --bindings=projectId=$SPINNAKER_PROJECT,zone=$SPINNAKER_ZONE,clusterId=$K8S_CLUSTER_NAME \
      --output_path=test_logs/${snapshot_name}__container__${SPINNAKER_PROJECT}.snapshot-$BUILD_NUMBER \
      --list container > /dev/null
}


function inject_resource_leaks() {
  # Inject resource leaks into index.html files.
  local title="$1"

  local taskname=$(basename `pwd`)
  local archive_dir=~jenkins/jobs/${taskname}/builds/${BUILD_NUMBER}/archive

  # Inject the resource diffs into our index.html file as a section at the end
  local diff_block=""
  for snapshot in container__${SPINNAKER_PROJECT} \
                  compute_storage__${SPINNAKER_PROJECT} \
                  compute_storage__spinnaker-build; do \
      if ! output=$(python citest/citest/gcp_testing/resource_snapshot.py \
            --compare test_logs/baseline__${snapshot}.snapshot-$BUILD_NUMBER \
                      test_logs/${snapshot_name}__${snapshot}.snapshot-$BUILD_NUMBER \
            --exclude compute.*Operations,storage.*AccessControls \
                      container compute storage); \
      then \
        diff_block="$diff_block<pre>$output</pre><p/>"; \
      fi; \
   done

  if [[ "$diff_block" == "" ]]; then
      diff_block="<p/><i>No resource leaks detected</i>"
  else
      local cleanup_command="<p>To cleanup leaks, run <code>$archive_dir/cleanup_leaks.sh</code>.</p>"
      diff_block="<p/><h2>${title}</h2>${diff_block}<p/>${cleanup_command}"
  fi

  # Rewrite the index.html file with our diff block at the end.
  # strip of end of body
  local doc=$(cat index.html | sed 's/\<\/body\>\<\/html\>//') || true
  echo "${doc}${diff_block}</body></html>" > index.html || true
}


function prepare_config_files() {
  # (1) Set a new storage bucket for this test.
  sed -i "s/storage_bucket:.*/storage_bucket: $GCS_BUCKET/" $SPINNAKER_LOCAL_YML_PATH


  # (2) Write an aws credentials file if AWS credentials were provided.
  if [ "$AWS_ACCESS_KEY" ] && [ "$AWS_SECRET_KEY" ]; then
    mkdir -p $HOME/tmp
    TMP_AWS_CREDENTIALS=$HOME/tmp/aws_credentials.$BUILD_NUMBER
    rm -f $TMP_AWS_CREDENTIALS; touch $TMP_AWS_CREDENTIALS
    chmod 600 $TMP_AWS_CREDENTIALS
    OPT_FILE_METADATA=$OPT_FILE_METADATA,aws_credentials=$TMP_AWS_CREDENTIALS
    cat >> $TMP_AWS_CREDENTIALS <<EOF
[default]
aws_secret_access_key = $AWS_SECRET_KEY
aws_access_key_id = $AWS_ACCESS_KEY
EOF
    rm -f ~/.aws/config; touch ~/.aws/config
    chmod 600 ~/.aws/config
    cat >> ~/.aws/config <<EOF
[profile citest]
aws_secret_access_key = $AWS_SECRET_KEY
aws_access_key_id = $AWS_ACCESS_KEY
region = us-east-1
EOF
  else
    TMP_AWS_CREDENTIALS=""
  fi


  # (3) Delete old kubernetes credentials, and create a new cluster
  rm -rf $KUBECONFIG_PATH
  gcloud config set container/use_client_certificate true
}


function provision_gcp_resources() {
  # (1) Create a GKE cluster for kubernetes tests
  gcloud container clusters create $K8S_CLUSTER_NAME \
    --zone $SPINNAKER_ZONE \
    --num-nodes $K8S_CLUSTER_SIZE \
    --project $SPINNAKER_PROJECT \
    --account $TESTER_SERVICE_ACCOUNT \
    --machine-type n1-standard-1

  # (2) Create the service account for GCR
  #     If we add --account here, a gcloud bug will delete that account
  #     so for now use our default account credentials
  gcloud iam service-accounts create $GCR_ACCOUNT \
      --project $SPINNAKER_PROJECT

  # Provide the kubernetes cluster name & service account to use
  OPT_FILE_METADATA=$OPT_FILE_METADATA
  OPT_METADATA=kube_cluster=$K8S_CLUSTER_NAME,gcr_account=$GCR_ACCOUNT_EMAIL

  # (3) Create a GCE VM to deploy spinnaker into
  #     I think this is using the image project service account to access the
  #     image project.
  #     The validate project permits the image project access.
  gcloud compute instances create $SPINNAKER_INSTANCE_NAME \
    --account $TESTER_SERVICE_ACCOUNT \
    --project $SPINNAKER_PROJECT \
    --image $IMAGE_TO_VALIDATE \
    --image-project $IMAGE_PROJECT \
    --machine-type n1-highmem-4 \
    --zone $SPINNAKER_ZONE \
    --scopes=compute-rw,logging-write,monitoring,cloud-platform \
    --metadata \
        startup-script=/opt/spinnaker/install/first_google_boot.sh,$OPT_METADATA \
    --metadata-from-file=spinnaker_local=$SPINNAKER_LOCAL_YML_PATH,$OPT_FILE_METADATA

  # Remove the local credentials file since it may contain AWS keys.
  if [ ! -z "$TMP_AWS_CREDENTIALS" ]; then
    rm $TMP_AWS_CREDENTIALS
  fi
}


function cleanup_gcp_resources() {
  # Delete provisioned resources
  gcloud compute instances delete -q $SPINNAKER_INSTANCE_NAME \
      --account $TESTER_SERVICE_ACCOUNT \
      --project $SPINNAKER_PROJECT \
      --zone $SPINNAKER_ZONE \

  gcloud container clusters delete -q $K8S_CLUSTER_NAME \
      --account $TESTER_SERVICE_ACCOUNT \
      --project $SPINNAKER_PROJECT \
      --zone $SPINNAKER_ZONE \

  gcloud iam service-accounts delete $GCR_ACCOUNT_EMAIL \
     --project $SPINNAKER_PROJECT

  gsutil rm -r -f gs://$GCS_BUCKET || true
}


function wait_on_pid() {
  local pid=$1
  local secs=$2
  set +x # Turn off line-level tracing in our loops
  while kill -0 $pid >& /dev/null; do
     if [[ $secs -le 0 ]]; then
        echo "Timed out waiting for pid=$pid."
        return 1
     fi
     sleep 1
     secs=$(expr $secs - 1)
  done
  set -x # Turn back on line-level tracing in our loops
  return 0
}


function wait_on_service() {
  local port=$1
  local name=$2
  local secs=120
  gcloud compute ssh \
     --account $TESTER_SERVICE_ACCOUNT \
     --command "while ! curl -s http://localhost:$port/health >& /dev/null; do echo '$name not yet ready...'; sleep 5; done" \
     --project $SPINNAKER_PROJECT \
     --zone $SPINNAKER_ZONE $SPINNAKER_INSTANCE_NAME &
  if wait_on_pid $! $secs; then
    echo "$(echo $name | tr [a-z] [A-Z]) is ready"
    date
    return 0
  else
    return 1
  fi
}


function wait_on_service_or_die() {
  local port=$1
  local name=$2
  if ! wait_on_service $port $name; then
    echo "FAILED"
    date
    exit -1
  fi
}

function wait_for_spinnaker_startup_or_die() {
  # Wait for gate to become available.
  date
  echo "Waiting for spinnaker..."
  gcloud compute ssh \
     --account $TESTER_SERVICE_ACCOUNT \
     --command "while ! nc -z localhost 8084; do sleep 1; done; echo 'gate is up.'" \
     --project $SPINNAKER_PROJECT \
     --zone $SPINNAKER_ZONE $SPINNAKER_INSTANCE_NAME &
  if ! wait_on_pid $! 150; then
    echo "FAILED to startup gate. Aborting"
    date
    exit -1
  fi

  # These services are critical to most of the tests
  wait_on_service_or_die 7002 "clouddriver"
  wait_on_service_or_die 8084 "gate"
  wait_on_service_or_die 8080 "front50"

  # These services are needed for some tests, but
  # we can execute other tests without them.
  wait_on_service 8084 "orca"
  wait_on_service 8087 "rosco"
  wait_on_service 8088 "igor"
  wait_on_service 8089 "echo"
}


function compress_server_logs() {
    gcloud compute ssh \
        --account $TESTER_SERVICE_ACCOUNT \
        --command 'cd /var/log; sudo tar czf $HOME/logs.tz --ignore-failed-read spinnaker redis/redis-server.log  syslog upstar\
t/spinnaker.log startupscript.log' \
            --project $SPINNAKER_PROJECT \
        --zone $SPINNAKER_ZONE \
        $SPINNAKER_INSTANCE_NAME
}

function wait_on_tests() {
  set +x # Turn off line-level tracing in our loops

  # We'll show an update every REPORT_EVERY seconds
  local echo_in=0
  while [[ ! -z ${!test_pid[@]} ]]; do
    for pid in "${!test_pid[@]}"; do
        local test_name="${test_pid[$pid]}"
        if kill -0 $pid; then
          # still running
          if [[ $echo_in -eq 0 ]]; then
            echo `date "+%Y-%m-%d %H:%M:%S"`  "Waiting on $test_name..."
            tail -1 $test_name.log || echo "$test_name hasnt started logging yet."
            echo ""
          fi
        else
          unset test_pid[$pid]
          echo `date "+%Y-%m-%d %H:%M:%S"`  "Finished $test_name:"
          cat $test_name.out
          if ! wait $pid; then
            FAILED_TESTS+=("$test_name")
            echo  "  FAILED $test_name"
          else
            echo  "  PASSED $test_name"
          fi
        fi
    done
    sleep 1
    if [[ echo_in -gt 0 ]]; then
       echo_in=$((echo_in - 1))
    else
       echo_in=$REPORT_EVERY
    fi
  done
  set -x # Resume line-level tracing
}


function run_test_with_args() {
  local test_name="$1"
  shift

  # quote all the args in case there was a space.
  # note that this will quote flags too

  echo `date "+%Y-%m-%d %H:%M:%S"` "Starting \"$test_name\""
  python $TEST_SRC_DIR/${test_name}.py "$@" >& ${test_name}.out &
  test_pid[$!]="$test_name"
  if  ! $PARALLELIZE_TESTS; then
      wait_on_tests
  fi
}


function run_standard_test() {
  local test_name="$1"
  shift
  standard_args=(\
      --gce_service_account=$TESTER_SERVICE_ACCOUNT \
      --gce_credentials_path=$PROJECT_JSON_PATH \
      --gce_project=$SPINNAKER_PROJECT \
      --gce_zone=$SPINNAKER_ZONE \
      --gce_instance=$SPINNAKER_INSTANCE_NAME \
      --test_stack=jenkins \
  )
  if [[ $# -gt 0 ]]; then
    standard_args=( ${standard_args[@]} "$@" )
  fi

  run_test_with_args $test_name "${standard_args[@]}"
}

function run_standard_google_test() {
  run_standard_test  "$@"
}

function run_standard_aws_test() {
  local test_name="$1"
  shift

  run_standard_test $test_name --aws_profile=citest "$@"
}


function run_all_tests() {
  export JENKINS_PASSWORD
  if [[ -z $JENKINS_USER ]]; then
    export JENKINS_USER=user
  else
    export JENKINS_USER
  fi

  export PYTHONPATH=./spinnaker/testing/citest
  TEST_SRC_DIR=./spinnaker/testing/citest/tests

  # We are going to run all the tests in parallel.
  # They should be independent of one another.
  # This will run much more quickly, but debugging may be harder.
  run_standard_google_test \
      "bake_and_deploy_test" \
      --jenkins_url=$JENKINS_URL \
      --jenkins_job=NoOpTrigger \
      --jenkins_token=TRIGGER_TOKEN \
      --jenkins_master=jenkins \
      --test_google

  run_standard_google_test "google_front50_test"
  run_standard_google_test "google_kato_test"
  run_standard_google_test "google_smoke_test"
  run_standard_google_test "kube_smoke_test"
  run_standard_google_test "google_http_lb_upsert_test"
  run_standard_google_test "google_http_lb_upsert_server_test"
  run_standard_google_test "google_server_group_test"

  run_standard_aws_test "aws_kato_test"
  run_standard_aws_test "aws_smoke_test"

  # In case we are not PARALLELIZE_TESTS
  wait_on_tests
}


function collect_reports_and_logs() {
  # Render the journals into HTML
  # (Individual HTML files and an overall index.html)
  python -m citest.reporting.generate_html_report *.journal;
  mkdir -p citest_logs
  mv *.log citest_logs
  mv *.out citest_logs
  mv *.journal citest_logs


  # Grab the log files from the instance before we succeed or fail
  # The logs we grab here could help diagnose errors
  echo "Collecting log files from server..."
  local count=0
  while ! compress_server_logs; do
    ((count++)) || true
    if [[ count -eq 60 ]]; then
      echo "Giving up fetching logs."
      break
    fi
    echo "Failed to fetch server logs ... try again."
    sleep 1
  done

  gcloud compute copy-files $SPINNAKER_INSTANCE_NAME:logs.tz . \
     --account $TESTER_SERVICE_ACCOUNT \
     --project $SPINNAKER_PROJECT \
     --zone $SPINNAKER_ZONE

  mkdir -p server_logs
  pushd server_logs
  tar xzf ../logs.tz
  rm -f ../logs.tz
  mv syslog syslog.log
  popd
}


function archive_to_google_cloud_storage() {
  if [[ -n $BASE_ARCHIVE_PATH ]]; then
    local archive_dir=${BASE_ARCHIVE_PATH}/${BUILD_NUMBER}
    echo "Archiving citest logs in $archive_dir..."
    gsutil cp citest_logs/*.journal ${archive_dir}
    gsutil cp test_logs/*.snapshot-${BUILD_NUMBER} ${archive_dir}
  fi
}


function generate_cleanup_leaked_resource_script() {
  local taskname=$(basename `pwd`)

  cat > cleanup_leaks.sh <<EOF
#!/bin/bash
  if [[ \${#} -eq 0 ]]; then
    BUILD_NUMBER=${BUILD_NUMBER}
  else
    BUILD_NUMBER=\$1
  fi
  echo "Cleaning up BUILD_NUMBER=\${BUILD_NUMBER}"

  archive_dir=~jenkins/jobs/${taskname}/builds/\${BUILD_NUMBER}/archive
  snapshot_dir=\${archive_dir}/test_logs

  cd `pwd`
  source testenv/bin/activate
  for snapshot in container__${SPINNAKER_PROJECT} \\
                  compute_storage__${SPINNAKER_PROJECT} \\
                  compute_storage__spinnaker-build; do
      echo "SNAPSHOT=\$snapshot"
      if [[ -f \${snapshot_dir}/final__\${snapshot}.snapshot-\${BUILD_NUMBER} ]]; then
          snapshot_name=final
      elif [[ -f \${snapshot_dir}/checkpoint__\${snapshot}.snapshot-\${BUILD_NUMBER} ]]; then
          snapshot_name=checkpoint
      else
          echo "No snapshot for '\${snapshot}' to compare against."
          continue
      fi
      python citest/citest/gcp_testing/resource_snapshot.py \\
          --compare \${snapshot_dir}/baseline__\${snapshot}.snapshot-\${BUILD_NUMBER} \\
                    \${snapshot_dir}/\${snapshot_name}__\${snapshot}.snapshot-\${BUILD_NUMBER} \\
          --exclude compute.*Operations,storage.*AccessControls \\
                    container compute storage \\
          --credentials_path=$PROJECT_JSON_PATH \\
          --delete_added \\
          --delete_for_real
  done
EOF
  chmod +x cleanup_leaks.sh
}

######################################################################

cleanup_workspace

# The spinnaker tests are in the spinnaker repository.
# Also, some utility scripts we'll use from here.
git clone http://github.com/spinnaker/spinnaker.git

prepare_citest
source testenv/bin/activate

prepare_stackdriver
snapshot_resources "baseline"

prepare_config_files
provision_gcp_resources

# Give it a minute to start up so we have a machine to ssh into
# when we start waiting.
sleep 90
wait_for_spinnaker_startup_or_die


sleep 15 # TODO(lwander/dpeach) temp change to see if echo starts up in time
date
echo "STARTING TESTS"

declare -a FAILED_TESTS
declare -a test_pid

run_all_tests
snapshot_resources "checkpoint"
collect_reports_and_logs

# Now either succeed or fail this step
if [[ ! -z "${FAILED_TESTS[*]}" ]]; then
  echo "TESTS FAILED: ${FAILED_TESTS[*]}"
  exit_code=-1
else
  echo "FINISHED SUCCESSFULLY"
  cleanup_gcp_resources
  snapshot_resources "final"
  exit_code=0
fi

generate_cleanup_leaked_resource_script
inject_resource_leaks "Resource Leaks"
archive_to_google_cloud_storage

exit ${exit_code}
