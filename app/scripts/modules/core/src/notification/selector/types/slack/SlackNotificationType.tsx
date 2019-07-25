import * as React from 'react';
import { FormikFormField, TextInput } from 'core/presentation';
import { INotificationTypeCustomConfig } from 'core/domain';

export class SlackNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { botName, fieldName } = this.props;
    return (
      <>
        <div className="sp-margin-m-bottom">
          <div className={'form-group'}>
            <label className={'col-md-4 sm-label-right'}>Slack Channel</label>
            <div className="col-md-6">
              <FormikFormField
                name={fieldName ? `${fieldName}.address` : 'address'}
                input={props => (
                  <TextInput inputClassName={'form-control input-sm'} {...props} placeholder="enter a Slack channel" />
                )}
                required={true}
              />
            </div>
          </div>
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
        </div>
      </>
    );
  }
}
