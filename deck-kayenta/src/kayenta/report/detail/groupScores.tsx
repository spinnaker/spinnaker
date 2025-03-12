import classNames from 'classnames';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryJudgeGroupScore, ICanaryScoreThresholds, IGroupWeights } from 'kayenta/domain';
import { ICanaryState } from 'kayenta/reducers';
import { canaryExecutionRequestSelector, serializedGroupWeightsSelector } from 'kayenta/selectors';
import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import ClickableHeader from './clickableHeader';
import { mapGroupToColor } from './colors';

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
