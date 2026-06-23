import { mount } from 'enzyme';
import React from 'react';

import type { IAccountDetails } from '@spinnaker/core';
import { AccountService } from '@spinnaker/core';
import type { IDockerImage } from '@spinnaker/docker';
import { DockerImageReader } from '@spinnaker/docker';

import type { IEcsDockerImage, IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';
import { TaskDefinition } from './TaskDefinition';

const flushPromises = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

describe('TaskDefinition', () => {
  let command: IEcsServerGroupCommand;

  const configureCommand = (_query: string) => Promise.resolve() as PromiseLike<void>;
  const notifyAngular = (_key: string, _value: any) => {};

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

  beforeEach(() => {
    command = {
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
    } as any as IEcsServerGroupCommand;

    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve(dockerAccounts));
    spyOn(DockerImageReader, 'findImages').and.returnValue(Promise.resolve(dockerImages));
  });

  describe('updateDockerRegistryAccount', () => {
    it('calls DockerImageReader.findImages with the selected account', async () => {
      const wrapper = mount(
        <TaskDefinition command={command} notifyAngular={notifyAngular} configureCommand={configureCommand} />,
      );

      await flushPromises();

      (wrapper.instance() as any).updateDockerRegistryAccount({ value: 'my-docker-account' });

      expect(DockerImageReader.findImages).toHaveBeenCalledWith({
        provider: 'dockerRegistry',
        account: 'my-docker-account',
        count: 50,
      });
    });

    it('updates component state and backingData with images returned for the account', async () => {
      const wrapper = mount(
        <TaskDefinition command={command} notifyAngular={notifyAngular} configureCommand={configureCommand} />,
      );

      (wrapper.instance() as any).updateDockerRegistryAccount({ value: 'my-docker-account' });
      await flushPromises();
      wrapper.update();

      expect((wrapper.instance() as TaskDefinition).state.dockerImages).toEqual(dockerImages as IEcsDockerImage[]);
      expect(command.backingData.filtered.images).toEqual(dockerImages as IEcsDockerImage[]);
    });

    it('clears existing images immediately when account changes', () => {
      command.backingData.filtered.images = dockerImages as IEcsDockerImage[];
      const wrapper = mount(
        <TaskDefinition command={command} notifyAngular={notifyAngular} configureCommand={configureCommand} />,
      );

      (wrapper.instance() as any).updateDockerRegistryAccount({ value: 'my-docker-account' });

      expect((wrapper.instance() as TaskDefinition).state.dockerImages).toEqual([]);
    });

    it('pre-selects the account from imageDescription when command already has one', () => {
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

      const wrapper = mount(
        <TaskDefinition command={command} notifyAngular={notifyAngular} configureCommand={configureCommand} />,
      );

      expect((wrapper.instance() as TaskDefinition).state.selectedDockerAccount).toBe('my-docker-account');
    });

    it('starts with no account selected when imageDescription has no account', () => {
      const wrapper = mount(
        <TaskDefinition command={command} notifyAngular={notifyAngular} configureCommand={configureCommand} />,
      );

      expect((wrapper.instance() as TaskDefinition).state.selectedDockerAccount).toBe('');
    });
  });
});
