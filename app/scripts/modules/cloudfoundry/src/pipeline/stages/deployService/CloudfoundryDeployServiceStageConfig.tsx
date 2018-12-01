import * as React from 'react';

import {
  AccountSelectField,
  AccountService,
  IAccount,
  IRegion,
  IService,
  IServicePlan,
  IStageConfigProps,
  RegionSelectField,
  ServicesReader,
  StageConfigField,
} from '@spinnaker/core';

import { includes } from 'lodash';

import './cloudfoundryDeployServiceStage.less';

export interface ICloudfoundryDeployServiceStageConfigState {
  accounts: IAccount[];
  cloudProvider: string;
  credentials: string;
  newTag: string;
  parameters: string;
  region: string;
  regions: IRegion[];
  service: string;
  services: string[];
  serviceName: string;
  serviceNamesAndPlans: IService[];
  servicePlan: string;
  servicePlans: string[];
  tags: string[];
}

export class CloudfoundryDeployServiceStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryDeployServiceStageConfigState
> {
  constructor(props: IStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      accounts: [],
      cloudProvider: 'cloudfoundry',
      credentials: props.stage.credentials,
      newTag: '',
      parameters: props.stage.parameters,
      region: props.stage.region,
      regions: [],
      service: props.stage.service,
      services: [],
      serviceName: props.stage.serviceName,
      serviceNamesAndPlans: [],
      servicePlan: props.stage.servicePlan,
      servicePlans: [],
      tags: props.stage.tags || [],
    };
  }

  public componentDidMount = (): void => {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts: accounts });
    });
    const { credentials, region } = this.props.stage;
    if (credentials) {
      this.clearAndReloadRegions();
    }
    if (region) {
      this.clearAndReloadServices();
    }
    this.props.stageFieldUpdated();
  };

  private clearAndReloadRegions = (): void => {
    this.setState({
      regions: [],
      serviceNamesAndPlans: [],
      servicePlans: [],
      services: [],
    });
    AccountService.getRegionsForAccount(this.props.stage.credentials).then(regions =>
      this.setState({ regions: regions }),
    );
  };

  private clearAndReloadServices = (): void => {
    this.setState({
      serviceNamesAndPlans: [],
      servicePlans: [],
      services: [],
    });
    const { credentials, region } = this.props.stage;
    ServicesReader.getServices(credentials, region).then(services => {
      const service = services.find(it => it.name === this.props.stage.service);
      this.setState({
        serviceNamesAndPlans: services,
        servicePlans: service ? service.servicePlans.map((it: IServicePlan) => it.name) : [],
        services: services.map(item => item.name),
      });
      this.props.stageFieldUpdated();
    });
  };

  private accountUpdated = (credentials: string): void => {
    this.setState({ credentials: credentials, region: '' });
    this.props.stage.credentials = credentials;
    this.props.stage.region = '';
    this.props.stage.service = '';
    this.props.stage.servicePlan = '';
    this.props.stageFieldUpdated();
    if (credentials) {
      this.clearAndReloadRegions();
    }
  };

  private regionUpdated = (region: string): void => {
    this.setState({ region: region, service: '', servicePlan: '' });
    this.props.stage.region = region;
    this.props.stage.service = '';
    this.props.stage.servicePlan = '';
    this.props.stageFieldUpdated();
    this.clearAndReloadServices();
  };

  private serviceUpdated = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const service = event.target.value;
    const { serviceNamesAndPlans } = this.state;
    const servicePlans = (serviceNamesAndPlans.find(it => it.name === service).servicePlans || []).map(it => it.name);
    this.setState({
      service,
      servicePlan: '',
      servicePlans,
    });
    this.props.stage.service = service;
    this.props.stage.servicePlan = '';
    this.props.stageFieldUpdated();
  };

  private servicePlanUpdated = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const servicePlan = event.target.value;
    this.setState({ servicePlan });
    this.props.stage.servicePlan = servicePlan;
    this.props.stageFieldUpdated();
  };

  private serviceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const serviceName = event.target.value;
    this.setState({ serviceName });
    this.props.stage.serviceName = serviceName;
    this.props.stageFieldUpdated();
  };

  private parametersUpdated = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
    const parameters = event.target.value;
    this.setState({ parameters });
    this.props.stage.parameters = parameters;
    this.props.stageFieldUpdated();
  };

  private newTagUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.setState({ newTag: event.target.value });
  };

  private addTag = (): void => {
    const { newTag } = this.state;
    const tags = this.props.stage.tags || [];

    if (!includes(tags, newTag.trim())) {
      const newTags = [...tags, newTag.trim()].sort((a, b) => a.localeCompare(b));
      this.props.stage.tags = newTags;
      this.setState({
        tags: newTags,
        newTag: '',
      });
      this.props.stageFieldUpdated();
    }
  };

  private deleteTag = (index: number): void => {
    const { tags } = this.props.stage;
    tags.splice(index, 1);
    this.props.stage.tags = tags;
    this.setState({ tags });
    this.props.stageFieldUpdated();
  };

  private timeoutUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.stage.timeout = event.target.value;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { stage } = this.props;
    const { credentials, parameters, service, serviceName, servicePlan, tags, timeout } = stage;
    const { accounts, newTag, regions, servicePlans, services } = this.state;
    return (
      <div className="form-horizontal cloudfoundry-deploy-service-stage">
        <StageConfigField label="Account">
          <AccountSelectField
            accounts={accounts}
            component={stage}
            field="credentials"
            provider="cloudfoundry"
            onChange={this.accountUpdated}
          />
        </StageConfigField>
        <RegionSelectField
          labelColumns={3}
          fieldColumns={8}
          component={stage}
          field="region"
          account={credentials}
          onChange={this.regionUpdated}
          regions={regions}
        />
        <StageConfigField label="Service">
          <select className="form-control input-sm" required={true} onChange={this.serviceUpdated} value={service}>
            <option value="" disabled={true}>
              Select...
            </option>
            {services.map((it: string) => {
              return (
                <option key={it} value={it}>
                  {it}
                </option>
              );
            })}
          </select>
        </StageConfigField>
        <StageConfigField label="Service Plan">
          <select
            className="form-control input-sm"
            required={true}
            onChange={this.servicePlanUpdated}
            value={servicePlan}
          >
            <option value="" disabled={true}>
              Select...
            </option>
            {servicePlans.map((it: string) => {
              return (
                <option key={it} value={it}>
                  {it}
                </option>
              );
            })}
          </select>
        </StageConfigField>
        <StageConfigField label="Service Name">
          <input
            type="text"
            className="form-control"
            required={true}
            onChange={this.serviceNameUpdated}
            value={serviceName}
          />
        </StageConfigField>
        <StageConfigField label="Tags">
          <div className="row">
            <div className="col-md-4">
              <input type="text" className="form-control" name="newTag" onChange={this.newTagUpdated} value={newTag} />
            </div>
            <div className="col-md-2">
              <button disabled={!newTag.trim()} className="btn btn-default btn-sm btn-add-tag" onClick={this.addTag}>
                Add Tag
              </button>
            </div>
          </div>
          <div>
            <div>
              {tags &&
                tags.map((tag: any, index: number) => {
                  return (
                    <span className="badge badge-pill" key={index}>
                      &nbsp;
                      {tag}
                      &nbsp;
                      <button
                        type="button"
                        className="close small"
                        aria-label="Close"
                        onClick={() => this.deleteTag(index)}
                      >
                        <span aria-hidden="true">&times;</span>
                      </button>
                    </span>
                  );
                })}
            </div>
          </div>
        </StageConfigField>
        <StageConfigField label="Parameters">
          <textarea className="form-control" onChange={this.parametersUpdated} value={parameters} />
        </StageConfigField>
        <StageConfigField label="Override Deploy Timeout (Seconds)" helpKey="cf.service.deploy.timeout">
          <input type="number" className="form-control" onChange={this.timeoutUpdated} value={timeout} />
        </StageConfigField>
      </div>
    );
  }
}
