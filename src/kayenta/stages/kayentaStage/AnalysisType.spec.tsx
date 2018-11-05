import * as React from 'react';
import { mount, ReactWrapper } from 'enzyme';

import { KayentaAnalysisType } from 'kayenta/domain';
import { AnalysisType, AnalysisTypeWarning, IAnalysisTypeProps } from './AnalysisType';

describe('<AnalysisType />', () => {
  const component = (props: IAnalysisTypeProps) => mount(<AnalysisType {...props} /> as any);

  const tests = [
    {
      it: 'only includes one radio button if only one analysis type is provided',
      props: {
        analysisTypes: [KayentaAnalysisType.Retrospective],
        selectedType: KayentaAnalysisType.Retrospective,
      },
      assertion: (wrapper: ReactWrapper) => {
        expect(wrapper.find('input[type="radio"]').length).toEqual(1);
      },
    },
    {
      it: 'only includes two radio buttons if only two analysis types are provided',
      props: {
        analysisTypes: [KayentaAnalysisType.Retrospective, KayentaAnalysisType.RealTime],
        selectedType: KayentaAnalysisType.Retrospective,
      },
      assertion: (wrapper: ReactWrapper) => {
        expect(wrapper.find('input[type="radio"]').length).toEqual(2);
      },
    },
    {
      it: 'renders a warning if selected analysis type is not one of the provided analysis types',
      props: {
        analysisTypes: [KayentaAnalysisType.Retrospective, KayentaAnalysisType.RealTime],
        selectedType: KayentaAnalysisType.RealTimeAutomatic,
      },
      assertion: (wrapper: ReactWrapper) => {
        expect(wrapper.find('input[type="radio"]').length).toEqual(2);
        expect(wrapper.find(AnalysisTypeWarning).exists()).toBeTruthy();
      },
    },
  ];

  tests.forEach(test => {
    it(test.it, () => test.assertion(component(test.props)));
  });
});
