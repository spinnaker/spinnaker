/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.slb.model.v20140515.DeleteLoadBalancerRequest;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersRequest;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import groovy.util.logging.Slf4j;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class DeleteAliCloudLoadBalancerClassicAtomicOperation implements AtomicOperation<Void> {

  private final Logger log =
      LoggerFactory.getLogger(DeleteAliCloudLoadBalancerClassicAtomicOperation.class);

  private final UpsertAliCloudLoadBalancerDescription description;

  private final ClientFactory clientFactory;

  public DeleteAliCloudLoadBalancerClassicAtomicOperation(
      UpsertAliCloudLoadBalancerDescription description, ClientFactory clientFactory) {
    this.description = description;
    this.clientFactory = clientFactory;
  }

  @Override
  public Void operate(List priorOutputs) {

    IAcsClient client =
        clientFactory.createClient(
            description.getRegion(),
            description.getCredentials().getAccessKeyId(),
            description.getCredentials().getAccessSecretKey());
    DescribeLoadBalancersResponse.LoadBalancer loadBalancerT = null;
    DescribeLoadBalancersRequest queryRequest = new DescribeLoadBalancersRequest();
    queryRequest.setLoadBalancerName(description.getLoadBalancerName());
    DescribeLoadBalancersResponse queryResponse;
    try {
      queryResponse = client.getAcsResponse(queryRequest);
      description.setLoadBalancerId(queryRequest.getLoadBalancerId());

      if (queryResponse.getLoadBalancers().size() > 0) {
        loadBalancerT = queryResponse.getLoadBalancers().get(0);
      }

    } catch (ServerException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    } catch (ClientException e) {
      log.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    }

    if (loadBalancerT != null) {
      DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest();
      request.setLoadBalancerId(loadBalancerT.getLoadBalancerId());
      try {
        client.getAcsResponse(request);
      } catch (ServerException e) {
        log.info(e.getMessage());
        throw new AliCloudException(e.getMessage());
      } catch (ClientException e) {
        log.info(e.getMessage());
        throw new AliCloudException(e.getMessage());
      }
    }

    return null;
  }
}
