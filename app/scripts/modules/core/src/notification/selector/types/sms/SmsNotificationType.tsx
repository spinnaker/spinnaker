import React from 'react';

import { INotificationTypeCustomConfig } from '../../../../domain';
import { FormikFormField, TextInput } from '../../../../presentation';

export class SmsNotificationType extends React.Component<INotificationTypeCustomConfig> {
  private validate = (value: string) => {
    let errorMessage: string;
    if (!/[0-9-]+$/i.test(value)) {
      errorMessage = 'Please enter a valid number';
    }
    return errorMessage;
  };

  public render() {
    const { fieldName } = this.props;
    return (
      <FormikFormField
        label="Phone Number"
        name={fieldName ? `${fieldName}.address` : 'address'}
        validate={this.validate}
        input={(props) => (
          <TextInput inputClassName={'form-control input-sm'} {...props} placeholder="enter a phone number" />
        )}
        required={true}
      />
    );
  }
}
