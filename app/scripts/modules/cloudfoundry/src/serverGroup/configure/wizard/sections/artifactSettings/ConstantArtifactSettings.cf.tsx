import * as React from 'react';

import { ICloudFoundryCreateServerGroupCommand } from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';
import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';
import { FormikProps } from 'formik';

export interface ICloudFoundryCloneServerGroupProps {
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
  serverGroup: ICloudFoundryServerGroup;
}

export class CloudFoundryServerGroupConstantArtifactSettings extends React.Component<
  ICloudFoundryCloneServerGroupProps
> {
  constructor(props: ICloudFoundryCloneServerGroupProps) {
    super(props);
    const { serverGroup } = props;

    this.props.formik.values.source = {
      account: serverGroup.account,
      asgName: serverGroup.name,
      region: serverGroup.region,
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
}
