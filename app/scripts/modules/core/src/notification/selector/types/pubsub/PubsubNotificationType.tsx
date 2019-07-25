import * as React from 'react';
import { FormikFormField, TextInput } from 'core/presentation';
import { INotificationTypeCustomConfig } from 'core/domain';

export class PubsubNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { fieldName } = this.props;
    return (
      <div className="sp-margin-m-bottom">
        <div className={'form-group'}>
          <label className={'col-md-4 sm-label-right'}>Publisher Name</label>
          <div className="col-md-6">
            <FormikFormField
              name={fieldName ? `${fieldName}.publisherName` : 'publisherName'}
              input={props => (
                <TextInput
                  inputClassName={'form-control input-sm'}
                  {...props}
                  placeholder="enter a pubsub publisher name"
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
