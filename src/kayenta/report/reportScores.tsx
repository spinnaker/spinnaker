import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import {
  ICanaryJudgeGroupScore,
  ICanaryJudgeScore
} from '../domain/ICanaryJudgeResult';
import CanaryJudgeScore from './score';
import GroupScores from './groupScores';
import * as Creators from 'kayenta/actions/creators';
import { judgeResultSelector } from '../selectors/index';

interface IReportScoresStateProps {
  groups: ICanaryJudgeGroupScore[];
  score: ICanaryJudgeScore;
}

interface IReportScoresDispatchProps {
  clearSelectedGroup: () => void;
}

/*
* Layout for the report scores.
* */
const ReportScores = ({ groups, score, clearSelectedGroup }: IReportScoresStateProps & IReportScoresDispatchProps) => (
  <section className="horizontal container">
    <CanaryJudgeScore score={score} onClick={clearSelectedGroup} className="flex-1"/>
    <GroupScores groups={groups} className="flex-6"/>
  </section>
);

const mapStateToProps = (state: ICanaryState): IReportScoresStateProps => ({
  groups: judgeResultSelector(state).groupScores,
  score: judgeResultSelector(state).score,
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IReportScoresDispatchProps => ({
  clearSelectedGroup: () => dispatch(Creators.selectReportMetricGroup({ group: null })),
});

export default connect(mapStateToProps, mapDispatchToProps)(ReportScores);
