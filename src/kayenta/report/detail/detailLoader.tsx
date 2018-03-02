import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import * as Creators from 'kayenta/actions/creators';
import ReportDetailLoadStates from './loadStates';
import { ICanaryState } from 'kayenta/reducers';

interface IResultLoaderStateParamsProps {
  resultIdStream: Observable<IResultLoaderStateParams>;
}

interface IResultLoaderDispatchProps {
  loadResult: (stateParams: IResultLoaderStateParams) => void;
}

interface IResultLoaderStateParams {
  configId: string;
  runId: string;
}

/*
 * Top-level .reportDetail state component.
 * Loads result details on changes to /report/:configId/:runId path parameter, renders load states.
 */
class ResultDetailLoader extends React.Component<IResultLoaderDispatchProps & IResultLoaderStateParamsProps> {
  private subscription: Subscription;

  constructor(props: IResultLoaderDispatchProps & IResultLoaderStateParamsProps) {
    super(props);
    const { resultIdStream, loadResult } = props;
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
      configId: stateParams.configId,
      runId: stateParams.runId,
    })),
});

export default connect(null, mapDispatchToProps)(ResultDetailLoader);
