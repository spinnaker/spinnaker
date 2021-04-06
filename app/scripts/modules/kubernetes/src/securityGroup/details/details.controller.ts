import { StateService } from '@uirouter/angularjs';
import { IController, IQService, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import {
  Application,
  IManifest,
  ISecurityGroupDetail,
  ManifestReader,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  SETTINGS,
} from '@spinnaker/core';

import { IKubernetesSecurityGroup } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

interface ISecurityGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesSecurityGroupDetailsController implements IController {
  public state = { loading: true };
  public securityGroup: IKubernetesSecurityGroup;
  public manifest: IManifest;

  public static $inject = [
    '$uibModal',
    '$state',
    '$scope',
    'securityGroupReader',
    'resolvedSecurityGroup',
    'app',
    '$q',
  ];
  constructor(
    private $uibModal: IModalService,
    private $state: StateService,
    private $scope: IScope,
    private securityGroupReader: SecurityGroupReader,
    resolvedSecurityGroup: ISecurityGroupFromStateParams,
    private app: Application,
    private $q: IQService,
  ) {
    const dataSource = app.getDataSource('securityGroups');
    dataSource
      .ready()
      .then(() => {
        this.extractSecurityGroup(resolvedSecurityGroup);
        this.$scope.isDisabled = !SETTINGS.kubernetesAdHocInfraWritesEnabled;
        dataSource.onRefresh(this.$scope, () => this.extractSecurityGroup(resolvedSecurityGroup));
      })
      .catch(() => this.autoClose());
  }

  public deleteSecurityGroup(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.securityGroup.name,
          namespace: this.securityGroup.region,
          account: this.securityGroup.account,
        },
        application: this.app,
        manifestController: (): string => null,
      },
    });
  }

  public editSecurityGroup(): void {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.manifest.manifest,
      this.securityGroup.moniker,
      this.securityGroup.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
    });
  }

  private extractSecurityGroup({ accountId, name, region }: ISecurityGroupFromStateParams): void {
    this.$q
      .all([
        this.securityGroupReader.getSecurityGroupDetails(
          this.app,
          accountId,
          'kubernetes',
          region,
          '', // unused vpc id
          name,
        ),
        ManifestReader.getManifest(accountId, region, name),
      ])
      .then(([securityGroup, manifest]: [ISecurityGroupDetail, IManifest]) => {
        if (!securityGroup) {
          return this.autoClose();
        }
        this.securityGroup = securityGroup as IKubernetesSecurityGroup;
        this.manifest = manifest;
        this.state.loading = false;
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

export const KUBERNETES_SECURITY_GROUP_DETAILS_CTRL = 'spinnaker.kubernetes.securityGroupDetails.controller';
module(KUBERNETES_SECURITY_GROUP_DETAILS_CTRL, [SECURITY_GROUP_READER]).controller(
  'kubernetesV2SecurityGroupDetailsCtrl',
  KubernetesSecurityGroupDetailsController,
);
