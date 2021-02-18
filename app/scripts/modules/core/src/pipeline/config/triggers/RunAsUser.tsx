import { IFormInputProps, ReactSelectInput, useLatestPromise } from 'core/presentation';
import { ServiceAccountReader } from 'core/serviceAccount';
import React from 'react';

export interface IRunAsUserInputProps extends IFormInputProps {
  application: string;
}

export function RunAsUserInput(props: IRunAsUserInputProps) {
  const fetchServiceAccounts = useLatestPromise(
    () => ServiceAccountReader.getServiceAccountsForApplication(props.application),
    [],
  );
  const isLoading = fetchServiceAccounts.status === 'PENDING';

  return (
    <ReactSelectInput
      {...props}
      isLoading={isLoading}
      stringOptions={fetchServiceAccounts.result || []}
      placeholder="Select Run As User"
    />
  );
}
