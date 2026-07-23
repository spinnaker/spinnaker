import React from 'react';
import type { Subscription } from 'rxjs';

import { SETTINGS } from '../config/settings';
import { HelpField } from '../help/HelpField';
import type { IPagerDutyService } from './pagerDuty.read.service';
import { PagerDutyReader } from './pagerDuty.read.service';
import { ReactSelectInput } from '../presentation';
import type { IScheduler } from '../scheduler/SchedulerFactory';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

export interface IPagerDutySelectFieldProps {
  value: string;
  onChange: (integrationKey: string) => void;
}

interface IPagerDutySelectFieldState {
  services: IPagerDutyService[];
  servicesLoaded: boolean;
}

const HELP_CONTENTS = `<p>Make sure your service exists in Pager Duty and includes the "Generic API"
      integration.</p>
      <h5><b>Setting up a new integration</b></h5>
      <ol>
        <li>Find your service in Pager Duty</li>
        <li>Click "New Integration"</li>
        <li>Select "Use our API directly"</li>
        <li>Make sure to select "Events API v1" (Spinnaker is not compatible with v2)</li>
      </ol>
      <p><b>Note:</b> it can take up to five minutes for the service to appear in Spinnaker</p>`;

export class PagerDutySelectField extends React.Component<IPagerDutySelectFieldProps, IPagerDutySelectFieldState> {
  public state: IPagerDutySelectFieldState = { services: [], servicesLoaded: false };

  private readerSubscription: Subscription;
  private scheduler: IScheduler;
  private schedulerSubscription: Subscription;

  public componentDidMount(): void {
    this.scheduler = SchedulerFactory.createScheduler(10000);
    this.schedulerSubscription = this.scheduler.subscribe(this.loadServices);
    this.loadServices();
  }

  public componentWillUnmount(): void {
    this.readerSubscription?.unsubscribe();
    this.schedulerSubscription?.unsubscribe();
    this.scheduler?.unsubscribe();
  }

  private loadServices = (): void => {
    this.readerSubscription?.unsubscribe();
    this.readerSubscription = PagerDutyReader.listServices().subscribe((services) => {
      this.setState({
        services: services.filter((service) => Boolean(service.integration_key)),
        servicesLoaded: true,
      });
    });
  };

  public render(): React.ReactNode {
    const required = Boolean(SETTINGS.pagerDuty?.required);
    return (
      <div className="form-group row">
        <div className="col-sm-3 sm-label-right">
          PagerDuty{required ? ' *' : ''} <HelpField content={HELP_CONTENTS} />
        </div>
        <div className="col-sm-9">
          <ReactSelectInput
            inputClassName="form-control input-sm"
            isLoading={!this.state.servicesLoaded}
            mode="VIRTUALIZED"
            name="pdApiKey"
            options={this.state.services.map((service) => ({
              label: service.name,
              value: service.integration_key,
            }))}
            placeholder="Select a PagerDuty Service"
            required={required}
            value={this.props.value}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) => this.props.onChange(event.target.value)}
          />
        </div>
      </div>
    );
  }
}
