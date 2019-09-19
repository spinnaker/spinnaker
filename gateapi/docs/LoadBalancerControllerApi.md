# \LoadBalancerControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetAllUsingGET**](LoadBalancerControllerApi.md#GetAllUsingGET) | **Get** /loadBalancers | Retrieve a list of load balancers for a given cloud provider
[**GetApplicationLoadBalancersUsingGET**](LoadBalancerControllerApi.md#GetApplicationLoadBalancersUsingGET) | **Get** /applications/{application}/loadBalancers | Retrieve a list of load balancers for a given application
[**GetLoadBalancerDetailsUsingGET**](LoadBalancerControllerApi.md#GetLoadBalancerDetailsUsingGET) | **Get** /loadBalancers/{account}/{region}/{name} | Retrieve a load balancer&#39;s details as a single element list for a given account, region, cloud provider and load balancer name
[**GetLoadBalancerUsingGET**](LoadBalancerControllerApi.md#GetLoadBalancerUsingGET) | **Get** /loadBalancers/{name} | Retrieve a load balancer for a given cloud provider


# **GetAllUsingGET**
> []interface{} GetAllUsingGET(ctx, optional)
Retrieve a list of load balancers for a given cloud provider

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetApplicationLoadBalancersUsingGET**
> []interface{} GetApplicationLoadBalancersUsingGET(ctx, application, optional)
Retrieve a list of load balancers for a given application

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

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetLoadBalancerDetailsUsingGET**
> []interface{} GetLoadBalancerDetailsUsingGET(ctx, account, name, region, optional)
Retrieve a load balancer's details as a single element list for a given account, region, cloud provider and load balancer name

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **name** | **string**| name | 
  **region** | **string**| region | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **name** | **string**| name | 
 **region** | **string**| region | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetLoadBalancerUsingGET**
> map[string]interface{} GetLoadBalancerUsingGET(ctx, name, optional)
Retrieve a load balancer for a given cloud provider

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **name** | **string**| name | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **name** | **string**| name | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

