import { IComponentControllerService, mock } from 'angular';
import { $q } from 'ngimport';

import { AccountService, IAccount } from '@spinnaker/core';

import { DockerImageReader, IDockerImage } from '@spinnaker/docker';
import {
  DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT,
  DockerImageAndTagSelectorController,
} from './dockerImageAndTagSelector.component';

describe('dockerImageAndTagSelector controller', () => {
  let $ctrl: DockerImageAndTagSelectorController, $componentController: IComponentControllerService, $scope: ng.IScope;

  let organization: string, registry: string, repository: string, showRegistry: boolean;

  const tag: string = undefined,
    account: string = undefined;

  beforeEach(mock.module(DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT));

  beforeEach(
    mock.inject((_$componentController_: IComponentControllerService, $rootScope: ng.IRootScopeService) => {
      $componentController = _$componentController_;
      $scope = $rootScope.$new();
    }),
  );

  const initialize = (accounts: IAccount[], images: IDockerImage[]) => {
    spyOn(AccountService, 'listAccounts').and.returnValue($q.when(accounts as any));
    spyOn(DockerImageReader, 'findImages').and.returnValue($q.when(images));
    $ctrl = $componentController(
      'dockerImageAndTagSelector',
      { DockerImageReader },
      { organization, registry, repository, tag, account, showRegistry },
    ) as DockerImageAndTagSelectorController;
    $ctrl.$onInit();
    $scope.$digest();
  };

  describe('option initialization', () => {
    beforeEach(() => {
      organization = 'my-org';
      repository = 'my-org/my-repo';
      registry = 'registry1';
      showRegistry = true;
    });

    it('fully initializes options when all arguments provided to controller', () => {
      const accounts: IAccount[] = [{ name: 'account1', accountId: '1', requiredGroupMembership: [], type: 'docker' }];
      const images: IDockerImage[] = [
        { account: 'account1', registry: 'registry1', repository: 'my-org/my-repo', tag: 'latest' },
      ];

      initialize(accounts, images);
      expect($ctrl.accounts).toEqual(['account1']);
      expect($ctrl.organizations).toEqual(['my-org']);
      expect($ctrl.repositories).toEqual(['my-org/my-repo']);
      expect($ctrl.tags).toEqual(['latest']);
    });
  });

  it('de-duplicates organizations, tags, repositories', () => {
    const accounts: IAccount[] = [
      { name: 'account1', accountId: '1', requiredGroupMembership: [], type: 'docker' },
      { name: 'account2', accountId: '3', requiredGroupMembership: [], type: 'docker' },
    ];
    const images: IDockerImage[] = [
      { account: 'account1', registry: 'registry1', repository: 'my-org/my-repo', tag: 'latest' },
      { account: 'account1', registry: 'registry1', repository: 'my-org/my-repo', tag: 'latest' },
      { account: 'account1', registry: 'registry1', repository: 'my-org/my-repo', tag: '1.1' },
      { account: 'account2', registry: 'registry1', repository: 'my-org/my-repo', tag: 'latest' },
    ];

    initialize(accounts, images);
    expect($ctrl.accounts).toEqual(['account1', 'account2']);
    expect($ctrl.organizations).toEqual(['my-org']);
    expect($ctrl.repositories).toEqual(['my-org/my-repo']);
    expect($ctrl.tags).toEqual(['latest', '1.1']);
  });
});
