import { PagerDutyWriter } from 'core/pagerDuty';
import { Tooltip } from 'core/presentation';
import React from 'react';

import { Application } from '../application.model';

export interface IPagerDutyButtonProps {
  app: Application;
}

export class PagerDutyButton extends React.Component<IPagerDutyButtonProps> {
  private pageApplicationOwner = (): void => {
    PagerDutyWriter.pageApplicationOwnerModal(this.props.app);
  };

  public render() {
    if (!this.props.app.attributes.pdApiKey) {
      return null;
    }
    return (
      <Tooltip value="Page application owner">
        <button className="btn btn-xs page-button btn-page-owner" onClick={this.pageApplicationOwner}>
          <i className="fa fa-phone" />
        </button>
      </Tooltip>
    );
  }
}
