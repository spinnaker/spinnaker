import React from 'react';

import { PageModal } from './PageModal';
import { Application } from '../application';
import { IPagerDutyService } from './pagerDuty.read.service';

export interface IPageButtonProps {
  applications?: Application[];
  closeCallback: (succeeded: boolean) => void;
  disabled?: boolean;
  services: IPagerDutyService[];
  forceOpen?: boolean;
}

export interface IPageButtonState {
  showModal: boolean;
}

export class PageButton extends React.Component<IPageButtonProps, IPageButtonState> {
  constructor(props: IPageButtonProps) {
    super(props);

    this.state = {
      showModal: props.forceOpen || false,
    };
  }

  public componentWillReceiveProps(nextProps: IPageButtonProps): void {
    if (nextProps.forceOpen && !this.state.showModal) {
      this.setState({ showModal: true });
    }
  }

  private closeCallback = (succeeded: boolean) => {
    this.setState({ showModal: false });
    this.props.closeCallback(succeeded);
  };

  private sendPage = () => {
    this.setState({ showModal: true });
  };

  public render() {
    return (
      <button
        disabled={this.props.disabled}
        className="btn btn-sm btn-primary"
        style={{ marginRight: '5px' }}
        onClick={this.sendPage}
      >
        <span>Send Page</span>
        {this.state.showModal && (
          <PageModal
            show={this.state.showModal}
            closeCallback={this.closeCallback}
            applications={this.props.applications}
            services={this.props.services}
          />
        )}
      </button>
    );
  }
}
