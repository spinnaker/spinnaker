import { defaultsDeep, set } from 'lodash';
import React from 'react';

import {
  AccountSelectInput,
  AccountService,
  AccountTag,
  FirewallLabels,
  HelpField,
  IAggregatedAccounts,
  IRegion,
  IStageConfigProps,
  MapEditor,
  RegionSelectField,
  SpelNumberInput,
  SpinFormik,
  StageConfigField,
} from '@spinnaker/core';
import { DockerImageAndTagSelector, DockerImageUtils, IDockerImageAndTagChanges } from '@spinnaker/docker';

import { TitusSecurityGroupPicker } from './TitusSecurityGroupPicker';
import { IJobDisruptionBudget, ITitusResources } from '../../../domain';
import { IPv6CheckboxInput, JobDisruptionBudget } from '../../../serverGroup/configure/wizard/pages';
import { TitusProviderSettings } from '../../../titus.settings';

export interface ITitusRunJobStageConfigState {
  credentials: string[];
  regions: IRegion[];
  loaded: boolean;
}

interface IClusterDefaults {
  application: string;
  containerAttributes: object;
  env: object;
  labels: object;
  resources: ITitusResources;
  retries: number;
  runtimeLimitSecs: number;
  securityGroups: string[];
  iamProfile?: string;
}

export class TitusRunJobStageConfig extends React.Component<IStageConfigProps, ITitusRunJobStageConfigState> {
  private credentialsKeyedByAccount: IAggregatedAccounts = {};
  private defaultIamProfile = '';

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

    const defaultIamProfile = TitusProviderSettings.defaults.iamProfile || '{{application}}InstanceProfile';
    this.defaultIamProfile = defaultIamProfile.replace('{{application}}', application.name);

    const clusterDefaults: IClusterDefaults = {
      application: application.name,
      containerAttributes: {},
      env: {},
      labels: {},
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

    if (stage.isNew) {
      clusterDefaults.iamProfile = this.defaultIamProfile;
    }

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
      if (regions.map((r) => r.name).every((r) => r !== this.props.stage.cluster.region)) {
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

    const accountDetails = this.credentialsKeyedByAccount[account];
    const ipv6Default = accountDetails.environment === 'test' ? 'true' : 'false';
    this.associateIPv6AddressChanged(ipv6Default);
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
    AccountService.getCredentialsKeyedByAccount('titus').then((credentialsKeyedByAccount) => {
      this.credentialsKeyedByAccount = credentialsKeyedByAccount;
      const credentials = Object.keys(credentialsKeyedByAccount);
      stage.credentials = stage.credentials || credentials[0];

      this.setRegistry(stage.credentials);
      this.updateRegions(stage.credentials);
      this.setState({ credentials, loaded: true });

      const account = this.credentialsKeyedByAccount[stage.credentials];
      const defaultIPv6Address =
        account.environment === 'test' &&
        stage.cluster.containerAttributes['titusParameter.agent.assignIPv6Address'] === undefined
          ? 'true'
          : 'false';
      this.associateIPv6AddressChanged(defaultIPv6Address);
    });
  }

  private mapChanged = (key: string, values: { [key: string]: string }) => {
    this.stageFieldChanged(key, values);
  };

  private groupsChanged = (groups: string[]) => {
    this.stageFieldChanged('cluster.securityGroups', groups);
    this.forceUpdate();
  };

  private associateIPv6AddressChanged = (value: string) => {
    const oldAttributes = this.props.stage.cluster.containerAttributes || {};
    const updatedAttributes = {
      ...oldAttributes,
      'titusParameter.agent.assignIPv6Address': value,
    };
    this.mapChanged('cluster.containerAttributes', updatedAttributes);
  };

  public disruptionBudgetChanged = (values: IJobDisruptionBudget) => {
    const { stage, stageFieldUpdated } = this.props;
    stage.cluster.disruptionBudget = values;
    stageFieldUpdated();
  };

  public render() {
    const { application, stage } = this.props;
    const { credentials, loaded, regions } = this.state;
    const awsAccount = (this.credentialsKeyedByAccount[stage.credentials] || { awsAccount: '' }).awsAccount;

    const entryPointList = stage.cluster.entryPointList?.length
      ? stage.cluster.entryPointList.join(',')
      : stage.cluster.entryPoint;
    const cmdList = stage.cluster.cmdList?.length ? stage.cluster.cmdList.join(',') : stage.cluster.cmd;

    return (
      <div className="form-horizontal">
        <div className="form-group">
          <label className="col-md-3 sm-label-right">
            <span className="label-text">Account</span>
          </label>
          <div className="col-md-5">
            <AccountSelectInput
              value={stage.credentials}
              onChange={(evt: any) => this.accountChanged(evt.target.value)}
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
          onChange={(region) => this.stageFieldChanged('region', region)}
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
        />

        <StageConfigField label="CPU(s)">
          <SpelNumberInput
            value={stage.cluster.resources.cpu}
            onChange={(value) => this.stageFieldChanged('cluster.resources.cpu', value)}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Memory (MB)">
          <SpelNumberInput
            onChange={(value) => this.stageFieldChanged('cluster.resources.memory', value)}
            value={stage.cluster.resources.memory}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Disk (MB)">
          <SpelNumberInput
            onChange={(value) => this.stageFieldChanged('cluster.resources.disk', value)}
            value={stage.cluster.resources.disk}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Network (Mbps)" helpKey="titus.deploy.network">
          <SpelNumberInput
            onChange={(value) => this.stageFieldChanged('cluster.resources.networkMbps', value)}
            value={stage.cluster.resources.networkMbps}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="GPU(s)" helpKey="titus.deploy.gpu">
          <SpelNumberInput
            onChange={(value) => this.stageFieldChanged('cluster.resources.gpu', value)}
            value={stage.cluster.resources.gpu}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Entrypoint(s)" helpKey="titus.deploy.entrypoint">
          <input
            type="text"
            className="form-control input-sm"
            value={entryPointList}
            onChange={(e) => this.stageFieldChanged('cluster.entryPointList', e.target.value.split(','))}
          />
        </StageConfigField>

        <StageConfigField label="Command(s)" helpKey="titus.deploy.command">
          <input
            type="text"
            className="form-control input-sm"
            value={cmdList}
            onChange={(e) => this.stageFieldChanged('cluster.cmdList', e.target.value.split(','))}
          />
        </StageConfigField>

        <StageConfigField label="Runtime Limit (Seconds)" helpKey="titus.deploy.runtimeLimitSecs">
          <SpelNumberInput
            value={stage.cluster.runtimeLimitSecs}
            onChange={(value) => this.stageFieldChanged('cluster.runtimeLimitSecs', value)}
            min={1}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Retries" helpKey="titus.deploy.retries">
          <SpelNumberInput
            onChange={(value) => this.stageFieldChanged('cluster.retries', value)}
            value={stage.cluster.retries}
            min={0}
            required={true}
          />
        </StageConfigField>

        <StageConfigField label="Property File" helpKey="titus.deploy.propertyFile">
          <input
            type="text"
            className="form-control input-sm"
            onChange={(e) => this.stageFieldChanged('propertyFile', e.target.value)}
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
                  onChange={(e) => this.stageFieldChanged('showAdvancedOptions', e.target.checked)}
                />
                <strong>Show Advanced Options</strong>
              </label>
            </div>
          </div>
        </div>

        <div className={`${stage.showAdvancedOptions === true ? 'collapse.in' : 'collapse'}`}>
          <div className="form-group">
            <label className="col-md-3 sm-label-right">
              <span className="label-text">IAM Instance Profile</span> <HelpField id="titus.deploy.iamProfile" />
            </label>
            <div className="col-md-4">
              <input
                type="text"
                className="form-control input-sm"
                value={stage.cluster.iamProfile}
                placeholder={this.defaultIamProfile}
                required={true}
                onChange={(e) => this.stageFieldChanged('cluster.iamProfile', e.target.value)}
              />
              {!stage.isNew && !stage.cluster.iamProfile && (
                <a
                  className="small clickable"
                  onClick={() => this.stageFieldChanged('cluster.iamProfile', this.defaultIamProfile)}
                >
                  Use suggested default
                </a>
              )}
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
              onChange={(e) => this.stageFieldChanged('cluster.capacityGroup', e.target.value)}
            />
          </StageConfigField>

          <StageConfigField label={FirewallLabels.get('Firewalls')} helpKey="titus.job.securityGroups">
            {(!stage.credentials || !stage.cluster.region) && (
              <div>Account and region must be selected before {FirewallLabels.get('firewalls')} can be added</div>
            )}
            {loaded && stage.credentials && stage.cluster.region && (
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

          <StageConfigField label="Associate IPv6 Address (Recommended)" helpKey="serverGroup.ipv6">
            <IPv6CheckboxInput
              value={stage.cluster.containerAttributes['titusParameter.agent.assignIPv6Address']}
              onChange={(e) => this.associateIPv6AddressChanged(e.target.value)}
            />
          </StageConfigField>

          <StageConfigField label={'Disruption Budget'} helpKey="titus.disruptionbudget.description">
            <SpinFormik
              initialValues={stage.cluster}
              onSubmit={() => {}}
              render={(formik) => (
                <JobDisruptionBudget
                  formik={formik}
                  app={application}
                  runJobView={true}
                  onStageChange={this.disruptionBudgetChanged}
                />
              )}
            />
          </StageConfigField>

          <StageConfigField label="Job Attributes (optional)">
            <MapEditor
              model={stage.cluster.labels}
              allowEmpty={true}
              onChange={(v: any) => this.mapChanged('cluster.labels', v)}
            />
          </StageConfigField>
          <StageConfigField label="Container Attributes (optional)">
            <MapEditor
              model={stage.cluster.containerAttributes}
              allowEmpty={true}
              onChange={(v: any) => this.mapChanged('cluster.containerAttributes', v)}
            />
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
            onChange={(e) => this.stageFieldChanged('waitForCompletion', e.target.checked)}
          />
        </StageConfigField>
      </div>
    );
  }
}
