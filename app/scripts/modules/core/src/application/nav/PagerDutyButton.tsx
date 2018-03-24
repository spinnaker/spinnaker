import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation';

export interface IPagerDutyButtonProps {
  app: Application;
}

@BindAll()
export class PagerDutyButton extends React.Component<IPagerDutyButtonProps> {

  private pageApplicationOwner(): void {
    ReactInjector.pagerDutyWriter.pageApplicationOwnerModal(this.props.app);
  }

  public render() {
    if (!this.props.app.attributes.pdApiKey) {
      return null;
    }
    return (
      <Tooltip value="Page application owner">
        <button
          className="btn btn-xs page-button btn-page-owner"
          onClick={this.pageApplicationOwner}
        >
          <i className="fa fa-phone"/>
        </button>
      </Tooltip>
    );
  }
}
