This subdirectory contain overrides from the standard installation scripts
that are either meant for Google Cloud Platform or currently being used
by Google.

NOTE(ewiseblatt): 20150918
The intent is for most of these to be migrated upward for general usage.
However as a first pass, this is a dump of what we have been using during
our development, so are not necessarily generalized or applicable outside GCE.


General layout is this:

google/
   codelab     (Ignore) Supporting script for building codelab image

   dev         Scripts meant for development support, not operational usage.
               These are not included in a typical deployment, though are
               still open source for developers.

   google_cloud_logging
               (Optional) Script to install Google Cloud Logging integration.

   stackdriver_monitoring
               (Optional) Administrative support for Spinnaker Stackdriver
               integration.

   runtime     Scripts that are released as part of the spinnaker runtime
               These include management and administrative things.
               Everything is a bash script, though many are actually
               implemented in python from the [pylib] directory.

To create a new GCE instance that is setup as a development environment:

   (1) google/dev/create_dev_vm.sh  \
       [--instance=$USER-spinnaker-dev] \
       [--zone=us-central1-f]
       [--master_config=<path>]

   (2) Log into the new instance

   (3) [GITHUB_REPOSITORY_USER=default] \
       source /opt/spinnaker/install/bootstrap_vm.sh
          This will:
             * Prompt you for your github credentials to create a .gitconfig.
             * Clone the github $GITHUB_REPOSITORY_USER/spinnaker repository.
                 If you ran this with GITHUB_REPOSITORY_USER=default then
                 it will checkout the upstream repositories (e.g. 'spinnaker')
             * Make a ./build directory.
             * Checkout all the Spinnaker subsystem repositories into ./build.
             * Leave you in the ./build directory.

   (4) ../spinnaker/google/dev/run_dev.sh
          This will:
             * Create a ./logs directory
             * Run all the subsystems using gradlew.
                 All the gradlew commands will write log fiels to ./logs
             * Stream all the gradlew stderr output to the console for
               as long as the script is running.
          Terminating the script (^C) will leave the spinnaker processes
          running and logging into ./logs. Only console reporting is stopped.


To stop spinnaker in a development environment:
   (1) ../spinnaker/google/dev/stop_dev.sh
       You can also specify an individual subsystem to stop


To reconfigure spinnaker in a development environment:
   (1) Edit the $HOME/.spinnaker/spinnaker-local.yml file
   (2) ../spinnaker/google/dev/stop_dev.sh
   (3) ../spinnaker/google/dev/run_dev.sh
      The environment is always reconfigured within run_dev.sh.


To update the sources:
   (1) ../spinnaker/google/dev/refresh_sources [--github_user=$GITHUB_USER]
        The --github_user is only used when new repositories need to clone
        (e.g. first time usage). The user is the owner of the repositories.
        "default" means the authoritative repository.

        There are various options for updating upstream/origin repositories.
        See '-h' for more options.


To create a release:
   (0) mkdir build; cd build;

   (1) ../spinnaker/google/dev/build_release --release=$RELEASE_NAME \
       [--github_user=default]
       [--release_repository_root=gs://]
          The release_repository_root is the parent directory to write a
          release into. The release itself will be a subdirectory called
          $RELEASE_NAME.

          Note the default is to create a new GCS bucket, in which case the
          name must be globally unique.

          --github_user is only needed if the repositories dont exist yet.
          If they already exist, they will be updated from "origin" before
          building (unless --norefresh_from_origin)


To create a GCE image from a release:
   (1) ../spinnaker/google/dev/create_gce_image --release=$RELEASE_NAME \
         [--image=$IMAGE_NAME] [--image_project=$IMAGE_PROJECT_NAME]


To create an instance from a GCE image:
   (1) gcloud compute instances create $RELEASE_NAME \
          --project $SPINNAKER_PROJECT \
          --zone $ZONE \
          --image $IMAGE_NAME \
          --image-project $IMAGE_PROJECT \
          --machine-type n1-standard-8 \
          --scopes compute-rw \
          --metadata=startup-script=/opt/spinnaker/install/first_time_boot.sh \
          --metadata-from-file spinnaker_config=$SPINNAKER_CONFIG_PATH,managed_project_credentials=$JSON_CREDENTIALS_PATH

          Note we are assuming spinnaker is deployed into a different project
          than we are managing, so we add service account credentials.
          Also we are seeding it with a spinnaker_config.cfg file. Otherwise
          we'd need to edit and reconfigure later once the instance is up.


To reconfigure a production spinnaker:
    (1) sudo /opt/spinnaker/scripts/stop_spinnaker.sh
    (2) edit /root/.spinnaker/spinnaker_config.cfg  (must be root)
    (3) sudo /opt/spinnaker/scripts/reconfigure_spinnaker.sh
    (4) sudo /opt/spinnaker/scripts/start_spinnaker.sh


To install spinnaker on an existing machine:
    (0) build a release (see above) and have the --release_repository_root on
        a filesystem that is accessable. This could be GCS. If so, you will
        need to have gsutil installed on the target machine to install to.

    (1) sudo $RELEASE_PATH/install/install_spinnaker.py \
             --release_path=$RELEASE_PATH

    (2) sudo cp /opt/spinnaker/config_templates/default_spinnaker_config.cfg \
                /root/.spinnaker/spinnaker_config.cfg
    (3) sudo chmod 600 /root/.spinnaker/spinnaker_config.cfg
    (4) sudo vi /root/.spinnaker/spinnaker_config.cfg
    (5) sudo /opt/spinnaker/scripts/reconfigure_spinnaker.sh
    (6) sudo /opt/spinnaker/scripts/start_spinnaker.sh

           You currently need to manually configure the machine to run
           start_spinnaker.sh on reboot.

