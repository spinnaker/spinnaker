import { Option } from 'react-select';

// From https://github.com/spinnaker/orca/blob/master/orca-core/src/main/java/com/netflix/spinnaker/orca/ExecutionStatus.java
export const STATUS_OPTIONS: Array<Option<string>> = [
  {
    label: 'Not Started',
    value: 'NOT_STARTED',
  },
  {
    label: 'Running',
    value: 'RUNNING',
  },
  {
    label: 'Paused',
    value: 'PAUSED',
  },
  {
    label: 'Suspended',
    value: 'SUSPENDED',
  },
  {
    label: 'Succeeded',
    value: 'SUCCEEDED',
  },
  {
    label: 'Failed Continue',
    value: 'FAILED_CONTINUE',
  },
  {
    label: 'Terminal',
    value: 'TERMINAL',
  },
  {
    label: 'Canceled',
    value: 'CANCELED',
  },
  {
    label: 'Redirect',
    value: 'REDIRECT',
  },
  {
    label: 'Stopped',
    value: 'STOPPED',
  },
  {
    label: 'Skipped',
    value: 'SKIPPED',
  },
  {
    label: 'Buffered',
    value: 'BUFFERED',
  },
];
