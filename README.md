# Summary

This directory is not part of citest, rather it is here for temporary
convenience sharing the repository and integration tests for Spinnaker that
use citest.

The `spinnaker_testing` package is a package adapting Spinnaker to citest
including a base SpinnakerAgent along with specializations for different
subsystems (e.g. Gate and Kato) that understand the application protocols
used for status and provide some helper functions.

The `spinnaker_system` directory contains system/integration tests for
spinnaker using citest and `spinnaker_testing` packages.

The run the tests in `spinnaker_system` you will need to set your `PYTHONPATH`.
The simplest way to do this is from the commandline. The path needs to point
to the repository root directory for citest, and this `spinnaker` directory
for the spinnaker testing temporarily added here.


Assuming you are in the repository root directory and testing against GCE
where:

    PROJECT_ID=ewiseblatt-spinnaker-test
    INSTANCE=ewiseblatt-20150805
    ZONE=us-central1-c

then:

    PYTHONPATH=.:spinnaker python \
      spinnaker/spinnaker_system/google_kato_test.py \
      --gce_project=$PROJECT_ID \
      --gce_instance=$INSTANCE \
      --gce_zone=$ZONE \
      --gce_ssh_passphrase_file=$HOME/.ssh/google_compute_engine.passphrase

If you were testing against some "native" host:

    PYTHONPATH=.:spinnaker python \
      spinnaker/spinnaker_system/google_kato_test.py \
      --native_host=$HOSTNAME \
      --managed_gce_project=$PROJECT_ID \
      --test_gce_zone=$ZONE

Note that `google_kato_test.py` is written to specifically test managing GCE
instances regardless of where Spinnaker is running from. So you can run
it against an AWS deployment, but will still be observing changes on GCE.

An example testing managing AWS instances, with Spinnaker running on
a "native" host where:

    HOSTNAME=localhost  # IP address where spinnaker is running.
    PROFILE=test        # user creates profile for aws cli tool outside citest.
    ZONE=us-east-1a     # an AWS zone.

then:

    PYTHONPATH=.:spinnaker python \
      spinnaker/spinnaker_system/aws_kato_test.py \
      --native_host=$HOSTNAME \
      --aws_profile=$PROFILE \
      --test_aws_zone=$ZONE

Note that `aws_kato_test.py` is written to specifically test managing AWS
instances regardless of where Spinnaker is running from. So you can run
it against a GCE deployment, but will still be observing changes on AWS.

