import React from 'react';

import { INotificationTypeCustomConfig } from '../../../../domain';
import { FormikFormField, TextInput } from '../../../../presentation';

export class GithubNotificationType extends React.Component<INotificationTypeCustomConfig> {
  public render() {
    const { fieldName } = this.props;
    return (
      <>
        <FormikFormField
          label="Github Repository"
          name={fieldName ? `${fieldName}.repo` : 'repo'}
          input={(props) => <TextInput inputClassName={'form-control input-sm'} {...props} />}
          required={true}
        />
        <FormikFormField
          label="Commit SHA"
          name={fieldName ? `${fieldName}.commit` : 'commit'}
          input={(props) => <TextInput inputClassName={'form-control input-sm'} {...props} />}
        />
      </>
    );
  }
}
