import { KayentaAnalysisType } from 'kayenta/domain';
import * as React from 'react';

import { noop } from '@spinnaker/core';

export interface IAnalysisTypeProps {
  analysisTypes: KayentaAnalysisType[];
  selectedType: KayentaAnalysisType;
  onChange?: (type: KayentaAnalysisType) => void;
}

export const AnalysisTypeWarning = () => (
  <div className="alert alert-warning">
    The analysis type you've selected isn't supported by any of this application's cloud providers. Please select a
    different type.
  </div>
);

export const AnalysisTypeRadioButton = ({
  selectedType,
  analysisTypes,
  onChange = noop,
  label,
  type,
}: IAnalysisTypeProps & { label: string; type: KayentaAnalysisType }) => {
  if (!analysisTypes.includes(type)) {
    return null;
  }
  return (
    <div className="radio">
      <label>
        <input type="radio" name="analysisType" checked={selectedType === type} onChange={() => onChange(type)} />
        {label}
      </label>
    </div>
  );
};

export const AnalysisType = (props: IAnalysisTypeProps) => {
  const { analysisTypes = [], selectedType } = props;
  return (
    <>
      {!analysisTypes.includes(selectedType) && <AnalysisTypeWarning />}
      <AnalysisTypeRadioButton {...props} label="Real Time (Automatic)" type={KayentaAnalysisType.RealTimeAutomatic} />
      <AnalysisTypeRadioButton {...props} label="Real Time (Manual)" type={KayentaAnalysisType.RealTime} />
      <AnalysisTypeRadioButton {...props} label="Retrospective" type={KayentaAnalysisType.Retrospective} />
    </>
  );
};
