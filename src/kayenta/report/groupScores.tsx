import * as React from 'react';
import * as classNames from 'classnames';
import { connect, Dispatch } from 'react-redux';

import { ICanaryJudgeGroupScore } from '../domain/ICanaryJudgeResult';
import GroupScore from './groupScore';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryState } from '../reducers/index';

export interface IGroupScoresOwnProps {
  groups: ICanaryJudgeGroupScore[];
  className: string;
}

interface IGroupScoresDispatchProps {
  select: (event: any) => void;
}

/*
* Renders list of group scores.
* */
const GroupScores = ({ groups, className, select }: IGroupScoresOwnProps & IGroupScoresDispatchProps) => (
  <ul className={classNames('list-unstyled', 'list-inline', className)}>
    {groups.map(g => (
      <li key={g.name}>
        <GroupScore
          onClick={select}
          group={g}
        />
      </li>
    ))}
  </ul>
);

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IGroupScoresOwnProps,
): IGroupScoresOwnProps & IGroupScoresDispatchProps => ({
  select: (event: any) =>
    dispatch(Creators.selectResultMetricGroup({ group: event.target.dataset.group })),
  ...ownProps,
});

export default connect(null, mapDispatchToProps)(GroupScores);
