import React from 'react';

import { NexusReaderService } from './nexusReader.service';
import { errorMessage, FormikFormField, ReactSelectInput, useLatestPromise } from '../../../../presentation';

export function NexusTrigger() {
  const fetchNames = useLatestPromise(() => NexusReaderService.getNexusNames(), []);

  const fetchError = () =>
    errorMessage(`Error fetching nexus names: ${fetchNames.error.data.status} ${fetchNames.error.data.error}`);
  const validationMessage = fetchNames.status === 'REJECTED' ? fetchError() : null;

  return (
    <FormikFormField
      name="nexusSearchName"
      label="Nexus Name"
      touched={true}
      validationMessage={validationMessage}
      input={(props) => (
        <ReactSelectInput
          {...props}
          isLoading={status === 'PENDING'}
          placeholder="Select Nexus search name..."
          stringOptions={fetchNames.result}
          clearable={false}
        />
      )}
    />
  );
}
