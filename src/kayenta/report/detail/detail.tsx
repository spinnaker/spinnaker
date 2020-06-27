import * as React from 'react';

import ReportHeader from './header';
import ReportScores from './reportScores';
import MetricResults from './metricResults';

import './detail.less';
import ReportExplanation from './reportExplanation';

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

    return (
      <>
        <div className="vertical flex-1">
          {isExpanded && <ReportHeader />}
          <ReportExplanation />
          <ReportScores isExpanded={isExpanded} toggleHeader={this.toggleDetailHeader} />
          <MetricResults />
        </div>
      </>
    );
  }
}
