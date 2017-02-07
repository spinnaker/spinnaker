import {mock} from 'angular';
import {
  DockerImageAndTagSelectorController,
  DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT
} from './dockerImageAndTagSelector.component';
import {AccountService, IAccount} from 'core/account/account.service';
import {DockerImageReaderService, IDockerImage} from './docker.image.reader.service';

describe('dockerImageAndTagSelector controller', () => {
  let $ctrl: DockerImageAndTagSelectorController,
      accountService: AccountService,
      dockerImageReader: DockerImageReaderService,
      $componentController: ng.IComponentControllerService,
      $q: ng.IQService,
      $scope: ng.IScope;

  let organization: string,
      registry: string,
      repository: string,
      tag: string,
      account: string,
      showRegistry: boolean;

  beforeEach(mock.module(DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT));

  beforeEach(mock.inject((_accountService_: AccountService,
                          _dockerImageReader_: DockerImageReaderService,
                          _$componentController_: ng.IComponentControllerService,
                          _$q_: ng.IQService,
                          $rootScope: ng.IRootScopeService) => {
    accountService = _accountService_;
    dockerImageReader = _dockerImageReader_;
    $componentController = _$componentController_;
    $q = _$q_;
    $scope = $rootScope.$new();
  }));

  let initialize = (accounts: IAccount[], images: IDockerImage[]) => {
    spyOn(accountService, 'listAccounts').and.returnValue($q.when(accounts));
    spyOn(dockerImageReader, 'findImages').and.returnValue($q.when(images));
    $ctrl = <DockerImageAndTagSelectorController>$componentController(
      'dockerImageAndTagSelector',
      { accountService, dockerImageReader },
      { organization, registry, repository, tag, account, showRegistry }
    );
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
      const accounts: IAccount[] = [
        { name: 'account1', accountId: '1', requiredGroupMembership: [], type: 'docker' }
      ];
      const images: IDockerImage[] = [
        { account: 'account1', registry: 'registry1', repository: 'my-org/my-repo', tag: 'latest' }
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
      { account: 'account2', registry: 'registry1', repository: 'my-org/my-repo', tag: 'latest' }
    ];

    initialize(accounts, images);
    expect($ctrl.accounts).toEqual(['account1', 'account2']);
    expect($ctrl.organizations).toEqual(['my-org']);
    expect($ctrl.repositories).toEqual(['my-org/my-repo']);
    expect($ctrl.tags).toEqual(['latest', '1.1']);
  });
});
