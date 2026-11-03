import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import type { IAccountDetails } from '@spinnaker/core';
import { AccountService } from '@spinnaker/core';
import type { IDockerImage } from '@spinnaker/docker';
import { DockerImageReader } from '@spinnaker/docker';

import { TaskDefinition } from './TaskDefinition';
import type { IEcsDockerImage, IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';

describe('TaskDefinition', () => {
  let command: IEcsServerGroupCommand;

  const configureCommand = (_query: string) => Promise.resolve();
  const onFieldChange = (_key: string, _value: any) => {};

  const dockerAccounts: IAccountDetails[] = [
    {
      name: 'my-docker-account',
      accountId: '1',
      requiredGroupMembership: [],
      type: 'dockerRegistry',
    } as IAccountDetails,
  ];

  const dockerImages: IDockerImage[] = [
    { account: 'my-docker-account', registry: 'my-registry', repository: 'my-repo', tag: 'latest' },
  ];

  const expectedImages = [
    {
      ...dockerImages[0],
      imageId: 'my-registry/my-repo:latest',
      message: '',
      fromTrigger: false,
      fromContext: false,
      stageId: '',
      imageLabelOrSha: '',
    },
  ] as IEcsDockerImage[];

  const renderTaskDefinition = async () => {
    const rendered = render(
      <TaskDefinition command={command} onFieldChange={onFieldChange} configureCommand={configureCommand} />,
    );
    await waitFor(() => expect(AccountService.listAccounts).toHaveBeenCalledWith('dockerRegistry'));
    await waitFor(() => expect(within(dockerAccountField(rendered.container)).getByRole('combobox')).toBeEnabled());
    return rendered;
  };

  const dockerAccountField = (root: HTMLElement) =>
    root.querySelector('[data-test-id="Artifacts.dockerRegistryAccount"]') as HTMLElement;

  const selectDockerAccount = async (root: HTMLElement, account: string) => {
    const field = dockerAccountField(root);
    fireEvent.mouseDown(field.querySelector('.Select-control'));
    fireEvent.mouseDown(await screen.findByRole('option', { name: account }));
  };

  const openContainerImageOptions = (root: HTMLElement) => {
    if (!screen.queryByRole('combobox', { name: 'Container image 1' })) {
      fireEvent.click(screen.getByRole('button', { name: 'Add New Container Mapping' }));
    }
    const field = root.querySelector('[data-test-id="Artifacts.containerImage"]') as HTMLElement;
    fireEvent.mouseDown(field.querySelector('.Select-control'));
  };

  beforeEach(() => {
    command = ({
      computeUnits: 256,
      reservedMemory: 512,
      imageDescription: null as any,
      targetGroupMappings: [],
      containerMappings: [] as any,
      targetGroup: '',
      loadBalancedContainer: '',
      taskDefinitionArtifact: {} as any,
      taskDefinitionArtifactAccount: '',
      evaluateTaskDefinitionArtifactExpressions: false,
      useTaskDefinitionArtifact: false,
      viewState: { dirty: { targetGroups: [] }, pipeline: null as any, currentStage: null as any } as any,
      backingData: { filtered: { images: [], targetGroups: [] } } as any,
    } as any) as IEcsServerGroupCommand;

    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(Promise.resolve(dockerAccounts));
    vi.spyOn(AccountService, 'getArtifactAccounts').mockReturnValue(Promise.resolve([]));
    vi.spyOn(DockerImageReader, 'findImages').mockReturnValue(Promise.resolve(dockerImages));
  });

  describe('updateDockerRegistryAccount', () => {
    it('calls DockerImageReader.findImages with the selected account', async () => {
      const { container } = await renderTaskDefinition();

      await selectDockerAccount(container, 'my-docker-account');

      expect(DockerImageReader.findImages).toHaveBeenCalledWith({
        provider: 'dockerRegistry',
        account: 'my-docker-account',
        count: 50,
      });
    });

    it('updates the image options and backingData with images returned for the account', async () => {
      const { container } = await renderTaskDefinition();

      await selectDockerAccount(container, 'my-docker-account');

      await waitFor(() => expect(command.backingData.filtered.images).toEqual(expectedImages));
      openContainerImageOptions(container);
      expect(await screen.findByRole('option', { name: '(my-registry/my-repo:latest)' })).toBeInTheDocument();
    });

    it('clears existing images immediately when account changes', async () => {
      command.backingData.filtered.images = expectedImages;
      vi.mocked(DockerImageReader.findImages).mockReturnValue(
        new Promise<IDockerImage[]>(() => {}),
      );
      const { container } = await renderTaskDefinition();

      openContainerImageOptions(container);
      expect(await screen.findByRole('option', { name: '(my-registry/my-repo:latest)' })).toBeInTheDocument();

      await selectDockerAccount(container, 'my-docker-account');

      // Images clear before the findImages promise resolves
      expect(command.backingData.filtered.images).toEqual([]);
      openContainerImageOptions(container);
      expect(await screen.findByText('No results found')).toBeInTheDocument();
      expect(screen.queryByRole('option', { name: '(my-registry/my-repo:latest)' })).not.toBeInTheDocument();
    });

    it('pre-selects the account from imageDescription when command already has one', async () => {
      command.imageDescription = {
        account: 'my-docker-account',
        registry: 'my-registry',
        repository: 'my-repo',
        tag: 'latest',
        imageId: 'my-registry/my-repo:latest',
        message: '',
        fromTrigger: false,
        fromContext: false,
        stageId: '',
        imageLabelOrSha: '',
      } as IEcsDockerImage;

      const { container } = await renderTaskDefinition();

      expect(
        await within(dockerAccountField(container)).findByText('my-docker-account', {
          selector: '.Select-value-label',
        }),
      ).toBeInTheDocument();
    });

    it('starts with no account selected when imageDescription has no account', async () => {
      const { container } = await renderTaskDefinition();

      const field = dockerAccountField(container);
      expect(within(field).getByText('Select a Docker registry account...')).toBeInTheDocument();
      expect(field.querySelector('.Select-value-label')).not.toBeInTheDocument();
      expect(screen.queryByText('my-docker-account')).not.toBeInTheDocument();
    });
  });
});
