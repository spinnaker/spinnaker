import React from 'react';

import { HelpField, MapEditor } from '@spinnaker/core';

import type { IEcsWizardPageProps } from './common';

const logDrivers = [
  'None',
  'awslogs',
  'fluentd',
  'gelf',
  'journald',
  'json-file',
  'logentries',
  'splunk',
  'sumologic',
  'syslog',
];

export const LoggingSettings = ({ command, onFieldChange }: IEcsWizardPageProps) => (
  <div className="container-fluid form-horizontal" data-test-id="EcsServerGroupWizard.logging">
    <div className="form-group">
      <div className="col-md-5 sm-label-right">
        <b>Log driver (Optional)</b> <HelpField id="ecs.logDriver" />
      </div>
      <div className="col-md-7">
        <select
          aria-label="Log driver"
          className="form-control input-sm"
          data-test-id="Logging.logDriver"
          onChange={(event) => onFieldChange('logDriver', event.target.value)}
          value={command.logDriver || ''}
        >
          <option value="">Select...</option>
          {logDrivers.map((driver) => (
            <option key={driver} value={driver}>
              {driver}
            </option>
          ))}
        </select>
      </div>
    </div>
    {command.logDriver && command.logDriver !== 'None' ? (
      <div className="form-group">
        <div className="sm-label-left">
          <b>Logging options (optional)</b> <HelpField id="ecs.logOptions" />
        </div>
        <MapEditor
          allowEmpty={true}
          keyLabel="Logging option"
          model={command.logOptions || {}}
          onChange={(logOptions) => onFieldChange('logOptions', logOptions)}
          valueLabel="Logging option value"
        />
      </div>
    ) : (
      <div className="form-group">Logging options are not available for your log driver selection.</div>
    )}
  </div>
);
