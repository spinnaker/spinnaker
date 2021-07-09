import React from 'react';
import { Option } from 'react-select';

import { INotificationTypeConfig } from '../../domain';
import { FormikFormField, ReactSelectInput } from '../../presentation';
import { Registry } from '../../registry';

import './notificationSelector.less';

export interface INotificationSelectorProps {
  fieldName?: string;
  onNotificationTypeChange?: (type: string) => void;
  type: string;
}

export interface INotificationSelectorState {
  notificationTypes: Option[];
}

export class NotificationSelector extends React.Component<INotificationSelectorProps, INotificationSelectorState> {
  constructor(props: INotificationSelectorProps) {
    super(props);
    this.state = {
      notificationTypes: Registry.pipeline.getNotificationTypes().map((type: INotificationTypeConfig) => ({
        label: type.label,
        value: type.key,
      })),
    };
  }

  private NotificationDetailFields = (props: { type: string; fieldName?: string }) => {
    const notificationConfig = Registry.pipeline.getNotificationConfig(props.type);
    if (notificationConfig && notificationConfig.component) {
      const notificationProps = {
        botName: notificationConfig.config.botName,
        fieldName: props.fieldName,
      } as React.Attributes;
      return React.createElement(notificationConfig.component, notificationProps);
    }
    return <></>;
  };

  public render() {
    const { NotificationDetailFields } = this;
    const { fieldName, onNotificationTypeChange, type } = this.props;
    const { notificationTypes } = this.state;
    return (
      <>
        <FormikFormField
          label="Notify via"
          name={fieldName ? `${fieldName}.type` : 'type'}
          input={(props) => (
            <ReactSelectInput
              clearable={false}
              {...props}
              inputClassName={'notification-type-select'}
              options={notificationTypes}
            />
          )}
          onChange={onNotificationTypeChange}
          required={true}
        />
        <NotificationDetailFields type={type} fieldName={fieldName} />
      </>
    );
  }
}
