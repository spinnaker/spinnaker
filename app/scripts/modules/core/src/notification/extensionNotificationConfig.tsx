import React from 'react';
import { FormikFormField, TextInput } from 'core/presentation';
import { HelpField } from 'core/help';
import { INotificationTypeCustomConfig } from 'core/domain';
import { INotificationParameter } from './NotificationService';

export const extensionNotificationConfig = (parameters: INotificationParameter[]) => {
  return class ExtensionNotificationConfig extends React.Component<INotificationTypeCustomConfig> {
    render() {
      return (
        <>
          {parameters.map((param) => (
            <FormikFormField
              key={param.name}
              name={param.name}
              label={param.label}
              help={param.description ? <HelpField content={param.description} /> : null}
              input={(props) => <TextInput {...props} />}
              {...this.props}
            />
          ))}
        </>
      );
    }
  };
};
