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
import com.aliyuncs.slb.model.v20140515.CreateLoadBalancerHTTPListenerRequest;
import com.aliyuncs.slb.model.v20140515.CreateLoadBalancerHTTPSListenerRequest;
import com.aliyuncs.slb.model.v20140515.CreateLoadBalancerRequest;
import com.aliyuncs.slb.model.v20140515.CreateLoadBalancerResponse;
import com.aliyuncs.slb.model.v20140515.CreateLoadBalancerTCPListenerRequest;
import com.aliyuncs.slb.model.v20140515.CreateLoadBalancerUDPListenerRequest;
import com.aliyuncs.slb.model.v20140515.DeleteLoadBalancerListenerRequest;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeRequest;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse.ListenerPortAndProtocal;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersRequest;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse;
import com.aliyuncs.slb.model.v20140515.SetLoadBalancerHTTPListenerAttributeRequest;
import com.aliyuncs.slb.model.v20140515.SetLoadBalancerHTTPSListenerAttributeRequest;
import com.aliyuncs.slb.model.v20140515.SetLoadBalancerStatusRequest;
import com.aliyuncs.slb.model.v20140515.SetLoadBalancerTCPListenerAttributeRequest;
import com.aliyuncs.slb.model.v20140515.SetLoadBalancerUDPListenerAttributeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.alicloud.model.Listener;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import groovy.util.logging.Slf4j;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class UpsertAliCloudLoadBalancerAtomicOperation implements AtomicOperation<Map> {

  private final Logger logger =
      LoggerFactory.getLogger(UpsertAliCloudLoadBalancerAtomicOperation.class);

  private final ObjectMapper objectMapper;

  private final UpsertAliCloudLoadBalancerDescription description;

  private final ClientFactory clientFactory;

  public UpsertAliCloudLoadBalancerAtomicOperation(
      UpsertAliCloudLoadBalancerDescription description,
      ObjectMapper objectMapper,
      ClientFactory clientFactory) {
    this.description = description;
    this.objectMapper = objectMapper;
    this.clientFactory = clientFactory;
  }

  private static final String STATUS = "active";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  /**
   * crete loadbalancer operation
   *
   * @param priorOutputs
   * @return
   */
  @Override
  public Map operate(List priorOutputs) {
    Map<String, Object> resultMap = new HashMap<>(30);

    IAcsClient client =
        clientFactory.createClient(
            description.getRegion(),
            description.getCredentials().getAccessKeyId(),
            description.getCredentials().getAccessSecretKey());
    DescribeLoadBalancersResponse.LoadBalancer loadBalancerT = null;
    // Create or Update load balancing instances
    // Query all load balancing instances under this user
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
      logger.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    } catch (ClientException e) {
      logger.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    }

    if (loadBalancerT != null) {
      description.setLoadBalancerId(loadBalancerT.getLoadBalancerId());
    } else {
      CreateLoadBalancerRequest loadBalancerRequest =
          objectMapper.convertValue(description, CreateLoadBalancerRequest.class);
      loadBalancerRequest.setLoadBalancerName(description.getLoadBalancerName());
      if (!StringUtils.isEmpty(description.getVSwitchId())) {
        loadBalancerRequest.setVSwitchId(description.getVSwitchId());
      }
      if ("internet".equalsIgnoreCase(loadBalancerRequest.getAddressType())) {
        loadBalancerRequest.setVSwitchId("");
      }

      // Instance delete protection off
      loadBalancerRequest.setDeleteProtection("off");
      CreateLoadBalancerResponse loadBalancerResponse;
      try {
        loadBalancerResponse = client.getAcsResponse(loadBalancerRequest);
        description.setLoadBalancerId(loadBalancerResponse.getLoadBalancerId());

      } catch (ServerException e) {
        logger.info(e.getMessage());
        throw new AliCloudException(e.getMessage());
      } catch (ClientException e) {
        logger.info(e.getMessage());
        throw new AliCloudException(e.getMessage());
      }
    }

    if (StringUtils.isEmpty(description.getLoadBalancerId())) {
      return null;
    }

    try {
      createListener(loadBalancerT == null ? false : true, client);
    } catch (ServerException e) {
      logger.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    } catch (ClientException e) {
      logger.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    }

    // Restart instance
    SetLoadBalancerStatusRequest statusRequest = new SetLoadBalancerStatusRequest();
    statusRequest.setLoadBalancerId(description.getLoadBalancerId());
    statusRequest.setLoadBalancerStatus(STATUS);
    try {
      client.getAcsResponse(statusRequest);
    } catch (ServerException e) {
      logger.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    } catch (ClientException e) {
      logger.info(e.getMessage());
      throw new AliCloudException(e.getMessage());
    }

    resultMap.put(description.getLoadBalancerName(), description.getLoadBalancerId());

    return resultMap;
  }

  private void createListener(boolean whetherToCreate, IAcsClient client) throws ClientException {

    if (!whetherToCreate) {
      addListener(description.getListeners(), client);
    } else {
      // query loadbalancer Instance information，Get the current instance listener information
      // (listener type, port number)
      DescribeLoadBalancerAttributeRequest describeLoadBalancerAttributeRequest =
          new DescribeLoadBalancerAttributeRequest();
      describeLoadBalancerAttributeRequest.setLoadBalancerId(description.getLoadBalancerId());
      DescribeLoadBalancerAttributeResponse describeLoadBalancerAttributeResponse =
          client.getAcsResponse(describeLoadBalancerAttributeRequest);
      List<ListenerPortAndProtocal> listenerPortsAndProtocal =
          describeLoadBalancerAttributeResponse.getListenerPortsAndProtocal();
      Set<String> deleteListenerList = new HashSet<>();
      Set<Listener> updateListenerList = new HashSet<>();
      Set<Listener> createListenerList = new HashSet<>();

      for (ListenerPortAndProtocal listenerPortAndProtocal : listenerPortsAndProtocal) {
        for (Listener listener : description.getListeners()) {
          String sign =
              listenerPortAndProtocal.getListenerProtocal().toUpperCase()
                  + listenerPortAndProtocal.getListenerPort();
          String sign2 =
              listener.getListenerProtocal() + String.valueOf(listener.getListenerPort());
          if (sign.equals(sign2)) {
            updateListenerList.add(listener);
          }
        }
      }

      for (ListenerPortAndProtocal listenerPortAndProtocal : listenerPortsAndProtocal) {
        if (updateListenerList.size() == 0) {
          deleteListenerList.add(listenerPortAndProtocal.getListenerPort() + "");
        } else {
          for (Listener listener : updateListenerList) {
            String sign =
                listenerPortAndProtocal.getListenerProtocal().toUpperCase()
                    + listenerPortAndProtocal.getListenerPort();
            String sign2 =
                listener.getListenerProtocal() + String.valueOf(listener.getListenerPort());
            if (!sign.equals(sign2)) {
              deleteListenerList.add(listenerPortAndProtocal.getListenerPort() + "");
            }
          }
        }
      }

      // Filter out the data you need to create
      for (Listener listener : description.getListeners()) {
        if (updateListenerList.size() == 0) {
          createListenerList.add(listener);
        } else {
          for (Listener updateListener : updateListenerList) {
            if (listener.getListenerPort().intValue()
                != updateListener.getListenerPort().intValue()) {
              createListenerList.add(listener);
            }
          }
        }
      }

      // Delete listeners
      for (String port : deleteListenerList) {
        DeleteLoadBalancerListenerRequest deleteLoadBalancerListenerRequest =
            new DeleteLoadBalancerListenerRequest();
        deleteLoadBalancerListenerRequest.setListenerPort(Integer.valueOf(port));
        deleteLoadBalancerListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
        client.getAcsResponse(deleteLoadBalancerListenerRequest);
      }

      // Modify listeners
      for (Listener listener : updateListenerList) {
        switch (listener.getListenerProtocal()) {
          case HTTPS:
            SetLoadBalancerHTTPSListenerAttributeRequest setCreateHTTPSListenerRequest =
                objectMapper.convertValue(
                    listener, SetLoadBalancerHTTPSListenerAttributeRequest.class);
            setCreateHTTPSListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
            client.getAcsResponse(setCreateHTTPSListenerRequest);
            break;
          case TCP:
            SetLoadBalancerTCPListenerAttributeRequest setCreateTCPListenerRequest =
                objectMapper.convertValue(
                    listener, SetLoadBalancerTCPListenerAttributeRequest.class);
            setCreateTCPListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
            client.getAcsResponse(setCreateTCPListenerRequest);
            break;
          case UDP:
            SetLoadBalancerUDPListenerAttributeRequest setCreateUDPListenerRequest =
                objectMapper.convertValue(
                    listener, SetLoadBalancerUDPListenerAttributeRequest.class);
            setCreateUDPListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
            client.getAcsResponse(setCreateUDPListenerRequest);
            break;
          default:
            SetLoadBalancerHTTPListenerAttributeRequest setHttpListenerRequest =
                objectMapper.convertValue(
                    listener, SetLoadBalancerHTTPListenerAttributeRequest.class);
            setHttpListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
            client.getAcsResponse(setHttpListenerRequest);
            break;
        }
      }
      // Create listeners
      addListener(new ArrayList<>(createListenerList), client);
    }
  }

  private void addListener(List<Listener> createListenerList, IAcsClient client)
      throws ClientException {
    for (Listener listener : createListenerList) {
      switch (listener.getListenerProtocal()) {
        case HTTPS:
          createHTTPSListener(client, listener);
          break;
        case TCP:
          createTCPListener(client, listener);
          break;
        case UDP:
          createUDPListener(client, listener);
          break;
        default:
          createHTTPListener(client, listener);
          break;
      }
    }
  }

  private void createHTTPListener(IAcsClient client, Listener listener) throws ClientException {
    CreateLoadBalancerHTTPListenerRequest httpListenerRequest =
        objectMapper.convertValue(listener, CreateLoadBalancerHTTPListenerRequest.class);
    httpListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
    client.getAcsResponse(httpListenerRequest);
  }

  private void createHTTPSListener(IAcsClient client, Listener listener) throws ClientException {
    CreateLoadBalancerHTTPSListenerRequest createHTTPSListenerRequest =
        objectMapper.convertValue(listener, CreateLoadBalancerHTTPSListenerRequest.class);
    createHTTPSListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
    client.getAcsResponse(createHTTPSListenerRequest);
  }

  private void createTCPListener(IAcsClient client, Listener listener) throws ClientException {
    CreateLoadBalancerTCPListenerRequest createTCPListenerRequest =
        objectMapper.convertValue(listener, CreateLoadBalancerTCPListenerRequest.class);
    createTCPListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
    client.getAcsResponse(createTCPListenerRequest);
  }

  private void createUDPListener(IAcsClient client, Listener listener) throws ClientException {
    CreateLoadBalancerUDPListenerRequest createUDPListenerRequest =
        objectMapper.convertValue(listener, CreateLoadBalancerUDPListenerRequest.class);
    createUDPListenerRequest.setLoadBalancerId(description.getLoadBalancerId());
    client.getAcsResponse(createUDPListenerRequest);
  }
}
