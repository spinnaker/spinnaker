import { module } from 'angular';

import React from 'react';
import { react2angular } from 'react2angular';
import { isEmpty } from 'lodash';

import { CheckboxInput, FormField, LayoutProvider, NumberInput, ReactSelectInput, SETTINGS } from '@spinnaker/core';

import { GceAutoScalingFieldLayout } from './GceAutoScalingFieldLayout';

enum maxReplicasUnit {
  fixed = 'fixed',
  percent = 'percent',
}

interface IGceScaleDownControl {
  maxScaledDownReplicas?: {
    fixed?: number;
    percent?: number;
  };
  timeWindowSec?: number;
}

interface IGceAutoscalingPolicy {
  scaleDownControl: IGceScaleDownControl;
}

interface IGceScaleDownControlsProps {
  policy: IGceAutoscalingPolicy;
  updatePolicy: (policy: IGceAutoscalingPolicy) => void;
}

const defaultScaleDownControl: IGceScaleDownControl = {
  maxScaledDownReplicas: {
    fixed: null,
    percent: 0,
  },
  timeWindowSec: 60,
};

function GceScaleDownControls({ policy, updatePolicy }: IGceScaleDownControlsProps) {
  if (!SETTINGS.feature.gceScaleDownControlsEnabled) {
    return null;
  }

  function updateScaleDownControl(scaleDownControl: IGceScaleDownControl) {
    updatePolicy({
      ...policy,
      scaleDownControl,
    });
  }

  function getMaxReplicasUnit(): maxReplicasUnit {
    if (Number.isInteger(policy.scaleDownControl.maxScaledDownReplicas.percent)) {
      return maxReplicasUnit.percent;
    }
    return maxReplicasUnit.fixed;
  }

  return (
    <LayoutProvider value={GceAutoScalingFieldLayout}>
      <div className="row">
        <FormField
          input={inputProps => <CheckboxInput {...inputProps} />}
          label="Enable scale-down controls"
          onChange={(e: React.ChangeEvent<any>) => {
            updateScaleDownControl(e.target.checked ? defaultScaleDownControl : {});
          }}
          value={!isEmpty(policy.scaleDownControl)}
        />
      </div>
      {!isEmpty(policy.scaleDownControl) && (
        <>
          <div className="row">
            <FormField
              input={inputProps => (
                <NumberInput
                  {...inputProps}
                  min={0}
                  max={getMaxReplicasUnit() === maxReplicasUnit.percent ? 100 : null}
                />
              )}
              label="Max scaled-down replicas"
              onChange={(e: React.ChangeEvent<any>) => {
                updateScaleDownControl({
                  ...policy.scaleDownControl,
                  maxScaledDownReplicas: {
                    [getMaxReplicasUnit()]: parseInt(e.target.value, 10),
                  },
                });
              }}
              value={policy.scaleDownControl.maxScaledDownReplicas[getMaxReplicasUnit()]}
            />
            <FormField
              input={inputProps => (
                <ReactSelectInput
                  {...inputProps}
                  clearable={false}
                  stringOptions={[maxReplicasUnit.percent, maxReplicasUnit.fixed]}
                />
              )}
              label=""
              onChange={(e: React.ChangeEvent<any>) => {
                updateScaleDownControl({
                  ...policy.scaleDownControl,
                  maxScaledDownReplicas: {
                    [e.target.value]: policy.scaleDownControl.maxScaledDownReplicas[getMaxReplicasUnit()],
                  },
                });
              }}
              value={getMaxReplicasUnit()}
            />
          </div>
          <div className="row">
            <FormField
              input={inputProps => <NumberInput {...inputProps} min={60} max={3600} />}
              label="Time window (seconds)"
              onChange={(e: React.ChangeEvent<any>) => {
                updateScaleDownControl({
                  ...policy.scaleDownControl,
                  timeWindowSec: parseInt(e.target.value, 10),
                });
              }}
              value={policy.scaleDownControl.timeWindowSec}
            />
          </div>
        </>
      )}
    </LayoutProvider>
  );
}

export const GCE_SCALE_DOWN_CONTROLS = 'spinnaker.gce.scaleDownControls';
module(GCE_SCALE_DOWN_CONTROLS, []).component(
  'gceScaleDownControls',
  react2angular(GceScaleDownControls, ['policy', 'updatePolicy']),
);
