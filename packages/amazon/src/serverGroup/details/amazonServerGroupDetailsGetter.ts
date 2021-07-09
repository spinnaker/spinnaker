import { isEmpty } from 'lodash';
import { Observable } from 'rxjs';

import { AccountService, IServerGroupDetailsProps, ISubnet, ServerGroupReader, SubnetReader } from '@spinnaker/core';
import { IAmazonLoadBalancer, IAmazonServerGroup, IAmazonServerGroupView } from '../../domain';
import { AwsReactInjector } from '../../reactShims';

import { AutoScalingProcessService } from './scalingProcesses/AutoScalingProcessService';

function extractServerGroupSummary(props: IServerGroupDetailsProps): PromiseLike<IAmazonServerGroup> {
  const { app, serverGroup } = props;
  return app.ready().then(() => {
    let summary: IAmazonServerGroup = app.serverGroups.data.find((toCheck: IAmazonServerGroup) => {
      return (
        toCheck.name === serverGroup.name &&
        toCheck.account === serverGroup.accountId &&
        toCheck.region === serverGroup.region
      );
    });
    if (!summary) {
      app.loadBalancers.data.some((loadBalancer: IAmazonLoadBalancer) => {
        if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
          return loadBalancer.serverGroups.some((possibleServerGroup) => {
            if (possibleServerGroup.name === serverGroup.name) {
              summary = possibleServerGroup;
              return true;
            }
            return false;
          });
        }
        return false;
      });
    }
    return summary;
  });
}

export function amazonServerGroupDetailsGetter(
  props: IServerGroupDetailsProps,
  autoClose: () => void,
): Observable<IAmazonServerGroup> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<IAmazonServerGroupView>((observer) => {
    extractServerGroupSummary(props).then((summary) => {
      ServerGroupReader.getServerGroup(
        app.name,
        serverGroupInfo.accountId,
        serverGroupInfo.region,
        serverGroupInfo.name,
      ).then((details: IAmazonServerGroup) => {
        // it's possible the summary was not found because the clusters are still loading
        Object.assign(details, summary, { account: serverGroupInfo.accountId });

        const serverGroup = AwsReactInjector.awsServerGroupTransformer.normalizeServerGroupDetails(details);

        AccountService.getAccountDetails(serverGroup.account).then((accountDetails) => {
          serverGroup.accountDetails = accountDetails;
          observer.next(serverGroup);
        });

        if (!isEmpty(serverGroup)) {
          const vpc = serverGroup.asg ? serverGroup.asg.vpczoneIdentifier : '';

          if (vpc !== '') {
            const subnetId = vpc.split(',')[0];
            SubnetReader.listSubnets().then((subnets: ISubnet[]) => {
              const subnet = subnets.find((s) => s.id === subnetId);
              serverGroup.subnetType = subnet.purpose;
              observer.next(serverGroup);
            });
          }

          serverGroup.disabledDate = AutoScalingProcessService.getDisabledDate(serverGroup);

          observer.next(serverGroup);
        } else {
          autoClose();
        }
      }, autoClose);
    }, autoClose);
  });
}
