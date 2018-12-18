import * as React from 'react';

import {
  AccountService,
  FormikFormField,
  IAccount,
  IAccountDetails,
  IRegion,
  IWizardPageProps,
  ReactSelectInput,
} from '@spinnaker/core';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryPackageSource,
} from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';
import { ICloudFoundryCluster, ICloudFoundryServerGroup } from 'cloudfoundry/domain';
import { CloudFoundryImageReader } from 'cloudfoundry/image/image.reader.cf';
import { FormikProps } from 'formik';

export interface IArtifactPackageProps extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
  formik: FormikProps<any>;
}

export interface IArtifactPackageState {
  clusters: ICloudFoundryCluster[];
  regions: IRegion[];
  filteredClusters: ICloudFoundryCluster[];
  serverGroups: ICloudFoundryServerGroup[];
  allCloudFoundryCredentials: IAccountDetails[];
}

export class ArtifactPackage extends React.Component<IArtifactPackageProps, IArtifactPackageState> {
  public state: IArtifactPackageState = {
    clusters: [],
    filteredClusters: [],
    serverGroups: [],
    regions: [],
    allCloudFoundryCredentials: undefined,
  };

  public componentDidMount(): void {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ allCloudFoundryCredentials: accounts });
      this.updateRegionList();
    });
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
      (cluster: ICloudFoundryCluster) =>
        cluster.serverGroups.filter(serverGroup => serverGroup.region === region).length > 0,
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

  public render() {
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
  }
}
