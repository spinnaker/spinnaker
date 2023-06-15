// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import React from 'react';

import type { IFormikStageConfigInjectedProps, IFormInputProps } from '@spinnaker/core';
import { FormikFormField, NumberInput, ReactSelectInput } from '@spinnaker/core';

import { VersionPicker } from './VersionPicker';
import { VersionList } from '../constants';
import type { IAmazonFunctionSourceData } from '../../../../domain';

export function WeightedDeploymentForm(props: IFormikStageConfigInjectedProps) {
  const { values } = props.formik;
  const { functions } = props.application;

  return (
    <div className="form-horizontal">
      <FormikFormField
        label="Target Version"
        name="versionNameA"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            options={VersionList}
            optionRenderer={(option) => <VersionPicker value={option.value as any} showingDetails={true} />}
          />
        )}
      />
      {values.versionNameA === '$PROVIDED' ? (
        <FormikFormField
          label="Version Number"
          name="versionNumberA"
          input={(inputProps: IFormInputProps) => (
            <ReactSelectInput
              {...inputProps}
              clearable={false}
              stringOptions={functions.data
                .filter((f: IAmazonFunctionSourceData) => f.account === values.account)
                .filter((f: IAmazonFunctionSourceData) => f.region === values.region)
                .filter((f: IAmazonFunctionSourceData) => f.functionName === values.functionName)
                .flatMap((f: IAmazonFunctionSourceData) =>
                  Object.values(f.revisions).sort(function (a: number, b: number) {
                    return b - a;
                  }),
                )
                .filter((r: any) => r !== '$LATEST')}
            />
          )}
        />
      ) : null}
      <FormikFormField
        name="trafficPercentA"
        label="Traffic %"
        input={(props) => <NumberInput {...props} min={0} max={100} />}
      />

      <FormikFormField
        label="Version Name"
        name="versionNameB"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            options={VersionList}
            optionRenderer={(option) => <VersionPicker value={option.value as any} showingDetails={true} />}
          />
        )}
      />
      {values.versionNameB === '$PROVIDED' ? (
        <FormikFormField
          label="Version Number"
          name="versionNumberB"
          input={(inputProps: IFormInputProps) => (
            <ReactSelectInput
              {...inputProps}
              clearable={false}
              stringOptions={functions.data
                .filter((f: IAmazonFunctionSourceData) => f.account === values.account)
                .filter((f: IAmazonFunctionSourceData) => f.region === values.region)
                .filter((f: IAmazonFunctionSourceData) => f.functionName === values.functionName)
                .flatMap((f: IAmazonFunctionSourceData) =>
                  Object.values(f.revisions).sort(function (a: number, b: number) {
                    return b - a;
                  }),
                )
                .filter((r: any) => r !== '$LATEST')}
            />
          )}
        />
      ) : null}
      <FormikFormField
        name="trafficPercentB"
        label="Traffic %"
        input={(props) => (
          <NumberInput
            {...props}
            min={0}
            max={100}
            disabled={true}
            value={values.trafficPercentA || values.trafficPercentA === 0 ? 100 - values.trafficPercentA : null}
          />
        )}
      />
    </div>
  );
}
