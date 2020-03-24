import React from 'react';
import { FormikProps } from 'formik';

import { Application, HelpField, Overridable } from '@spinnaker/core';
import { IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';

export interface DetailsFieldProps {
  app: Application;
  formik: FormikProps<IAmazonServerGroupCommand>;
}

@Overridable('aws.serverGroup.configure.detailsField')
export default class ServerGroupDetailsField extends React.Component<DetailsFieldProps> {
  private freeFormDetailsChanged = (freeFormDetails: string) => {
    const { setFieldValue, values } = this.props.formik;
    values.freeFormDetails = freeFormDetails; // have to do it here to make sure it's done before calling values.clusterChanged
    setFieldValue('freeFormDetails', freeFormDetails);
    values.clusterChanged(values);
  };

  render() {
    const { errors, values } = this.props.formik;
    return (
      <>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Detail <HelpField id="aws.serverGroup.detail" />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.freeFormDetails}
              onChange={e => this.freeFormDetailsChanged(e.target.value)}
            />
          </div>
        </div>
        {errors.freeFormDetails && (
          <div className="form-group row slide-in">
            <div className="col-sm-9 col-sm-offset-2 error-message">
              <span>{errors.freeFormDetails}</span>
            </div>
          </div>
        )}
      </>
    );
  }
}
