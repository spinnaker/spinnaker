import * as React from 'react';

import { Option } from 'react-select';

import { IService, IServicePlan, ReactSelectInput, StageConfigField, TextAreaInput, TextInput } from '@spinnaker/core';

import { ServiceTagsInput } from './ServiceTagsInput';
import { ICloudfoundryServiceManifestDirectSource, ICloudFoundryServiceManifestSource } from './interfaces';

interface ICreateServiceInstanceDirectInputProps {
  onChange: (serviceInput: ICloudFoundryServiceManifestSource) => void;
  serviceInput: ICloudfoundryServiceManifestDirectSource;
  serviceNamesAndPlans: IService[];
}

interface ICreateServiceInstanceDirectInputState {
  parameters?: string;
  service: string;
  serviceName: string;
  servicePlan: string;
  tags?: string[];
}

export class CreateServiceInstanceDirectInput extends React.Component<
  ICreateServiceInstanceDirectInputProps,
  ICreateServiceInstanceDirectInputState
> {
  constructor(props: ICreateServiceInstanceDirectInputProps) {
    super(props);
    const { serviceInput } = props;
    this.state = {
      ...serviceInput,
    };
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

  private serviceUpdated = (option: Option<string>): void => {
    const service = option.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({
      service,
      servicePlan: '',
    });
    onChange({
      ...serviceInput,
      service,
      servicePlan: '',
    } as ICloudFoundryServiceManifestSource);
  };

  private servicePlanUpdated = (option: Option<string>): void => {
    const servicePlan = option.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ servicePlan });
    onChange({
      ...serviceInput,
      servicePlan,
    } as ICloudFoundryServiceManifestSource);
  };

  private parametersUpdated = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
    const parameters = event.target.value;
    const { onChange, serviceInput } = this.props;
    this.setState({ parameters });
    onChange({
      ...serviceInput,
      parameters,
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
    const { serviceInput, serviceNamesAndPlans } = this.props;
    const { parameters, service, serviceName, servicePlan, tags } = serviceInput;
    const services = serviceNamesAndPlans.map(item => item.name);
    const serviceWithPlans = serviceNamesAndPlans.find(it => it.name === serviceInput.service);
    const servicePlans = serviceWithPlans ? serviceWithPlans.servicePlans.map((it: IServicePlan) => it.name) : [];
    return (
      <div>
        <StageConfigField label="Service Name">
          <TextInput type="text" className="form-control" onChange={this.serviceNameUpdated} value={serviceName} />
        </StageConfigField>
        <StageConfigField label="Service">
          <ReactSelectInput clearable={false} onChange={this.serviceUpdated} value={service} stringOptions={services} />
        </StageConfigField>
        <StageConfigField label="Service Plan">
          <ReactSelectInput
            clearable={false}
            onChange={this.servicePlanUpdated}
            value={servicePlan}
            stringOptions={servicePlans}
          />
        </StageConfigField>
        <StageConfigField label="Tags">
          <ServiceTagsInput tags={tags} onChange={this.tagsUpdated} />
        </StageConfigField>
        <StageConfigField label="Parameters">
          <TextAreaInput className="form-control" onChange={this.parametersUpdated} value={parameters} />
        </StageConfigField>
      </div>
    );
  }
}
