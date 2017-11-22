import * as React from 'react';
import * as classNames from 'classnames';

import { ICanaryJudgeGroupScore } from '../domain/ICanaryJudgeResult';

export interface IGroupScoreProps {
  group: ICanaryJudgeGroupScore;
  style?: {[key: string]: string };
  onClick: (event: any) => void;
  className: string;
}

/*
* Renders an individual group score.
* */
export default ({ group, onClick, style, className }: IGroupScoreProps) => (
  <section
    style={style}
    onClick={() => onClick(group.name)}
    className={classNames('clickable', 'text-center', 'group-score', className)}
  >
    <h3 className="heading-3 uppercase label">{group.name}</h3>
    <div className="arrow-down" style={{borderTopColor: style.backgroundColor}}/>
  </section>
);
