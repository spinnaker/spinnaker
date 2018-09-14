import * as React from 'react';
import { FormikProps, Field } from 'formik';

import { IServerGroupCommand, IWizardPageProps, wizardPage } from '@spinnaker/core';

import { CapacitySelector } from '../capacity/CapacitySelector';
import { MinMaxDesired } from '../capacity/MinMaxDesired';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupCapacityProps {
  hideTargetHealthyDeployPercentage?: boolean;
}

class ServerGroupCapacityImpl extends React.Component<
  IWizardPageProps & IServerGroupCapacityProps & FormikProps<IServerGroupCommand>
> {
  public static LABEL = 'Capacity';

  public validate(values: IServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

    if (values.capacity.min < 0 || values.capacity.max < 0 || values.capacity.desired < 0) {
      errors.capacity = 'Capacity min, max, and desired all have to be non-negative values.';
    }
    if (!this.props.hideTargetHealthyDeployPercentage) {
      const amazonValues = values as IAmazonServerGroupCommand;
      if (
        amazonValues.targetHealthyDeployPercentage === undefined ||
        amazonValues.targetHealthyDeployPercentage === null
      ) {
        errors.targetHealthyDeployPercentage = 'Target Healthy Deploy Percentage required.';
      }
    }
    return errors;
  }

  public render() {
    const { setFieldValue, hideTargetHealthyDeployPercentage, values } = this.props;
    return (
      <div className="clearfix">
        <div className="row">
          <div className="col-md-12">
            <CapacitySelector command={values} setFieldValue={setFieldValue} MinMaxDesired={MinMaxDesired} />
          </div>
        </div>
        {!hideTargetHealthyDeployPercentage && (
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
        )}
      </div>
    );
  }
}

export const ServerGroupCapacity = wizardPage<IServerGroupCapacityProps>(ServerGroupCapacityImpl);
