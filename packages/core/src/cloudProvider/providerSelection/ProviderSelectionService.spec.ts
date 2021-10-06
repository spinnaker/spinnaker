import type { IQService, IRootScopeService, IScope } from 'angular';
import { mock } from 'angular';

import { CloudProviderRegistry } from '..';
import { ProviderSelectionModal } from './ProviderSelectionModal';
import { ProviderSelectionService } from './ProviderSelectionService';
import type { IAccountDetails } from '../../account/AccountService';
import { AccountService } from '../../account/AccountService';
import type { Application } from '../../application/application.model';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { SETTINGS } from '../../config/settings';

function fakeAccount(provider: string): IAccountDetails {
  return {
    cloudProvider: provider,
    accountId: 'foobaraccount',
    name: 'foo-bar-account',
    requiredGroupMembership: [],
    type: 'foobaraccount',
    accountType: 'foo',
    authorized: true,
    challengeDestructiveActions: true,
    environment: 'foo-env',
    primaryAccount: true,
    regions: [],
  };
}

describe('ProviderSelectionService: API', () => {
  // required to ensure registry provider is available
  let $q: IQService, $scope: IScope;

  beforeEach(
    mock.inject((_$q_: IQService, $rootScope: IRootScopeService) => {
      $q = _$q_;
      $scope = $rootScope.$new();
    }),
  );

  let hasValue: boolean, accounts: IAccountDetails[];
  beforeEach(() => {
    spyOn(AccountService, 'applicationAccounts').and.callFake(() => $q.when(accounts));
    spyOn(CloudProviderRegistry, 'hasValue').and.callFake(() => hasValue);
    spyOn(ProviderSelectionModal, 'show').and.returnValue($q.when('modalProvider') as any);
  });

  beforeEach(() => {
    SETTINGS.providers.testProvider = {
      defaults: {
        account: 'testProviderAccount',
        region: 'testProviderRegion',
      },
    };
  });

  afterEach(SETTINGS.resetToOriginal);

  let application: Application, config: any;
  beforeEach(() => {
    hasValue = false;
    accounts = [];
    delete SETTINGS.defaultProvider;

    application = ApplicationModelBuilder.createApplicationForTests('app');
    application.attributes = { cloudProviders: 'testProvider' };

    config = {
      name: 'testProvider',
      securityGroup: {},
    };
  });

  it('should use the specified, default provider if the requested provider cannot be found', () => {
    let provider = '';
    SETTINGS.defaultProvider = 'defaultProvider';

    CloudProviderRegistry.registerProvider('fakeProvider', config);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('defaultProvider');
  });

  it('should use "aws" as the default provider if the requested provider cannot be found and there is no default set', () => {
    let provider = '';
    CloudProviderRegistry.registerProvider('fakeProvider', config);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('aws');
  });

  it('should return the specified provider if that provider is registered', () => {
    let provider = '';
    hasValue = true;
    accounts = [fakeAccount('testProvider')];
    CloudProviderRegistry.registerProvider('testProvider', config);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('testProvider');
  });

  it('should return the "use provider" value instead of the configured one if one is specified', () => {
    let provider = '';
    hasValue = true;
    accounts = [fakeAccount('testProvider')];
    config.securityGroup.useProvider = 'titus';
    CloudProviderRegistry.registerProvider('testProvider', config);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('titus');
  });

  it('should use the specified provider from the configuration', () => {
    let provider = '';
    hasValue = true;
    accounts = [fakeAccount('aws'), fakeAccount('titus')];
    CloudProviderRegistry.registerProvider('aws', { securityGroup: {} } as any);
    CloudProviderRegistry.registerProvider('titus', { securityGroup: { useProvider: 'aws' } } as any);

    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('aws');
  });

  it('should use the provider "selected" from the "modal"', () => {
    let provider = '';
    hasValue = true;
    accounts = [fakeAccount('aws'), fakeAccount('titus'), fakeAccount('testProvider')];
    CloudProviderRegistry.registerProvider('aws', { securityGroup: {} } as any);
    CloudProviderRegistry.registerProvider('titus', { securityGroup: { useProvider: 'aws' } } as any);
    CloudProviderRegistry.registerProvider('testProvider', config);

    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('modalProvider');
  });

  it('should not return a filtered provider', () => {
    let provider = '';
    hasValue = true;
    const k8s = fakeAccount('kubernetes');
    accounts = [k8s];
    CloudProviderRegistry.registerProvider('kubernetes', config);
    SETTINGS.defaultProvider = 'defaultProvider';

    const filterFn = (_app: Application, acc: IAccountDetails) => acc.cloudProvider !== 'kubernetes';
    ProviderSelectionService.selectProvider(application, 'securityGroup', filterFn).then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('defaultProvider');
  });

  it('should not launch a modal if one of two providers is filtered out by filter function', () => {
    let provider = '';
    hasValue = true;
    const k8s = fakeAccount('kubernetes');
    accounts = [k8s, fakeAccount('titus')];
    CloudProviderRegistry.registerProvider('titus', config);
    CloudProviderRegistry.registerProvider('kubernetes', config);

    const filterFn = (_app: Application, acc: IAccountDetails) => acc.cloudProvider !== 'kubernetes';
    ProviderSelectionService.selectProvider(application, 'securityGroup', filterFn).then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('titus');
  });

  it('should return k8s provider in case the adHocInfrastructureWritesEnabled is set to true and is the only provider configured', () => {
    let provider = '';
    hasValue = true;
    const k8s = fakeAccount('kubernetes');
    k8s.type = 'kubernetes';
    accounts = [k8s];
    const configuration = {
      name: 'Kubernetes',
      adHocInfrastructureWritesEnabled: true,
    };
    CloudProviderRegistry.registerProvider('kubernetes', configuration);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('kubernetes');
  });

  it('should use "aws" as the default provider in case the only provider is k8s and the adHocInfrastructureWritesEnabled is set to false', () => {
    let provider = '';
    hasValue = true;
    const k8s = fakeAccount('kubernetes');
    k8s.type = 'kubernetes';
    accounts = [k8s];
    const configuration = {
      name: 'Kubernetes',
      adHocInfrastructureWritesEnabled: false,
    };
    CloudProviderRegistry.registerProvider('kubernetes', configuration);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('aws');
  });

  it('should use "gce" as the default provider in case the only provider is gce and the adHocInfrastructureWritesEnabled is not specified', () => {
    let provider = '';
    hasValue = true;
    const k8s = fakeAccount('gce');
    k8s.type = 'gce';
    accounts = [k8s];
    const configuration = {
      name: 'Kubernetes',
    };
    CloudProviderRegistry.registerProvider('gce', configuration);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('gce');
  });

  it('should not use "k8s" as an option for the modal when the k8s adHocInfrastructureWritesEnabled is set to false and there are others providers', () => {
    let provider = '';
    hasValue = true;
    const k8s = fakeAccount('kubernetes');
    k8s.type = 'kubernetes';
    accounts = [k8s, fakeAccount('gce')];
    const configuration = {
      name: 'Kubernetes',
      adHocInfrastructureWritesEnabled: false,
    };
    CloudProviderRegistry.registerProvider('kubernetes', configuration);
    CloudProviderRegistry.registerProvider('gce', config);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('gce');
  });

  it('should use "modalProvider" when the k8s adHocInfrastructureWritesEnabled is set to true and there are others providers', () => {
    let provider = '';
    hasValue = true;
    const k8s = fakeAccount('kubernetes');
    k8s.type = 'kubernetes';
    accounts = [k8s, fakeAccount('gce')];
    const configuration = {
      name: 'Kubernetes',
      adHocInfrastructureWritesEnabled: true,
    };
    CloudProviderRegistry.registerProvider('kubernetes', configuration);
    CloudProviderRegistry.registerProvider('gce', config);
    ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('modalProvider');
  });

  // Unit tests for the isDisabled function, used to disable and enable buttons that create infrastructure ad-hoc operations
  // in the core module (Create Server Group, Create Load Balancer, Create Firewall, Create Function)
  describe('Toggle Infrastructure Ad-hoc Operations', function () {
    // If an application is configured to only have kubernetes as a cloud provider and only one account exists, which is a kubernetes account,
    // then show the create infrastructure buttons if adHocInfrastructureWritesEnabled is set to true
    it('create infrastructure buttons are enabled for applications with kubernetes cloud provider when adHocInfrastructureWritesEnabled is set to true', () => {
      let isDisabled_result = false;
      hasValue = true;
      const k8s_account = fakeAccount('kubernetes');
      k8s_account.type = 'kubernetes';

      accounts = [k8s_account];
      const configuration = {
        name: 'kubernetes',
        adHocInfrastructureWritesEnabled: true,
      };
      CloudProviderRegistry.registerProvider('kubernetes', configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(false);
    });

    // If an application is configured to only have kubernetes as a cloud provider and only one account exists, which is a kubernetes account,
    // then disable the create infrastructure buttons when adHocInfrastructureWritesEnabled is set to false
    it('disable create infrastructure buttons for kubernetes applications when adHocInfrastructureWritesEnabled is false', () => {
      let isDisabled_result = false;
      hasValue = true;
      const k8s_account = fakeAccount('kubernetes');
      k8s_account.type = 'kubernetes';

      accounts = [k8s_account];
      const configuration = {
        name: 'kubernetes',
        adHocInfrastructureWritesEnabled: false,
      };
      CloudProviderRegistry.registerProvider('kubernetes', configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(true);
    });

    // If the application is configured to have multiple cloud providers (kuberentes and gce) and different accounts exist with
    // different cloud providers (kuberentes and gce), then create infrastructure buttons appear even though adHocInfrastructureWritesEnabled is false.
    // This is because the buttons allow for ad-hoc operations for the non-kubernetes provider (GCE in this case)
    it('create infrastructure buttons are enabled for apps with a cloud provider that does not have its ad-hoc operation disabled', () => {
      let provider = '';
      hasValue = true;
      let isDisabled_result = false;
      const k8s_account = fakeAccount('kubernetes');
      k8s_account.type = 'kubernetes';
      accounts = [k8s_account, fakeAccount('gce')];
      const kubernetes_configuration = {
        name: 'Kubernetes',
        adHocInfrastructureWritesEnabled: false,
      };
      CloudProviderRegistry.registerProvider('kubernetes', kubernetes_configuration);
      CloudProviderRegistry.registerProvider('gce', config);
      ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
        provider = _provider;
      });
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(false);
      expect(provider).toBe('gce');
    });

    // If an application is configured to have kubernetes as a cloud provider, and there are multiple kubernetes accounts, then the create
    // infrastructure buttons are disabled if adHocInfrastructureWritesEnabled is false
    it('create infrastructure buttons are disabled when all accounts have cloud providers with ad-hoc operations disabled', () => {
      let isDisabled_result = false;
      hasValue = true;
      const k8s_account_1 = fakeAccount('kubernetes');
      k8s_account_1.type = 'kubernetes';
      const k8s_account_2 = fakeAccount('kubernetes');
      k8s_account_2.type = 'kubernetes';

      accounts = [k8s_account_1, k8s_account_2];
      const configuration = {
        name: 'kubernetes',
        adHocInfrastructureWritesEnabled: false,
      };
      CloudProviderRegistry.registerProvider('kubernetes', configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(true);
    });

    // If an application is configured to only have aws as a cloud provider and only one account exists, which is an aws account,
    // then disable the create infrastructure buttons if adHocInfrastructureWritesEnabled is set to false
    it('create infrastructure buttons are enabled for applications with aws cloud provider when adHocInfrastructureWritesEnabled is set to false', () => {
      let isDisabled_result = false;
      hasValue = true;
      const aws_account = fakeAccount('aws');
      aws_account.type = 'aws';

      accounts = [aws_account];
      const configuration = {
        name: 'aws',
        adHocInfrastructureWritesEnabled: false,
      };
      CloudProviderRegistry.registerProvider('aws', configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(true);
    });

    // If an application is configured to only have aws as a cloud provider and only one account exists, which is an aws account,
    // then enable the create infrastructure buttons if adHocInfrastructureWritesEnabled is set to true
    it('create infrastructure buttons are enabled for applications with aws cloud provider when adHocInfrastructureWritesEnabled is set to true', () => {
      let isDisabled_result = false;
      hasValue = true;
      const aws_account = fakeAccount('aws');
      aws_account.type = 'aws';

      accounts = [aws_account];
      const configuration = {
        name: 'aws',
        adHocInfrastructureWritesEnabled: false,
      };
      CloudProviderRegistry.registerProvider('aws', configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(true);
    });

    // If an application is configured to have kubernetes and aws as cloud providers, and there are multiple kubernetes and aws accounts,
    // then the create infrastructure buttons are disabled if both aws and kubernetes providers have ad-hoc operations disabled
    it('create infrastructure buttons are disabled when all accounts have different cloud providers with ad-hoc operations disabled', () => {
      let isDisabled_result = false;
      hasValue = true;
      const k8s_account = fakeAccount('kubernetes');
      k8s_account.type = 'kubernetes';
      const aws_account = fakeAccount('aws');
      aws_account.type = 'aws';

      accounts = [k8s_account, aws_account];
      const k8s_configuration = {
        name: 'kubernetes',
        adHocInfrastructureWritesEnabled: false,
      };
      const aws_configuration = {
        name: 'aws',
        adHocInfrastructureWritesEnabled: false,
      };
      CloudProviderRegistry.registerProvider('kubernetes', k8s_configuration);
      CloudProviderRegistry.registerProvider('aws', aws_configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(true);
    });

    // If an application is configured to have kubernetes and aws as cloud providers, and there are multiple kubernetes and aws accounts,
    // then the create infrastructure buttons are enabled if both aws and kubernetes providers have ad-hoc operations enabled the resulting
    // element shown when the buttons are clicked is modalProvider (used to allow infrastructure creation for multiple providers, aws and k8s)
    it('create infrastructure buttons are disabled when all accounts have different cloud providers with ad-hoc operations disabled', () => {
      let isDisabled_result = false;
      let provider = '';
      hasValue = true;
      const k8s_account = fakeAccount('kubernetes');
      k8s_account.type = 'kubernetes';
      const aws_account = fakeAccount('aws');
      aws_account.type = 'aws';

      accounts = [k8s_account, aws_account];
      const k8s_configuration = {
        name: 'kubernetes',
        adHocInfrastructureWritesEnabled: true,
      };
      const aws_configuration = {
        name: 'aws',
        adHocInfrastructureWritesEnabled: true,
      };
      CloudProviderRegistry.registerProvider('kubernetes', k8s_configuration);
      CloudProviderRegistry.registerProvider('aws', aws_configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
        provider = _provider;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(false);
      expect(provider).toBe('modalProvider');
    });

    // If an application is configured to have kubernetes and aws as cloud providers, and there are multiple kubernetes and aws accounts,
    // then the create infrastructure buttons are enabled if one of aws or kubernetes provider has ad-hoc operations enabled
    // the selected provider seen in the modal opened is for aws providers
    it('create infrastructure buttons are enabled when accounts have different providers, but one providers has ad-hoc operations enabled', () => {
      let isDisabled_result = false;
      let provider = '';

      hasValue = true;
      const k8s_account = fakeAccount('kubernetes');
      k8s_account.type = 'kubernetes';
      const aws_account = fakeAccount('aws');
      aws_account.type = 'aws';

      accounts = [k8s_account, aws_account];
      const k8s_configuration = {
        name: 'kubernetes',
        adHocInfrastructureWritesEnabled: false,
      };
      const aws_configuration = {
        name: 'aws',
        adHocInfrastructureWritesEnabled: true,
      };
      CloudProviderRegistry.registerProvider('kubernetes', k8s_configuration);
      CloudProviderRegistry.registerProvider('aws', aws_configuration);
      ProviderSelectionService.isDisabled(application).then((isDisable) => {
        isDisabled_result = isDisable;
      });
      ProviderSelectionService.selectProvider(application, 'securityGroup').then((_provider) => {
        provider = _provider;
      });
      $scope.$digest();
      expect(isDisabled_result).toBe(false);
      expect(provider).toBe('aws');
    });
  });
});
