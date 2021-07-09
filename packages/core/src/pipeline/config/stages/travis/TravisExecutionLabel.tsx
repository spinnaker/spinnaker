import React from 'react';

import { IExecutionStageSummary } from '../../../../domain';

export class TravisExecutionLabel extends React.Component<{ stage: IExecutionStageSummary }, any> {
  public render() {
    const buildInfo = this.props.stage.masterStage.context.buildInfo || {};
    const testResults = (buildInfo.testResults || []).map((result: any, index: number) => (
      <div key={index}>
        {'( '}
        <span className="tests-pass-count">{result.totalCount - result.failCount - result.skipCount}</span>
        {' / '}
        <span className="tests-fail-count">{result.failCount}</span>
        {result.skipCount > 0 && (
          <span>
            <span> / </span>
            <span className="tests-skip-count">{result.skipCount}</span>
          </span>
        )}
        {' )'}
      </div>
    ));

    return (
      <span className="stage-label">
        <span>{this.props.stage.name}</span>
        {buildInfo.number && <div>Build #{buildInfo.number}</div>}
        {testResults}
      </span>
    );
  }
}
