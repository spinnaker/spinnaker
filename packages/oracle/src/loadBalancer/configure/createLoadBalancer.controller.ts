import UIROUTER_ANGULARJS, { StateService } from '@uirouter/angularjs';
import { IController, module } from 'angular';
import ANGULAR_UI_BOOTSTRAP, { IModalServiceInstance } from 'angular-ui-bootstrap';
import { cloneDeep, trimEnd } from 'lodash';

import {
  AccountService,
  Application,
  IAccountDetails,
  ILoadBalancer,
  INetwork,
  IRegion,
  ISubnet,
  LoadBalancerWriter,
  NameUtils,
  NetworkReader,
  SubnetReader,
  TaskMonitor,
} from '@spinnaker/core';
import {
  IOracleBackEndSet,
  IOracleListener,
  IOracleListenerCertificate,
  IOracleLoadBalancer,
  IOracleSubnet,
  LoadBalancingPolicy,
} from '../../domain/IOracleLoadBalancer';

import { ORACLE_LOAD_BALANCER_TRANSFORMER, OracleLoadBalancerTransformer } from '../loadBalancer.transformer';

export class OracleLoadBalancerController implements IController {
  public oracle = 'oracle';
  public shapes: string[] = ['100Mbps', '400Mbps', '8000Mbps']; // TODO desagar use listShapes to get this from clouddriver later
  public loadBalancingPolicies: string[] = Object.keys(LoadBalancingPolicy).map((k) => (LoadBalancingPolicy as any)[k]);
  public pages: { [key: string]: any } = {
    properties: require('./createLoadBalancerProperties.html'),
    listeners: require('./listeners.html'),
    backendSets: require('./backendSets.html'),
    certificates: require('./certificates.html'),
  };

  public state: { [key: string]: boolean } = {
    accountsLoaded: false,
    submitting: false,
  };

  public allVnets: INetwork[];
  public allSubnets: IOracleSubnet[];
  public filteredVnets: INetwork[];
  public filteredSubnets: ISubnet[];
  public filteredSubnetsByType: ISubnet[];
  public selectedVnet: INetwork;
  public selectedSubnets: IOracleSubnet[];
  public numSubnetsAllowed = 1;
  public listeners: IOracleListener[] = [];
  public backendSets: IOracleBackEndSet[] = [];
  public certificates: IOracleListenerCertificate[] = [];

  public static $inject = [
    '$scope',
    '$uibModalInstance',
    '$state',
    'oracleLoadBalancerTransformer',
    'application',
    'loadBalancer',
    'isNew',
  ];
  constructor(
    private $scope: ng.IScope,
    private $uibModalInstance: IModalServiceInstance,
    private $state: StateService,
    private oracleLoadBalancerTransformer: OracleLoadBalancerTransformer,
    private application: Application,
    private loadBalancer: IOracleLoadBalancer,
    private isNew: boolean,
  ) {
    this.initializeController();
  }

  public onApplicationRefresh() {
    // If the user has already closed the modal, do not navigate to the new details view
    if (this.$scope.$$destroyed) {
      return;
    }
    this.$uibModalInstance.close();
    const newStateParams = {
      name: this.loadBalancer.name,
      accountId: this.loadBalancer.account,
      region: this.loadBalancer.region,
      provider: 'oracle',
    };

    if (!this.$state.includes('**.loadBalancerDetails')) {
      this.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      this.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }

  public onTaskComplete() {
    this.application.loadBalancers.refresh();
    this.application.loadBalancers.onNextRefresh(this.$scope, this.onApplicationRefresh);
  }

  public initializeCreateMode() {
    AccountService.listAccounts(this.oracle).then((accounts: IAccountDetails[]) => {
      this.$scope.accounts = accounts; // TODO desagar does this need to be in $scope?
      this.state.accountsLoaded = true;
      this.$scope.state = this.state;
      this.accountUpdated();
    });
    this.loadVnets();
    this.loadSubnets();
  }

  private initControllerFromLoadBalancerCmd() {
    this.numSubnetsAllowed = this.calcNumSubnetsAllowed();
    if (this.$scope.loadBalancerCmd.listeners) {
      Object.keys(this.$scope.loadBalancerCmd.listeners).forEach((lis) => {
        this.listeners.push(this.$scope.loadBalancerCmd.listeners[lis]);
      });
    }
    if (this.$scope.loadBalancerCmd.backendSets) {
      Object.keys(this.$scope.loadBalancerCmd.backendSets).forEach((b) => {
        this.backendSets.push(this.$scope.loadBalancerCmd.backendSets[b]);
      });
    }
    if (this.$scope.loadBalancerCmd.certificates) {
      Object.keys(this.$scope.loadBalancerCmd.certificates).forEach((b) => {
        this.certificates.push(this.$scope.loadBalancerCmd.certificates[b]);
      });
    }
  }

  public initializeController() {
    if (this.loadBalancer) {
      this.$scope.loadBalancerCmd = this.oracleLoadBalancerTransformer.convertLoadBalancerForEditing(this.loadBalancer);
      this.initControllerFromLoadBalancerCmd();
      if (this.isNew) {
        const nameParts = NameUtils.parseLoadBalancerName(this.loadBalancer.name);
        this.$scope.loadBalancerCmd.stack = nameParts.stack;
        this.$scope.loadBalancerCmd.detail = nameParts.freeFormDetails;
        delete this.$scope.loadBalancerCmd.name;
      }
    } else {
      this.$scope.loadBalancerCmd = this.oracleLoadBalancerTransformer.constructNewLoadBalancerTemplate(
        this.application,
      );
    }
    this.$scope.prevBackendSetNames = [];
    this.$scope.prevCertNames = [];
    if (this.isNew) {
      this.updateName();
      this.updateLoadBalancerNames();
      this.initializeCreateMode();
    }
    this.$scope.taskMonitor = new TaskMonitor({
      application: this.application,
      title: (this.isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: this.$uibModalInstance,
      onTaskComplete: this.onTaskComplete,
    });
  }

  public updateLoadBalancerNames() {
    const account = this.$scope.loadBalancerCmd.credentials;
    const region = this.$scope.loadBalancerCmd.region;

    const accountLoadBalancerNamesByRegion: { [key: string]: string[] } = {};
    this.application
      .getDataSource('loadBalancers')
      .refresh(true)
      .then(() => {
        const loadBalancers: ILoadBalancer[] = this.application.loadBalancers.data;
        loadBalancers.forEach((loadBalancer) => {
          if (loadBalancer.account === account) {
            accountLoadBalancerNamesByRegion[loadBalancer.region] =
              accountLoadBalancerNamesByRegion[loadBalancer.region] || [];
            accountLoadBalancerNamesByRegion[loadBalancer.region].push(loadBalancer.name);
          }
        });

        this.$scope.existingLoadBalancerNames = accountLoadBalancerNamesByRegion[region] || [];
      });
  }

  public validateBeforeSubmit() {
    return this.propertiesValid() && this.listenersValid();
  }

  /**
   * Used to prevent form submission if listeners are invalid
   * Currently it calls the two validations applicable to listeners.
   * @returns {boolean}
   */
  public listenersValid() {
    return this.listenersUniqueProtocolPort() && this.listenersBackendSetsExist() && this.listenersCertificatesExist();
  }

  /**
   * Used to prevent form submission if the properties section is invalid
   * Current the only validation is for subnet count.
   */
  public propertiesValid() {
    return this.selectedSubnets && this.selectedSubnets.length === this.calcNumSubnetsAllowed();
  }

  public listenersUniqueProtocolPort() {
    // validate that listeners have unique protocol/port combination
    const countsMap: { [key: string]: number } = {};
    this.listeners.reduce((counts, listener) => {
      const protocolPort = listener.protocol + '_' + listener.port;
      counts[protocolPort] = counts[protocolPort] ? counts[protocolPort] + 1 : 1;
      return counts;
    }, countsMap);
    // There should be no protocol/port combo in the countsMap with a count > 1
    return (
      Object.keys(countsMap).filter((key) => {
        return countsMap[key] > 1;
      }).length === 0
    );
  }

  public listenersBackendSetsExist() {
    // validate that the listeners' selected backend sets must exist. This is needed because Angular
    // does not clear the selected backendSet from the drop down if the backend set is deleted.
    const listenersWithNonExistentBackendSet: IOracleListener[] = this.listeners.filter(
      (listener) => !this.backendSets.find((backendSet) => backendSet.name === listener.defaultBackendSetName),
    );
    return listenersWithNonExistentBackendSet.length === 0;
  }

  public listenersCertificatesExist() {
    // validate that the listeners' selected certificate names exist. This is needed because Angular
    // does not clear the selected certificate from the drop down if the certificate is deleted.
    const listenersWithNonExistentCertificate: IOracleListener[] = this.listeners.filter(
      (listener) =>
        listener.sslConfiguration &&
        !this.certificates.find((cert) => cert.certificateName === listener.sslConfiguration.certificateName),
    );
    return listenersWithNonExistentCertificate.length === 0;
  }

  public updateName() {
    this.$scope.loadBalancerCmd.name = this.getName();
  }

  public getName() {
    const lb = this.$scope.loadBalancerCmd;
    const lbName = [this.application.name, lb.stack || '', lb.detail || ''].join('-');
    return trimEnd(lbName, '-');
  }

  public accountUpdated() {
    this.loadRegionsForAccount();
  }

  public regionUpdated() {
    this.updateLoadBalancerNames();
    this.updateVnets();
  }

  public loadRegionsForAccount() {
    AccountService.getRegionsForAccount(this.$scope.loadBalancerCmd.credentials).then((regions: IRegion[]) => {
      this.$scope.regions = regions; // TODO desagar does this need to be in $scope?
      if (regions.length === 1) {
        this.$scope.loadBalancerCmd.region = regions[0].name;
        this.regionUpdated();
      }
    });
  }

  public loadVnets() {
    NetworkReader.listNetworksByProvider(this.oracle).then((vnets: INetwork[]) => {
      this.allVnets = vnets || [];
      if (this.$scope.loadBalancerCmd.region) {
        this.updateVnets();
      }
    });
  }

  public loadSubnets() {
    SubnetReader.listSubnetsByProvider(this.oracle).then((subnets: IOracleSubnet[]) => {
      this.allSubnets = subnets || [];
    });
  }

  public updateVnets() {
    const account = this.$scope.loadBalancerCmd.credentials;
    const region = this.$scope.loadBalancerCmd.region;
    this.filteredVnets = this.allVnets.filter((vnet: INetwork) => {
      return vnet.account === account && vnet.region === region;
    });
  }

  public updateSubnets(network: INetwork) {
    this.selectedSubnets = [];
    this.$scope.loadBalancerCmd.subnetIds = [];
    this.filteredSubnets = this.allSubnets.filter((subnet: IOracleSubnet) => {
      return subnet.vcnId === network.id;
    });
    this.filteredSubnetsByType = cloneDeep(this.filteredSubnets);
  }

  public selectedVnetChanged(network: INetwork) {
    this.selectedVnet = network;
    this.$scope.loadBalancerCmd.vpcId = network.id;
    this.updateSubnets(network);
  }

  public selectedSubnetsChanged(selectedSubnet: IOracleSubnet) {
    if (selectedSubnet.availabilityDomain) {
      this.filteredSubnetsByType = this.filteredSubnets.filter((subnet: IOracleSubnet) => {
        return !!subnet.availabilityDomain;
      });
    } else {
      this.filteredSubnetsByType = [];
    }
  }

  public selectedSubnetRemoved() {
    if (this.selectedSubnets.length === 0) {
      this.filteredSubnetsByType = cloneDeep(this.filteredSubnets);
    }
    if (this.selectedSubnets.length === 1 && this.selectedSubnets[0].availabilityDomain) {
      this.filteredSubnetsByType = this.filteredSubnets.filter((subnet: IOracleSubnet) => {
        return !!subnet.availabilityDomain;
      });
    }
  }

  public isPrivateChanged() {
    this.numSubnetsAllowed = this.calcNumSubnetsAllowed();
  }

  public listenerIsSslChanged(listener: IOracleListener) {
    if (listener.isSsl) {
      listener.sslConfiguration = this.oracleLoadBalancerTransformer.constructNewSSLConfiguration();
    } else {
      listener.sslConfiguration = undefined;
    }
  }

  public calcNumSubnetsAllowed() {
    if (this.$scope.loadBalancerCmd.isPrivate) {
      return 1;
    }

    if (this.selectedSubnets && this.selectedSubnets.length === 1 && !this.selectedSubnets[0].availabilityDomain) {
      return 1;
    }

    return 2;
  }

  public getSubnetLimit() {}

  public removeListener(idx: number) {
    this.listeners.splice(idx, 1);
  }

  public addListener() {
    this.listeners.push(this.oracleLoadBalancerTransformer.constructNewListenerTemplate());
  }

  public removeBackendSet(idx: number) {
    const backendSet = this.backendSets[idx];
    this.backendSets.splice(idx, 1);
    this.$scope.prevBackendSetNames.splice(idx, 1);
    // Also clear the defaultBackendSetName field of any listeners who are using this backendSet
    this.listeners.forEach((lis) => {
      if (lis.defaultBackendSetName === backendSet.name) {
        lis.defaultBackendSetName = undefined;
      }
    });
  }

  public isBackendSetRemovable(idx: number): boolean {
    const backendSet = this.backendSets[idx];
    if (backendSet && backendSet.backends && backendSet.backends.length > 0) {
      return false;
    }
    let hasListener = false;
    this.listeners.forEach((lis) => {
      if (lis.defaultBackendSetName === backendSet.name) {
        hasListener = true;
      }
    });
    return !hasListener;
  }

  public addBackendSet() {
    const nameSuffix: number = this.backendSets.length + 1;
    const name: string = 'backendSet' + nameSuffix;
    this.$scope.prevBackendSetNames.push(name);
    this.backendSets.push(this.oracleLoadBalancerTransformer.constructNewBackendSetTemplate(name));
  }

  public backendSetNameChanged(idx: number) {
    const prevName = this.$scope.prevBackendSetNames && this.$scope.prevBackendSetNames[idx];
    if (prevName && prevName !== this.backendSets[idx].name) {
      this.listeners
        .filter((lis) => lis.defaultBackendSetName === prevName)
        .forEach((lis) => {
          lis.defaultBackendSetName = this.backendSets[idx].name;
        });
    }
  }

  public isCertRemovable(idx: number): boolean {
    const cert = this.certificates[idx];
    let hasListener = false;
    this.listeners.forEach((lis) => {
      if (lis.isSsl && lis.sslConfiguration && lis.sslConfiguration.certificateName === cert.certificateName) {
        hasListener = true;
      }
    });
    return !hasListener;
  }

  public removeCert(idx: number) {
    const cert = this.certificates[idx];
    this.certificates.splice(idx, 1);
    this.$scope.prevCertNames.splice(idx, 1);
    // Also clear the certificateName field of any listeners who are using this certificate
    this.listeners.forEach((lis) => {
      if (lis.sslConfiguration && lis.sslConfiguration.certificateName === cert.certificateName) {
        lis.sslConfiguration.certificateName = undefined;
      }
    });
  }

  public addCert() {
    const nameSuffix: number = this.certificates.length + 1;
    const name: string = 'certificate' + nameSuffix;
    this.$scope.prevCertNames.push(name);
    this.certificates.push(this.oracleLoadBalancerTransformer.constructNewCertificateTemplate(name));
  }

  public certNameChanged(idx: number) {
    const prevName = this.$scope.prevCertNames && this.$scope.prevCertNames[idx];
    if (prevName && prevName !== this.certificates[idx].certificateName) {
      this.listeners
        .filter((lis) => lis.sslConfiguration && lis.sslConfiguration.certificateName === prevName)
        .forEach((lis) => {
          lis.sslConfiguration.certificateName = this.certificates[idx].certificateName;
        });
    }
  }

  public submit() {
    const descriptor = this.isNew ? 'Create' : 'Update';

    this.$scope.taskMonitor.submit(() => {
      const params = {
        cloudProvider: 'oracle',
        application: this.application.name,
        clusterName: this.$scope.loadBalancerCmd.clusterName,
        resourceGroupName: this.$scope.loadBalancerCmd.clusterName,
        loadBalancerName: this.$scope.loadBalancerCmd.name,
        loadBalancerId: null as string,
      };
      if (this.loadBalancer && this.loadBalancer.id) {
        params.loadBalancerId = this.loadBalancer.id;
      }

      if (this.selectedVnet) {
        this.$scope.loadBalancerCmd.vpcId = this.selectedVnet.id;
      }

      if (this.selectedSubnets && this.selectedSubnets.length > 0) {
        this.$scope.loadBalancerCmd.subnetIds = this.selectedSubnets.map((subnet: IOracleSubnet) => {
          return subnet.id;
        });

        for (const subnet of this.selectedSubnets) {
          if (!this.$scope.loadBalancerCmd.subnetTypeMap) {
            this.$scope.loadBalancerCmd.subnetTypeMap = {
              [subnet.id]: !subnet.availabilityDomain ? 'Regional' : 'AD',
            };
          } else {
            this.$scope.loadBalancerCmd.subnetTypeMap[subnet.id] = !subnet.availabilityDomain ? 'Regional' : 'AD';
          }
        }
      }

      if (this.backendSets) {
        this.$scope.loadBalancerCmd.backendSets = this.backendSets.reduce(
          (backendSetsMap: { [name: string]: IOracleBackEndSet }, backendSet: IOracleBackEndSet) => {
            backendSetsMap[backendSet.name] = backendSet;
            return backendSetsMap;
          },
          {},
        );
      }

      if (this.listeners) {
        this.$scope.loadBalancerCmd.listeners = this.listeners.reduce(
          (listenersMap: { [name: string]: IOracleListener }, listener: IOracleListener) => {
            listener.name = listener.protocol + '_' + listener.port;
            listenersMap[listener.name] = listener;
            return listenersMap;
          },
          {},
        );
      }

      if (this.certificates) {
        this.$scope.loadBalancerCmd.certificates = this.certificates.reduce(
          (certMap: { [name: string]: IOracleListenerCertificate }, cert: IOracleListenerCertificate) => {
            certMap[cert.certificateName] = cert;
            if (!cert.isNew) {
              // existing certificate sends only the name
              certMap[cert.certificateName].publicCertificate = null;
            }
            return certMap;
          },
          {},
        );
      }

      this.$scope.loadBalancerCmd.type = 'upsertLoadBalancer';
      if (!this.$scope.loadBalancerCmd.vnet && !this.$scope.loadBalancerCmd.subnetType) {
        this.$scope.loadBalancerCmd.securityGroups = null;
      }

      return LoadBalancerWriter.upsertLoadBalancer(this.$scope.loadBalancerCmd, this.application, descriptor, params);
    });
  }

  public cancel() {
    this.$uibModalInstance.dismiss();
  }
}

export const ORACLE_LOAD_BALANCER_CREATE_CONTROLLER = 'spinnaker.oracle.loadBalancer.create.controller';
module(ORACLE_LOAD_BALANCER_CREATE_CONTROLLER, [
  ANGULAR_UI_BOOTSTRAP as any,
  UIROUTER_ANGULARJS,
  ORACLE_LOAD_BALANCER_TRANSFORMER,
]).controller('oracleCreateLoadBalancerCtrl', OracleLoadBalancerController);
