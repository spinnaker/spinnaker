import React from 'react';
import { mount } from 'enzyme';
import { IStage } from '../../../../../domain';
import { REACT_MODULE } from '../../../../../reactShims';
import { mock } from 'angular';

import { ApplicationModelBuilder } from '../../../../../application';

import { AccountService } from '../../../../../account';

import { StageConfigField } from '../../../..';
import { BakeHelmConfigForm } from './BakeHelmConfigForm';
import { SpinFormik } from '../../../../../presentation';

describe('<BakeHelmConfigForm />', () => {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  const helmChartFilePathFieldName = 'Helm Chart File Path';

  const getProps = () => {
    return {
      application: ApplicationModelBuilder.createApplicationForTests('my-application'),
      pipeline: {
        application: 'my-application',
        id: 'pipeline-id',
        limitConcurrent: true,
        keepWaitingPipelines: true,
        name: 'My Pipeline',
        parameterConfig: [],
        stages: [],
        triggers: [],
      },
    } as any;
  };

  beforeEach(() =>
    spyOn(AccountService, 'getArtifactAccounts').and.returnValue(
      Promise.resolve([
        { name: 'gitrepo', types: ['something-else', 'git/repo'] },
        { name: 'notgitrepo', types: ['something-else'] },
      ]),
    ),
  );

  it('renders the helm chart file path element when the template artifact is from an account that handles git/repo artifacts', async () => {
    const stage = ({
      inputArtifacts: [{ account: 'gitrepo' }],
    } as unknown) as IStage;

    const props = getProps();

    const component = mount(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmConfigForm {...props} formik={formik} />}
      />,
    );

    await new Promise((resolve) => setTimeout(resolve)); // wait one js tick for promise to resolve
    component.setProps({}); // force a re-render

    expect(component.find(StageConfigField).findWhere((x) => x.text() === helmChartFilePathFieldName).length).toBe(1);
  });

  it('does not render the helm chart file path element when the template artifact is from an account that does not handle git/repo artifacts', async () => {
    const stage = ({
      inputArtifacts: [{ account: 'notgitrepo' }],
    } as unknown) as IStage;

    const props = getProps();

    const component = mount(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmConfigForm {...props} formik={formik} />}
      />,
    );

    await new Promise((resolve) => setTimeout(resolve)); // wait one js tick for promise to resolve
    component.setProps({}); // force a re-render

    expect(component.find(StageConfigField).findWhere((x) => x.text() === helmChartFilePathFieldName).length).toBe(0);
  });
});
