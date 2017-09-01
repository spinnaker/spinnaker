<p>The owner email for the following applications matches multiple users:</p>
<#foreach event in notification.additionalContext.events>
${event.email} owns...
<ul>
  <#foreach application in event.applications><li>${application.name}</li></#foreach>
</ul>
<br/>
</#foreach>
