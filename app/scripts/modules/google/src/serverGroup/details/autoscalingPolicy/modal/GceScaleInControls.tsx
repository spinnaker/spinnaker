import { module } from 'angular';
import { isEmpty } from 'lodash';
import React from 'react';
import { react2angular } from 'react2angular';

import {
  CheckboxInput,
  FormField,
  LayoutProvider,
  NumberInput,
  ReactSelectInput,
  withErrorBoundary,
} from '@spinnaker/core';

import { GceAutoScalingFieldLayout, IGceAutoscalingPolicy, IGceScaleInControl } from '../../../../autoscalingPolicy';

enum maxReplicasUnit {
  fixed = 'fixed',
  percent = 'percent',
}

interface IGceScaleInControlsProps {
  policy: IGceAutoscalingPolicy;
  updatePolicy: (policy: IGceAutoscalingPolicy) => void;
}

const defaultScaleInControl: IGceScaleInControl = {
  maxScaledInReplicas: {
    fixed: null,
    percent: 0,
  },
  timeWindowSec: 60,
};

function GceScaleInControls({ policy, updatePolicy }: IGceScaleInControlsProps) {
  function updateScaleInControl(scaleInControl: IGceScaleInControl) {
    updatePolicy({
      ...policy,
      scaleInControl,
    });
  }

  function getMaxReplicasUnit(): maxReplicasUnit {
    if (Number.isInteger(policy.scaleInControl.maxScaledInReplicas.percent)) {
      return maxReplicasUnit.percent;
    }
    return maxReplicasUnit.fixed;
  }

  return (
    <LayoutProvider value={GceAutoScalingFieldLayout}>
      <div className="row">
        <FormField
          input={(inputProps) => <CheckboxInput {...inputProps} />}
          label="Enable scale-in controls"
          onChange={(e: React.ChangeEvent<any>) => {
            updateScaleInControl(e.target.checked ? defaultScaleInControl : {});
          }}
          value={!isEmpty(policy.scaleInControl)}
        />
      </div>
      {!isEmpty(policy.scaleInControl) && (
        <>
          <div className="row">
            <FormField
              input={(inputProps) => (
                <NumberInput
                  {...inputProps}
                  min={0}
                  max={getMaxReplicasUnit() === maxReplicasUnit.percent ? 100 : null}
                />
              )}
              label="Max scaled-in replicas"
              onChange={(e: React.ChangeEvent<any>) => {
                updateScaleInControl({
                  ...policy.scaleInControl,
                  maxScaledInReplicas: {
                    [getMaxReplicasUnit()]: parseInt(e.target.value, 10),
                  },
                });
              }}
              value={policy.scaleInControl.maxScaledInReplicas[getMaxReplicasUnit()]}
            />
            <FormField
              input={(inputProps) => (
                <ReactSelectInput
                  {...inputProps}
                  clearable={false}
                  stringOptions={[maxReplicasUnit.percent, maxReplicasUnit.fixed]}
                />
              )}
              label=""
              onChange={(e: React.ChangeEvent<any>) => {
                updateScaleInControl({
                  ...policy.scaleInControl,
                  maxScaledInReplicas: {
                    [e.target.value]: policy.scaleInControl.maxScaledInReplicas[getMaxReplicasUnit()],
                  },
                });
              }}
              value={getMaxReplicasUnit()}
            />
          </div>
          <div className="row">
            <FormField
              input={(inputProps) => <NumberInput {...inputProps} min={60} max={3600} />}
              label="Time window (seconds)"
              onChange={(e: React.ChangeEvent<any>) => {
                updateScaleInControl({
                  ...policy.scaleInControl,
                  timeWindowSec: parseInt(e.target.value, 10),
                });
              }}
              value={policy.scaleInControl.timeWindowSec}
            />
          </div>
        </>
      )}
    </LayoutProvider>
  );
}

export const GCE_SCALE_IN_CONTROLS = 'spinnaker.gce.scaleInControls';
module(GCE_SCALE_IN_CONTROLS, []).component(
  'gceScaleInControls',
  react2angular(withErrorBoundary(GceScaleInControls, 'gceScaleInControls'), ['policy', 'updatePolicy']),
);
