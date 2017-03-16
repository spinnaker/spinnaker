#!/usr/bin/env bash

## auto-generated datadog install file written by halyard

DATADOG_APP_KEY="{%app-key%}"
DATADOG_API_KEY="{%api-key%}"

/opt/spinnaker-monitoring/third_party/datadog/install.sh --dashboards_only
