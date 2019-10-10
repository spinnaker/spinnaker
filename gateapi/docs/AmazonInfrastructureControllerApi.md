# \AmazonInfrastructureControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**ApplicationFunctionsUsingGET**](AmazonInfrastructureControllerApi.md#ApplicationFunctionsUsingGET) | **Get** /applications/{application}/functions | Get application functions
[**FunctionsUsingGET**](AmazonInfrastructureControllerApi.md#FunctionsUsingGET) | **Get** /functions | Get functions
[**InstanceTypesUsingGET**](AmazonInfrastructureControllerApi.md#InstanceTypesUsingGET) | **Get** /instanceTypes | Get instance types
[**SubnetsUsingGET**](AmazonInfrastructureControllerApi.md#SubnetsUsingGET) | **Get** /subnets | Get subnets
[**VpcsUsingGET**](AmazonInfrastructureControllerApi.md#VpcsUsingGET) | **Get** /vpcs | Get VPCs


# **ApplicationFunctionsUsingGET**
> []interface{} ApplicationFunctionsUsingGET(ctx, application)
Get application functions

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **FunctionsUsingGET**
> []interface{} FunctionsUsingGET(ctx, optional)
Get functions

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **functionName** | **string**| functionName | 
 **region** | **string**| region | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **InstanceTypesUsingGET**
> []interface{} InstanceTypesUsingGET(ctx, )
Get instance types

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **SubnetsUsingGET**
> []interface{} SubnetsUsingGET(ctx, )
Get subnets

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **VpcsUsingGET**
> []interface{} VpcsUsingGET(ctx, )
Get VPCs

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

