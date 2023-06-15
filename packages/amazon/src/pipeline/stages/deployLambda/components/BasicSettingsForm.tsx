// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type { Option } from 'react-select';

import type {
  IAccount,
  IAccountDetails,
  IFormikStageConfigInjectedProps,
  IFormInputProps,
  IRegion,
} from '@spinnaker/core';
import {
  AccountService,
  CheckboxInput,
  FormikFormField,
  HelpField,
  NameUtils,
  ReactSelectInput,
  TetheredCreatable,
  TextInput,
  useData,
} from '@spinnaker/core';

import { availableRuntimes, lambdaHelpFields } from './function.constants';

export function BasicSettingsForm(props: IFormikStageConfigInjectedProps) {
  const { values } = props.formik;

  const setFunctionName = () => {
    const ns = NameUtils.getClusterName(props.application.name, values.stackName, values.detailName);
    const fn = values.functionUid;

    props.formik.setFieldValue('functionName', `${ns}-${fn}`);
  };

  const onAliasChange = (o: Option, field: any) => {
    props.formik.setFieldValue(
      field,
      o.map((layer: any) => layer.value),
    );
  };

  const onRegionChange = (fieldValue: string) => {
    props.formik.setFieldValue('enableLambdaAtEdge', false);
    props.formik.setFieldValue('region', fieldValue);
  };

  const onFunctionUidChange = (fieldValue: string) => {
    props.formik.setFieldValue('functionUid', fieldValue);
    setFunctionName();
  };

  const onStackNameChange = (fieldValue: string) => {
    props.formik.setFieldValue('stackName', fieldValue);
    setFunctionName();
  };

  const onDetailChange = (fieldValue: string) => {
    props.formik.setFieldValue('detailName', fieldValue);
    setFunctionName();
  };

  const { result: fetchAccountsResult, status: fetchAccountsStatus } = useData(
    () => AccountService.listAccounts('aws'),
    [],
    [],
  );

  return (
    <div>
      <FormikFormField
        label="Account"
        name="account"
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
            {...inputProps}
            clearable={false}
            isLoading={fetchAccountsStatus === 'PENDING'}
            stringOptions={fetchAccountsResult
              .filter((acc: IAccountDetails) => acc.name === values.account)
              .flatMap((acc: IAccountDetails) => acc.regions)
              .map((reg: IRegion) => reg.name)}
          />
        )}
      />

      <FormikFormField
        name="functionUid"
        label="Function Name"
        onChange={onFunctionUidChange}
        help={<HelpField id="aws.function.name" />}
        input={(props) => <TextInput {...props} />}
      />

      <FormikFormField
        name="stackName"
        label="Stack"
        help={<HelpField content={lambdaHelpFields.stack} />}
        onChange={onStackNameChange}
        input={(props) => <TextInput {...props} />}
      />
      <FormikFormField
        name="detailName"
        label="Detail"
        help={<HelpField content={lambdaHelpFields.detail} />}
        onChange={onDetailChange}
        input={(props) => <TextInput {...props} />}
      />
      <FormikFormField
        name="aliasNames"
        label="Alias Name"
        help={
          <HelpField content="AWS Lambda aliases are like a pointer to a specific function version. Users can access the function version using the alias Amazon Resource Name (ARN). Input the alias name and select `Create option ALIAS-INPUT` to add the alias." />
        }
        input={(inputProps: IFormInputProps) => (
          <TetheredCreatable
            {...inputProps}
            multi={true}
            clearable={false}
            placeholder={'Input Alias Name...'}
            onChange={(e: Option) => {
              onAliasChange(e, 'aliases');
            }}
            value={values.aliases ? values.aliases.map((alias: string) => ({ value: alias, label: alias })) : []}
          />
        )}
      />
      <FormikFormField
        name="runtime"
        label="Runtime"
        help={<HelpField id="aws.function.runtime" />}
        input={(props) => <ReactSelectInput {...props} stringOptions={availableRuntimes} clearable={true} />}
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
        name="handler"
        label="Handler"
        help={<HelpField id="aws.function.handler" />}
        input={(props) => <TextInput {...props} placeholder="filename.method" />}
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
