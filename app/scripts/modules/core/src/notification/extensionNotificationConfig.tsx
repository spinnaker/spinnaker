import React from 'react';

import { INotificationParameter } from './NotificationService';
import { INotificationTypeCustomConfig } from '../domain';
import { HelpField } from '../help';
import { FormikFormField, TextInput } from '../presentation';

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
