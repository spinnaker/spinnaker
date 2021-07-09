import React from 'react';

import { INotificationTypeCustomConfig } from '../../../../domain';
import { FormikFormField, TextInput, Validators } from '../../../../presentation';

export class BearychatNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { fieldName } = this.props;
    return (
      <FormikFormField
        label="Email Address"
        name={fieldName ? `${fieldName}.address` : 'address'}
        validate={Validators.emailValue('Please enter a valid email address')}
        input={(props) => <TextInput inputClassName={'form-control input-sm'} {...props} />}
        required={true}
      />
    );
  }
}
