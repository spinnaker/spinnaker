import * as React from 'react';

import Select, { Option } from 'react-select';

import {
  AccountService,
  IAccount,
  IArtifactAccount,
  IRegion,
  IService,
  IServicePlan,
  IStageConfigProps,
  ServicesReader,
  StageConfigField,
} from '@spinnaker/core';

import { includes } from 'lodash';

import './cloudfoundryDeployServiceStage.less';

interface ICloudfoundryServiceManifestDirectSource {
  parameters?: string;
  service: string;
  serviceName: string;
  servicePlan: string;
  tags?: string[];
}

interface ICloudfoundryServiceManifestArtifactSource {
  account: string;
  reference: string;
}

type ICloudFoundryServiceManifestSource = { type: string } & (
  | ICloudfoundryServiceManifestDirectSource
  | ICloudfoundryServiceManifestArtifactSource);

interface ICloudfoundryServiceStageConfigProps extends IStageConfigProps {
  manifest: ICloudFoundryServiceManifestSource;
}

interface ICloudfoundryDeployServiceStageConfigState {
  accounts: IAccount[];
  artifactAccount: string;
  artifactAccounts: IArtifactAccount[];
  cloudProvider: string;
  credentials: string;
  newTag: string;
  parameters: string;
  reference: string;
  region: string;
  regions: IRegion[];
  service: string;
  services: string[];
  serviceName: string;
  serviceNamesAndPlans: IService[];
  servicePlan: string;
  servicePlans: string[];
  tags: string[];
  timeout: string;
  type: string;
}

export class CloudfoundryDeployServiceStageConfig extends React.Component<
  ICloudfoundryServiceStageConfigProps,
  ICloudfoundryDeployServiceStageConfigState
> {
  constructor(props: ICloudfoundryServiceStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    props.stage.manifest = props.stage.manifest || {
      service: '',
      serviceName: '',
      servicePlan: '',
      type: 'direct',
    };

    this.state = {
      accounts: [],
      artifactAccount: props.stage.manifest.account,
      artifactAccounts: [],
      cloudProvider: 'cloudfoundry',
      credentials: props.stage.credentials,
      newTag: '',
      parameters: props.stage.manifest.parameters,
      reference: props.stage.manifest.reference,
      region: props.stage.region,
      regions: [],
      service: props.stage.manifest.service,
      services: [],
      serviceName: props.stage.manifest.serviceName,
      serviceNamesAndPlans: [],
      servicePlan: props.stage.manifest.servicePlan,
      servicePlans: [],
      tags: props.stage.manifest.tags || [],
      timeout: props.stage.timeout,
      type: props.stage.manifest.type,
    };
  }

  private manifestTypeUpdated = (type: string): void => {
    switch (type) {
      case 'direct':
        this.props.stage.manifest = {
          service: '',
          serviceName: '',
          servicePlan: '',
          type: 'direct',
        };
        this.setState({ type: 'direct' });
        break;
      case 'artifact':
        this.props.stage.manifest = {
          account: '',
          reference: '',
          type: 'artifact',
        };
        this.setState({ type: 'artifact' });
        break;
    }
  };

  public componentDidMount = (): void => {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts: accounts });
    });
    AccountService.getArtifactAccounts().then(artifactAccounts => {
      this.setState({ artifactAccounts: artifactAccounts });
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
      const service = services.find(it => it.name === this.props.stage.manifest.service);
      this.setState({
        serviceNamesAndPlans: services,
        servicePlans: service ? service.servicePlans.map((it: IServicePlan) => it.name) : [],
        services: services.map(item => item.name),
      });
      this.props.stageFieldUpdated();
    });
  };

  private accountUpdated = (option: Option<string>): void => {
    const credentials = option.value;
    this.setState({ credentials: credentials, region: '' });
    this.props.stage.credentials = credentials;
    this.props.stage.region = '';
    this.props.stage.manifest.service = '';
    this.props.stage.manifest.serviceName = '';
    this.props.stage.manifest.servicePlan = '';
    this.props.stageFieldUpdated();
    if (credentials) {
      this.clearAndReloadRegions();
    }
  };

  private regionUpdated = (option: Option<string>): void => {
    const region = option.value;
    this.setState({ region: region, service: '', servicePlan: '' });
    this.props.stage.region = region;
    this.props.stage.manifest.service = '';
    this.props.stage.manifest.serviceName = '';
    this.props.stage.manifest.servicePlan = '';
    this.props.stageFieldUpdated();
    this.clearAndReloadServices();
  };

  private serviceUpdated = (option: Option<string>): void => {
    const service = option.value;
    const { serviceNamesAndPlans } = this.state;
    const servicePlans = (serviceNamesAndPlans.find(it => it.name === service).servicePlans || []).map(it => it.name);
    this.setState({
      service,
      servicePlan: '',
      servicePlans,
    });
    this.props.stage.manifest.service = service;
    this.props.stage.manifest.servicePlan = '';
    this.props.stageFieldUpdated();
  };

  private servicePlanUpdated = (option: Option<string>): void => {
    const servicePlan = option.value;
    this.setState({ servicePlan });
    this.props.stage.manifest.servicePlan = servicePlan;
    this.props.stageFieldUpdated();
  };

  private serviceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const serviceName = event.target.value;
    this.setState({ serviceName });
    this.props.stage.manifest.serviceName = serviceName;
    this.props.stageFieldUpdated();
  };

  private parametersUpdated = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
    const parameters = event.target.value;
    this.setState({ parameters });
    this.props.stage.manifest.parameters = parameters;
    this.props.stageFieldUpdated();
  };

  private newTagUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.setState({ newTag: event.target.value });
  };

  private artifactAccountUpdated = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const artifactAccount = event.target.value;
    this.setState({ artifactAccount });
    this.props.stage.manifest.account = artifactAccount;
    this.props.stageFieldUpdated();
  };

  private referenceUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const reference = event.target.value;
    this.setState({ reference });
    this.props.stage.manifest.reference = reference;
    this.props.stageFieldUpdated();
  };

  private addTag = (): void => {
    const { newTag } = this.state;
    const tags = this.props.stage.manifest.tags || [];

    if (!includes(tags, newTag.trim())) {
      const newTags = [...tags, newTag.trim()].sort((a, b) => a.localeCompare(b));
      this.props.stage.manifest.tags = newTags;
      this.setState({
        tags: newTags,
        newTag: '',
      });
      this.props.stageFieldUpdated();
    }
  };

  private deleteTag = (index: number): void => {
    const { tags } = this.props.stage.manifest;
    tags.splice(index, 1);
    this.props.stage.manifest.tags = tags;
    this.setState({ tags });
    this.props.stageFieldUpdated();
  };

  private timeoutUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const timeout = event.target.value;
    this.setState({ timeout });
    this.props.stage.timeout = timeout;
    this.props.stageFieldUpdated();
  };

  private directManifestInput = (
    manifest: ICloudfoundryServiceManifestDirectSource,
    state: ICloudfoundryDeployServiceStageConfigState,
  ): JSX.Element => {
    const { parameters, service, serviceName, servicePlan, tags } = manifest;
    const { newTag, servicePlans, services } = state;
    return (
      <div>
        <StageConfigField label="Service Name">
          <input
            type="text"
            className="form-control"
            required={true}
            onChange={this.serviceNameUpdated}
            value={serviceName}
          />
        </StageConfigField>
        <StageConfigField label="Service">
          <Select
            options={
              services &&
              services.map((serv: string) => ({
                label: serv,
                value: serv,
              }))
            }
            clearable={false}
            value={service}
            onChange={this.serviceUpdated}
          />
        </StageConfigField>
        <StageConfigField label="Service Plan">
          <Select
            options={
              servicePlans &&
              servicePlans.map((servPlan: string) => ({
                label: servPlan,
                value: servPlan,
              }))
            }
            clearable={false}
            value={servicePlan}
            onChange={this.servicePlanUpdated}
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
      </div>
    );
  };

  private artifactManifestInput = (
    manifest: ICloudfoundryServiceManifestArtifactSource,
    state: ICloudfoundryDeployServiceStageConfigState,
  ): JSX.Element => {
    const { account, reference } = manifest;
    const { artifactAccounts } = state;
    return (
      <div>
        <StageConfigField label="Artifact Account">
          <select
            className="form-control input-sm"
            required={true}
            onChange={this.artifactAccountUpdated}
            value={account}
          >
            <option value="" disabled={true}>
              Select...
            </option>
            {artifactAccounts.map((it: IArtifactAccount) => {
              return (
                <option key={it.name} value={it.name}>
                  {it.name}
                </option>
              );
            })}
          </select>
        </StageConfigField>
        <StageConfigField label="Reference">
          <input
            type="text"
            className="form-control"
            required={true}
            onChange={this.referenceUpdated}
            value={reference}
          />
        </StageConfigField>
      </div>
    );
  };

  public render() {
    const { stage } = this.props;
    const { credentials, manifest, region, timeout } = stage;
    const { accounts, regions } = this.state;

    let manifestInput;

    switch (manifest.type) {
      case 'direct':
        manifestInput = this.directManifestInput(manifest, this.state);
        break;
      case 'artifact':
        manifestInput = this.artifactManifestInput(manifest, this.state);
        break;
    }

    return (
      <div className="form-horizontal cloudfoundry-deploy-service-stage">
        <StageConfigField label="Account">
          <Select
            options={
              accounts &&
              accounts.map((acc: IAccount) => ({
                label: acc.name,
                value: acc.name,
              }))
            }
            clearable={false}
            value={credentials}
            onChange={this.accountUpdated}
          />
        </StageConfigField>
        <StageConfigField label="Region">
          <Select
            options={
              regions &&
              regions.map((r: IRegion) => ({
                label: r.name,
                value: r.name,
              }))
            }
            clearable={false}
            value={region}
            onChange={this.regionUpdated}
          />
        </StageConfigField>
        <StageConfigField label="Source Type">
          <div className="col-md-7">
            <div className="radio radio-inline">
              <label>
                <input
                  type="radio"
                  checked={manifest.type === 'artifact'}
                  onChange={() => this.manifestTypeUpdated('artifact')}
                />{' '}
                Artifact
              </label>
            </div>
            <div className="radio radio-inline">
              <label>
                <input
                  type="radio"
                  checked={manifest.type === 'direct'}
                  onChange={() => this.manifestTypeUpdated('direct')}
                />{' '}
                Form
              </label>
            </div>
          </div>
        </StageConfigField>
        {manifestInput}
        <StageConfigField label="Override Deploy Timeout (Seconds)" helpKey="cf.service.deploy.timeout">
          <input type="number" className="form-control" onChange={this.timeoutUpdated} value={timeout} />
        </StageConfigField>
      </div>
    );
  }
}
