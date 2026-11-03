import { render, screen } from '@testing-library/react';
import { KayentaAnalysisType } from '../../domain';
import * as React from 'react';

import type { IAnalysisTypeProps } from './AnalysisType';
import { AnalysisType, AnalysisTypeWarning } from './AnalysisType';

describe('<AnalysisType />', () => {
  const tests = [
    {
      it: 'only includes one radio button if only one analysis type is provided',
      props: {
        analysisTypes: [KayentaAnalysisType.Retrospective],
        selectedType: KayentaAnalysisType.Retrospective,
      },
      assertion: () => {
        expect(screen.getAllByRole('radio')).toHaveLength(1);
      },
    },
    {
      it: 'only includes two radio buttons if only two analysis types are provided',
      props: {
        analysisTypes: [KayentaAnalysisType.Retrospective, KayentaAnalysisType.RealTime],
        selectedType: KayentaAnalysisType.Retrospective,
      },
      assertion: () => {
        expect(screen.getAllByRole('radio')).toHaveLength(2);
      },
    },
    {
      it: 'renders a warning if selected analysis type is not one of the provided analysis types',
      props: {
        analysisTypes: [KayentaAnalysisType.Retrospective, KayentaAnalysisType.RealTime],
        selectedType: KayentaAnalysisType.RealTimeAutomatic,
      },
      assertion: () => {
        expect(screen.getAllByRole('radio')).toHaveLength(2);
        expect(
          screen.getByText(
            /the analysis type you've selected isn't supported by any of this application's cloud providers/i,
          ),
        ).toBeVisible();
      },
    },
  ];

  tests.forEach((test) => {
    it(test.it, () => {
      render(<AnalysisType {...test.props} />);
      test.assertion();
    });
  });
});
