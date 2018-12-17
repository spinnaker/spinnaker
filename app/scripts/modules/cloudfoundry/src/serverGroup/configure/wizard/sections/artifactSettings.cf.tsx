import * as React from 'react';

import { Option } from 'react-select';

import {
  AccountService,
  FormikFormField,
  HelpField,
  IAccount,
  IAccountDetails,
  IWizardPageProps,
  IArtifactAccount,
  IRegion,
  ReactSelectInput,
  TextInput,
  wizardPage,
} from '@spinnaker/core';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryPackageSource,
} from '../../serverGroupConfigurationModel.cf';
import { CloudFoundryImageReader } from 'cloudfoundry/image/image.reader.cf';
import { ICloudFoundryCluster, ICloudFoundryServerGroup } from 'cloudfoundry/domain';
import { CloudFoundryRadioButtonInput } from 'cloudfoundry/presentation/forms/inputs/CloudFoundryRadioButtonInput';

export interface ICloudFoundryCreateServerGroupArtifactSettingsProps
  extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
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

class ArtifactSettingsImpl extends React.Component<
  ICloudFoundryCreateServerGroupArtifactSettingsProps,
  ICloudFoundryServerGroupArtifactSettingsState
> {
  public static get LABEL() {
    return 'Artifact';
  }

  public state: ICloudFoundryServerGroupArtifactSettingsState = {
    clusters: [],
    filteredClusters: [],
    serverGroups: [],
    regions: [],
    allCloudFoundryCredentials: undefined,
  };

  public componentDidMount(): void {
    if (this.props.formik.values.artifact.type === 'package') {
      AccountService.listAccounts('cloudfoundry').then(accounts => {
        this.setState({ allCloudFoundryCredentials: accounts });
        this.updateRegionList();
      });
    }
  }

  private accountChanged = (): void => {
    this.props.formik.setFieldValue('artifact.region', '');
    this.props.formik.setFieldValue('artifact.clusterName', '');
    this.props.formik.setFieldValue('artifact.serverGroupName', '');
    this.updateRegionList();
  };

  private updateRegionList = (): void => {
    const { artifact } = this.props.formik.values;
    const { account } = artifact;
    if (account) {
      AccountService.getRegionsForAccount(account).then(regions => {
        this.setState({ regions: regions });
      });
      CloudFoundryImageReader.findImages(account).then((clusters: ICloudFoundryCluster[]) => {
        this.setState({
          clusters,
          filteredClusters: [],
          serverGroups: [],
        });
        this.updateClusterList();
      });
    }
  };

  private regionChanged = (): void => {
    this.updateClusterList();
    this.props.formik.setFieldValue('artifact.clusterName', '');
    this.props.formik.setFieldValue('artifact.serverGroupName', '');
  };

  private updateClusterList = (): void => {
    const artifact = this.props.formik.values.artifact as { type: string } & ICloudFoundryPackageSource;
    const { region } = artifact;
    const { clusters } = this.state;
    const filteredClusters = clusters.filter(
      cluster => cluster.serverGroups.filter(serverGroup => serverGroup.region === region).length > 0,
    );
    this.setState({
      filteredClusters: filteredClusters,
      serverGroups: [],
    });
    this.updateServerGroupList();
  };

  private clusterChanged = (): void => {
    this.updateServerGroupList();
    this.props.formik.setFieldValue('artifact.serverGroupName', '');
  };

  private updateServerGroupList = (): void => {
    const artifact = this.props.formik.values.artifact as { type: string } & ICloudFoundryPackageSource;
    const { clusterName } = artifact;
    const cluster = this.state.clusters.find(it => it.name === clusterName);
    const serverGroups = cluster
      ? cluster.serverGroups.filter(serverGroup => serverGroup.region === artifact.region)
      : [];
    this.setState({
      serverGroups: serverGroups,
    });
  };

  private artifactTypeUpdated = (type: string): void => {
    switch (type) {
      case 'package':
        if (!this.state.allCloudFoundryCredentials) {
          AccountService.listAccounts('cloudfoundry').then(accounts => {
            this.setState({ allCloudFoundryCredentials: accounts });
          });
        }
        this.props.formik.setFieldValue('artifact.account', '');
        this.props.formik.setFieldValue('artifact.clusterName', '');
        this.props.formik.setFieldValue('artifact.region', '');
        this.props.formik.setFieldValue('artifact.serverGroupName', '');
        break;
      case 'artifact':
        this.props.formik.setFieldValue('artifact.account', '');
        this.props.formik.setFieldValue('artifact.reference', '');
        break;
      case 'trigger':
        this.props.formik.setFieldValue('artifact.account', '');
        this.props.formik.setFieldValue('artifact.pattern', '');
        break;
    }
    this.props.formik.setFieldValue('artifact.type', type);
  };

  private getArtifactInput = (): JSX.Element => {
    const { artifactAccounts } = this.props;
    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.account"
            label="Artifact Account"
            fastField={false}
            input={props => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={artifactAccounts && artifactAccounts.map((acc: IArtifactAccount) => acc.name)}
                clearable={false}
              />
            )}
            required={true}
          />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.reference"
            label="Reference"
            input={props => <TextInput {...props} />}
            required={true}
          />
        </div>
      </div>
    );
  };

  private getTriggerInput = (): JSX.Element => {
    const { artifactAccounts } = this.props;

    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <div>
            <FormikFormField
              name="artifact.pattern"
              label="Artifact Pattern"
              input={props => <TextInput {...props} />}
              required={true}
            />
          </div>
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.account"
            label="Artifact Account"
            fastField={false}
            input={props => (
              <ReactSelectInput
                {...props}
                inputClassName="cloudfoundry-react-select"
                stringOptions={artifactAccounts && artifactAccounts.map((acc: IArtifactAccount) => acc.name)}
                clearable={false}
              />
            )}
            help={<HelpField id="cf.artifact.trigger.account" />}
            required={true}
          />
        </div>
      </div>
    );
  };

  private getPackageInput = (): JSX.Element => {
    const { regions, filteredClusters, serverGroups, allCloudFoundryCredentials } = this.state;

    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.account"
            label="Account"
            fastField={false}
            input={props => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={
                  allCloudFoundryCredentials && allCloudFoundryCredentials.map((acc: IAccount) => acc.name)
                }
                clearable={false}
              />
            )}
            onChange={this.accountChanged}
            required={true}
          />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.region"
            label="Region"
            fastField={false}
            input={props => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={regions && regions.map((region: IRegion) => region.name)}
                clearable={false}
              />
            )}
            onChange={this.regionChanged}
            required={true}
          />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.clusterName"
            label="Cluster"
            fastField={false}
            input={props => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={filteredClusters && filteredClusters.map((c: ICloudFoundryCluster) => c.name)}
                clearable={false}
              />
            )}
            onChange={this.clusterChanged}
            required={true}
          />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="artifact.serverGroupName"
            label="Server Group"
            fastField={false}
            input={props => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={serverGroups && serverGroups.map((sg: ICloudFoundryServerGroup) => sg.name)}
                clearable={false}
              />
            )}
            required={true}
          />
        </div>
      </div>
    );
  };

  private getArtifactTypeOptions(): Array<Option<string>> {
    const { mode } = this.props.formik.values.viewState;
    if (mode === 'editPipeline' || mode === 'createPipeline') {
      return [
        { label: 'Artifact', value: 'artifact' },
        { label: 'Trigger', value: 'trigger' },
        { label: 'Package', value: 'package' },
      ];
    } else {
      return [{ label: 'Artifact', value: 'artifact' }, { label: 'Package', value: 'package' }];
    }
  }

  private getArtifactTypeInput(): JSX.Element {
    return (
      <div className="sp-margin-m-bottom">
        <FormikFormField
          name="artifact.type"
          label="Source Type"
          input={props => <CloudFoundryRadioButtonInput {...props} options={this.getArtifactTypeOptions()} />}
          onChange={this.artifactTypeUpdated}
        />
      </div>
    );
  }

  private getArtifactContentInput(): JSX.Element {
    switch (this.props.formik.values.artifact.type) {
      case 'package':
        return this.getPackageInput();
      case 'trigger':
        return this.getTriggerInput();
      default:
        return this.getArtifactInput();
    }
  }

  public render(): JSX.Element {
    return (
      <div className="form-group">
        {this.getArtifactTypeInput()}
        {this.getArtifactContentInput()}
      </div>
    );
  }

  public validate(_values: ICloudFoundryCreateServerGroupArtifactSettingsProps) {
    return {};
  }
}

export const CloudFoundryServerGroupArtifactSettings = wizardPage(ArtifactSettingsImpl);
