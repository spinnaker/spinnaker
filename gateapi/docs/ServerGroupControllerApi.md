# \ServerGroupControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetServerGroupDetailsUsingGET**](ServerGroupControllerApi.md#GetServerGroupDetailsUsingGET) | **Get** /applications/{applicationName}/serverGroups/{account}/{region}/{serverGroupName} | Retrieve a server group&#39;s details
[**GetServerGroupsForApplicationUsingGET**](ServerGroupControllerApi.md#GetServerGroupsForApplicationUsingGET) | **Get** /applications/{applicationName}/serverGroups | Retrieve a list of server groups for a given application


# **GetServerGroupDetailsUsingGET**
> interface{} GetServerGroupDetailsUsingGET(ctx, account, applicationName, region, serverGroupName, optional)
Retrieve a server group's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **applicationName** | **string**| applicationName | 
  **region** | **string**| region | 
  **serverGroupName** | **string**| serverGroupName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **applicationName** | **string**| applicationName | 
 **region** | **string**| region | 
 **serverGroupName** | **string**| serverGroupName | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **includeDetails** | **string**| includeDetails | [default to true]

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetServerGroupsForApplicationUsingGET**
> []interface{} GetServerGroupsForApplicationUsingGET(ctx, applicationName, optional)
Retrieve a list of server groups for a given application

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **applicationName** | **string**| applicationName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **applicationName** | **string**| applicationName | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **cloudProvider** | **string**| cloudProvider | 
 **clusters** | **string**| clusters | 
 **expand** | **string**| expand | [default to false]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

