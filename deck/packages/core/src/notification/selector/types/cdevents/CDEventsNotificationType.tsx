import React from 'react';

import type { INotificationTypeCustomConfig } from '../../../../domain';
import { FormikFormField, TextInput, Validators } from '../../../../presentation';

export class CDEventsNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { fieldName } = this.props;
    return (
      <>
        <FormikFormField
          label="Events Broker URL"
          name={fieldName ? `${fieldName}.address` : 'address'}
          validate={Validators.skipIfSpel(Validators.urlValue('Please enter a valid URL'))}
          input={(props) => (
            <TextInput
              inputClassName={'form-control input-sm'}
              {...props}
              placeholder="Enter an events message broker URL"
            />
          )}
          required={true}
        />
        <FormikFormField
          label="CDEvents Type"
          name={fieldName ? `${fieldName}.cdEventsType` : 'cdEventsType'}
          validate={Validators.skipIfSpel(Validators.cdeventsTypeValue('Please enter a valid CDEvents Type'))}
          input={(props) => (
            <TextInput inputClassName={'form-control input-sm'} {...props} placeholder="Enter a CDEvents type" />
          )}
          required={true}
        />
      </>
    );
  }
}
