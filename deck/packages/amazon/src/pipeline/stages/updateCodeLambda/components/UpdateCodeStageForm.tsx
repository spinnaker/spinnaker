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
import {
  AccountService,
  CheckboxInput,
  FormikFormField,
  HelpField,
  ReactSelectInput,
  TextInput,
  useData,
} from '@spinnaker/core';

export function UpdateCodeLambdaFunctionStageForm(props: IFormikStageConfigInjectedProps) {
  const { values } = props.formik;
  const { functions } = props.application;

  const { result: fetchAccountsResult, status: fetchAccountsStatus } = useData(
    () => AccountService.listAccounts('aws'),
    [],
    [],
  );

  const onAccountChange = (fieldValue: any): void => {
    props.formik.setFieldValue('region', null);
    props.formik.setFieldValue('functionName', null);

    props.formik.setFieldValue('account', fieldValue);
  };

  const onRegionChange = (fieldValue: any): void => {
    props.formik.setFieldValue('functionName', null);
    props.formik.setFieldValue('region', fieldValue);
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
      <h4> Basic Settings </h4>
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
        name="s3bucket"
        label="S3 Bucket"
        help={<HelpField id="aws.function.s3bucket" />}
        input={(props) => <TextInput {...props} placeholder="S3 bucket name" />}
      />
      <FormikFormField
        name="s3key"
        label="S3 Key"
        help={<HelpField id="aws.function.s3key" />}
        input={(props) => <TextInput {...props} placeholder="object.zip" />}
      />
      <FormikFormField
        name="publish"
        label="Publish"
        help={<HelpField id="aws.function.publish" />}
        input={(props) => <CheckboxInput {...props} />}
      />
    </div>
  );
}
