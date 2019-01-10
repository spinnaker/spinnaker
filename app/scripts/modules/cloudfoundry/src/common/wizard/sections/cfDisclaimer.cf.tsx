import * as React from 'react';

export class CfDisclaimerPage extends React.Component {
  public render() {
    return (
      <div className="row">
        <div className="col-md-12">
          <div className="well">
            <p>Alpha implementation</p>
            <p>
              The implementation of the <code>Cloud Foundry Spinnaker</code> code is currently in
              <code>Alpha</code> state.
            </p>
            <p>
              As such it is possible that the behavior of the software will change in ways that are not backwards
              compatible with the current implementation.
            </p>
          </div>
        </div>
      </div>
    );
  }
}
