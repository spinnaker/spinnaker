import { Field, FormikProps } from 'formik';
import React from 'react';

import { IServerGroupCommand, IWizardPageComponent } from '@spinnaker/core';

import { CapacitySelector } from '../capacity/CapacitySelector';
import { MinMaxDesired } from '../capacity/MinMaxDesired';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupCapacityProps {
  formik: FormikProps<IServerGroupCommand>;
}

export class ServerGroupCapacity
  extends React.Component<IServerGroupCapacityProps>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  public validate(values: IServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

    if (values.capacity.min < 0 || values.capacity.max < 0 || values.capacity.desired < 0) {
      errors.capacity = 'Capacity min, max, and desired all have to be non-negative values.';
    }

    const amazonValues = values as IAmazonServerGroupCommand;
    if (
      amazonValues.targetHealthyDeployPercentage === undefined ||
      amazonValues.targetHealthyDeployPercentage === null
    ) {
      errors.targetHealthyDeployPercentage = 'Target Healthy Deploy Percentage required.';
    }
    return errors;
  }

  public render() {
    const { setFieldValue, values } = this.props.formik;

    return (
      <div className="container-fluid form-horizontal">
        <div className="row">
          <div className="col-md-12">
            <CapacitySelector command={values} setFieldValue={setFieldValue} MinMaxDesired={MinMaxDesired} />
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
