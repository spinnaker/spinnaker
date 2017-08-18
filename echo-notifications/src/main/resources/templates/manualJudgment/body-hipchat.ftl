Spinnaker application <strong>${notification.source.application}</strong> is awaiting <strong>manual judgment</strong>.
<br/><br/>
<#if (notification.additionalContext.instructions)??>Instructions:<br/>
${notification.additionalContext.instructions}
<br/><br/>
</#if>For more details, please visit:
<br/><br/>
<a href="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}">
  ${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}
</a>
