// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type { IFormikStageConfigInjectedProps, IFormInputProps } from '@spinnaker/core';
import { FormikFormField, ReactSelectInput } from '@spinnaker/core';

import { retrieveHealthCheck } from './HealthCheckStrategy';
import { HealthCheckList } from './health.constants';

export function BlueGreenDeploymentForm(props: IFormikStageConfigInjectedProps) {
  const { values } = props.formik;

  return (
    <div>
      <FormikFormField
        label="Health Check Type"
        name="healthCheckType"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput {...inputProps} clearable={false} options={HealthCheckList} />
        )}
      />
      {values.healthCheckType ? retrieveHealthCheck(values.healthCheckType, props) : null}
    </div>
  );
}
