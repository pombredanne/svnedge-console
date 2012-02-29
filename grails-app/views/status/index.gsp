<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <meta name="layout" content="main" />
    <style>
      p.dialogTitle {
        padding-top: 0px;
        margin-top: 5px;
      }
      p.dialogBody {
        margin-top: 10px;
      }
    </style>
    <script type="text/javascript">

      // add restart support for unapplied updates
      Event.observe(window, 'load', function() {
          var restartLinkElement = $('restartLink')
          if (restartLinkElement == null) {
              return
          }
          restartLinkElement.observe('click', function(event){
            // show modal info dialog
            var title = "<p class='dialogTitle'>${message(code: 'packagesUpdate.page.installUpdatesStatus.serverIsRestarting')}</p>"
            var msg = "<p class='dialogBody'>${message(code: 'packagesUpdate.page.installUpdatesStatus.serverIsRestarting.tip')}"
            msg += " ${message(code: 'packagesUpdate.page.installUpdatesStatus.serverIsRestarting.tip2')}</p>"
            msg += "<img src=\"${resource(dir:'images',file:'spinner-gray-bg.gif')}\" align=\"baseline\"/>"
            Dialog.info(msg, {title: title, width:250, height: 90, showProgress: false, className: "bluelighting"});

            // execute restart
            new Ajax.Request('/csvn/status/restartConsole', {
                method:'post',
                requestHeaders: {Accept: 'text/json'},
                onSuccess: function(transport) {
                   responseData = transport.responseText.evalJSON(true);
                   status = responseData.result.restart
                   timeoutId = window.setTimeout('waitForRestart()', 5000);
                },
                onFailure: function(transport) {
                   Dialog.setInfoMessage("<p class='dialogBody'>${message(code: 'packagesUpdate.page.installUpdatesStatus.serverIsRestarting.failed')}</p>");
                }
            })
          });
      })

      var pingUrl = "/csvn/images/project/project-homeicon.gif";
      var timeoutId

      function waitForRestart() {
        var image = new Image();
        var uniqueUrl = pingUrl + "?s=" + Math.random();
        image.onload = function() {
          if (this.height > 0)  {
            window.location = "/csvn"
          }
          else {
            timeoutId = window.setTimeout('waitForRestart()', 5000);
          }
        }
        image.onerror = function() {
          timeoutId = window.setTimeout('waitForRestart()', 5000);
        }
        image.src = uniqueUrl
      }

    <g:if test="${isReplicaMode}">

        /** Handle for the polling function */
        var periodicUpdater

        // instantiate the polling task on load
        Event.observe(window, 'load', function() {
            periodicUpdater = new PeriodicalExecuter(fetchReplicationData, 1)
        })

        /** function to fetch replication info and update ui */
        function fetchReplicationData() {

          new Ajax.Request('/csvn/status/replicationInfo', {
                  method:'get',
                  requestHeaders: {Accept: 'text/json'},
                  onSuccess: function(transport) {
                     responseData = transport.responseText.evalJSON(true);
                     numberOfCommands = responseData.relicaServerInfo.runningCmdsSize
                     updateUiCommandsRunning(numberOfCommands)
                  }
          })
        }

        /**
         * If there are commands running, then print the number and 
         */
        function updateUiCommandsRunning(numberOfCommands) {
            if (numberOfCommands > 0) {
                $('spinner').src = '/csvn/images/replica/commands_updating_spinner.gif';
                $('commandsCount').innerHTML = '<g:message code="status.page.status.replication.commands_running"/> ' + numberOfCommands;
            } else {
                $('spinner').src = '/csvn/images/fping_up.gif';
                $('commandsCount').innerHTML = '<g:message code="status.page.status.replication.no_commands"/>'; 
            }
        }
    </g:if>
    </script>

  </head>
  <body>

    <content tag="title">
      <g:message code="status.page.header.title" />
    </content>
    
    <!-- Following content goes in the left nav area -->
    <g:render template="/server/leftNav" />
    
  <g:form method="post">
  <div class="row-fluid">
    <div class="span7 well">
    <g:if test="${isReplicaMode}">
      <div class="ImageListParent">
        <strong><g:message code="status.page.replica.name" /></strong> ${currentReplica.name}
      </div>
      <g:if test="${currentReplica.svnMasterUrl}">
         <div class="ImageListParent">
           <strong><g:message code="status.page.replica.location" /></strong> ${currentReplica.svnMasterUrl}
         </div>
      </g:if>
    </g:if>
    <g:if test="${ctfUrl}">
      <div class="ImageListParent">
        <strong><g:message code="status.page.url.teamforge" /></strong> <a href="${ctfUrl}" target="_blank">${ctfUrl}</a>
      </div>
    </g:if>
    <g:if test="${isStarted}">
      <div class="ImageListParent">
        <strong><g:message code="status.page.subversion" /> </strong>
        <img src="${resource(dir:'images', file:'fping_up.gif')}" width="16" height="16"
                         hspace="4" alt="<g:message code='status.page.subversion.on' />"/><g:message code="status.page.subversion.on" />
      </div>
      <div class="ImageListParent">
        <strong><g:message code="status.page.hostname" /> </strong> ${server.hostname}
      </div>
     <g:if test="${!ctfUrl && server.viewvcURL()}">
      <div class="ImageListParent">
        <strong><g:message code="status.page.url.repository" /></strong> <a href="${server.svnURL()}" target="_blank">${server.svnURL()}</a>
      </div>
      <div class="ImageListParent"><strong><g:message code="status.page.url.repository.browse" /></strong>
        <a href="${server.viewvcURL()}" target="_blank">${server.viewvcURL()}</a>
      </div>
     </g:if>
      <g:if test="${isReplicaMode}">
        <div class="ImageListParent"><strong><g:message code="status.page.status.replication.activity" /></strong>
        <g:set var="replicationStatusIcon" value="fping_up.gif" />
        <g:if test="${replicaCommandsSize > 0}">
            <g:set var="replicationStatusIcon" value="replica/commands_updating_spinner.gif" />
        </g:if>
        <img src="/csvn/images/${replicationStatusIcon}" id="spinner">
             <div id="commandsCount">
               <g:if test="${replicaCommandsSize == 0}">
                 <g:message code="status.page.status.replication.no_commands"/>
               </g:if>
               <g:else>
                 <g:message code="status.page.status.replication.commands_running"/> ${replicaCommandsSize}
               </g:else>
             </div>
        </div>
      </g:if>
   </g:if>
   <g:else>
      <div class="ImageListParent">
        <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_SYSTEM">
        </g:ifAnyGranted>
        <strong><g:message code="status.page.subversion" /> </strong>
        <img src="${resource(dir:'images', file:'fping_down.gif')}" width="16" height="16"
                         hspace="4" alt="<g:message code='status.page.subversion.off' />"/><g:message code="status.page.subversion.off" />
      </div>
      <div class="ImageListParent">
        <strong><g:message code="status.page.hostname" /> </strong> ${server.hostname}
      </div>
    </g:else>
    </div>
    <div class="span1">
        <g:if test="${isStarted}">
          <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_SYSTEM">
            <g:actionSubmit class="btn btn-danger" value="${message(code:'status.page.subversion.stop')}" action="stop"/>
          </g:ifAnyGranted>
        </g:if>
        <g:else>
          <g:ifAnyGranted role="ROLE_ADMIN,ROLE_ADMIN_SYSTEM">
            <g:actionSubmit class="btn btn-success" value="${message(code:'status.page.subversion.start')}" action="start"/>
          </g:ifAnyGranted>
        </g:else>       
    </div>
    </div>
    </g:form>


      <h2><g:message code="status.page.header.information"/></h2>
      <table class="table table-striped table-bordered table-condensed">
        <tbody>
        <g:if test="${softwareVersion}">
          <tr>
            <td><strong><g:message code="status.page.status.version.software"/></strong></td>
            <td>${softwareVersion}</td>
          </tr>
        </g:if>
        <g:if test="${svnVersion}">
          <tr>
            <td><strong><g:message code="status.page.status.version.subversion"/></strong></td>
            <td>${svnVersion}</td>
          </tr>
        </g:if>
        <g:each status="i" var="stat" in="${perfStats}">
          <tr>
            <td><strong>${stat.label}</strong></td>
            <td>${stat.value ?: message(code: 'status.page.status.noData')}</td>
          </tr>
        </g:each>
        </tbody>
      </table>
  
  </body>
</html>
