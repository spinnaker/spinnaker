import { module } from 'angular';
import React from 'react';
import { react2angular } from 'react2angular';

import {
  CheckboxInput,
  FormField,
  HelpField,
  IFormInputProps,
  LayoutProvider,
  withErrorBoundary,
} from '@spinnaker/core';

import { GceAutoScalingFieldLayout } from '../../GceAutoScalingFieldLayout';
import { GcePredictiveMethod, IGceAutoscalingPolicy } from '../../IGceAutoscalingPolicy';
import { GCEProviderSettings } from '../../../gce.settings';

interface IGcePredictiveAutoscalingProps {
  policy: IGceAutoscalingPolicy;
  updatePolicy: (policy: IGceAutoscalingPolicy) => void;
}

function GcePredictiveAutoscaling({ policy, updatePolicy }: IGcePredictiveAutoscalingProps) {
  if (!GCEProviderSettings.feature.predictiveAutoscaling) {
    return null;
  }

  function togglePredictiveAutoscaling(predictiveMethod: GcePredictiveMethod) {
    updatePolicy({
      ...policy,
      cpuUtilization: {
        ...policy.cpuUtilization,
        predictiveMethod,
      },
    });
  }

  return (
    <LayoutProvider value={GceAutoScalingFieldLayout}>
      <div className="row">
        <FormField
          help={<HelpField id="gce.serverGroup.scalingPolicy.predictiveAutoscaling" />}
          input={(inputProps: IFormInputProps) => <CheckboxInput {...inputProps} />}
          label="Enable predictive autoscaling"
          onChange={(e: React.ChangeEvent<any>) => {
            togglePredictiveAutoscaling(e.target.checked ? GcePredictiveMethod.STANDARD : GcePredictiveMethod.NONE);
          }}
          value={policy?.cpuUtilization?.predictiveMethod === GcePredictiveMethod.STANDARD}
        />
      </div>
    </LayoutProvider>
  );
}

export const GCE_PREDICTIVE_AUTOSCALING = 'spinnaker.gce.predictiveAutoscaling';
module(GCE_PREDICTIVE_AUTOSCALING, []).component(
  'gcePredictiveAutoscaling',
  react2angular(withErrorBoundary(GcePredictiveAutoscaling, 'gcePredictiveAutoscaling'), ['policy', 'updatePolicy']),
);
