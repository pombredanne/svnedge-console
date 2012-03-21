%{--
  - CollabNet Subversion Edge
  - Copyright (C) 2012, CollabNet Inc. All rights reserved.
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -  
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  --}%

<%@ page import="org.springframework.web.util.JavaScriptUtils; org.springframework.scheduling.quartz.QuartzJobBean" %>

<h3>${heading}</h3>
<table class="table table-striped table-bordered table-condensed tablesorter" id="${tableName}"></table>

  <script type="text/javascript">
  /* Data set */
  var aDataSet = [
  <g:each in="${itemList}" var="jobCtx" status="i">
    <g:set var="jobDesc" value="${jobCtx.mergedJobDataMap.description}"></g:set>
    <g:if test="${view == 'scheduled' && jobCtx.mergedJobDataMap.urlConfigure}">
      <g:set var="jobDescLink" value="<a href='${jobCtx.mergedJobDataMap.urlConfigure}'>${message(code: 'job.page.list.jobConfigure')}</a>"></g:set>
    </g:if>
    <g:elseif test="${view == 'running' && jobCtx.mergedJobDataMap.urlProgress}">
      <g:set var="jobDescLink" value="<a target='${jobCtx.mergedJobDataMap.id}' href='${jobCtx.mergedJobDataMap.urlProgress}'>${message(code:'job.page.list.jobProgress')}</a>"></g:set>
    </g:elseif>
    <g:elseif test="${view == 'finished' && jobCtx.mergedJobDataMap.urlResult}">
      <g:set var="jobDescLink" value="<a href='${jobCtx.mergedJobDataMap.urlResult}'>${message(code: 'job.page.list.jobResult')}</a>"></g:set>
    </g:elseif>

    <g:set var="scheduledTime" value="${view == 'scheduled' ? jobCtx.nextFireTime : jobCtx.scheduledFireTime}"/>
    ['${i + 1}',
      '${jobCtx.mergedJobDataMap.id}',
      '${JavaScriptUtils.javaScriptEscape(jobDesc)}',
      '${(scheduledTime) ? formatDate(format: logDateFormat, date: scheduledTime) : "-"}',
      '${(jobCtx.fireTime) ? formatDate(format: logDateFormat, date: jobCtx.fireTime) : "-"}',
      '${(jobCtx.jobRunTime > -1) ? formatDate(format: logDateFormat, date: new Date(jobCtx.fireTime.time + jobCtx.jobRunTime)) : "-"}'
    ] <g:if test="${i < (itemList.size() - 1)}">,</g:if>
  </g:each>
  ];
</script>
  
<g:javascript library="jquery.dataTables.min"/>
<g:javascript library="DT_bootstrap"/>
<g:javascript>
  /* Table initialisation */
  $(document).ready(function() {
    $('#${tableName}').dataTable( {
      "aaData": aDataSet,
      "aoColumns": [
        {"sTitle": "#"},
        {"sTitle": "${message(code: 'job.page.list.column.id')}"},
        {"sTitle": "${message(code: 'job.page.list.column.description')}" },
        {"sTitle": "${message(code: 'job.page.list.column.scheduled')}" },
        {"sTitle": "${message(code: 'job.page.list.column.started_at')}" },
        {"sTitle": "${message(code: 'job.page.list.column.finished_at')}" }
		  ],
      "sDom": "<'row'<'span4'l><'pull-right'f>r>t<'row'<'span4'i><'pull-right'p>><'spacer'>",
      "sPaginationType": "bootstrap",
      "bStateSave": true,
      "oLanguage": {
        "sLengthMenu": "${message(code:'datatable.rowsPerPage')}",
        "oPaginate": {
            "sNext": "${message(code:'default.paginate.next')}",
            "sPrevious": "${message(code:'default.paginate.prev')}"
        },
        "sSearch": "${message(code:'default.filter.label')}",
        "sZeroRecords": "${message(code:'default.search.noResults.message')}",
        "sEmptyTable": "${message(code: 'job.page.list.row.job_idle')}",
        "sInfo": "${message(code:'datatable.showing')}",
        "sInfoEmpty": "${message(code:'datatable.showing.empty')}",
        "sInfoFiltered": " ${message(code:'datatable.filtered')}"
        },
      "aaSorting": [[ 0, "asc" ]]
    } );
  } );
</g:javascript>