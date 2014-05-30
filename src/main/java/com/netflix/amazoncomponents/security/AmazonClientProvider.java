/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.amazoncomponents.security;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.amazoncomponents.data.AmazonObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provider of Amazon Clients.
 *
 * @author Dan Woods
 */
public class AmazonClientProvider {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public AmazonClientProvider() {
    this(new DefaultHttpClient());
  }

  public AmazonClientProvider(HttpClient httpClient) {
    this(httpClient, new AmazonObjectMapper());
  }

  public AmazonClientProvider(ObjectMapper objectMapper) {
    this(null, objectMapper);
  }

  public AmazonClientProvider(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public AmazonEC2 getAmazonEC2(AmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    AmazonEC2Client client = new AmazonEC2Client(amazonCredentials.getCredentials());
    if (!amazonCredentials.isEddaConfigured()) {
      return client;
    } else {
      return (AmazonEC2) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{AmazonEC2.class},
        getInvocationHandler(client, region, amazonCredentials));
    }

  }

  public AmazonAutoScaling getAutoScaling(AmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    AmazonAutoScalingClient client = new AmazonAutoScalingClient(amazonCredentials.getCredentials());
    if (!amazonCredentials.isEddaConfigured()) {
      return client;
    } else {
      return (AmazonAutoScaling) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{AmazonAutoScaling.class},
        getInvocationHandler(client, region, amazonCredentials));
    }

  }

  public AmazonRoute53 getAmazonRoute53(AmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    AmazonRoute53Client amazonRoute53 = new AmazonRoute53Client(amazonCredentials.getCredentials());
    if (region != null && region.length() > 0) {
      amazonRoute53.setRegion(Region.getRegion(Regions.fromName(region)));
    }

    return amazonRoute53;
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(AmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    AmazonElasticLoadBalancingClient amazonElasticLoadBalancing = new AmazonElasticLoadBalancingClient(amazonCredentials.getCredentials());
    if (region != null && region.length() > 0) {
      amazonElasticLoadBalancing.setRegion(Region.getRegion(Regions.fromName(region)));
    }

    return amazonElasticLoadBalancing;
  }

  private GeneralAmazonClientInvocationHandler getInvocationHandler(AmazonWebServiceClient client, String region, AmazonCredentials amazonCredentials) {
    if (region != null && region.length() > 0) {
      client.setRegion(Region.getRegion(Regions.fromName(region)));
    }

    return new GeneralAmazonClientInvocationHandler(client, String.format(amazonCredentials.getEdda(), region),
      this.httpClient == null ? new DefaultHttpClient() : this.httpClient, objectMapper);
  }

  private static void checkCredentials(AmazonCredentials amazonCredentials) {
    if (amazonCredentials == null) throw new IllegalArgumentException("Credentials cannot be null");
  }

  public static class GeneralAmazonClientInvocationHandler implements InvocationHandler {
    private final String edda;
    private final HttpClient httpClient;
    private final AmazonWebServiceClient delegate;
    private final ObjectMapper objectMapper;

    public GeneralAmazonClientInvocationHandler(AmazonWebServiceClient delegate, String edda, HttpClient httpClient, ObjectMapper objectMapper) {
      this.edda = edda;
      this.httpClient = httpClient;
      this.objectMapper = objectMapper;
      this.delegate = delegate;
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
      List<Instance> instances = describe(request, "instanceIds", "../view/instances", Instance.class, new TypeReference<List<Instance>>() {
      });
      Reservation reservation = new Reservation().withReservationId("1234").withInstances(instances);
      return new DescribeInstancesResult().withReservations(reservation);
    }

    public DescribeLoadBalancersResult describeLoadBalancers() {
      return describeLoadBalancers(null);
    }

    public DescribeLoadBalancersResult describeLoadBalancers(DescribeLoadBalancersRequest request) {
      List<LoadBalancerDescription> loadBalancerDescriptions = describe(request, "loadBalancerNames", "loadBalancers", LoadBalancerDescription.class, new TypeReference<List<LoadBalancerDescription>>() {
      });
      return new DescribeLoadBalancersResult().withLoadBalancerDescriptions(loadBalancerDescriptions);
    }

    public <T> T describe(Object request, String idKey, final String object, final Class singleType, TypeReference<T> collectionType) {
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
                  results.add(objectMapper.readValue(getJson(object, (String) id), singleType));
                }
              }
            } else {
              results.addAll((Collection)objectMapper.readValue(getJson(object, null), collectionType));
            }
            return (T) results;
          } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
            throw new RuntimeException(e);
          }
        } else {
          try {
            return (T) objectMapper.readValue(getJson(object, null), collectionType);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      } catch (Exception e) {
        AmazonServiceException ex = new AmazonServiceException("400 Bad Request -- Edda could not find one of the managed objects requested.", e);
        ex.setStatusCode(400);
        ex.setServiceName(delegate.getServiceName());
        ex.setErrorType(AmazonServiceException.ErrorType.Unknown);
        throw ex;
      }

    }

    private String getJson(String objectName) throws IOException {
      return getJson(objectName, null);
    }

    private String getJson(String objectName, String key) throws IOException {
      String url = key != null ? edda + "/REST/v2/aws/" + objectName + "/" +
        key : edda + "/REST/v2/aws/" + objectName + ";_expand";
      HttpGet get = new HttpGet(url);
      HttpResponse response = httpClient.execute(get);
      return getStringFromInputStream(response.getEntity().getContent());
    }

    private static String getStringFromInputStream(InputStream is) throws IOException {
      StringBuilder sb = new StringBuilder();
      String line;
      try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
        while ((line = br.readLine()) != null) {
          sb.append(line);
        }
      }
      return sb.toString();
    }

  }
}

