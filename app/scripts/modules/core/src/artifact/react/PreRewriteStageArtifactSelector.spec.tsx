import * as React from 'react';
import { mount } from 'enzyme';

import {
  AccountService,
  ArtifactAccountSelector,
  ExpectedArtifactEditor,
  ExpectedArtifactSelector,
  IArtifactAccount,
  IPipeline,
  IStage,
  noop,
} from '@spinnaker/core';

import { PreRewriteStageArtifactSelector } from './PreRewriteStageArtifactSelector';

describe('<PreRewriteStageArtifactSelector />', () => {
  let pipeline: IPipeline;
  let stage: IStage;
  let component: any;
  let promise: Promise<IArtifactAccount[]>;

  beforeEach(() => {
    promise = Promise.resolve([
      {
        name: 'github-account-1',
        types: ['github/file'],
      },
      {
        name: 'github-account-2',
        types: ['github/file'],
      },
    ]);
    spyOn(AccountService, 'getArtifactAccounts').and.callFake(() => promise);

    stage = {
      name: 'Google Cloud Build',
      refId: '1',
      requisiteStageRefIds: [],
      type: 'googleCloudBuild',
    };

    pipeline = {
      application: 'my-app',
      expectedArtifacts: [
        {
          displayName: 'shy-newt-62',
          defaultArtifact: {
            id: '5aa6d7ef-2cbd-4384-973b-2d8e75cd69b8',
            name: 'gcb.yml',
            reference: 'https://api.github.com/repos/maggieneterval/spinnaker-kubernetes-demo/contents/gcb.yml',
            type: 'github/file',
            version: 'master',
          },
          id: '626ce062-6e60-498d-a562-289db3f7faf5',
          matchArtifact: {
            id: '863f61af-1d74-40ba-886b-27c985b5f6e7',
            name: 'gcb.yml',
            type: 'github/file',
          },
          usePriorArtifact: false,
          useDefaultArtifact: true,
        },
      ],
      id: '123',
      index: 0,
      keepWaitingPipelines: false,
      limitConcurrent: true,
      name: 'my-pipeline',
      stages: [stage],
      strategy: false,
      triggers: [],
      parameterConfig: [],
    };
  });

  it('renders only an ExpectedArtifactSelector by default', () => {
    component = mount(
      <PreRewriteStageArtifactSelector
        excludedArtifactTypes={[]}
        selectedArtifactId={null}
        pipeline={pipeline}
        selectedArtifactAccount={null}
        setArtifactAccount={noop}
        setArtifactId={noop}
        stage={stage}
      />,
    );
    return promise
      .then(() => component.update())
      .then(() => {
        expect(component.find(ExpectedArtifactSelector).length).toEqual(1);
        expect(component.find(ArtifactAccountSelector).length).toEqual(0);
        expect(component.find(ExpectedArtifactEditor).length).toEqual(0);
      });
  });

  it('renders an ExpectedArtifactSelector and ArtifactAccountSelector when an expected artifact with more than one possible account is selected', () => {
    component = mount(
      <PreRewriteStageArtifactSelector
        excludedArtifactTypes={[]}
        selectedArtifactId="626ce062-6e60-498d-a562-289db3f7faf5"
        pipeline={pipeline}
        selectedArtifactAccount="github-account-2"
        setArtifactAccount={noop}
        setArtifactId={noop}
        stage={stage}
      />,
    );
    return promise
      .then(() => component.update())
      .then(() => {
        expect(component.find(ExpectedArtifactSelector).length).toEqual(1);
        expect(component.find(ArtifactAccountSelector).length).toEqual(1);
        expect(component.find(ExpectedArtifactEditor).length).toEqual(0);
      });
  });

  it('renders an ExpectedArtifactSelector and ExpectedArtifactEditor (which includes an ArtifactAccountSelector) when "Create new" is selected', () => {
    component = mount(
      <PreRewriteStageArtifactSelector
        excludedArtifactTypes={[]}
        selectedArtifactId={null}
        pipeline={pipeline}
        selectedArtifactAccount={null}
        setArtifactAccount={noop}
        setArtifactId={noop}
        stage={stage}
      />,
    );
    component.instance().onRequestCreateArtifact();
    return promise
      .then(() => component.update())
      .then(() => {
        expect(component.find(ExpectedArtifactSelector).length).toEqual(1);
        expect(component.find(ArtifactAccountSelector).length).toEqual(1);
        expect(component.find(ExpectedArtifactEditor).length).toEqual(1);
      });
  });
});
