import * as React from 'react';
import { FormikFormField, TextInput } from 'core/presentation';
import { INotificationTypeCustomConfig } from 'core/domain';

export class HipchatNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { botName, fieldName } = this.props;
    return (
      <>
        {!!botName && (
          <div className={'row'}>
            <div className="col-md-6 col-md-offset-4">
              <strong>Please note:</strong> You need to invite the
              <strong> {botName} </strong> bot to <strong>private</strong> rooms to receive HipChat notifications
              <br />
            </div>
          </div>
        )}
        <div className="sp-margin-m-bottom">
          <div className={'form-group'}>
            <label className={'col-md-4 sm-label-right'}>HipChat Room</label>
            <div className="col-md-6">
              <FormikFormField
                name={fieldName ? `${fieldName}.address` : 'address'}
                input={props => (
                  <TextInput inputClassName={'form-control input-sm'} {...props} placeholder="enter a HipChat room" />
                )}
                required={true}
              />
            </div>
          </div>
        </div>
      </>
    );
  }
}
