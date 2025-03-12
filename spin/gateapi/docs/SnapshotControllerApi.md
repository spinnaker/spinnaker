# \SnapshotControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetCurrentSnapshotUsingGET**](SnapshotControllerApi.md#GetCurrentSnapshotUsingGET) | **Get** /applications/{application}/snapshots/{account} | Get current snapshot
[**GetSnapshotHistoryUsingGET**](SnapshotControllerApi.md#GetSnapshotHistoryUsingGET) | **Get** /applications/{application}/snapshots/{account}/history | Get snapshot history


# **GetCurrentSnapshotUsingGET**
> map[string]interface{} GetCurrentSnapshotUsingGET(ctx, account, application)
Get current snapshot

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
  **application** | **string**| application | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetSnapshotHistoryUsingGET**
> []interface{} GetSnapshotHistoryUsingGET(ctx, account, application, optional)
Get snapshot history

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
  **application** | **string**| application | 
 **optional** | ***SnapshotControllerApiGetSnapshotHistoryUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a SnapshotControllerApiGetSnapshotHistoryUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **limit** | **optional.Int32**| limit | [default to 20]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

