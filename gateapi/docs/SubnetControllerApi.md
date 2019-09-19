# \SubnetControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllByCloudProviderUsingGET1**](SubnetControllerApi.md#AllByCloudProviderUsingGET1) | **Get** /subnets/{cloudProvider} | Retrieve a list of subnets for a given cloud provider


# **AllByCloudProviderUsingGET1**
> []interface{} AllByCloudProviderUsingGET1(ctx, cloudProvider, optional)
Retrieve a list of subnets for a given cloud provider

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **cloudProvider** | **string**| cloudProvider | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **cloudProvider** | **string**| cloudProvider | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

