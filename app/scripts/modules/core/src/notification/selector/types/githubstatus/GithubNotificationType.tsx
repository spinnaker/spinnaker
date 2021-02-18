import { INotificationTypeCustomConfig } from 'core/domain';
import { FormikFormField, TextInput } from 'core/presentation';
import React from 'react';

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
