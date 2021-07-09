import { FormikProps } from 'formik';
import React from 'react';
import { Option } from 'react-select';

import { INotification, INotificationTypeConfig, IPipelineCommand } from '../../domain';
import { NotificationSelector, NotificationTransformer } from '../../notification';
import { CheckboxInput, FormikFormField, HoverablePopover } from '../../presentation';
import { Registry } from '../../registry';

export interface INotificationDetailsProps {
  formik: FormikProps<IPipelineCommand>;
  notifications: INotification[];
}

export interface INotificationDetailsState {
  notificationTypes: Option[];
}

export class NotificationDetails extends React.Component<INotificationDetailsProps, INotificationDetailsState> {
  constructor(props: INotificationDetailsProps) {
    super(props);

    this.state = {
      notificationTypes: Registry.pipeline.getNotificationTypes().map((type: INotificationTypeConfig) => ({
        label: type.label,
        value: type.key,
      })),
    };
  }

  private notificationToolTip = (notifications: INotification[]) => (
    <div className="notifications-list">
      <ul>
        {notifications.map((notification, i) => {
          return (
            <li key={i}>
              <b>{notification.address}</b> when:
              {notification.when.map((w, j) => {
                return (
                  <ul key={j}>
                    <li>{NotificationTransformer.getNotificationWhenDisplayName(w)}</li>
                  </ul>
                );
              })}
            </li>
          );
        })}
      </ul>
    </div>
  );

  private onNotificationTypeChange = (type: string) => {
    const notificationTypeUpdate = Registry.pipeline.getNotificationConfig(type);
    const { values } = this.props.formik;
    if (!!notificationTypeUpdate && values.notification && values.notification.address) {
      this.props.formik.setFieldValue('notification.address', '');
    }
  };

  public render() {
    const { notificationToolTip, onNotificationTypeChange } = this;
    const { formik, notifications } = this.props;
    const { values } = formik;
    return (
      <>
        <FormikFormField
          name="notificationEnabled"
          label="Notifications"
          input={(props) => <CheckboxInput {...props} text={'Notify me when the pipeline completes'} />}
        />
        <div className="form-group">
          {notifications.length === 1 && (
            <div className="col-md-offset-4 col-md-6">
              There is{' '}
              <HoverablePopover placement="bottom" template={notificationToolTip(notifications)}>
                <a>one notification</a>
              </HoverablePopover>{' '}
              for this pipeline
            </div>
          )}
          {notifications.length > 1 && (
            <div className="col-md-offset-4 col-md-6">
              There are{' '}
              <HoverablePopover placement="bottom" template={notificationToolTip(notifications)}>
                <a>{notifications.length} notifications</a>
              </HoverablePopover>{' '}
              configured for this pipeline
            </div>
          )}
        </div>
        {values.notificationEnabled && (
          <NotificationSelector
            onNotificationTypeChange={onNotificationTypeChange}
            fieldName={'notification'}
            type={values.notification.type}
          />
        )}
      </>
    );
  }
}
