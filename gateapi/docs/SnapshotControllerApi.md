# \SnapshotControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetCurrentSnapshotUsingGET**](SnapshotControllerApi.md#GetCurrentSnapshotUsingGET) | **Get** /applications/{application}/snapshots/{account} | Get current snapshot
[**GetSnapshotHistoryUsingGET**](SnapshotControllerApi.md#GetSnapshotHistoryUsingGET) | **Get** /applications/{application}/snapshots/{account}/history | Get snapshot history


# **GetCurrentSnapshotUsingGET**
> map[string]interface{} GetCurrentSnapshotUsingGET(ctx, application, account)
Get current snapshot

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **application** | **string**| application | 
  **account** | **string**| account | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetSnapshotHistoryUsingGET**
> []HashMap GetSnapshotHistoryUsingGET(ctx, application, account, optional)
Get snapshot history

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
 **limit** | **int32**| limit | [default to 20]

### Return type

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

