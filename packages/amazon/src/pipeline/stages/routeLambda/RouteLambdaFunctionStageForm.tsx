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
  FormikFormField,
  HelpField,
  NumberInput,
  ReactSelectInput,
  TextInput,
  useData,
} from '@spinnaker/core';

import { TriggerEventsForm } from './TriggerEventsForm';
import { DeploymentStrategyForm } from './components';
import { DeploymentStrategyList, DeploymentStrategyPicker } from './constants';

export function RouteLambdaFunctionStageForm(props: IFormikStageConfigInjectedProps) {
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
        label="Alias"
        name="aliasName"
        input={(inputProps: IFormInputProps) => <TextInput {...inputProps} />}
      />

      <h4> Alias Settings </h4>
      <TriggerEventsForm {...props} />

      <FormikFormField
        name="provisionedConcurrentExecutions"
        label="Provisioned Concurrency"
        help={
          <HelpField content="To enable your function to scale without fluctuations in latency, use provisioned concurrency. Provisioned concurrency runs continually and has separate pricing for concurrency and execution duration. Concurrency cannot be provisioned with a weighted deployment strategy." />
        }
        input={(props) =>
          values.deploymentStrategy === '$WEIGHTED' ? (
            <NumberInput {...props} min={0} max={0} />
          ) : (
            <NumberInput {...props} min={0} max={3000} />
          )
        }
        required={false}
      />

      <h4> Deployment Strategy </h4>
      <FormikFormField
        label="Strategy"
        name="deploymentStrategy"
        help={<HelpField content="" />}
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            options={DeploymentStrategyList}
            optionRenderer={(option) => (
              <DeploymentStrategyPicker config={props} value={option.value as any} showingDetails={true} />
            )}
          />
        )}
      />
      {values.deploymentStrategy ? <DeploymentStrategyForm {...props} /> : null}
    </div>
  );
}
