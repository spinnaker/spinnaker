import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import * as Creators from '../actions/creators';
import ReportDetailLoadStates from './loadStates';
import { ICanaryState } from '../reducers/index';

interface IReportLoaderStateParamsProps {
  reportIdStream: Observable<IReportLoaderStateParams>;
}

interface IReportLoaderDispatchProps {
  loadReport: (stateParams: IReportLoaderStateParams) => void;
}

interface IReportLoaderStateParams {
  id: string;
}

/*
 * Top-level .reportDetail state component.
 * Loads report details on changes to /report/:id path parameter, renders load states.
 */
class ReportDetailLoader extends React.Component<IReportLoaderDispatchProps & IReportLoaderStateParamsProps> {
  private subscription: Subscription;

  constructor({ reportIdStream, loadReport }: IReportLoaderDispatchProps & IReportLoaderStateParamsProps) {
    super();
    this.subscription = reportIdStream.subscribe(loadReport);
  }

  public componentWillUnmount(): void {
    this.subscription.unsubscribe();
  }

  public render() {
    return <ReportDetailLoadStates/>;
  }
}


const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IReportLoaderDispatchProps => ({
  loadReport: (stateParams: IReportLoaderStateParams) =>
    dispatch(Creators.loadReportRequest({ id: stateParams.id })),
});

export default connect(null, mapDispatchToProps)(ReportDetailLoader);
