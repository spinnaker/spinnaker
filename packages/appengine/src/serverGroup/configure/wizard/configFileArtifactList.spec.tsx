import { mount } from 'enzyme';
import React from 'react';

import type { IArtifactAccount, IArtifactAccountPair } from '@spinnaker/core';
import { StageArtifactSelector } from '@spinnaker/core';
import { mockDeployStage, mockPipeline } from '@spinnaker/mocks';

import { ConfigFileArtifactList } from './ConfigFileArtifactList';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { mockHttpClient } from '../../../../../core/src/api/mock/jasmine';

describe('<ConfigFileArtifactList/>', () => {
  it('renders empty children when null/empty artifacts are passed in', () => {
    const configArtifacts: IArtifactAccountPair[] = [];
    const wrapper = mount(
      <ConfigFileArtifactList
        configArtifacts={configArtifacts}
        pipeline={mockPipeline}
        stage={mockDeployStage}
        updateConfigArtifacts={() => {}}
      />,
    );
    expect(wrapper.find(StageArtifactSelector).length).toBe(0);
  });

  it('renders 2 children of StageArtifactSelector when 2 artifacts are passed in', async () => {
    const http = mockHttpClient();
    const body: IArtifactAccount[] = [{ name: 'http-acc', types: ['http'] }];
    http.expectGET(`/artifacts/credentials`).respond(200, body);
    http.expectGET(`/artifacts/credentials`).respond(200, body);

    const configArtifacts = [
      { account: 'http-acc', id: '123abc', artifact: { id: '123abc' } },
      { account: 'http-acc', id: '1234abcd', artifact: { id: '1234abcd' } },
    ];

    const wrapper = mount(
      <ConfigFileArtifactList
        configArtifacts={configArtifacts}
        pipeline={mockPipeline}
        stage={mockDeployStage}
        updateConfigArtifacts={() => {}}
      />,
    );
    expect(wrapper.find(StageArtifactSelector).length).toBe(2);
    await http.flush();
  });

  it('renders 1 children of StageArtifactSelector when 1 expectedArtifacts are passed in', async () => {
    const http = mockHttpClient();
    const body: IArtifactAccount[] = [
      {
        name: 'http-acc',
        types: ['http'],
      },
    ];
    http.expectGET(`/artifacts/credentials`).respond(200, body);

    // artifact intentionally left null indicating an expected artifact
    const configArtifacts = [
      {
        account: 'http-acc',
        id: '123abc',
      },
    ];

    const wrapper = mount(
      <ConfigFileArtifactList
        configArtifacts={configArtifacts}
        pipeline={mockPipeline}
        stage={mockDeployStage}
        updateConfigArtifacts={() => {}}
      />,
    );
    expect(wrapper.find(StageArtifactSelector).length).toBe(1);
    await http.flush();
  });
});
