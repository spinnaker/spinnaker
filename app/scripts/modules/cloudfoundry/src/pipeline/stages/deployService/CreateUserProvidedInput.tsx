import * as React from 'react';

import { StageConfigField, TextAreaInput, TextInput } from '@spinnaker/core';

import { ServiceTagsInput } from './ServiceTagsInput';
import { ICloudFoundryServiceManifestSource, ICloudFoundryServiceUserProvidedSource } from './interfaces';

interface ICreateServiceInstanceUserProvidedInputProps {
  onChange: (serviceInput: ICloudFoundryServiceManifestSource) => void;
  serviceInput: ICloudFoundryServiceUserProvidedSource;
}

interface ICreateServiceInstanceUserProvidedInputState {
  serviceName: string;
  tags?: string[];
  syslogDrainUrl?: string;
  credentials?: string;
  routeServiceUrl: string;
}

export class CreateUserProvidedInput extends React.Component<
  ICreateServiceInstanceUserProvidedInputProps,
  ICreateServiceInstanceUserProvidedInputState
> {
  constructor(props: ICreateServiceInstanceUserProvidedInputProps) {
    super(props);
    const { serviceInput } = props;
    this.state = { ...serviceInput };
  }

  private serviceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const serviceName = event.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ serviceName });
    onChange({
      ...serviceInput,
      serviceName,
    } as ICloudFoundryServiceManifestSource);
  };

  private syslogDrainUrlUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const syslogDrainUrl = event.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ syslogDrainUrl });
    onChange({
      ...serviceInput,
      syslogDrainUrl,
    } as ICloudFoundryServiceManifestSource);
  };

  private routeServiceUrlUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const routeServiceUrl = event.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ routeServiceUrl });
    onChange({
      ...serviceInput,
      routeServiceUrl,
    } as ICloudFoundryServiceManifestSource);
  };

  private credentialsUpdated = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
    const credentials = event.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ credentials });
    onChange({
      ...serviceInput,
      credentials,
    } as ICloudFoundryServiceManifestSource);
  };

  private tagsUpdated = (tags: string[]) => {
    const { onChange, serviceInput } = this.props;
    this.setState({ tags });
    onChange({
      ...serviceInput,
      tags,
    } as ICloudFoundryServiceManifestSource);
  };

  public render() {
    const { serviceName, tags, syslogDrainUrl, credentials, routeServiceUrl } = this.state;
    return (
      <>
        <StageConfigField label="Service Name">
          <TextInput type="text" className="form-control" onChange={this.serviceNameUpdated} value={serviceName} />
        </StageConfigField>
        <StageConfigField label="Syslog Drain URL">
          <TextInput onChange={this.syslogDrainUrlUpdated} value={syslogDrainUrl} />
        </StageConfigField>
        <StageConfigField label="Resource Service URL">
          <TextInput onChange={this.routeServiceUrlUpdated} value={routeServiceUrl} />
        </StageConfigField>
        <StageConfigField label="Credentials">
          <TextAreaInput onChange={this.credentialsUpdated} value={credentials} />
        </StageConfigField>
        <StageConfigField label="Tags">
          <ServiceTagsInput tags={tags} onChange={this.tagsUpdated} />
        </StageConfigField>
      </>
    );
  }
}
