import * as React from 'react';
import { HelpField } from '@spinnaker/core';

import { KayentaAnalysisType } from 'kayenta/domain';

export interface IAnalysisTypeProps {
  type: KayentaAnalysisType;
  onChange(type: KayentaAnalysisType): void;
}

const HELP_FIELD_ID_PREFIX = 'pipeline.config.canary.analysisType';

export const AnalysisType = ({ type, onChange }: IAnalysisTypeProps) => {
  return (
    <>
      <div className="radio">
        <label>
          <input
            type="radio"
            name="analysisType"
            checked={type === KayentaAnalysisType.RealTimeAutomatic}
            onChange={() => onChange(KayentaAnalysisType.RealTimeAutomatic)}
          />
          Real Time (Automatic)
          {' '}
          <HelpField id={`${HELP_FIELD_ID_PREFIX}.${KayentaAnalysisType.RealTimeAutomatic}`}/>
        </label>
      </div>
      <div className="radio">
        <label>
          <input
            type="radio"
            name="analysisType"
            checked={type === KayentaAnalysisType.RealTime}
            onChange={() => onChange(KayentaAnalysisType.RealTime)}
          />
          Real Time (Manual)
          {' '}
          <HelpField id={`${HELP_FIELD_ID_PREFIX}.${KayentaAnalysisType.RealTime}`}/>
        </label>
      </div>
      <div className="radio">
        <label>
          <input
            type="radio"
            name="analysisType"
            checked={type === KayentaAnalysisType.Retrospective}
            onChange={() => onChange(KayentaAnalysisType.Retrospective)}
          />
          Retrospective
          {' '}
          <HelpField id={`${HELP_FIELD_ID_PREFIX}.${KayentaAnalysisType.Retrospective}`}/>
        </label>
      </div>
    </>
  );
};
