import React from 'react';

import { StageConfigField, TextAreaInput, TextInput } from '@spinnaker/core';

import {
  ICloudFoundryServiceManifestSource,
  ICloudFoundryServiceUserProvidedSource,
} from './ICloudFoundryServiceManifestSource';
import { ServiceTagsInput } from './ServiceTagsInput';

interface ICreateServiceInstanceUserProvidedInputProps {
  onChange: (serviceInput: ICloudFoundryServiceManifestSource) => void;
  service: ICloudFoundryServiceUserProvidedSource;
  onServiceChanged: (_: ICloudFoundryServiceUserProvidedSource) => void;
}

export class CreateUserProvidedInput extends React.Component<ICreateServiceInstanceUserProvidedInputProps> {
  constructor(props: ICreateServiceInstanceUserProvidedInputProps) {
    super(props);
  }

  private serviceInstanceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.onServiceChanged({
      ...this.props.service,
      serviceInstanceName: event.target.value,
    });
  };

  private syslogDrainUrlUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.onServiceChanged({
      ...this.props.service,
      syslogDrainUrl: event.target.value,
    });
  };

  private routeServiceUrlUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.onServiceChanged({
      ...this.props.service,
      routeServiceUrl: event.target.value,
    });
  };

  private credentialsUpdated = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
    this.props.onServiceChanged({
      ...this.props.service,
      credentials: event.target.value,
    });
  };

  private tagsUpdated = (tags: string[]) => {
    this.props.onServiceChanged({
      ...this.props.service,
      tags: tags,
    });
  };

  private updatableUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.onServiceChanged({
      ...this.props.service,
      updatable: event.target.checked,
      versioned: false,
    });
  };

  private versionedUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.onServiceChanged({
      ...this.props.service,
      versioned: event.target.checked,
      updatable: false,
    });
  };

  public render() {
    const { service } = this.props;
    return (
      <>
        <StageConfigField label="Service Instance Name">
          <TextInput
            type="text"
            className="form-control"
            onChange={this.serviceInstanceNameUpdated}
            value={service.serviceInstanceName}
          />
        </StageConfigField>
        <StageConfigField label="Syslog Drain URL">
          <TextInput onChange={this.syslogDrainUrlUpdated} value={service.syslogDrainUrl} />
        </StageConfigField>
        <StageConfigField label="Route Service URL">
          <TextInput onChange={this.routeServiceUrlUpdated} value={service.routeServiceUrl} />
        </StageConfigField>
        <StageConfigField label="Credentials">
          <TextAreaInput onChange={this.credentialsUpdated} value={service.credentials} />
        </StageConfigField>
        <StageConfigField label="Tags">
          <ServiceTagsInput tags={service.tags || []} onChange={this.tagsUpdated} />
        </StageConfigField>
        <StageConfigField label="Updatable" helpKey={'pipeline.config.cf.createservice.updatable'}>
          <input
            type="checkbox"
            disabled={!!service.versioned}
            checked={!!service.updatable}
            onChange={this.updatableUpdated}
          />
        </StageConfigField>
        <StageConfigField label="Versioned" helpKey={'pipeline.config.cf.createservice.versioned'}>
          <input
            type="checkbox"
            disabled={!!service.updatable}
            checked={!!service.versioned}
            onChange={this.versionedUpdated}
          />
        </StageConfigField>
      </>
    );
  }
}
