import { render, screen } from '@testing-library/react';
import React from 'react';

import { BakeHelmfileConfigForm } from './BakeHelmfileConfigForm';
import { AccountService } from '../../../../../account';
import { ApplicationModelBuilder } from '../../../../../application';
import { ExpectedArtifactService } from '../../../../../artifact';
import type { IExpectedArtifact, IStage } from '../../../../../domain';
import { SpinFormik } from '../../../../../presentation';

describe('<BakeHelmfileConfigForm />', () => {
  const helmfileFilePathFieldName = 'Helmfile File Path';

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

  it('renders the helmfile file path element when the template artifact is from an account that handles git/repo artifacts', async () => {
    const stage = ({
      inputArtifacts: [{ account: 'gitrepo' }],
    } as unknown) as IStage;

    const props = getProps();

    render(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmfileConfigForm {...props} formik={formik} />}
      />,
    );

    expect(await screen.findByText(helmfileFilePathFieldName)).toBeVisible();
  });

  it('does not render the helmfile file path element when the template artifact is from an account that does not handle git/repo artifacts', async () => {
    const stage = ({
      inputArtifacts: [{ account: 'notgitrepo' }],
    } as unknown) as IStage;

    const props = getProps();

    render(
      <SpinFormik
        initialValues={stage}
        onSubmit={() => null}
        validate={() => null}
        render={(formik) => <BakeHelmfileConfigForm {...props} formik={formik} />}
      />,
    );

    await screen.findByText('Template Artifact');
    expect(screen.queryByText(helmfileFilePathFieldName)).not.toBeInTheDocument();
  });

  it('render the helmfile file path if the id of the git artifact is given but the account value does not exist', async () => {
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
        render={(formik) => <BakeHelmfileConfigForm {...props} formik={formik} />}
      />,
    );

    expect(await screen.findByText(expectedArtifactDisplayName)).toBeVisible();
    expect(screen.getByText(helmfileFilePathFieldName)).toBeVisible();
  });
});
