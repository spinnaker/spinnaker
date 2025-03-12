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
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
  **applicationName** | **string**| applicationName | 
  **region** | **string**| region | 
  **serverGroupName** | **string**| serverGroupName | 
 **optional** | ***ServerGroupControllerApiGetServerGroupDetailsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ServerGroupControllerApiGetServerGroupDetailsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------




 **xRateLimitApp** | **optional.String**| X-RateLimit-App | 
 **includeDetails** | **optional.String**| includeDetails | [default to true]

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
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **applicationName** | **string**| applicationName | 
 **optional** | ***ServerGroupControllerApiGetServerGroupsForApplicationUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ServerGroupControllerApiGetServerGroupsForApplicationUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **xRateLimitApp** | **optional.String**| X-RateLimit-App | 
 **cloudProvider** | **optional.String**| cloudProvider | 
 **clusters** | **optional.String**| clusters | 
 **expand** | **optional.String**| expand | [default to false]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

