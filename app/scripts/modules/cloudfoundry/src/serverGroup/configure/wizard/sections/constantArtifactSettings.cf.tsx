import * as React from 'react';

import { IWizardPageProps, wizardPage } from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../../serverGroupConfigurationModel.cf';
import { ICloudFoundryServerGroupArtifactSettingsState } from 'cloudfoundry/serverGroup/configure/wizard/sections/artifactSettings.cf';
import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

export interface ICloudFoundryCloneServerGroupProps extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
  serverGroup: ICloudFoundryServerGroup;
}

class ArtifactSettingsImpl extends React.Component<
  ICloudFoundryCloneServerGroupProps,
  ICloudFoundryServerGroupArtifactSettingsState
> {
  public static get LABEL() {
    return 'Artifact';
  }

  public static validate(_values: any): any {
    return {};
  }
  constructor(props: ICloudFoundryCloneServerGroupProps) {
    super(props);
    const { serverGroup } = props;

    this.props.formik.values.artifact = {
      account: serverGroup.account,
      clusterName: serverGroup.cluster,
      region: serverGroup.region,
      serverGroupName: serverGroup.name,
      type: 'package',
    };
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;

    return (
      <div>
        <div className="form-group row">
          <label className="col-md-3 col-form-label sm-label-right">Account</label>
          <div className="col-md-7">
            <input className="form-control" type="text" id="cluster" value={serverGroup.account} readOnly={true} />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 col-form-label sm-label-right">Region</label>
          <div className="col-md-7">
            <input className="form-control" type="text" id="cluster" value={serverGroup.region} readOnly={true} />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 col-form-label sm-label-right">Cluster</label>
          <div className="col-md-7">
            <input className="form-control" type="text" id="cluster" value={serverGroup.cluster} readOnly={true} />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 col-form-label sm-label-right">Server Group</label>
          <div className="col-md-7">
            <input className="form-control" type="text" id="cluster" value={serverGroup.name} readOnly={true} />
          </div>
        </div>
      </div>
    );
  }

  public validate(_values: any): any {
    return {};
  }
}

export const CloudFoundryServerGroupConstantArtifactSettings = wizardPage(ArtifactSettingsImpl);
