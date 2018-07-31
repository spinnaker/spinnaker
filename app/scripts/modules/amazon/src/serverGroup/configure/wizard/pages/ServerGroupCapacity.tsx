import * as React from 'react';
import { FormikProps, Field } from 'formik';

import { IWizardPageProps, wizardPage } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';
import { CapacitySelector } from '../capacity/CapacitySelector';
import { AmazonMinMaxDesired } from '../capacity/AmazonMinMaxDesired';

export interface IServerGroupCapacityProps {}

export interface IServerGroupCapacityState {}

class ServerGroupCapacityImpl extends React.Component<
  IServerGroupCapacityProps & IWizardPageProps & FormikProps<IAmazonServerGroupCommand>,
  IServerGroupCapacityState
> {
  public static LABEL = 'Capacity';

  public validate(values: IAmazonServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

    if (values.capacity.min < 0 || values.capacity.max < 0 || values.capacity.desired < 0) {
      errors.capacity = 'Capacity min, max, and desired all have to non-negative values.';
    }
    if (values.targetHealthyDeployPercentage === undefined || values.targetHealthyDeployPercentage === null) {
      errors.targetHealthyDeployPercentage = 'Target Healthy Deploy Percentage required.';
    }
    return errors;
  }

  public render() {
    const { setFieldValue, values } = this.props;
    return (
      <div className="clearfix">
        <div className="row">
          <div className="col-md-12">
            <CapacitySelector command={values} setFieldValue={setFieldValue} MinMaxDesired={AmazonMinMaxDesired} />
          </div>
        </div>
        <div className="row">
          <div className="col-md-12">
            <div className="form-group form-inline" style={{ marginTop: '20px' }}>
              <div className="col-md-12">
                Consider deployment successful when{' '}
                <Field
                  type="number"
                  name="targetHealthyDeployPercentage"
                  min="0"
                  max="100"
                  className="form-control input-sm inline-number"
                  required={true}
                />{' '}
                percent of instances are healthy.
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

export const ServerGroupCapacity = wizardPage<IServerGroupCapacityProps>(ServerGroupCapacityImpl);
