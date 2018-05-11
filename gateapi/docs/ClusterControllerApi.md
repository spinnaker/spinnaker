# \ClusterControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetClusterLoadBalancersUsingGET**](ClusterControllerApi.md#GetClusterLoadBalancersUsingGET) | **Get** /applications/{application}/clusters/{account}/{clusterName}/{type}/loadBalancers | Retrieve a cluster&#39;s loadbalancers
[**GetClustersUsingGET**](ClusterControllerApi.md#GetClustersUsingGET) | **Get** /applications/{application}/clusters/{account}/{clusterName} | Retrieve a cluster&#39;s details
[**GetClustersUsingGET1**](ClusterControllerApi.md#GetClustersUsingGET1) | **Get** /applications/{application}/clusters/{account} | Retrieve a list of clusters for an account
[**GetClustersUsingGET2**](ClusterControllerApi.md#GetClustersUsingGET2) | **Get** /applications/{application}/clusters | Retrieve a list of cluster names for an application, grouped by account
[**GetScalingActivitiesUsingGET**](ClusterControllerApi.md#GetScalingActivitiesUsingGET) | **Get** /applications/{application}/clusters/{account}/{clusterName}/serverGroups/{serverGroupName}/scalingActivities | Retrieve a list of scaling activities for a server group
[**GetServerGroupsUsingGET**](ClusterControllerApi.md#GetServerGroupsUsingGET) | **Get** /applications/{application}/clusters/{account}/{clusterName}/serverGroups/{serverGroupName} | Retrieve a server group&#39;s details
[**GetServerGroupsUsingGET1**](ClusterControllerApi.md#GetServerGroupsUsingGET1) | **Get** /applications/{application}/clusters/{account}/{clusterName}/serverGroups | Retrieve a list of server groups for a cluster
[**GetTargetServerGroupUsingGET**](ClusterControllerApi.md#GetTargetServerGroupUsingGET) | **Get** /applications/{application}/clusters/{account}/{clusterName}/{cloudProvider}/{scope}/serverGroups/target/{target} | Retrieve a server group that matches a target coordinate (e.g., newest, ancestor) relative to a cluster


# **GetClusterLoadBalancersUsingGET**
> []interface{} GetClusterLoadBalancersUsingGET(ctx, applicationName, account, clusterName, type_, optional)
Retrieve a cluster's loadbalancers

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **applicationName** | **string**| applicationName | 
  **account** | **string**| account | 
  **clusterName** | **string**| clusterName | 
  **type_** | **string**| type | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **applicationName** | **string**| applicationName | 
 **account** | **string**| account | 
 **clusterName** | **string**| clusterName | 
 **type_** | **string**| type | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetClustersUsingGET**
> map[string]interface{} GetClustersUsingGET(ctx, application, account, clusterName, optional)
Retrieve a cluster's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 
  **clusterName** | **string**| clusterName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **account** | **string**| account | 
 **clusterName** | **string**| clusterName | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetClustersUsingGET1**
> []HashMap GetClustersUsingGET1(ctx, application, account, optional)
Retrieve a list of clusters for an account

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **account** | **string**| account | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetClustersUsingGET2**
> map[string]interface{} GetClustersUsingGET2(ctx, application, optional)
Retrieve a list of cluster names for an application, grouped by account

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetScalingActivitiesUsingGET**
> []HashMap GetScalingActivitiesUsingGET(ctx, application, account, clusterName, serverGroupName, optional)
Retrieve a list of scaling activities for a server group

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 
  **clusterName** | **string**| clusterName | 
  **serverGroupName** | **string**| serverGroupName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **account** | **string**| account | 
 **clusterName** | **string**| clusterName | 
 **serverGroupName** | **string**| serverGroupName | 
 **provider** | **string**| provider | [default to aws]
 **region** | **string**| region | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetServerGroupsUsingGET**
> []HashMap GetServerGroupsUsingGET(ctx, application, account, clusterName, serverGroupName, optional)
Retrieve a server group's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 
  **clusterName** | **string**| clusterName | 
  **serverGroupName** | **string**| serverGroupName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **account** | **string**| account | 
 **clusterName** | **string**| clusterName | 
 **serverGroupName** | **string**| serverGroupName | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetServerGroupsUsingGET1**
> []HashMap GetServerGroupsUsingGET1(ctx, application, account, clusterName, optional)
Retrieve a list of server groups for a cluster

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 
  **clusterName** | **string**| clusterName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **account** | **string**| account | 
 **clusterName** | **string**| clusterName | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetTargetServerGroupUsingGET**
> map[string]interface{} GetTargetServerGroupUsingGET(ctx, application, account, clusterName, cloudProvider, scope, target, optional)
Retrieve a server group that matches a target coordinate (e.g., newest, ancestor) relative to a cluster

`scope` is either a zone or a region

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 
  **clusterName** | **string**| clusterName | 
  **cloudProvider** | **string**| cloudProvider | 
  **scope** | **string**| scope | 
  **target** | **string**| target | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **string**| application | 
 **account** | **string**| account | 
 **clusterName** | **string**| clusterName | 
 **cloudProvider** | **string**| cloudProvider | 
 **scope** | **string**| scope | 
 **target** | **string**| target | 
 **onlyEnabled** | **bool**| onlyEnabled | 
 **validateOldest** | **bool**| validateOldest | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

