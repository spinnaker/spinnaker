import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { StateService } from '@uirouter/angularjs';

import { Application, ISecurityGroupDetail, SECURITY_GROUP_READER, SecurityGroupReader } from '@spinnaker/core';

import { IKubernetesSecurityGroup } from './IKubernetesSecurityGroup';
import { KubernetesManifestCommandBuilder } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';
import { ManifestWizard } from 'kubernetes/v2/manifest/wizard/ManifestWizard';

interface ISecurityGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesSecurityGroupDetailsController implements IController {
  public state = { loading: true };
  private securityGroupFromParams: ISecurityGroupFromStateParams;
  public securityGroup: IKubernetesSecurityGroup;

  constructor(
    private $uibModal: IModalService,
    private $state: StateService,
    private $scope: IScope,
    private securityGroupReader: SecurityGroupReader,
    resolvedSecurityGroup: ISecurityGroupFromStateParams,
    private app: Application,
  ) {
    'ngInject';
    this.securityGroupFromParams = resolvedSecurityGroup;
    this.extractSecurityGroup();
  }

  public deleteSecurityGroup(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.securityGroupFromParams.name,
          namespace: this.securityGroupFromParams.region,
          account: this.securityGroupFromParams.accountId,
        },
        application: this.app,
        manifestController: (): string => null,
      },
    });
  }

  public editSecurityGroup(): void {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.securityGroup.manifest,
      this.securityGroup.moniker,
      this.securityGroupFromParams.accountId,
    ).then(builtCommand => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
    });
  }

  private extractSecurityGroup(): void {
    this.securityGroupReader
      .getSecurityGroupDetails(
        this.app,
        this.securityGroupFromParams.accountId,
        'kubernetes',
        this.securityGroupFromParams.region,
        '', // unused vpc id
        this.securityGroupFromParams.name,
      )
      .then((rawSecurityGroup: ISecurityGroupDetail) => {
        this.securityGroup = rawSecurityGroup as IKubernetesSecurityGroup;
        this.securityGroup.namespace = this.securityGroup.region;
        this.securityGroup.displayName = this.securityGroup.manifest.metadata.name;
        this.securityGroup.kind = this.securityGroup.manifest.kind;
        this.securityGroup.apiVersion = this.securityGroup.manifest.apiVersion;
        this.state.loading = false;
      })
      .catch(() => {
        this.autoClose();
      });
  }

  private autoClose(): void {
    if (this.$scope.$$destroyed) {
      return;
    } else {
      this.$state.params.allowModalToStayOpen = true;
      this.$state.go('^', null, { location: 'replace' });
    }
  }
}

export const KUBERNETES_V2_SECURITY_GROUP_DETAILS_CTRL = 'spinnaker.kubernetes.v2.securityGroupDetails.controller';
module(KUBERNETES_V2_SECURITY_GROUP_DETAILS_CTRL, [SECURITY_GROUP_READER]).controller(
  'kubernetesV2SecurityGroupDetailsCtrl',
  KubernetesSecurityGroupDetailsController,
);
