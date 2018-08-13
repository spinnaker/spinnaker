import * as React from 'react';

import { AccountService, Checklist } from '@spinnaker/core';

export interface IAvailabilityZoneSelectorProps {
  region: string;
  credentials: string;
  onChange: (zones: string[]) => void;
  allZones: string[];
  usePreferredZones?: boolean;
  selectedZones: string[];
}

export interface IAvailabilityZoneSelectorState {
  usePreferredZones: boolean;
}

export class AvailabilityZoneSelector extends React.Component<
  IAvailabilityZoneSelectorProps,
  IAvailabilityZoneSelectorState
> {
  constructor(props: IAvailabilityZoneSelectorProps) {
    super(props);
    this.state = {
      usePreferredZones: props.usePreferredZones || (!props.selectedZones || props.selectedZones.length === 0),
    };
  }

  public componentDidMount(): void {
    this.setDefaultZones(this.props);
  }

  public componentWillReceiveProps(nextProps: IAvailabilityZoneSelectorProps): void {
    if (nextProps.region !== this.props.region || nextProps.credentials !== this.props.credentials) {
      this.setDefaultZones(nextProps);
    }
  }

  private setDefaultZones(props: IAvailabilityZoneSelectorProps) {
    const { credentials, onChange, region } = props;

    AccountService.getAvailabilityZonesForAccountAndRegion('aws', credentials, region).then(
      preferredZones => preferredZones && onChange(preferredZones.slice()),
    );
  }

  private handleUsePreferredZonesChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const usePreferredZones = event.target.value === 'true';
    this.setState({ usePreferredZones });

    if (usePreferredZones) {
      this.setDefaultZones(this.props);
    }
  };

  private handleSelectedZonesChanged = (zones: Set<string>): void => {
    this.props.onChange([...zones]);
  };

  public render(): React.ReactElement<AvailabilityZoneSelector> {
    const { region, allZones, selectedZones } = this.props;
    const { usePreferredZones } = this.state;

    return (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Availability Zones</div>
        {region && (
          <div className="col-md-7">
            <p>Automatic Availability Zone Balancing:</p>
            <select
              className="form-control input-sm"
              value={usePreferredZones ? 'true' : 'false'}
              onChange={this.handleUsePreferredZonesChanged}
            >
              <option value="true">Enabled</option>
              <option value="false">Manual</option>
            </select>
            <br />
            {usePreferredZones && (
              <div>
                <p className="form-control-static">Server group will be available in:</p>
                <ul>{selectedZones.map(zone => <li key={zone}>{zone}</li>)}</ul>
              </div>
            )}
            {!usePreferredZones && (
              <div>
                Restrict server group instances to:
                <Checklist
                  items={new Set(allZones)}
                  checked={new Set(selectedZones)}
                  onChange={this.handleSelectedZonesChanged}
                />
              </div>
            )}
          </div>
        )}
      </div>
    );
  }
}
