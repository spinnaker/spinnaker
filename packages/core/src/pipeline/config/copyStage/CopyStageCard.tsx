import React from 'react';

import { CloudProviderLogo } from '../../../cloudProvider';
import { IStage } from '../../../domain';
import { robotToHuman } from '../../../presentation';

import './copyStageCard.less';

export interface ICopyStageCardProps {
  pipeline?: string;
  strategy?: string;
  stage: IStage;
}

export function CopyStageCard(props: ICopyStageCardProps) {
  const {
    pipeline,
    strategy,
    stage: { cloudProviderType, comments, name, type },
  } = props;

  return (
    <div className="copy-stage-card container-fluid">
      <div className="row">
        <div className="col-md-10">
          <b>{name}</b>
        </div>
        <div className="col-md-2">
          {cloudProviderType && (
            <div className="pull-right">
              <CloudProviderLogo provider={cloudProviderType} height="10px" width="10px" />
            </div>
          )}
        </div>
      </div>
      <div className="row">
        <div className="col-md-12">
          <p>
            <b>Type:</b> {robotToHuman(type)}
          </p>
          {pipeline && (
            <p>
              <b>Pipeline:</b> {pipeline}
            </p>
          )}
          {strategy && (
            <p>
              <b>Strategy:</b> {strategy}
            </p>
          )}
          <p className="small">{comments}</p>
        </div>
      </div>
    </div>
  );
}
