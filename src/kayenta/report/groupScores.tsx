import * as React from 'react';
import * as classNames from 'classnames';

import { ICanaryJudgeGroupScore } from '../domain/ICanaryJudgeResult';
import GroupScore from './groupScore';

interface IGroupScoresProps {
  groups: ICanaryJudgeGroupScore[];
  className: string;
}

/*
* Renders list of group scores.
* */
export default ({ groups, className }: IGroupScoresProps) => (
  <ul className={classNames('list-unstyled', 'list-inline', className)}>
    {groups.map(g => (<li key={g.name}><GroupScore group={g}/></li>))}
  </ul>
);
