import * as React from 'react';

import { FormikFormField, HelpField, IWizardPageProps, ReactSelectInput, TextInput } from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';
import { IArtifactAccount } from 'core/index';

export interface IArtifactTriggerProps extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
  artifactAccounts: IArtifactAccount[];
}

export class ArtifactTrigger extends React.Component<IArtifactTriggerProps> {
  public render() {
    const { artifactAccounts } = this.props;

    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <div>
            <FormikFormField
              name="artifact.pattern"
              label="Artifact Pattern"
              input={props => <TextInput {...props} />}
              required={true}
            />
          </div>
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.account"
            label="Artifact Account"
            fastField={false}
            input={props => (
              <ReactSelectInput
                {...props}
                inputClassName="cloudfoundry-react-select"
                stringOptions={artifactAccounts && artifactAccounts.map((acc: IArtifactAccount) => acc.name)}
                clearable={false}
              />
            )}
            help={<HelpField id="cf.artifact.trigger.account" />}
            required={true}
          />
        </div>
      </div>
    );
  }
}
