import * as React from 'react';

import { FormikErrors, FormikProps } from 'formik';
import Select, { Option } from 'react-select';

import {
  AccountService,
  HelpField,
  IAccountDetails,
  IWizardPageProps,
  IArtifactAccount,
  IRegion,
  wizardPage,
  RegionSelectField,
  NgReact,
  ValidationMessage,
} from '@spinnaker/core';

import {
  ICloudFoundryArtifactSource,
  ICloudFoundryBinarySource,
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryPackageSource,
  ICloudFoundryTriggerSource,
} from '../../serverGroupConfigurationModel.cf';
import { CloudFoundryImageReader } from 'cloudfoundry/image/image.reader.cf';
import { ICloudFoundryCluster, ICloudFoundryServerGroup } from 'cloudfoundry/domain';

export interface ICloudFoundryCreateServerGroupArtifactSettingsProps {
  artifactAccounts: IArtifactAccount[];
  artifact?: any;
}

export interface ICloudFoundryServerGroupArtifactSettingsState {
  clusters: ICloudFoundryCluster[];
  filteredClusters: ICloudFoundryCluster[];
  serverGroups: ICloudFoundryServerGroup[];
  regions: IRegion[];
  allCloudFoundryCredentials: IAccountDetails[];
}

function isArtifactSource(
  artifact: ICloudFoundryBinarySource,
): artifact is { type: string } & ICloudFoundryArtifactSource {
  return artifact.type === 'artifact';
}

function isPackageSource(
  artifact: ICloudFoundryBinarySource,
): artifact is { type: string } & ICloudFoundryPackageSource {
  return artifact.type === 'package';
}

function isTriggerSource(
  artifact: ICloudFoundryBinarySource,
): artifact is { type: string } & ICloudFoundryTriggerSource {
  return artifact.type === 'trigger';
}

class ArtifactSettingsImpl extends React.Component<
  ICloudFoundryCreateServerGroupArtifactSettingsProps &
    IWizardPageProps &
    FormikProps<ICloudFoundryCreateServerGroupCommand>,
  ICloudFoundryServerGroupArtifactSettingsState
> {
  public static get LABEL() {
    return 'Artifact';
  }

  constructor(
    props: ICloudFoundryCreateServerGroupArtifactSettingsProps &
      IWizardPageProps &
      FormikProps<ICloudFoundryCreateServerGroupCommand>,
  ) {
    super(props);
    this.state = {
      clusters: [],
      filteredClusters: [],
      serverGroups: [],
      regions: [],
      allCloudFoundryCredentials: undefined,
    };
  }

  private packageUpdated = (option: Option<string>): void => {
    const { artifact } = this.props.values;
    if (isPackageSource(artifact)) {
      artifact.serverGroupName = option.value;
      this.props.setFieldValue('artifact.serverGroupName', option.value);
    }
  };

  private packageAccountUpdated = (account: string): void => {
    const { artifact } = this.props.values;
    if (isPackageSource(artifact)) {
      artifact.account = account;
      this.props.setFieldValue('artifact.account', account);
      if (artifact.account) {
        AccountService.getRegionsForAccount(artifact.account).then(regions => {
          this.setState({ regions: regions });
        });
        CloudFoundryImageReader.findImages(artifact.account).then((clusters: ICloudFoundryCluster[]) => {
          const filteredClusters = clusters.filter(
            cluster => cluster.serverGroups.filter(serverGroup => serverGroup.region === artifact.region).length > 0,
          );
          const firstCluster = filteredClusters.length > 0 ? filteredClusters[0] : undefined;
          const serverGroups = firstCluster
            ? firstCluster.serverGroups.filter(serverGroup => serverGroup.region === artifact.region)
            : [];
          this.props.setFieldValue('artifact.cluster', firstCluster);
          this.setState({
            clusters: clusters,
            filteredClusters: filteredClusters,
            serverGroups: serverGroups,
          });
        });
      }
    }
  };

  private packageRegionUpdated = (region: string): void => {
    const { artifact } = this.props.values;
    const { clusters } = this.state;
    if (isPackageSource(artifact)) {
      artifact.region = region;
      this.props.setFieldValue('artifact.region', region);

      const filteredClusters = clusters.filter(
        cluster => cluster.serverGroups.filter(serverGroup => serverGroup.region === artifact.region).length > 0,
      );
      const serverGroups = artifact.cluster
        ? artifact.cluster.serverGroups.filter(serverGroup => serverGroup.region === artifact.region)
        : [];
      this.setState({
        filteredClusters: filteredClusters,
        serverGroups: serverGroups,
      });
    }
  };

  private clusterUpdated = (option: Option<string>): void => {
    const cluster = this.state.clusters.find(it => it.name === option.value);
    const { artifact } = this.props.values;
    if (isPackageSource(artifact)) {
      artifact.cluster = cluster;
      this.props.setFieldValue('artifact.cluster', cluster);
      const serverGroups = artifact.cluster
        ? artifact.cluster.serverGroups.filter(serverGroup => serverGroup.region === artifact.region)
        : [];
      this.setState({
        serverGroups: serverGroups,
      });
    }
  };

  private artifactTypeUpdated = (artifactType: string): void => {
    switch (artifactType) {
      case 'package':
        if (!this.state.allCloudFoundryCredentials) {
          AccountService.listAccounts('cloudfoundry').then(accounts => {
            this.setState({ allCloudFoundryCredentials: accounts });
          });
        }
        const { clusters } = this.state;
        this.props.values.artifact = {
          type: 'package',
          cluster: clusters[0],
          serverGroupName: '',
          region: '',
          account: this.props.values.credentials || '',
        };
        break;
      case 'artifact':
        this.props.values.artifact = { type: 'artifact', reference: '', account: '' };
        break;
      case 'trigger':
        this.props.values.artifact = { type: 'trigger', pattern: '', account: '' };
        break;
    }
    this.props.setFieldValue('artifact.type', artifactType);
  };

  private artifactAccountUpdated = (option: Option<string>): void => {
    const { artifact } = this.props.values;
    if (isArtifactSource(artifact) || isTriggerSource(artifact)) {
      artifact.account = option.value;
      this.props.setFieldValue('artifact.account', option.value);
    }
  };

  private artifactReferenceUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const reference = event.target.value;
    const { artifact } = this.props.values;
    if (isArtifactSource(artifact)) {
      artifact.reference = reference;
      this.props.setFieldValue('artifact.reference', reference);
    }
  };

  private artifactPatternUpdater = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const pattern = event.target.value;
    const { artifact } = this.props.values;
    if (isTriggerSource(artifact)) {
      artifact.pattern = pattern;
      this.props.setFieldValue('artifact.pattern', pattern);
    }
  };

  private getArtifactInput = (): JSX.Element => {
    const { artifact } = this.props.values;
    const { artifactAccounts, errors } = this.props;
    return (
      <div>
        <div className="form-group row">
          <label className="col-md-3 sm-label-right">Artifact Account</label>
          <div className="col-md-7">
            <Select
              options={
                artifactAccounts &&
                artifactAccounts.map((account: IArtifactAccount) => ({
                  label: account.name,
                  value: account.name,
                }))
              }
              clearable={false}
              value={isArtifactSource(artifact) && artifact.account}
              onChange={this.artifactAccountUpdated}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Reference</div>
          <div className="col-md-7">
            <input
              type="text"
              required={true}
              className="form-control input-sm"
              value={isArtifactSource(artifact) && artifact.reference}
              onChange={this.artifactReferenceUpdated}
            />
          </div>
        </div>
        {errors.artifact &&
          errors.artifact.account && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.artifact.account} type={'error'} />
            </div>
          )}
        {errors.artifact &&
          errors.artifact.reference && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.artifact.reference} type={'error'} />
            </div>
          )}
      </div>
    );
  };

  private getPackageInput = (): JSX.Element => {
    const { artifact } = this.props.values;
    const { regions, filteredClusters, serverGroups, allCloudFoundryCredentials } = this.state;
    const { errors } = this.props;
    const { AccountSelectField } = NgReact;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <AccountSelectField
              component={artifact}
              field="credentials"
              accounts={allCloudFoundryCredentials}
              provider="cloudfoundry"
              onChange={this.packageAccountUpdated}
            />
          </div>
        </div>
        <RegionSelectField
          labelColumns={3}
          component={artifact}
          field="region"
          account={artifact.account}
          onChange={this.packageRegionUpdated}
          regions={regions}
        />
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Cluster</div>
          <div className="col-md-7">
            <Select
              options={filteredClusters.map((c: ICloudFoundryCluster) => ({
                label: c.name,
                value: c.name,
              }))}
              clearable={false}
              value={isPackageSource(artifact) && artifact.cluster ? artifact.cluster.name : null}
              onChange={this.clusterUpdated}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Server Group</div>
          <div className="col-md-7">
            <Select
              options={
                isPackageSource(artifact) &&
                artifact.cluster &&
                serverGroups.map((sg: ICloudFoundryServerGroup) => ({
                  label: sg.name,
                  value: sg.name,
                }))
              }
              clearable={false}
              value={isPackageSource(artifact) && artifact.serverGroupName}
              onChange={this.packageUpdated}
            />
          </div>
        </div>
        {errors.artifact &&
          errors.artifact.account && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.artifact.account} type={'error'} />
            </div>
          )}
        {errors.artifact &&
          errors.artifact.region && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.artifact.region} type={'error'} />
            </div>
          )}
        {errors.artifact &&
          errors.artifact.serverGroupName && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.artifact.serverGroupName} type={'error'} />
            </div>
          )}
      </div>
    );
  };

  private getTriggerInput = (): JSX.Element => {
    const { artifact } = this.props.values;
    const { artifactAccounts, errors } = this.props;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Artifact Pattern</div>
          <div className="col-md-7">
            <input
              className="form-control input-sm no-spel"
              value={isTriggerSource(artifact) && artifact.pattern}
              onChange={this.artifactPatternUpdater}
            />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 sm-label-right">
            Artifact Account <HelpField id="cf.artifact.trigger.account" />
          </label>
          <div className="col-md-7">
            <Select
              options={
                artifactAccounts &&
                artifactAccounts.map((account: IArtifactAccount) => ({
                  label: account.name,
                  value: account.name,
                }))
              }
              clearable={true}
              value={isTriggerSource(artifact) && artifact.account}
              onChange={this.artifactAccountUpdated}
            />
          </div>
        </div>
        {errors.artifact &&
          errors.artifact.account && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.artifact.account} type={'error'} />
            </div>
          )}
        {errors.artifact &&
          errors.artifact.pattern && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.artifact.pattern} type={'error'} />
            </div>
          )}
      </div>
    );
  };

  public render(): JSX.Element {
    let artifactInput;
    const { artifact } = this.props.values;

    switch (artifact.type) {
      case 'package':
        artifactInput = this.getPackageInput();
        break;
      case 'trigger':
        artifactInput = this.getTriggerInput();
        break;
      default:
        artifactInput = this.getArtifactInput();
    }
    return (
      <div>
        <div className="form-group row">
          <label className="col-md-3 sm-label-right">Source Type</label>
          <div className="col-md-7">
            {this.props.values.viewState.mode === 'pipeline' && (
              <div className="radio radio-inline">
                <label>
                  <input
                    type="radio"
                    value="trigger"
                    checked={artifact.type === 'trigger'}
                    onChange={() => this.artifactTypeUpdated('trigger')}
                  />{' '}
                  Trigger
                </label>
              </div>
            )}
            <div className="radio radio-inline">
              <label>
                <input
                  type="radio"
                  value="artifact"
                  checked={artifact.type === 'artifact'}
                  onChange={() => this.artifactTypeUpdated('artifact')}
                />{' '}
                Artifact
              </label>
            </div>
            <div className="radio radio-inline">
              <label>
                <input
                  type="radio"
                  value="package"
                  checked={artifact.type === 'package'}
                  onChange={() => this.artifactTypeUpdated('package')}
                />{' '}
                Package <HelpField id="cf.artifact.package" />
              </label>
            </div>
          </div>
        </div>
        {artifactInput}
      </div>
    );
  }

  public validate(
    values: ICloudFoundryCreateServerGroupArtifactSettingsProps,
  ): FormikErrors<ICloudFoundryCreateServerGroupCommand> {
    const errors = {} as FormikErrors<ICloudFoundryCreateServerGroupCommand>;
    if (values.artifact.type === 'trigger') {
      if (!values.artifact.account) {
        errors.artifact = errors.artifact || {};
        errors.artifact.account = 'Account must be selected';
      }
      if (!values.artifact.pattern) {
        errors.artifact = errors.artifact || {};
        errors.artifact.pattern = 'Pattern must be specified';
      }
    }
    if (values.artifact.type === 'artifact') {
      if (!values.artifact.account) {
        errors.artifact = errors.artifact || {};
        errors.artifact.account = 'Account must be selected';
      }
      if (!values.artifact.reference) {
        errors.artifact = errors.artifact || {};
        errors.artifact.reference = 'Reference must be specified';
      }
    }
    if (values.artifact.type === 'package') {
      if (!values.artifact.account) {
        errors.artifact = errors.artifact || {};
        errors.artifact.account = 'Account must be selected';
      }
      if (!values.artifact.region) {
        errors.artifact = errors.artifact || {};
        errors.artifact.region = 'A region group must be specified';
      }
      if (!values.artifact.serverGroupName) {
        errors.artifact = errors.artifact || {};
        errors.artifact.serverGroupName = 'A server group must be specified';
      }
    }
    return errors;
  }
}

export const CloudFoundryServerGroupArtifactSettings = wizardPage<ICloudFoundryCreateServerGroupArtifactSettingsProps>(
  ArtifactSettingsImpl,
);
