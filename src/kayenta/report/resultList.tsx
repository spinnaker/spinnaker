import * as React from 'react';
import { connect } from 'react-redux';
import { UISref } from '@uirouter/react';

import { ICanaryState } from '../reducers/index';
import { ICanaryJudgeResultSummary } from '../domain/ICanaryJudgeResultSummary';

interface IResultListStateProps {
  summaries: ICanaryJudgeResultSummary[];
}

const ResultList = ({ summaries }: IResultListStateProps) => (
  <ul className="list-unstyled">
    {summaries.map(s => (
      <li key={s.name}>
        <UISref to="^.reportDetail" params={{id: s.name}}>
          <a className="clickable">{s.updatedTimestampIso}</a>
        </UISref>
      </li>
    ))}
  </ul>
);

const mapStateToProps = (state: ICanaryState) => ({
  summaries: [...state.data.resultSummaries].sort((a, b) => b.updatedTimestamp - a.updatedTimestamp),
});

export default connect(mapStateToProps)(ResultList);
