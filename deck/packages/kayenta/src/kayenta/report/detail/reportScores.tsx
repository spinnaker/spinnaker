import classNames from 'classnames';
import { sortBy } from 'lodash';
import * as React from 'react';
import type { Dispatch } from 'react-redux';
import { connect } from 'react-redux';

import * as Creators from '../../actions/creators';
import AllMetricResultsHeader from './allMetricResultsHeader';
import type { ICanaryJudgeGroupScore, ICanaryJudgeScore, ICanaryScoreThresholds } from '../../domain';
import GroupScores from './groupScores';
import type { ICanaryState } from '../../reducers';
import { judgeResultSelector, serializedGroupWeightsSelector } from '../../selectors';

import './reportScores.less';

interface IReportScoresStateProps {
  groups: ICanaryJudgeGroupScore[];
  score: ICanaryJudgeScore;
  scoreThresholds: ICanaryScoreThresholds;
  selectedGroup: string;
}

interface IReportScoresDispatchProps {
  clearSelectedGroup: () => void;
}

interface IReportScoreParentProps {
  isExpanded: boolean;
  toggleHeader: () => void;
}

/*
 * Layout for the report scores.
 * */
const ReportScores = ({
  groups,
  isExpanded,
  selectedGroup,
  clearSelectedGroup,
  score,
  scoreThresholds,
  toggleHeader,
}: IReportScoresStateProps & IReportScoresDispatchProps & IReportScoreParentProps) => {
  const chevronStyle = {
    transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
    transition: 'all ease 0.15s',
  };

  return (
    <section className="horizontal report-scores">
      <AllMetricResultsHeader
        onClick={clearSelectedGroup}
        score={score}
        scoreThresholds={scoreThresholds}
        className={classNames('flex-1', 'report-score', { active: !selectedGroup })}
      />
      <GroupScores groups={groups} className="flex-12" />
      <div className="kayenta-overview-toggle horizontal middle" onClick={() => toggleHeader()}>
        {isExpanded ? <p>hide details</p> : <p>show details</p>}
        <span className="glyphicon glyphicon-chevron-right" style={chevronStyle} />
      </div>
    </section>
  );
};

const mapStateToProps = (state: ICanaryState): IReportScoresStateProps => ({
  groups: sortBy(
    judgeResultSelector(state).groupScores,
    // Sort by group weight, then by name.
    [(group: ICanaryJudgeGroupScore) => -serializedGroupWeightsSelector(state)[group.name], 'name'],
  ),
  score: judgeResultSelector(state).score,
  scoreThresholds: state.selectedRun.run.canaryExecutionRequest.thresholds,
  selectedGroup: state.selectedRun.selectedGroup,
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IReportScoresDispatchProps => ({
  clearSelectedGroup: () => dispatch(Creators.selectReportMetricGroup({ group: null })),
});

export default connect(mapStateToProps, mapDispatchToProps)(ReportScores);
