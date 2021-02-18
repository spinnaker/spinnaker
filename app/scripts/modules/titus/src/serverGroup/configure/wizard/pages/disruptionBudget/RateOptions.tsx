import React from 'react';

import { FormikFormField, HelpField, NumberInput } from '@spinnaker/core';

import { IFieldOption, IFieldOptionComponentProps } from './JobDisruptionBudget';
import { defaultJobDisruptionBudget } from '../../../serverGroupConfiguration.service';

const RatePercentagePerInterval = (componentProps: IFieldOptionComponentProps) => (
  <>
    <FormikFormField
      name="disruptionBudget.ratePercentagePerInterval.intervalMs"
      label="Interval"
      input={(props) => (
        <div>
          <NumberInput {...props} disabled={componentProps.isDisabled} />
          <HelpField expand={true} content="(milliseconds)" />
        </div>
      )}
    />
    <FormikFormField
      name="disruptionBudget.ratePercentagePerInterval.percentageLimitPerInterval"
      label="Percentage Per Interval"
      input={(props) => (
        <div>
          <NumberInput {...props} disabled={componentProps.isDisabled} />
          <HelpField expand={true} content="0.0-100.0 (double)" />
        </div>
      )}
    />
  </>
);

const RatePerInterval = () => (
  <>
    <FormikFormField
      name="disruptionBudget.ratePerInterval.intervalMs"
      label="Interval"
      input={(props) => (
        <div>
          <NumberInput {...props} />
          <HelpField expand={true} content="(milliseconds)" />
        </div>
      )}
    />
    <FormikFormField
      name="disruptionBudget.ratePerInterval.limitPerInterval"
      label="Limit Per Interval"
      input={(props) => (
        <div>
          <NumberInput {...props} />
          <HelpField expand={true} content="(tasks)" />
        </div>
      )}
    />
  </>
);

export const rateOptions: IFieldOption[] = [
  {
    field: 'rateUnlimited',
    label: 'Unlimited',
    value: 0,
    description: `
        No limits on how many containers in a job may be relocated, provided the other disruption budget constraints
        are not violated.
      `,
    defaultValues: true,
  },
  {
    field: 'ratePercentagePerInterval',
    label: 'Rate Percentage Per Interval',
    value: 1,
    description: `
        Percentage of containers that can be relocated within a time interval.
      `,
    defaultValues: defaultJobDisruptionBudget.ratePercentagePerInterval,
    fieldComponent: RatePercentagePerInterval,
  },
  {
    field: 'ratePerInterval',
    label: 'Rate Per Interval',
    value: 2,
    description: `
        Limit number of relocations within a given time interval.
      `,
    defaultValues: { intervalMs: 60000, limitPerInterval: 2 },
    fieldComponent: RatePerInterval,
  },
];
