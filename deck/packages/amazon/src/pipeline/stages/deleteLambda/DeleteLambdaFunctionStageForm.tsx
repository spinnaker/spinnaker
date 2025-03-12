// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type {
  IAccount,
  IAccountDetails,
  IFormikStageConfigInjectedProps,
  IFormInputProps,
  IFunction,
  IRegion,
} from '@spinnaker/core';
import { AccountService, FormikFormField, HelpField, NumberInput, ReactSelectInput, useData } from '@spinnaker/core';

import { DeleteVersionList, DeleteVersionPicker } from './constants';
import type { IAmazonFunctionSourceData } from '../../../domain';

export function DeleteLambdaFunctionStageForm(props: IFormikStageConfigInjectedProps) {
  const { values } = props.formik;
  const { functions } = props.application;

  const { result: fetchAccountsResult, status: fetchAccountsStatus } = useData(
    () => AccountService.listAccounts('aws'),
    [],
    [],
  );

  const onAccountChange = (fieldName: string, fieldValue: any): void => {
    props.formik.setFieldValue('region', null);
    props.formik.setFieldValue('functionName', null);

    props.formik.setFieldValue(fieldName, fieldValue);
  };

  const onRegionChange = (fieldName: string, fieldValue: any): void => {
    props.formik.setFieldValue('functionName', null);

    props.formik.setFieldValue(fieldName, fieldValue);
  };

  const availableFunctions =
    values.account && values.region
      ? functions.data
          .filter((f: IFunction) => f.account === values.account)
          .filter((f: IFunction) => f.region === values.region)
          .map((f: IFunction) => f.functionName)
      : [];

  return (
    <div className="form-horizontal">
      <FormikFormField
        label="Account"
        name="account"
        onChange={onAccountChange}
        required={true}
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            isLoading={fetchAccountsStatus === 'PENDING'}
            stringOptions={fetchAccountsResult.map((acc: IAccount) => acc.name)}
          />
        )}
      />
      <FormikFormField
        label="Region"
        name="region"
        onChange={onRegionChange}
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            clearable={false}
            disabled={!values.account}
            placeholder={values.account ? 'Select...' : 'Select an Account...'}
            {...inputProps}
            isLoading={fetchAccountsStatus === 'PENDING'}
            stringOptions={fetchAccountsResult
              .filter((acc: IAccountDetails) => acc.name === values.account)
              .flatMap((acc: IAccountDetails) => acc.regions)
              .map((reg: IRegion) => reg.name)}
          />
        )}
      />
      <FormikFormField
        label="Function Name"
        name="functionName"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            clearable={false}
            disabled={!(values.account && values.region)}
            placeholder={values.account && values.region ? 'Select...' : 'Select an Account and Region...'}
            {...inputProps}
            stringOptions={availableFunctions}
          />
        )}
      />
      <FormikFormField
        label="Target Version"
        name="version"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            options={DeleteVersionList}
            optionRenderer={(option) => (
              <DeleteVersionPicker config={props} value={option.value as any} showingDetails={true} />
            )}
          />
        )}
      />
      {values.version === '$PROVIDED' ? (
        <FormikFormField
          label="Version Number"
          name="versionNumber"
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
      {values.version === '$MOVING' ? (
        <FormikFormField
          name="retentionNumber"
          help={<HelpField content="The number of Lambda versions to retain" />}
          label="Prior Versions to Retain"
          input={(props) => <NumberInput {...props} min={1} max={100} />}
        />
      ) : null}
    </div>
  );
}
