import type { FormikProps } from 'formik';
import { mount } from 'enzyme';
import React from 'react';

import { FormikStageConfig } from './FormikStageConfig';
import type { IStage } from '../../../domain';

describe('FormikStageConfig', () => {
  const mountConfig = (stage: IStage, onChange: (values: IStage) => void) => {
    let formik: FormikProps<IStage>;
    const wrapper = mount(
      <FormikStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={stage}
        onChange={onChange}
        render={(props) => {
          formik = props.formik;
          return null;
        }}
      />,
    );
    return { wrapper, setField: (name: string, value: any) => formik.setFieldValue(name, value) };
  };

  const stageWith = (extra: Partial<IStage> = {}): IStage => ({
    refId: '1',
    name: 'Script',
    type: 'script',
    requisiteStageRefIds: [],
    command: 'a',
    failPipeline: true,
    ...extra,
  });

  it('keeps stage fields changed outside the form', (done) => {
    const stage = stageWith();
    const onChange = (values: IStage) => {
      expect(values.command).toBe('b');
      expect(values.failPipeline).toBe(false);
      done();
    };
    const { wrapper, setField } = mountConfig(stage, onChange);

    // Like Execution Options, which edits the stage outside the form
    stage.failPipeline = false;
    wrapper.setProps({ stage });
    setField('command', 'b');
  });

  it('removes fields the form removed', (done) => {
    const stage = stageWith();
    const onChange = (values: IStage) => {
      expect(values.command).toBeUndefined();
      expect(values.failPipeline).toBe(true);
      done();
    };
    const { setField } = mountConfig(stage, onChange);

    setField('command', undefined);
  });
});
