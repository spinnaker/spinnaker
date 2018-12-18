import * as React from 'react';

import { FormikFormField, IWizardPageProps, ReactSelectInput, TextInput } from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';
import { IArtifactAccount } from 'core/index';

export interface IArtifactSelectionProps extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
  artifactAccounts: IArtifactAccount[];
}

export class ArtifactSelection extends React.Component<IArtifactSelectionProps> {
  public render() {
    const { artifactAccounts } = this.props;
    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.account"
            label="Artifact Account"
            fastField={false}
            input={props => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={artifactAccounts && artifactAccounts.map((acc: IArtifactAccount) => acc.name)}
                clearable={false}
              />
            )}
            required={true}
          />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.reference"
            label="Reference"
            input={props => <TextInput {...props} />}
            required={true}
          />
        </div>
      </div>
    );
  }
}
