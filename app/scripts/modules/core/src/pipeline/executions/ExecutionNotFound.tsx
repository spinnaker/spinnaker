import * as React from 'react';
import { ReactInjector } from 'core/reactShims';

export class ExecutionNotFound extends React.Component {
  public render() {
    const { params } = ReactInjector.$state;
    return (
      <div className="application">
        <div>
          <h2 className="text-center">Execution Not Found</h2>
          <p className="text-center" style={{ marginBottom: '20px' }}>
            Please check your URL - we can't find any data for <em>{params.executionId}</em>.
          </p>
        </div>
      </div>
    );
  }
}
