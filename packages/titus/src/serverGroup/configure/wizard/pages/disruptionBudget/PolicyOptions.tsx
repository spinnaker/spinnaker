import React from 'react';

import { FormikFormField, HelpField, NumberInput } from '@spinnaker/core';

import { IFieldOption, IFieldOptionComponentProps } from './JobDisruptionBudget';
import { defaultJobDisruptionBudget } from '../../../serverGroupConfiguration.service';

const SelfManagedField = () => (
  <FormikFormField
    name="disruptionBudget.selfManaged.relocationTimeMs"
    label="Relocation Time"
    input={(props) => (
      <div>
        <NumberInput {...props} />
        <HelpField expand={true} content="(milliseconds)" />
      </div>
    )}
  />
);

const UnhealthyTasksLimitsField = () => (
  <FormikFormField
    name="disruptionBudget.unhealthyTasksLimit.limitOfUnhealthyContainers"
    label="Limit of Unhealthy Containers"
    input={(props) => (
      <div>
        <NumberInput {...props} />
        <HelpField expand={true} content="(integer)" />
      </div>
    )}
  />
);

const AvailabilityPercentageLimit = (componentProps: IFieldOptionComponentProps) => (
  <FormikFormField
    name="disruptionBudget.availabilityPercentageLimit.percentageOfHealthyContainers"
    label="Percentage of Healthy Containers"
    input={(props) => (
      <div>
        <NumberInput {...props} disabled={componentProps.isDisabled} />
        <HelpField expand={true} content="0.0-100.0 (double)" />
      </div>
    )}
  />
);

const RelocationLimit = () => (
  <FormikFormField
    name="disruptionBudget.relocationLimit.limit"
    label="Limit"
    input={(props) => (
      <div>
        <NumberInput {...props} />
        <HelpField expand={true} content="(tasks)" />
      </div>
    )}
  />
);

export const policyOptions: IFieldOption[] = [
  {
    field: 'availabilityPercentageLimit',
    label: 'Availability Percentage Limit',
    value: 0,
    description: `
      Lets you specify the required percentage of tasks in a healthy state.
      Tasks will not be terminated by the eviction service if this limit would be violated.
    `,
    defaultValues: defaultJobDisruptionBudget.availabilityPercentageLimit,
    fieldComponent: AvailabilityPercentageLimit,
  },
  {
    field: 'relocationLimit',
    label: 'Relocation Limit',
    value: 1,
    description: `
      Lets you specify the maximum number of times a task can be relocated.
      Should only be used with batch tasks, which have a maximum execution time.
    `,
    defaultValues: {
      limit: 1,
    },
    fieldComponent: RelocationLimit,
  },
  {
    field: 'unhealthyTasksLimit',
    label: 'Unhealthy Tasks Limit',
    value: 2,
    description: `
      Lets you specify the maximum allowed amount of tasks in an unhealthy state.
      Tasks will not be terminated by the eviction service if this limit would be violated.
    `,
    defaultValues: {
      limitOfUnhealthyContainers: 1,
    },
    fieldComponent: UnhealthyTasksLimitsField,
  },
  {
    field: 'selfManaged',
    label: 'Self Managed',
    value: 3,
    description: `
      Requires that you orchestrate custom termination logic.
      If the containers are not terminated within the configured amount of time,
      the system default migration policy is used instead.
    `,
    defaultValues: {
      relocationTimeMs: 24 * 60 * 60 * 1000,
    },
    fieldComponent: SelfManagedField,
  },
];
