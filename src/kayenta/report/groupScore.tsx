import * as React from 'react';

import { ICanaryJudgeGroupScore } from '../domain/ICanaryJudgeResult';

export interface IGroupScoreProps {
  group: ICanaryJudgeGroupScore;
  onClick: (event: any) => void;
}

/*
* Renders an individual group score.
* */
export default ({ group, onClick }: IGroupScoreProps) => (
  <section
    data-group={group.name}
    onClick={onClick}
    className="clickable"
  >
    {group.name} | {group.score}
  </section>
);
