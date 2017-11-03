import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { sortBy } from 'lodash';

import { ICanaryState } from '../reducers/index';
import {
  ICanaryJudgeGroupScore,
  ICanaryJudgeScore
} from '../domain/ICanaryJudgeResult';
import CanaryJudgeScore from './score';
import GroupScores from './groupScores';
import * as Creators from 'kayenta/actions/creators';
import {
  judgeResultSelector,
  serializedGroupWeightsSelector
} from '../selectors/index';

import './reportScores.less';

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
  <section className="horizontal report-scores">
    <CanaryJudgeScore score={score} onClick={clearSelectedGroup} className="flex-1"/>
    <GroupScores groups={groups} className="flex-12"/>
  </section>
);

const mapStateToProps = (state: ICanaryState): IReportScoresStateProps => ({
  groups: sortBy(
    judgeResultSelector(state).groupScores,
    // Sort by group weight, then by name.
    [
      (group: ICanaryJudgeGroupScore) => -serializedGroupWeightsSelector(state)[group.name],
      'name',
    ]
  ),
  score: judgeResultSelector(state).score,
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IReportScoresDispatchProps => ({
  clearSelectedGroup: () => dispatch(Creators.selectReportMetricGroup({ group: null })),
});

export default connect(mapStateToProps, mapDispatchToProps)(ReportScores);
