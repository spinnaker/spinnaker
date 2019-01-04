import * as React from 'react';

import { Option } from 'react-select';

import {
  AccountService,
  IAccount,
  IRegion,
  IService,
  IStageConfigProps,
  ReactSelectInput,
  ServicesReader,
  StageConfigField,
} from '@spinnaker/core';

import { CreateServiceInstanceArtifactInput } from './CreateServiceInstanceArtifactInput';
import { CreateServiceInstanceDirectInput } from './CreateServiceInstanceDirectInput';
import { CreateUserProvidedInput } from './CreateUserProvidedInput';
import {
  ICloudfoundryServiceManifestArtifactSource,
  ICloudfoundryServiceManifestDirectSource,
  ICloudFoundryServiceManifestSource,
  ICloudFoundryServiceUserProvidedSource,
} from './interfaces';
import './cloudfoundryDeployServiceStage.less';

interface ICloudfoundryServiceStageConfigProps extends IStageConfigProps {
  manifest?: ICloudFoundryServiceManifestSource;
}

interface ICloudfoundryDeployServiceStageConfigState {
  credentials: string;
  accounts: IAccount[];
  cloudProvider: string;
  region: string;
  regions: IRegion[];
  serviceNamesAndPlans: IService[];
  timeout: string;
  type: string;
  manifest?: ICloudFoundryServiceManifestSource;
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
      parameters: '',
      type: 'direct',
    };
    this.state = {
      credentials: props.stage.credentials,
      accounts: [],
      cloudProvider: 'cloudfoundry',
      region: props.stage.region,
      regions: [],
      serviceNamesAndPlans: [],
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
      case 'userProvided':
        this.props.stage.manifest = {
          credentials: '',
          routeServiceUrl: '',
          serviceName: '',
          syslogDrainUrl: '',
          tags: [],
          type: 'userProvided',
        };
        this.setState({ type: 'userProvided' });
        break;
    }
  };

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
    });
    AccountService.getRegionsForAccount(this.props.stage.credentials).then(regions => this.setState({ regions }));
  };

  private clearAndReloadServices = (): void => {
    this.setState({ serviceNamesAndPlans: [] });
    const { credentials, region } = this.props.stage;
    ServicesReader.getServices(credentials, region).then(serviceNamesAndPlans => {
      this.setState({ serviceNamesAndPlans });
      this.props.stageFieldUpdated();
    });
  };

  private accountUpdated = (option: Option<string>): void => {
    const credentials = option.target.value;
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
    const region = option.target.value;
    this.setState({ region: region });
    this.props.stage.region = region;
    this.props.stage.manifest.service = '';
    this.props.stage.manifest.serviceName = '';
    this.props.stage.manifest.servicePlan = '';
    this.props.stageFieldUpdated();
    this.clearAndReloadServices();
  };

  private timeoutUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const timeout = event.target.value;
    this.setState({ timeout });
    this.props.stage.timeout = timeout;
    this.props.stageFieldUpdated();
  };

  private serviceManifestSourceUpdated = (manifest: ICloudFoundryServiceManifestSource) => {
    this.props.stage.manifest = manifest;
    this.props.stageFieldUpdated();
    this.setState({ manifest: manifest as ICloudFoundryServiceManifestSource });
  };

  public render() {
    const { credentials, manifest, region, timeout } = this.props.stage;
    const { accounts, regions, serviceNamesAndPlans } = this.state;
    const { serviceManifestSourceUpdated } = this;
    let manifestInput;

    switch (manifest.type) {
      case 'direct':
        const direct = manifest as { type: string } & ICloudfoundryServiceManifestDirectSource;
        manifestInput = (
          <CreateServiceInstanceDirectInput
            onChange={serviceManifestSourceUpdated}
            serviceInput={direct}
            serviceNamesAndPlans={serviceNamesAndPlans}
          />
        );
        break;
      case 'artifact':
        const artifact = manifest as { type: string } & ICloudfoundryServiceManifestArtifactSource;
        manifestInput = (
          <CreateServiceInstanceArtifactInput onChange={serviceManifestSourceUpdated} serviceInput={artifact} />
        );
        break;
      case 'userProvided':
        const userProvided = manifest as { type: string } & ICloudFoundryServiceUserProvidedSource;
        manifestInput = <CreateUserProvidedInput onChange={serviceManifestSourceUpdated} serviceInput={userProvided} />;
        break;
    }

    return (
      <div className="form-horizontal cloudfoundry-deploy-service-stage">
        <StageConfigField label="Account">
          <ReactSelectInput
            clearable={false}
            onChange={this.accountUpdated}
            value={credentials}
            stringOptions={accounts.map(it => it.name)}
          />
        </StageConfigField>
        <StageConfigField label="Region">
          <ReactSelectInput
            clearable={false}
            onChange={this.regionUpdated}
            value={region}
            stringOptions={regions.map(it => it.name)}
          />
        </StageConfigField>
        <StageConfigField label="Source Type">
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
          <div className="radio radio-inline">
            <label>
              <input
                type="radio"
                checked={manifest.type === 'userProvided'}
                onChange={() => this.manifestTypeUpdated('userProvided')}
              />{' '}
              User-Provided
            </label>
          </div>
        </StageConfigField>
        {manifestInput}
        {manifest.type !== 'userProvided' && (
          <StageConfigField label="Override Deploy Timeout (Seconds)" helpKey="cf.service.deploy.timeout">
            <input type="number" className="form-control" onChange={this.timeoutUpdated} value={timeout} />
          </StageConfigField>
        )}
      </div>
    );
  }
}
