import type { IKayentaStage } from 'kayenta/domain';
import React from 'react';

export interface IForAnalysisTypeProps {
  stage?: Pick<IKayentaStage, 'analysisType'>;
  types: string;
  children: React.ReactNode;
}

export function ForAnalysisType({ stage, types, children }: IForAnalysisTypeProps) {
  const allowedTypes = types.split(',').map((type) => type.trim());
  if (!stage || !allowedTypes.includes(stage.analysisType)) {
    return null;
  }

  return <>{children}</>;
}
