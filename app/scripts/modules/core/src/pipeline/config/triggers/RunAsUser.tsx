import React from 'react';

import { IFormInputProps, ReactSelectInput, useLatestPromise } from '../../../presentation';
import { ServiceAccountReader } from '../../../serviceAccount';

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
