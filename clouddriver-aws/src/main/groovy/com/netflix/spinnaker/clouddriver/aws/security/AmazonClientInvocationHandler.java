/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;

class AmazonClientInvocationHandler implements InvocationHandler {
  static final ThreadLocal<Map<String, List<String>>> lastResponseHeaders = new ThreadLocal<>();

  private final String edda;
  private final HttpClient httpClient;
  private final Object delegate;
  private final String serviceName;
  private final ObjectMapper objectMapper;

  public AmazonClientInvocationHandler(Object delegate, String serviceName, String edda, HttpClient httpClient, ObjectMapper objectMapper) {
    this.edda = edda;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.delegate = delegate;
    this.serviceName = serviceName;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      Method thisMethod = this.getClass().getMethod(method.getName(), args != null && args.length > 0 ?
        getClassArgs(args) : new Class[0]);
      return thisMethod.invoke(this, args);
    } catch (NoSuchMethodException e) {
      return method.invoke(delegate, args);
    }
  }

  static Class[] getClassArgs(Object[] args) {
    List<Class> classes = new ArrayList<>();
    for (Object object : args) {
      classes.add(object.getClass());
    }
    return classes.toArray(new Class[classes.size()]);
  }

  public DescribeAlarmsResult describeAlarms() {
    return describeAlarms(null);
  }

  public DescribeAlarmsResult describeAlarms(DescribeAlarmsRequest request) {
    return new DescribeAlarmsResult().withMetricAlarms(describe(request, "alarmNames", "alarms", MetricAlarm.class, new TypeReference<List<MetricAlarm>>() {
    }));
  }

  public DescribeScheduledActionsResult describeScheduledActions() {
    return describeScheduledActions(null);
  }

  public DescribeScheduledActionsResult describeScheduledActions(DescribeScheduledActionsRequest request) {
    return new DescribeScheduledActionsResult().withScheduledUpdateGroupActions(describe(request, "scheduledActionNames", "scheduledActions", ScheduledUpdateGroupAction.class, new TypeReference<List<ScheduledUpdateGroupAction>>() {
    }));
  }

  public DescribePoliciesResult describePolicies() {
    return describePolicies(null);
  }

  public DescribePoliciesResult describePolicies(DescribePoliciesRequest request) {
    return new DescribePoliciesResult().withScalingPolicies(describe(request, "policyNames", "scalingPolicies", ScalingPolicy.class, new TypeReference<List<ScalingPolicy>>() {
    }));
  }

  public DescribeLaunchConfigurationsResult describeLaunchConfigurations(DescribeLaunchConfigurationsRequest request) {
    return new DescribeLaunchConfigurationsResult().withLaunchConfigurations(describe(request, "launchConfigurationNames", "launchConfigurations", LaunchConfiguration.class, new TypeReference<List<LaunchConfiguration>>() {
    }));
  }

  public DescribeLaunchConfigurationsResult describeLaunchConfigurations() {
    return describeLaunchConfigurations(null);
  }

  public DescribeSecurityGroupsResult describeSecurityGroups(DescribeSecurityGroupsRequest request) {
    return new DescribeSecurityGroupsResult().withSecurityGroups(describe(request, "groupIds", "securityGroups", SecurityGroup.class, new TypeReference<List<SecurityGroup>>() {
    }));
  }

  public DescribeSecurityGroupsResult describeSecurityGroups() {
    return describeSecurityGroups(null);
  }

  public DescribeSubnetsResult describeSubnets(DescribeSubnetsRequest request) {
    return new DescribeSubnetsResult().withSubnets(describe(request, "subnetIds", "subnets", Subnet.class, new TypeReference<List<Subnet>>() {
    }));
  }

  public DescribeSubnetsResult describeSubnets() {
    return describeSubnets(null);
  }

  public DescribeAutoScalingGroupsResult describeAutoScalingGroups(DescribeAutoScalingGroupsRequest request) {
    return new DescribeAutoScalingGroupsResult().withAutoScalingGroups(describe(request, "autoScalingGroupNames", "autoScalingGroups", AutoScalingGroup.class, new TypeReference<List<AutoScalingGroup>>() {
    }));
  }

  public DescribeVpcsResult describeVpcs() {
    return describeVpcs(null);
  }

  public DescribeVpcsResult describeVpcs(DescribeVpcsRequest request) {
    List<Vpc> vpcs = describe(request, "vpcIds", "vpcs", Vpc.class, new TypeReference<List<Vpc>>() {
    });
    return new DescribeVpcsResult().withVpcs(vpcs);
  }

  public DescribeAutoScalingGroupsResult describeAutoScalingGroups() {
    return describeAutoScalingGroups(null);
  }

  public DescribeInstancesResult describeInstances() {
    return describeInstances(null);
  }

  public DescribeImagesResult describeImages() {
    return describeImages(null);
  }

  public DescribeImagesResult describeImages(DescribeImagesRequest request) {
    return new DescribeImagesResult().withImages(describe(request, "imageIds", "images", Image.class, new TypeReference<List<Image>>() {
    }));
  }

  public DescribeInstancesResult describeInstances(DescribeInstancesRequest request) {
    List<com.amazonaws.services.ec2.model.Instance> instances = describe(request, "instanceIds", "../view/instances", com.amazonaws.services.ec2.model.Instance.class, new TypeReference<List<com.amazonaws.services.ec2.model.Instance>>() {
    });
    Reservation reservation = new Reservation().withReservationId("1234").withInstances(instances);
    return new DescribeInstancesResult().withReservations(reservation);
  }

  public com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult describeLoadBalancers() {
    return describeLoadBalancers(null);
  }

  public com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult describeLoadBalancers(com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest request) {
    List<LoadBalancerDescription> loadBalancerDescriptions = describe(request, "loadBalancerNames", "loadBalancers", LoadBalancerDescription.class, new TypeReference<List<LoadBalancerDescription>>() {
    });
    return new com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancerDescriptions);
  }

  public DescribeReservedInstancesOfferingsResult describeReservedInstancesOfferings() {
    return describeReservedInstancesOfferings(null);
  }

  public DescribeReservedInstancesOfferingsResult describeReservedInstancesOfferings(DescribeReservedInstancesOfferingsRequest request) {
    List<ReservedInstancesOffering> reservedInstancesOfferings = describe(request, "reservedInstancesOfferingIds", "reservedInstancesOfferings", ReservedInstancesOffering.class, new TypeReference<List<ReservedInstancesOffering>>() {
    });
    return new DescribeReservedInstancesOfferingsResult().withReservedInstancesOfferings(reservedInstancesOfferings);
  }

  public <T> T describe(AmazonWebServiceRequest request, String idKey, final String object, final Class singleType, TypeReference<T> collectionType) {
    try {
      if (request != null) {
        try {
          Field field = request.getClass().getDeclaredField(idKey);
          field.setAccessible(true);
          List collection = (List) field.get(request);
          List results = new ArrayList<>();
          if (collection != null && collection.size() > 0) {
            for (Object id : collection) {
              if (id instanceof String) {
                final byte[] json = getJson(object, (String) id);
                results.add(objectMapper.readValue(json, singleType));
              }
            }
          } else {
            final byte[] json = getJson(object, null);
            results.addAll((Collection) objectMapper.readValue(json, collectionType));
          }
          return (T) results;
        } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        try {
          byte[] json = getJson(object, null);
          if (json == null) {
            throw new RuntimeException(String.format("Could not retrieve JSON Data from Edda host (%s) for object (%s).", edda, object));
          }
          return (T) objectMapper.readValue(json, collectionType);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    } catch (Exception e) {
      AmazonServiceException ex = new AmazonServiceException("400 Bad Request -- Edda could not find one of the managed objects requested.", e);
      ex.setStatusCode(400);
      ex.setServiceName(serviceName);
      ex.setErrorType(AmazonServiceException.ErrorType.Unknown);
      throw ex;
    }

  }

  private byte[] getJson(String objectName, String key) throws IOException {
    String url = edda + "/REST/v2/aws/" + objectName + (key == null ? ";_expand" : "/" + key);
    HttpGet get = new HttpGet(url);
    HttpResponse response = httpClient.execute(get);
    Map<String, List<String>> headers = new HashMap<>();
    for (Header h : response.getAllHeaders()) {
      String headerName = h.getName().toLowerCase();
      List<String> values = headers.get(headerName);
      if (values == null) {
        values = new ArrayList<>();
        headers.put(headerName, values);
      }
      values.add(h.getValue());
    }
    lastResponseHeaders.set(headers);
    HttpEntity entity = response.getEntity();
    try {
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        return null;
      } else {
        return getBytesFromInputStream(entity.getContent(), entity.getContentLength());
      }
    } finally {
      EntityUtils.consume(entity);
    }
  }

  private static byte[] getBytesFromInputStream(InputStream is, long contentLength) throws IOException {
    final int bufLen = contentLength < 0 || contentLength > Integer.MAX_VALUE ? 128 * 1024 : (int) contentLength;
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(bufLen);
    final byte[] buf = new byte[16 * 1024];
    int bytesRead;
    while ((bytesRead = is.read(buf)) != -1) {
      baos.write(buf, 0, bytesRead);
    }
    is.close();
    return baos.toByteArray();
  }


}
