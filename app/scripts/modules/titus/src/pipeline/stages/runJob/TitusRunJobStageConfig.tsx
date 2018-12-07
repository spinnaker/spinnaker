import * as React from 'react';
import { defaultsDeep, set } from 'lodash';

import {
  AccountTag,
  IStageConfigProps,
  RegionSelectField,
  StageConfigField,
  HelpField,
  IAggregatedAccounts,
  IRegion,
  AccountService,
  FirewallLabels,
  MapEditor,
  AccountSelectInput,
} from '@spinnaker/core';

import { DockerImageAndTagSelector, DockerImageUtils, IDockerImageAndTagChanges } from '@spinnaker/docker';

import { TitusSecurityGroupPicker } from './TitusSecurityGroupPicker';

export interface ITitusRunJobStageConfigState {
  credentials: string[];
  regions: IRegion[];
  loaded: boolean;
}

export class TitusRunJobStageConfig extends React.Component<IStageConfigProps, ITitusRunJobStageConfigState> {
  private credentialsKeyedByAccount: IAggregatedAccounts = {};

  public state: ITitusRunJobStageConfigState = {
    credentials: [],
    regions: [],
    loaded: false,
  };

  public constructor(props: IStageConfigProps) {
    super(props);
    const { application, stage } = props;
    stage.cluster = stage.cluster || {};
    stage.waitForCompletion = stage.waitForCompletion === undefined ? true : stage.waitForCompletion;

    if (stage.cluster.imageId && !stage.cluster.imageId.includes('${')) {
      Object.assign(stage, DockerImageUtils.splitImageId(stage.cluster.imageId));
    }

    if (!stage.credentials && application.defaultCredentials.titus) {
      stage.credentials = application.defaultCredentials.titus;
    }

    if (!stage.cluster.capacity) {
      stage.cluster.capacity = {
        min: 1,
        max: 1,
        desired: 1,
      };
    }

    const clusterDefaults = {
      application: application.name,
      env: {},
      resources: {
        cpu: 1,
        disk: 10000,
        gpu: 0,
        memory: 512,
        networkMbps: 128,
      },
      retries: 0,
      runtimeLimitSecs: 3600,
      securityGroups: [] as string[],
    };
    defaultsDeep(stage.cluster, clusterDefaults);

    stage.cloudProvider = stage.cloudProvider || 'titus';
    stage.deferredInitialization = true;
  }

  private setRegistry(account: string) {
    if (account) {
      this.props.stage.registry = this.credentialsKeyedByAccount[account].registry;
    }
  }

  private updateRegions(account: string) {
    let regions: IRegion[];
    if (account) {
      regions = this.credentialsKeyedByAccount[account].regions;
      if (regions.map(r => r.name).every(r => r !== this.props.stage.cluster.region)) {
        delete this.props.stage.cluster.region;
        this.props.stageFieldUpdated();
      }
    } else {
      regions = [];
    }
    this.setState({ regions });
  }

  private accountChanged = (account: string) => {
    set(this.props.stage, 'account', account);
    this.stageFieldChanged('credentials', account);
    this.setRegistry(account);
    this.updateRegions(account);
  };

  private dockerChanged = (changes: IDockerImageAndTagChanges) => {
    // Temporary until stage config section is no longer angular
    const { imageId, ...rest } = changes;
    Object.assign(this.props.stage, rest);
    if (imageId) {
      this.props.stage.cluster.imageId = imageId;
    } else {
      delete this.props.stage.cluster.imageId;
    }
    this.props.stageFieldUpdated();
    this.forceUpdate();
  };

  private stageFieldChanged = (fieldIndex: string, value: any) => {
    set(this.props.stage, fieldIndex, value);
    this.props.stageFieldUpdated();
    this.forceUpdate();
  };

  public componentDidMount() {
    const { stage } = this.props;
    AccountService.getCredentialsKeyedByAccount('titus').then(credentialsKeyedByAccount => {
      this.credentialsKeyedByAccount = credentialsKeyedByAccount;
      const credentials = Object.keys(credentialsKeyedByAccount);
      stage.credentials = stage.credentials || credentials[0];

      this.setRegistry(stage.credentials);
      this.updateRegions(stage.credentials);
      this.setState({ credentials, loaded: true });
    });
  }

  private mapChanged = (key: string, values: { [key: string]: string }) => {
    this.stageFieldChanged(key, values);
  };

  private groupsChanged = (groups: string[]) => {
    this.stageFieldChanged('cluster.securityGroups', groups);
    this.forceUpdate();
  };

  public render() {
    const { stage } = this.props;
    const { credentials, loaded, regions } = this.state;
    const awsAccount = (this.credentialsKeyedByAccount[stage.credentials] || { awsAccount: '' }).awsAccount;

    return (
      <div className="form-horizontal">
        <div className="form-group">
          <label className="col-md-3 sm-label-right">
            <span className="label-text">Account</span>
          </label>
          <div className="col-md-5">
            <AccountSelectInput
              value={stage.credentials}
              onChange={evt => this.accountChanged(evt.target.value)}
              accounts={credentials}
              provider="titus"
            />
            {stage.credentials !== undefined && (
              <div className="small">
                Uses resources from the Amazon account <AccountTag account={awsAccount} />
              </div>
            )}
          </div>
        </div>

        <RegionSelectField
          labelColumns={3}
          fieldColumns={5}
          component={stage.cluster}
          field="region"
          account={stage.credentials}
          regions={regions}
          onChange={region => this.stageFieldChanged('region', region)}
        />

        <DockerImageAndTagSelector
          specifyTagByRegex={false}
          account={stage.credentials}
          digest={stage.digest}
          imageId={stage.cluster.imageId}
          organization={stage.organization}
          registry={stage.registry}
          repository={stage.repository}
          tag={stage.tag}
          showRegistry={false}
          onChange={this.dockerChanged}
          deferInitialization={stage.deferredInitialization}
          labelClass="col-md-2 col-md-offset-1 sm-label-right"
          fieldClass="col-md-6"
        />

        <StageConfigField label="CPU(s)">
          <input
            type="number"
            className="form-control input-sm"
            value={stage.cluster.resources.cpu}
            onChange={e => this.stageFieldChanged('cluster.resources.cpu', e.target.value)}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Memory (MB)">
          <input
            type="number"
            className="form-control input-sm"
            onChange={e => this.stageFieldChanged('cluster.resources.memory', e.target.value)}
            value={stage.cluster.resources.memory}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Disk (MB)">
          <input
            type="number"
            className="form-control input-sm"
            onChange={e => this.stageFieldChanged('cluster.resources.disk', e.target.value)}
            value={stage.cluster.resources.disk}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Network (Mbps)" helpKey="titus.deploy.network">
          <input
            type="number"
            className="form-control input-sm"
            onChange={e => this.stageFieldChanged('cluster.resources.networkMbps', e.target.value)}
            value={stage.cluster.resources.networkMbps}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="GPU(s)" helpKey="titus.deploy.gpu">
          <input
            type="number"
            className="form-control input-sm"
            onChange={e => this.stageFieldChanged('cluster.resources.gpu', e.target.value)}
            value={stage.cluster.resources.gpu}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Entrypoint">
          <input
            type="text"
            className="form-control input-sm"
            value={stage.cluster.entryPoint}
            onChange={e => this.stageFieldChanged('cluster.entryPoint', e.target.value)}
          />
        </StageConfigField>

        <StageConfigField label="Runtime Limit (Seconds)" helpKey="titus.deploy.runtimeLimitSecs">
          <input
            type="number"
            className="form-control input-sm"
            value={stage.cluster.runtimeLimitSecs}
            onChange={e => this.stageFieldChanged('cluster.runtimeLimitSecs', e.target.value)}
            min="1"
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Retries" helpKey="titus.deploy.retries">
          <input
            type="number"
            className="form-control input-sm"
            onChange={e => this.stageFieldChanged('cluster.retries', e.target.value)}
            value={stage.cluster.retries}
            min="0"
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Property File" helpKey="titus.deploy.propertyFile">
          <input
            type="text"
            className="form-control input-sm"
            onChange={e => this.stageFieldChanged('propertyFile', e.target.value)}
            value={stage.propertyFile}
          />
        </StageConfigField>

        <div className="form-group">
          <div className="col-md-9 col-md-offset-1">
            <div className="checkbox">
              <label>
                <input
                  type="checkbox"
                  checked={stage.showAdvancedOptions}
                  onChange={e => this.stageFieldChanged('showAdvancedOptions', e.target.checked)}
                />
                <strong>Show Advanced Options</strong>
              </label>
            </div>
          </div>
        </div>

        <div className={`${stage.showAdvancedOptions === true ? 'collapse.in' : 'collapse'}`}>
          <div className="form-group">
            <label className="col-md-3 sm-label-right">
              <span className="label-text">IAM Instance Profile (optional)</span>
              <HelpField id="titus.deploy.iamProfile" />
            </label>
            <div className="col-md-4">
              <input
                type="text"
                className="form-control input-sm"
                value={stage.cluster.iamProfile}
                onChange={e => this.stageFieldChanged('cluster.iamProfile', e.target.value)}
              />
            </div>
            <div className="col-md-1 small" style={{ whiteSpace: 'nowrap', paddingLeft: '0px', paddingTop: '7px' }}>
              in <AccountTag account={awsAccount} />
            </div>
          </div>

          <StageConfigField label="Capacity Group" fieldColumns={4} helpKey="titus.job.capacityGroup">
            <input
              type="text"
              className="form-control input-sm"
              value={stage.cluster.capacityGroup || ''}
              onChange={e => this.stageFieldChanged('cluster.capacityGroup', e.target.value)}
            />
          </StageConfigField>

          <StageConfigField label={FirewallLabels.get('Firewalls')} helpKey="titus.job.securityGroups">
            {(!stage.credentials || !stage.cluster.region) && (
              <div>Account and region must be selected before {FirewallLabels.get('firewalls')} can be added</div>
            )}
            {loaded &&
              stage.credentials &&
              stage.cluster.region && (
                <TitusSecurityGroupPicker
                  account={stage.credentials}
                  region={stage.cluster.region}
                  command={stage}
                  amazonAccount={awsAccount}
                  hideLabel={true}
                  groupsToEdit={stage.cluster.securityGroups}
                  onChange={this.groupsChanged}
                />
              )}
          </StageConfigField>

          <StageConfigField label="Environment Variables (optional)">
            <MapEditor
              model={stage.cluster.env}
              allowEmpty={true}
              onChange={(v: any) => this.mapChanged('cluster.env', v)}
            />
          </StageConfigField>
        </div>

        <StageConfigField label="Wait for results" helpKey="titus.job.waitForCompletion">
          <input
            type="checkbox"
            className="input-sm"
            name="waitForCompletion"
            checked={stage.waitForCompletion}
            onChange={e => this.stageFieldChanged('waitForCompletion', e.target.checked)}
          />
        </StageConfigField>
      </div>
    );
  }
}
