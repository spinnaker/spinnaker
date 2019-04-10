import * as React from 'react';

import { Option } from 'react-select';
import { Observable, Subject } from 'rxjs';

import {
  IService,
  IServicePlan,
  ServicesReader,
  ReactSelectInput,
  StageConfigField,
  TextAreaInput,
  TextInput,
} from '@spinnaker/core';

import { ICloudfoundryServiceManifestDirectSource } from './ICloudFoundryServiceManifestSource';
import { ServiceTagsInput } from './ServiceTagsInput';

interface ICreateServiceInstanceDirectInputProps {
  credentials: string;
  region: string;
  service: ICloudfoundryServiceManifestDirectSource;
  onServiceChanged: (_: ICloudfoundryServiceManifestDirectSource) => void;
}

interface ICreateServiceInstanceDirectInputState {
  serviceNamesAndPlans: IService[];
}

export class CreateServiceInstanceDirectInput extends React.Component<
  ICreateServiceInstanceDirectInputProps,
  ICreateServiceInstanceDirectInputState
> {
  private destroy$ = new Subject();
  constructor(props: ICreateServiceInstanceDirectInputProps) {
    super(props);
    this.state = { serviceNamesAndPlans: [] };
  }

  public componentDidUpdate(prevProps: Readonly<ICreateServiceInstanceDirectInputProps>) {
    const { credentials, region } = this.props;
    if ((credentials && credentials !== prevProps.credentials) || region !== prevProps.region) {
      this.loadServices(credentials, region);
    }
  }

  public componentDidMount(): void {
    this.loadServices(this.props.credentials, this.props.region);
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private loadServices(credentials: string, region: string) {
    if (credentials && region) {
      Observable.fromPromise(ServicesReader.getServices(credentials, region))
        .takeUntil(this.destroy$)
        .subscribe(serviceNamesAndPlans => this.setState({ serviceNamesAndPlans }));
    }
  }

  private serviceInstanceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.onServiceChanged({
      ...this.props.service,
      serviceInstanceName: event.target.value,
    });
  };

  private serviceUpdated = (option: Option<string>) => {
    this.props.onServiceChanged({
      ...this.props.service,
      service: option.target.value,
      servicePlan: '',
    });
  };

  private servicePlanUpdated = (option: Option<string>) => {
    this.props.onServiceChanged({
      ...this.props.service,
      servicePlan: option.target.value,
    });
  };

  private parametersUpdated = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    this.props.onServiceChanged({
      ...this.props.service,
      parameters: event.target.value,
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
    });
  };

  public render() {
    const { service } = this.props;
    const services = this.state.serviceNamesAndPlans.map(item => item.name);
    const serviceWithPlans = this.state.serviceNamesAndPlans.find(it => it.name === service.service);
    const servicePlans = serviceWithPlans ? serviceWithPlans.servicePlans.map((it: IServicePlan) => it.name) : [];
    return (
      <div>
        <StageConfigField label="Service Instance Name">
          <TextInput
            type="text"
            className="form-control"
            onChange={this.serviceInstanceNameUpdated}
            value={service.serviceInstanceName}
          />
        </StageConfigField>
        <StageConfigField label="Service">
          <ReactSelectInput
            clearable={false}
            onChange={this.serviceUpdated}
            value={service.service}
            stringOptions={services}
          />
        </StageConfigField>
        <StageConfigField label="Service Plan">
          <ReactSelectInput
            clearable={false}
            onChange={this.servicePlanUpdated}
            value={service.servicePlan}
            stringOptions={servicePlans}
          />
        </StageConfigField>
        <StageConfigField label="Tags">
          <ServiceTagsInput tags={service.tags || []} onChange={this.tagsUpdated} />
        </StageConfigField>
        <StageConfigField label="Parameters">
          <TextAreaInput className="form-control" onChange={this.parametersUpdated} value={service.parameters || ''} />
        </StageConfigField>
        <StageConfigField label="Updatable">
          <input type="checkbox" checked={!!service.updatable} onChange={this.updatableUpdated} />
          {!service.updatable && (
            <div>
              If a service instance with the name '{service.serviceInstanceName}' is already present then it will not be
              updated, and the operation will succeed.
            </div>
          )}
        </StageConfigField>
      </div>
    );
  }
}
