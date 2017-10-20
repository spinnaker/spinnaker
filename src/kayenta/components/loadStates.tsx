import * as React from 'react';

import { Spinner } from '@spinnaker/core';

import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';

export interface ILoadStatesProps {
  state: AsyncRequestState;
}

/*
* Builder for rendering load states. Defaults to standard spinner during
* `Requesting` state.
* */
export default class LoadStatesBuilder {
  private requesting: JSX.Element;
  private fulfilled: JSX.Element;
  private failed: JSX.Element;

  public onRequesting(requesting: JSX.Element): LoadStatesBuilder {
    this.requesting = requesting;
    return this;
  }

  public onFulfilled(fulfilled: JSX.Element): LoadStatesBuilder {
    this.fulfilled = fulfilled;
    return this;
  }

  public onFailed(failed: JSX.Element): LoadStatesBuilder {
    this.failed = failed;
    return this;
  }

  public build(): React.SFC<ILoadStatesProps> {
    return ({ state }: ILoadStatesProps) => {
      switch (state) {
        case AsyncRequestState.Requesting:
          return this.requesting || (
            <div className="horizontal center middle spinner-container">
              <Spinner/>
            </div>
          );
        case AsyncRequestState.Fulfilled:
          return this.fulfilled;
        case AsyncRequestState.Failed:
          return this.failed;
      }
    }
  }
}
