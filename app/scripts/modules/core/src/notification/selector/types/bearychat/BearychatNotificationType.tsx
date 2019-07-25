import * as React from 'react';
import { FormikFormField, TextInput, Validators } from 'core/presentation';
import { INotificationTypeCustomConfig } from 'core/domain';

export class BearychatNoficationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { fieldName } = this.props;
    return (
      <div className="sp-margin-m-bottom">
        <div className={'form-group'}>
          <label className={'col-md-4 sm-label-right'}>Email Address</label>
          <div className="col-md-6">
            <FormikFormField
              name={fieldName ? `${fieldName}.address` : 'address'}
              validate={Validators.emailValue('Please enter a valid email address')}
              input={props => <TextInput inputClassName={'form-control input-sm'} {...props} />}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
