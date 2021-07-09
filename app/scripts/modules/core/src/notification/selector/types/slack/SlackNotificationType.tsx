import React from 'react';

import { INotificationTypeCustomConfig } from '../../../../domain';
import { FormikFormField, TextInput } from '../../../../presentation';

export class SlackNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { botName, fieldName } = this.props;
    return (
      <>
        <FormikFormField
          label="Slack Channel"
          name={fieldName ? `${fieldName}.address` : 'address'}
          input={(props) => (
            <TextInput inputClassName={'form-control input-sm'} {...props} placeholder="enter a Slack channel" />
          )}
          required={true}
        />
        {!!botName && (
          <div className="row">
            <div className="col-md-6 col-md-offset-4">
              <strong>Note:</strong> You will need to invite the
              <strong> {botName} </strong>
              bot to this channel to receive Slack notifications
              <br />
            </div>
          </div>
        )}
      </>
    );
  }
}
