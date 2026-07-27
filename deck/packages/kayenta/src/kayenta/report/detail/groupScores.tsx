import classNames from 'classnames';
import * as React from 'react';
import type { Dispatch } from 'react-redux';
import { connect } from 'react-redux';

import * as Creators from '../../actions/creators';
import ClickableHeader from './clickableHeader';
import { mapGroupToColor } from './colors';
import type { ICanaryJudgeGroupScore, ICanaryScoreThresholds, IGroupWeights } from '../../domain';
import type { ICanaryState } from '../../reducers';
import { canaryExecutionRequestSelector, serializedGroupWeightsSelector } from '../../selectors';

export interface IGroupScoresOwnProps {
  groups: ICanaryJudgeGroupScore[];
  className?: string;
}

interface IGroupScoresStateProps {
  groupWeights: IGroupWeights;
  scoreThresholds: ICanaryScoreThresholds;
  selectedGroup: string;
}

interface IGroupScoresDispatchProps {
  select: (event: any) => void;
}

/*
 * Renders list of group scores.
 * */
const GroupScores = ({
  groups,
  groupWeights,
  scoreThresholds,
  className,
  select,
  selectedGroup,
}: IGroupScoresOwnProps & IGroupScoresDispatchProps & IGroupScoresStateProps) => (
  <section className={classNames('horizontal', className)}>
    {groups.map((g) => (
      <ClickableHeader
        key={g.name}
        style={{
          width: `${groupWeights[g.name]}%`, // TODO: at some point (around 4%), the group name doesn't fit.
          backgroundColor: mapGroupToColor(g, scoreThresholds),
        }}
        onClick={() => select(g.name)}
        label={g.name}
        className={classNames('report-score', { active: g.name === selectedGroup })}
      />
    ))}
  </section>
);

const mapStateToProps = (state: ICanaryState): IGroupScoresStateProps => ({
  selectedGroup: state.selectedRun.selectedGroup,
  groupWeights: serializedGroupWeightsSelector(state),
  scoreThresholds: canaryExecutionRequestSelector(state).thresholds,
});

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IGroupScoresOwnProps,
): IGroupScoresOwnProps & IGroupScoresDispatchProps => ({
  select: (group: string) => dispatch(Creators.selectReportMetricGroup({ group })),
  ...ownProps,
});

export default connect(mapStateToProps, mapDispatchToProps)(GroupScores);
