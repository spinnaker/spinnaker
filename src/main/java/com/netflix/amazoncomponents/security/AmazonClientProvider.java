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

import com.amazonaws.*;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
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
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.amazoncomponents.data.AmazonObjectMapper;
import com.netflix.amazoncomponents.model.RetryCallback;
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Provider of Amazon Clients.
 *
 * @author Dan Woods
 */
public class AmazonClientProvider {

  private HttpClient httpClient;
  private ObjectMapper objectMapper;
  private RetryCallback retryCallback = new RetryCallback() {
    @Override
    public boolean doCall(Throwable t, int attempts) {
      if (attempts < 5) {
        try {
          Thread.sleep(150);
        } catch (InterruptedException IGNORE) {
          //
        }
        return true;
      } else {
        return false;
      }
    }
  };

  private static final ThreadLocal<Map<String, List<String>>> lastResponseHeaders = new ThreadLocal<>();

  public AmazonClientProvider() {
    this((HttpClient) null);
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

  public void setRetryCallback(RetryCallback retryCallback) {
    this.retryCallback = retryCallback;
  }

  /**
   * When edda serves the request, http headers are captured and available to the calling thread.
   * Header names are lower-case.
   * @return the HTTP headers from the last response for the requesting thread, if available.
   */
  public Map<String, List<String>> getLastResponseHeaders() {
    return lastResponseHeaders.get();
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonEC2.class, AmazonEC2Client.class, amazonCredentials, region);

  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonAutoScaling.class, AmazonAutoScalingClient.class, amazonCredentials, region);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonRoute53.class, AmazonRoute53Client.class, amazonCredentials, region);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonElasticLoadBalancing.class, AmazonElasticLoadBalancingClient.class, amazonCredentials, region);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonSimpleWorkflow.class, AmazonSimpleWorkflowClient.class, amazonCredentials, region);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region);
  }

  public AmazonSNS getAmazonSNS(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonSNS.class, AmazonSNSClient.class, amazonCredentials, region);
  }

  public AmazonCloudWatch getCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region);
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonEC2.class, AmazonEC2Client.class, amazonCredentials, region, skipEdda);
  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonAutoScaling.class, AmazonAutoScalingClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonRoute53.class, AmazonRoute53Client.class, amazonCredentials, region, skipEdda);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonElasticLoadBalancing.class, AmazonElasticLoadBalancingClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonSimpleWorkflow.class, AmazonSimpleWorkflowClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSNS getAmazonSNS(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonSNS.class, AmazonSNSClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonCloudWatch getCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getThrottlingHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region, skipEdda);
  }


  protected <T extends AmazonWebServiceClient, U> U getThrottlingHandler(Class<U> interfaceKlazz, Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region) {
    return getThrottlingHandler(interfaceKlazz, impl, amazonCredentials, region, false);
  }

  protected <T extends AmazonWebServiceClient, U> U getThrottlingHandler(Class<U> interfaceKlazz, Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    try {
      T delegate = getClient(impl, amazonCredentials, region);
      U client = (U) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{interfaceKlazz}, new ThrottledAmazonClientInvocationHandler(delegate, retryCallback));
      if (amazonCredentials.getEddaEnabled() && !skipEdda) {
        return (U) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{interfaceKlazz},
          getInvocationHandler(client, delegate.getServiceName(), region, amazonCredentials));
      } else {
        return client;
      }
    } catch (Exception e) {
      throw new RuntimeException("Instantiation of client implementation failed!", e);
    }
  }

  protected <T extends AmazonWebServiceClient> T getClient(Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region) throws IllegalAccessException, InvocationTargetException,
    InstantiationException, NoSuchMethodException {
    Constructor<T> constructor = impl.getConstructor(AWSCredentialsProvider.class);
    T delegate = constructor.newInstance(amazonCredentials.getCredentialsProvider());
    if (region != null && region.length() > 0) {
      delegate.setRegion(Region.getRegion(Regions.fromName(region)));
    }
    return delegate;
  }

  protected GeneralAmazonClientInvocationHandler getInvocationHandler(Object client, String serviceName, String region, NetflixAmazonCredentials amazonCredentials) {
    return new GeneralAmazonClientInvocationHandler(client, serviceName, String.format(amazonCredentials.getEdda(), region),
      this.httpClient == null ? HttpClients.createDefault() : this.httpClient, objectMapper);
  }

  private static void checkCredentials(NetflixAmazonCredentials amazonCredentials) {
    if (amazonCredentials == null) {
      throw new IllegalArgumentException("Credentials cannot be null");
    }
  }

  public static class ThrottledAmazonClientInvocationHandler implements InvocationHandler {

    private AmazonWebServiceClient delegate;
    private RetryCallback retryCallback;

    ThrottledAmazonClientInvocationHandler(AmazonWebServiceClient delegate, RetryCallback retryCallback) {
      this.delegate = delegate;
      this.retryCallback = retryCallback;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        return invoke(proxy, method, args, 1);
      } finally {
        lastResponseHeaders.remove();
      }
    }

    private Object invoke(Object proxy, Method method, Object[] args, int attempts) throws Throwable {
      try {
        return method.invoke(delegate, args);
      } catch (InvocationTargetException ex) {
        Throwable t = ex.getTargetException();
        if (t instanceof AmazonServiceException) {
          AmazonServiceException e = (AmazonServiceException) t;
          if ("RequestLimitExceeded".equals(e.getErrorCode())) {
            boolean tryAgain = retryCallback.doCall(e, attempts);
            if (tryAgain) {
              return invoke(proxy, method, args, ++attempts);
            }
          }
        }
        throw t;
      }
    }
  }

  public static class GeneralAmazonClientInvocationHandler implements InvocationHandler {
    private final String edda;
    private final HttpClient httpClient;
    private final Object delegate;
    private final String serviceName;
    private final ObjectMapper objectMapper;

    public GeneralAmazonClientInvocationHandler(Object delegate, String serviceName, String edda, HttpClient httpClient, ObjectMapper objectMapper) {
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
                  results.add(objectMapper.readValue(getJson(object, (String) id), singleType));
                }
              }
            } else {
              results.addAll((Collection) objectMapper.readValue(getJson(object, null), collectionType));
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
      String url = key != null ? edda + "/REST/v2/aws/" + objectName + "/" +
        key : edda + "/REST/v2/aws/" + objectName + ";_expand";
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
}

