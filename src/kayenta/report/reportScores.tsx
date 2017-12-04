import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { sortBy } from 'lodash';
import * as classNames from 'classnames';

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
  selectedGroup: string;
}

interface IReportScoresDispatchProps {
  clearSelectedGroup: () => void;
}

/*
* Layout for the report scores.
* */
const ReportScores = ({ groups, score, selectedGroup, clearSelectedGroup }: IReportScoresStateProps & IReportScoresDispatchProps) => (
  <section className="horizontal report-scores">
    <CanaryJudgeScore
      score={score}
      onClick={clearSelectedGroup}
      className={classNames('flex-1', 'report-score', { active: !selectedGroup })}
    />
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
  selectedGroup: state.selectedRun.selectedGroup,
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IReportScoresDispatchProps => ({
  clearSelectedGroup: () => dispatch(Creators.selectReportMetricGroup({ group: null })),
});

export default connect(mapStateToProps, mapDispatchToProps)(ReportScores);
