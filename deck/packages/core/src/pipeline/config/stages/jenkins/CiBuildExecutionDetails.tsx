import React from 'react';

import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';
import { robotToHuman } from '../../../../presentation/robotToHumanFilter/robotToHuman.filter';

interface ICiBuildExecutionDetailsProps extends IExecutionDetailsSectionProps {
  title: string;
  buildServiceLabel: string;
}

export function getCiBuildFailureMessage(stage: any): string {
  let failureMessage = stage.failureMessage;
  const buildInfo = ((stage.context || {}) as any).buildInfo || {};
  const testResults: Array<{ failCount: number }> = buildInfo.testResults || [];
  const failingTestCount = testResults
    .filter((results) => results.failCount > 0)
    .reduce((acc, results) => acc + results.failCount, 0);

  if (buildInfo.result === 'FAILURE') {
    failureMessage = 'Build failed.';
  }
  if (failingTestCount) {
    failureMessage = `${failingTestCount} test${failingTestCount > 1 ? 's' : ''} failed.`;
  }
  return failureMessage;
}

export function CiBuildExecutionDetails(props: ICiBuildExecutionDetailsProps) {
  const { stage } = props;
  const context = (stage.context || {}) as any;
  const buildInfo = context.buildInfo || {};
  const testResults = buildInfo.testResults || [];

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className={`col-md-${testResults.length ? 6 : 12}`}>
          <h5>{props.title}</h5>
          <dl className="dl-narrow dl-horizontal">
            <dt>{props.buildServiceLabel}</dt>
            <dd>{context.master}</dd>
            <dt>Job</dt>
            <dd>{context.job}</dd>
            <dt>Build</dt>
            <dd>
              {!context.buildInfo && context.buildNumber}
              {buildInfo.url && (
                <a href={buildInfo.url} target="_blank">
                  #{buildInfo.number}
                </a>
              )}
            </dd>
          </dl>
        </div>
        {!!testResults.length && (
          <div className="col-md-6">
            <h5>Test Results</h5>
            {testResults.map((result: any) => (
              <div className="row" key={result.urlName}>
                <div className="col-md-6">
                  <p>
                    <a target="_blank" href={`${buildInfo.url}${result.urlName}`} className="pad-left">
                      {robotToHuman(result.urlName)}
                    </a>
                  </p>
                </div>
                <div className="col-md-6">
                  <p className="test-results">
                    <span className="test-result-section">
                      {result.totalCount - result.failCount - result.skipCount} <span className="small fa fa-check" />
                    </span>
                    <span className="test-result-section">
                      {result.failCount} <span className="small glyphicon glyphicon-remove informational" />
                    </span>
                    {!!result.skipCount && (
                      <span className="test-result-section">
                        {result.skipCount} <span className="small glyphicon glyphicon-minus" />
                      </span>
                    )}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      {context.parameters && <KeyValueDetails title="Parameters" values={context.parameters} />}
      {context.propertyFileContents && <KeyValueDetails title="Property File" values={context.propertyFileContents} />}
      {stage.isFailed && <StageFailureMessage stage={stage} message={getCiBuildFailureMessage(stage)} />}
    </ExecutionDetailsSection>
  );
}

function KeyValueDetails({ title, values }: { title: string; values: { [key: string]: any } }) {
  return (
    <div className="row">
      <div className="col-md-12">
        <h5 style={{ marginBottom: 0, paddingBottom: 5 }}>{title}</h5>
        <dl>
          {Object.entries(values).map(([key, value]) => (
            <React.Fragment key={key}>
              <dt>{key}</dt>
              <dd>{value}</dd>
            </React.Fragment>
          ))}
        </dl>
      </div>
    </div>
  );
}
