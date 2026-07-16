import React from 'react';

import { AzureWizardPage } from './common';

export class ServerGroupCapacityAndZones extends AzureWizardPage {
  public validate(values: any): { [key: string]: any } {
    if (values.zonesEnabled && (!values.zones || values.zones.length === 0)) {
      return { zones: 'At least one zone is required when zones are enabled.' };
    }
    return {};
  }

  private capacityChanged = (capacity: string) => {
    const parsed = Number(capacity);
    this.props.formik.values.sku = { ...(this.props.formik.values.sku || {}), capacity: parsed };
    this.props.formik.setFieldValue('sku.capacity', parsed);
  };

  private zoneToggled = (zone: string, enabled: boolean) => {
    const current = this.props.formik.values.zones || [];
    const zones = enabled ? current.concat(zone) : current.filter((candidate: string) => candidate !== zone);
    this.props.formik.values.zones = zones;
    this.props.formik.setFieldValue('zones', zones);
  };

  private zonesEnabledChanged = (enabled: boolean) => {
    this.setField('zonesEnabled', enabled);
    if (enabled) {
      this.setField('enableInboundNAT', false);
    }
  };

  public render() {
    const { values } = this.props.formik;
    const zones = values.backingData?.filtered?.zones || [];
    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Capacity</div>
          <div className="col-md-3">
            <input
              className="form-control input-sm"
              min="0"
              onChange={(event) => this.capacityChanged(event.target.value)}
              type="number"
              value={values.sku?.capacity ?? 0}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Use Zones</div>
          <div className="col-md-7">
            <input
              checked={!!values.zonesEnabled}
              onChange={(event) => this.zonesEnabledChanged(event.target.checked)}
              type="checkbox"
            />
          </div>
        </div>
        {values.zonesEnabled && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Zones</div>
            <div className="col-md-7">
              {zones.map((zone: string) => (
                <label className="checkbox-inline" key={zone}>
                  <input
                    checked={(values.zones || []).includes(zone)}
                    onChange={(event) => this.zoneToggled(zone, event.target.checked)}
                    type="checkbox"
                  />
                  {zone}
                </label>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }
}
