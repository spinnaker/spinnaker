<html>
<#if (notification.additionalContext.instructions)??>
<b>Instructions:</b>
${markdownToHtml.convert(notification.additionalContext.instructions)}

</#if>
For more details, please visit:
<#if (notification.additionalContext.stageId)??>
  <#if (notification.additionalContext.restrictExecutionDuringTimeWindow)??>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}&step=1
  <#else>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}
  </#if>
<#else>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}
</#if>
</html>
