# \EcsServerGroupEventsControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetEventsUsingGET**](EcsServerGroupEventsControllerApi.md#GetEventsUsingGET) | **Get** /applications/{application}/serverGroups/{account}/{serverGroupName}/events | Retrieves a list of events for a server group


# **GetEventsUsingGET**
> []interface{} GetEventsUsingGET(ctx, application, account, serverGroupName, region, provider)
Retrieves a list of events for a server group

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 
  **serverGroupName** | **string**| serverGroupName | 
  **region** | **string**| region | 
  **provider** | **string**| provider | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

