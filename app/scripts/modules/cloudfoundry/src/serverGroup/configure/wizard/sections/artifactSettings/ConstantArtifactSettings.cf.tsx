import { FormikProps } from 'formik';
import React from 'react';

import { ICloudFoundryCreateServerGroupCommand, ICloudFoundrySource } from '../../../serverGroupConfigurationModel.cf';

export interface ICloudFoundryCloneServerGroupProps {
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
  source: ICloudFoundrySource;
}

export class CloudFoundryServerGroupConstantArtifactSettings extends React.Component<
  ICloudFoundryCloneServerGroupProps
> {
  constructor(props: ICloudFoundryCloneServerGroupProps) {
    super(props);
    const { source } = props;
    this.props.formik.setFieldValue('source', source);
  }

  public render(): JSX.Element {
    const { source } = this.props;

    return (
      <div>
        <div className="form-group row">
          <label className="col-md-3 col-form-label sm-label-right">Account</label>
          <div className="col-md-7">
            <input className="form-control" type="text" id="cluster" value={source.account} readOnly={true} />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 col-form-label sm-label-right">Region</label>
          <div className="col-md-7">
            <input className="form-control" type="text" id="cluster" value={source.region} readOnly={true} />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 col-form-label sm-label-right">Server Group</label>
          <div className="col-md-7">
            <input className="form-control" type="text" id="cluster" value={source.asgName} readOnly={true} />
          </div>
        </div>
      </div>
    );
  }
}
