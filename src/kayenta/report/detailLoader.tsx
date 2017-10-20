import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import * as Creators from '../actions/creators';
import ReportDetailLoadStates from './loadStates';
import { ICanaryState } from '../reducers/index';

interface IResultLoaderStateParamsProps {
  resultIdStream: Observable<IResultLoaderStateParams>;
}

interface IResultLoaderDispatchProps {
  loadResult: (stateParams: IResultLoaderStateParams) => void;
}

interface IResultLoaderStateParams {
  configName: string;
  runId: string;
}

/*
 * Top-level .reportDetail state component.
 * Loads result details on changes to /report/:configName/:runId path parameter, renders load states.
 */
class ResultDetailLoader extends React.Component<IResultLoaderDispatchProps & IResultLoaderStateParamsProps> {
  private subscription: Subscription;

  constructor({ resultIdStream, loadResult }: IResultLoaderDispatchProps & IResultLoaderStateParamsProps) {
    super();
    this.subscription = resultIdStream.subscribe(loadResult);
  }

  public componentWillUnmount(): void {
    this.subscription.unsubscribe();
  }

  public render() {
    return <ReportDetailLoadStates/>;
  }
}


const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IResultLoaderDispatchProps => ({
  loadResult: (stateParams: IResultLoaderStateParams) =>
    dispatch(Creators.loadRunRequest({
      configName: stateParams.configName,
      runId: stateParams.runId,
    })),
});

export default connect(null, mapDispatchToProps)(ResultDetailLoader);
