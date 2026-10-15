import * as React from 'react';

import { HelpField } from '@spinnaker/core';

import { CanarySettings } from '../canary.settings';
import { MetricConfigMode } from '../domain/IMetricConfigMode';
import FormRow from '../layout/formRow';
import RadioChoice from '../layout/radioChoice';

export interface IMetricConfigModeToggleProps {
  mode: MetricConfigMode;
  onChange: (mode: MetricConfigMode) => void;
}

/*
 * Small stateless toggle between a provider's structured "Guided" form .
 *
 * Templates are on by default. When an operator sets `canary.templatesEnabled` to `false` this
 * renders nothing at all -- providers stay in Guided-only mode.
 */
export default function MetricConfigModeToggle({ mode, onChange }: IMetricConfigModeToggleProps) {
  if (CanarySettings.templatesEnabled === false) {
    return null;
  }

  return (
    <FormRow label="Configuration Mode">
      <RadioChoice
        value={MetricConfigMode.TEMPLATE}
        label="Template"
        name="metricConfigMode"
        current={mode}
        action={() => onChange(MetricConfigMode.TEMPLATE)}
      />
      <RadioChoice
        value={MetricConfigMode.GUIDED}
        label={
          <>
            Guided (deprecated) <HelpField id="canary.config.guidedModeDeprecated" />
          </>
        }
        name="metricConfigMode"
        current={mode}
        action={() => onChange(MetricConfigMode.GUIDED)}
      />
    </FormRow>
  );
}
