import * as React from 'react';

import ReportHeader from './header';
import ReportScores from './reportScores';
import MetricResults from './metricResults';

import './detail.less';

/*
* Layout for report detail view.
* */

export interface IDetailViewState {
  isExpanded: boolean;
}

export default class DetailView extends React.Component<any, IDetailViewState> {
  public state: IDetailViewState = { isExpanded: true };

  private toggleDetailHeader = (): void => {
    this.setState({ isExpanded: !this.state.isExpanded });
  };

  public render() {
    const { isExpanded } = this.state;

    const chevronStyle = {
      transform: this.state.isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
      transition: 'all ease 0.15s',
    };

    return (
      <>
        <div className="kayenta-overview-toggle" onClick={this.toggleDetailHeader}>
          {isExpanded ? <p>hide details</p> : <p>show details</p>}
          <span className="glyphicon glyphicon-chevron-right" style={chevronStyle} />
        </div>
        <div className="vertical flex-1">
          {isExpanded && <ReportHeader />}
          <ReportScores />
          <MetricResults />
        </div>
      </>
    );
  }
}
