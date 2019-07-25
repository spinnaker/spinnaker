import * as React from 'react';
import { FormikFormField, TextInput } from 'core/presentation';
import { INotificationTypeCustomConfig } from 'core/domain';

export class GooglechatNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { fieldName } = this.props;
    return (
      <div className="sp-margin-m-bottom">
        <div className={'form-group'}>
          <label className={'col-md-4 sm-label-right'}>Chat Webhook URL</label>
          <div className="col-md-6">
            <FormikFormField
              name={fieldName ? `${fieldName}.address` : 'address'}
              input={props => (
                <TextInput
                  inputClassName={'form-control input-sm'}
                  {...props}
                  placeholder="URL starts with https://chat.googleapis.com/v1/spaces/"
                />
              )}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
