import * as React from 'react';
import Select, { Option } from 'react-select';

import { IgorService, IStageConfigProps, StageConfigField, YamlEditor, yamlDocumentsToString } from '@spinnaker/core';

export interface IGoogleCloudBuildStageConfigState {
  googleCloudBuildAccounts: string[];
  rawBuildDefinitionYaml: string;
}

export class GoogleCloudBuildStageConfig extends React.Component<IStageConfigProps, IGoogleCloudBuildStageConfigState> {
  public constructor(props: IStageConfigProps) {
    super(props);
    this.state = {
      googleCloudBuildAccounts: [],
      rawBuildDefinitionYaml: props.stage.buildDefinition ? yamlDocumentsToString([props.stage.buildDefinition]) : '',
    };
  }

  public componentDidMount = (): void => {
    if (this.props.stage.isNew) {
      this.props.updateStageField({ application: this.props.application.name });
    }
    this.fetchGoogleCloudBuildAccounts();
  };

  private onYamlChange = (rawYaml: string, yamlDocs: any): void => {
    this.setState({ rawBuildDefinitionYaml: rawYaml });
    const buildDefinition = Array.isArray(yamlDocs) && yamlDocs.length > 0 ? yamlDocs[0] : null;
    this.props.updateStageField({ buildDefinition });
  };

  private onAccountChange = (accountOption: Option) => {
    this.props.updateStageField({ account: accountOption.value });
  };

  private getAccountOptions = (): Option[] => {
    return this.state.googleCloudBuildAccounts.map(account => ({
      label: account,
      value: account,
    }));
  };

  private fetchGoogleCloudBuildAccounts = () => {
    IgorService.getGcbAccounts().then((googleCloudBuildAccounts: string[]) => {
      this.setState({
        googleCloudBuildAccounts,
      });
    });
  };

  public render() {
    return (
      <div>
        <StageConfigField label="Account">
          <Select
            clearable={false}
            onChange={this.onAccountChange}
            options={this.getAccountOptions()}
            value={this.props.stage.account}
          />
        </StageConfigField>
        <StageConfigField label="Build Config">
          <YamlEditor value={this.state.rawBuildDefinitionYaml} onChange={this.onYamlChange} />
        </StageConfigField>
      </div>
    );
  }
}
