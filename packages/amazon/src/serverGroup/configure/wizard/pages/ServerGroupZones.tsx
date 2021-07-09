import { FormikProps } from 'formik';
import React from 'react';

import { IWizardPageComponent } from '@spinnaker/core';

import { AvailabilityZoneSelector } from '../../../AvailabilityZoneSelector';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupZonesProps {
  formik: FormikProps<IAmazonServerGroupCommand>;
}

export class ServerGroupZones
  extends React.Component<IServerGroupZonesProps>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  public validate(values: IAmazonServerGroupCommand) {
    const errors = {} as any;

    if (!values.availabilityZones || values.availabilityZones.length === 0) {
      errors.availabilityZones = 'You must select at least one availability zone.';
    }
    return errors;
  }

  private handleAvailabilityZonesChanged = (zones: string[]): void => {
    const { values, setFieldValue } = this.props.formik;
    values.usePreferredZonesChanged(values);
    setFieldValue('availabilityZones', zones);
  };

  private rebalanceToggled = () => {
    const { values, setFieldValue } = this.props.formik;
    values.toggleSuspendedProcess(values, 'AZRebalance');
    setFieldValue('suspendedProcesses', values.suspendedProcesses);
    this.setState({});
  };

  public render() {
    const { values } = this.props.formik;
    return (
      <div className="container-fluid form-horizontal">
        <AvailabilityZoneSelector
          credentials={values.credentials}
          region={values.region}
          onChange={this.handleAvailabilityZonesChanged}
          selectedZones={values.availabilityZones}
          allZones={values.backingData.filtered.availabilityZones}
          usePreferredZones={values.viewState.usePreferredZones}
        />
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>AZ Rebalance</b>
          </div>
          <div className="col-md-7 checkbox">
            <label>
              <input
                type="checkbox"
                onChange={this.rebalanceToggled}
                checked={!values.processIsSuspended(values, 'AZRebalance')}
              />
              Keep instances evenly distributed across zones
            </label>
          </div>
        </div>
      </div>
    );
  }
}
