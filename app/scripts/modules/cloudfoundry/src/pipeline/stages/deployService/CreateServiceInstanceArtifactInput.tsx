import * as React from 'react';

import { AccountService, IArtifactAccount, ReactSelectInput, StageConfigField, TextInput } from '@spinnaker/core';

import { ICloudfoundryServiceManifestArtifactSource, ICloudFoundryServiceManifestSource } from './interfaces';

interface ICreateServiceInstanceArtifactInputProps {
  onChange: (serviceInput: ICloudFoundryServiceManifestSource) => void;
  serviceInput: ICloudfoundryServiceManifestArtifactSource;
}

interface ICreateServiceInstanceArtifactInputState {
  account: string;
  reference: string;
  artifactAccounts: IArtifactAccount[];
}

export class CreateServiceInstanceArtifactInput extends React.Component<
  ICreateServiceInstanceArtifactInputProps,
  ICreateServiceInstanceArtifactInputState
> {
  constructor(props: ICreateServiceInstanceArtifactInputProps) {
    super(props);
    const { serviceInput } = props;
    this.state = {
      ...serviceInput,
      artifactAccounts: [],
    };
  }

  public componentDidMount = (): void => {
    AccountService.getArtifactAccounts().then(artifactAccounts => {
      this.setState({ artifactAccounts: artifactAccounts });
    });
  };

  private artifactAccountUpdated = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const account = event.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ account });
    onChange({
      ...serviceInput,
      account,
    } as ICloudFoundryServiceManifestSource);
  };

  private referenceUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const reference = event.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ reference });
    onChange({
      ...serviceInput,
      reference,
    } as ICloudFoundryServiceManifestSource);
  };

  public render() {
    const { account, reference } = this.props.serviceInput;
    const { artifactAccounts } = this.state;
    return (
      <div>
        <StageConfigField label="Artifact Account">
          <ReactSelectInput
            clearable={false}
            onChange={this.artifactAccountUpdated}
            value={account}
            stringOptions={artifactAccounts.map(it => it.name)}
          />
        </StageConfigField>
        <StageConfigField label="Reference">
          <TextInput type="text" className="form-control" onChange={this.referenceUpdated} value={reference} />
        </StageConfigField>
      </div>
    );
  }
}
