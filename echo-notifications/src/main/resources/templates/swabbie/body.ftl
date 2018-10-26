<html>
<head>
</head>
<body style="-moz-box-sizing: border-box; -ms-text-size-adjust: 100%; -webkit-box-sizing: border-box; -webkit-text-size-adjust: 100%; Margin: 0; background-color: #FAFAFA !important; box-sizing: border-box; color: #0a0a0a; font-family: Helvetica, Arial, sans-serif; font-size: 16px; font-weight: normal; line-height: 1.3; margin: 0; min-width: 100%; padding: 0; text-align: left; width: 100% !important;">
<table class="container" align="center" style="Margin: 0 auto; background: #fefefe; border-collapse: collapse; border-spacing: 0; margin: 0 auto; padding: 0; text-align: inherit; vertical-align: top; width: 580px;">
  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td style="-moz-hyphens: auto; -webkit-hyphens: auto; Margin: 0; background-color: #D8E8ED; border-collapse: collapse !important; color: #3A5469; font-family: Helvetica, Arial, sans-serif; font-size: 20px; font-weight: normal; hyphens: auto; line-height: 1.3; margin: 0; padding: 0 20px; padding-left: 0; text-align: left; text-transform: uppercase; vertical-align: middle; word-wrap: break-word;" class="sp-header">
      Cleanup Notifications
    </td>
  </tr>
  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td class="summary" colspan="2" style="-moz-hyphens: auto; -webkit-hyphens: auto; Margin: 0; background-color: #EFEFEF; border-collapse: collapse !important; color: #3A5469; font-family: Helvetica, Arial, sans-serif; font-size: 14px; font-weight: normal; hyphens: auto; line-height: 1.3; margin: 0; padding: 20px 20px; text-align: left; vertical-align: top; word-wrap: break-word;">
      <strong style="color: #229CB4; font-size: 24px; font-weight: bold;">${notification.additionalContext.resources?size}</strong>
      <span style="font-size: 16px;">
        ${notification.additionalContext.resourceType}(s) scheduled for cleanup.
      </span>
    </td>
  </tr>
  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td colspan="2" class="caption title" style="-moz-hyphens: auto; -webkit-hyphens: auto; Margin: 0; background-color: white; border-collapse: collapse !important; color: #555; font-family: Helvetica, Arial, sans-serif; font-size: 13px; font-weight: normal; hyphens: auto; line-height: 1.3; margin: 0; margin-bottom: 8px; padding: 12px 20px; text-align: left; vertical-align: top; word-wrap: break-word;">
      Resources
    </td>
  </tr>
  <#foreach resource in notification.additionalContext.resources>
  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td class="item" colspan="2" style="-moz-hyphens: auto; -webkit-hyphens: auto; Margin: 0; background-color: white; border-bottom: 1px dotted #ddd; border-collapse: collapse !important; color: #0a0a0a; font-family: Helvetica, Arial, sans-serif; font-size: 14px; font-weight: normal; hyphens: auto; line-height: 1.3; margin: 0; padding: 8px 32px; text-align: left; vertical-align: top; word-wrap: break-word;">
      <div style="margin: 4px 0;">
        <a href="${notification.additionalContext.spinnakerLink}${resource.resourceId}" target="_blank"
           style="Margin: 0; color: #2199e8; font-family: Helvetica, Arial, sans-serif; font-weight: normal; line-height: 1.3; margin: 0; padding: 0; text-align: left; text-decoration: none;">
          ${resource.resourceId}
          <#if resource.resourceId != resource.name>
            - (${resource.name})
          </#if>
        </a>
          <#if resource.lastSeenInfo??>
            . Last seen in ${resource.lastSeenInfo.usedByResourceIdentifier} on ${resource.lastSeenInfo.timeSeen?number_to_date?string("EEE, d MMM yyyy")}
          </#if>
        . Opt this resource out of deletion <a href="${notification.additionalContext.optOutLink}/${resource.namespace}/${resource.resourceId}/optOut" style="Margin: 0; color: #2199e8; font-family: Helvetica, Arial, sans-serif; font-weight: normal; line-height: 1.3; margin: 0; padding: 0; text-align: left; text-decoration: none;"> here </a>
        or view detailed resource information <a href="${notification.additionalContext.optOutLink}/${resource.namespace}/${resource.resourceId}" style="Margin: 0; color: #2199e8; font-family: Helvetica, Arial, sans-serif; font-weight: normal; line-height: 1.3; margin: 0; padding: 0; text-align: left; text-decoration: none;"> here</a>.
        Cleanup scheduled for <em>${resource.projectedDeletionStamp?number_to_date?string("EEE, d MMM yyyy")}</em>
        &nbsp;&nbsp;
      </div>
    </td>
  </tr>
  </#foreach>
  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td colspan="2" class="footer" style="-moz-hyphens: auto; -webkit-hyphens: auto; Margin: 0; background-color: #F2f2f2; border-collapse: collapse !important; color: #3A5469; font-family: Helvetica, Arial, sans-serif; font-size: 14px; font-weight: normal; hyphens: auto; line-height: 1.3; margin: 0; padding: 30px; text-align: left; vertical-align: top; word-wrap: break-word;">
      For questions, reach out to #spinnaker in Slack.<br>
    </td>
  </tr>
</table>
</body>
</html>
