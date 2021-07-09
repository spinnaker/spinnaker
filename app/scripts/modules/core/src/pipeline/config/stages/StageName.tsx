import { module } from 'angular';
/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react';
import { react2angular } from 'react2angular';

import { IStage } from '../../../domain';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

export interface IStageNameProps {
  stages: IStage[];
  refId: string | number;
}

const nameFromRefId = (stages: IStage[], refId: string | number) => {
  const stage = stages.find((s) => s.refId === refId);
  if (stage) {
    return stage.name;
  }
  return '';
};

export const StageName: React.SFC<IStageNameProps> = (props) => {
  return <span>{nameFromRefId(props.stages, props.refId)}</span>;
};

export const STAGE_NAME = 'spinnaker.core.artifact.stageName.component';

module(STAGE_NAME, []).component(
  'stageName',
  react2angular(withErrorBoundary(StageName, 'stageName'), ['stages', 'refId']),
);
