import { render, screen } from '@testing-library/react';
import React from 'react';

import { BakeHelmConfigForm } from './BakeHelmConfigForm';
import { AccountService } from '../../../../../account';
import { ApplicationModelBuilder } from '../../../../../application';
import { ExpectedArtifactService } from '../../../../../artifact';
import type { IExpectedArtifact, IStage } from '../../../../../domain';
import { SpinFormik } from '../../../../../presentation';

describe('<BakeHelmConfigForm />', () => {
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
    vi.spyOn(AccountService, 'getArtifactAccounts').mockReturnValue(
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

    render(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmConfigForm {...props} formik={formik} />}
      />,
    );

    expect(await screen.findByText(helmChartFilePathFieldName)).toBeVisible();
  });

  it('does not render the helm chart file path element when the template artifact is from an account that does not handle git/repo artifacts', async () => {
    const stage = ({
      inputArtifacts: [{ account: 'notgitrepo' }],
    } as unknown) as IStage;

    const props = getProps();

    render(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmConfigForm {...props} formik={formik} />}
      />,
    );

    await screen.findByText('Template Artifact');
    expect(screen.queryByText(helmChartFilePathFieldName)).not.toBeInTheDocument();
  });

  it('render the helm chart file path if the id of the git artifact is given but the account value does not exist', async () => {
    const expectedArtifactDisplayName = 'test-artifact';
    const expectedArtifactId = 'test-artifact-id';
    const expectedGitArtifact: IExpectedArtifact = {
      defaultArtifact: {
        customKind: true,
        id: 'defaultArtifact-id',
      },
      displayName: expectedArtifactDisplayName,
      id: expectedArtifactId,
      matchArtifact: {
        artifactAccount: 'gitrepo',
        id: expectedArtifactId,
        reference: 'git repo',
        type: 'git/repo',
        version: 'master',
      },
      useDefaultArtifact: false,
      usePriorArtifact: false,
    };
    const stage = ({
      inputArtifacts: [{ id: expectedArtifactId }],
    } as unknown) as IStage;

    vi.spyOn(ExpectedArtifactService, 'getExpectedArtifactsAvailableToStage').mockReturnValue([expectedGitArtifact]);

    const props = getProps();

    render(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmConfigForm {...props} formik={formik} />}
      />,
    );

    expect(await screen.findByText(expectedArtifactDisplayName)).toBeVisible();
    expect(screen.getByText(helmChartFilePathFieldName)).toBeVisible();
  });

  it('render the include crds checkbox if the template render is HELM3', async () => {
    const stage = ({
      templateRenderer: 'HELM3',
    } as unknown) as IStage;

    const props = getProps();

    render(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmConfigForm {...props} formik={formik} />}
      />,
    );

    expect(await screen.findByText('Include CRDs')).toBeVisible();
  });

  it('does not render the include crds checkbox if the template render is HELM2', async () => {
    const stage = ({
      templateRenderer: 'HELM2',
    } as unknown) as IStage;

    const props = getProps();

    render(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmConfigForm {...props} formik={formik} />}
      />,
    );

    await screen.findByText('Template Artifact');
    expect(screen.queryByText('Include CRDs')).not.toBeInTheDocument();
  });
});
