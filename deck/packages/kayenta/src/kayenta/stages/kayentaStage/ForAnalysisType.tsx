import React from 'react';

import type { IKayentaStage } from '../../domain';

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
