import * as React from 'react';
import { FormikFormField, TextInput } from 'core/presentation';
import { INotificationTypeCustomConfig } from 'core/domain';

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
      <div className="sp-margin-m-bottom">
        <div className={'form-group'}>
          <label className={'col-md-4 sm-label-right'}>Phone Number</label>
          <div className="col-md-6">
            <FormikFormField
              name={fieldName ? `${fieldName}.address` : 'address'}
              validate={this.validate}
              input={props => (
                <TextInput inputClassName={'form-control input-sm'} {...props} placeholder="enter a phone number" />
              )}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
