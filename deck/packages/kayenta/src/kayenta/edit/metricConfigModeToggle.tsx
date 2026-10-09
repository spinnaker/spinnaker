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
 * Small stateless toggle between a provider's structured "Guided" form (soft-deprecated, kept
 * fully functional) and the unified "Template" experience (the default for new metrics).
 *
 * When the `templatesEnabled` ops kill-switch (CanarySettings.templatesEnabled) is off, this
 * renders nothing at all -- providers stay in Guided-only mode in that case.
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
