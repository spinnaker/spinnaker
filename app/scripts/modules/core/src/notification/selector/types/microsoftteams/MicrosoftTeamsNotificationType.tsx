import React from 'react';

import { INotificationTypeCustomConfig } from '../../../../domain';
import { FormikFormField, TextInput } from '../../../../presentation';

export class MicrosoftTeamsNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { fieldName } = this.props;
    return (
      <FormikFormField
        label="Teams Webhook URL"
        name={fieldName ? `${fieldName}.address` : 'address'}
        input={(props) => (
          <TextInput
            inputClassName={'form-control input-sm'}
            {...props}
            placeholder="URL starts with https://outlook.office.com/webhook/"
          />
        )}
        required={true}
      />
    );
  }
}
