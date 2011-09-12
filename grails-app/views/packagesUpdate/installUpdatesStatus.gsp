<html>
  <head>
    <title>CollabNet Subversion Edge <g:message code="packagesUpdate.page.installUpdatesStatus.title" /></title>

      <meta name="layout" content="pkgupdates" />
      <g:javascript library="prototype" />
      <g:javascript src="jsProgressBarHandler.js" />
      <script type="text/javascript" src="/csvn/plugins/cometd-0.1.5/dojo/dojo.js"
                djconfig="parseOnLoad: true, isDebug: false"></script>

    <g:set var="restartServer" value="${message(code:'packagesUpdate.page.installUpdatesStatus.restartServer')}" />
    <g:set var="installFinished" value="${message(code:'packagesUpdate.page.installUpdatesStatus.finished')}" />
    <g:set var="serverRestarting" value="${message(code:'packagesUpdate.page.installUpdatesStatus.serverIsRestarting')}" />
    <g:set var="serverRestartingTip" value="${message(code:'packagesUpdate.page.installUpdatesStatus.serverIsRestarting.tip')}" />
    <g:set var="serverRestartingTip2" value="${message(code:'packagesUpdate.page.installUpdatesStatus.serverIsRestarting.tip2')}" />

    <script type="text/javascript">
        /**
         * Author: Marcello de Sales (mdesales@collab.net)
         * Date: May 12, 2010
         */
        dojo.require('dojox.cometd');
        dojo.require("dijit.Dialog");

        /** The timer instance */
        var t;

        /** Defines if timer, used in background requests, has been loaded */
        var timerIsOn = false;

        /** Defines if the process has been finished */
        var hasFinished = false;

        /**
         * Makes an asynchronous non-blocking HTTP POST request to 
         * "packagesUpdate/confirmStart" to confirm the start of the upgrade 
         * process.
         */
        function startUpgrade() {
            var xhrArgs = {
                url: "confirmStart",
                handleAs: "text",
                preventCache: true,
                handle: function(error, ioargs) {
                }
            }
            //Call the asynchronous xhrGet
            var deferred = dojo.xhrPost(xhrArgs);
        }

        /**
         * Function instance that is used to initialize upgrade process. Namely,
         * the dojox.cometd component, as well as the major UI objects for the 
         * initial status.
         */
        var init = function() {
            dojox.cometd.init('/csvn/plugins/cometd-0.1.5/cometd');
            dojox.cometd.subscribe('/csvn-updates/percentages', onMessage);
            dojox.cometd.subscribe('/csvn-updates/status', onMessage);

            dojo.byId('restartButton').disabled = true;
            dijit.byId('updateProcessDialog').show();
            startUpgrade();
        };
        dojo.addOnLoad(init);

        /**
         * Function instance that is used to finish the upgrade process. It
         * finishes the dojox.cometd components.
         */
        var destroy = function() {
            dojox.cometd.unsubscribe('/csvn-updates/percentages');
            dojox.cometd.unsubscribe('/csvn-updates/status');
            dojox.cometd.disconnect();
        };
        dojo.addOnUnload(destroy);

        /**
         * Callback function for the cometd client that receives the messages
         * from the subscribed channels.
         * @param m is the message object proxied by the cometd server from 
         * one of the subscribed channels.
         * @see init()
         */
        function onMessage(m) {
            if (!hasFinished) {
                var c = m.channel;
                var o = eval('('+m.data+')')
                if (c == "/csvn-updates/percentages") {
                    myJsProgressBarHandler.setPercentage('progressStatus_overallPercentage', o.overallPercentage)

                    if (o.overallPercentage == 100) {
                        hasFinished = true
                        dojo.byId('restartButton').disabled = false;
                        dojo.byId('roller').style.display = 'none';
                        dojo.byId('restartServer').innerHTML = "${restartServer}";
                        dojo.byId('progressStatus_phase').innerHTML = "${installFinished}"
                        dojo.byId('progressStatus_statusMessage').innerHTML = "";
                        destroy()
                    }

                } else {

                    if (o.phase != "") {
                        dojo.byId('progressStatus_phase').innerHTML = o.phase;
                    }
                    if (o.statusMessage != "") {
                        dojo.byId('progressStatus_statusMessage').innerHTML = o.statusMessage;
                        dojo.byId('progressStatus_statusMessage').scrollTop = dojo.byId('progressStatus_statusMessage').scrollHeight;
                    }
                }
            }
        }

        /**
         * Makes an asynchronous non-blocking HTTP GET request to "/csvn" to 
         * confirm the verify if the server is reachable while the server is 
         * restarting. When the server is back, the user is redirected to the
         * login page.
         */
        function serverRestartListener() {
            //The parameters to pass to xhrGet, the url, how to handle it, and the callbacks.
            var xhrArgs = {
                url: "/csvn",
                handleAs: "text",
                preventCache: true,
                handle: function(error, ioargs) {
                    switch (ioargs.xhr.status) {
                    case 200:
                    case 301:
                    case 302:
                        window.location = "/csvn"
                        break;
                    }
                }
            }
            //Call the asynchronous xhrGet
            var deferred = dojo.xhrGet(xhrArgs);
        }

        /**
         * Utility method that waits for the server to restart at every
         * 5 seconds.
         */
        function waitForCsvnServer() {
            serverRestartListener();
            t = setTimeout("waitForCsvnServer();", 5000);
        }

        /**
         * Makes an asynchronous non-blocking HTTP GET request to 
         * "packagesUpdate/restartServer" to request the server to restart.
         */
        function requestServerRestart() {
            var xhrArgs = {
                url: "restartServer",
                handleAs: "text",
                preventCache: true,
                handle: function(error, ioargs) {
                }
            }
            //Call the asynchronous xhrGet
            var deferred = dojo.xhrPost(xhrArgs);
        }

        /**
         * Starts the background listener process, changing UI objects for the
         * transition from when the process finishes to the server restart.
         */
        function startBackgroundListener() {
            dojo.byId('roller').style.display = '';
            dojo.byId('progressStatus_phase').innerHTML = "${serverRestarting}&#133;";
            dojo.byId('progressStatus_statusMessage').value = '';
            dojo.byId('progressStatus_statusMessage').style.display = 'none';
            dojo.byId('progressStatus_overallPercentage').style.display = 'none';
            dojo.byId('restartServer').innerHTML = "${serverRestartingTip} <BR> ${serverRestartingTip2}";
            dojo.byId('restartButton').disabled = true;
            requestServerRestart();
            if (!timerIsOn) {
                timerIsOn = true;
                waitForCsvnServer();
            }
        }

    </script>

    <style type="text/css">
        @import "/csvn/plugins/cometd-0.1.5/dojo/resources/dojo.css";
        .dijitDialogCloseIcon { display:none }

        div#progressStatus_statusMessage {
          width:350px;
          height: 35px;
          background-color: #dddddd;
          margin-top: 10px;
          padding: 5px 2px 0px 5px;
          overflow: hidden;
        }

    </style>
    <link rel="stylesheet"
        href="/csvn/plugins/cometd-0.1.5/dijit/themes/tundra/tundra.css" />

  </head>

    <g:if test="${session.install.equals('addOns')}">
        <g:set var="processType" value="${message(code:'packagesUpdate.page.installUpdatesStatus.installing')}" />
    </g:if>
    <g:else>
        <g:set var="processType" value="${message(code:'packagesUpdate.page.installUpdatesStatus.upgrading')}" />
    </g:else>

  <content tag="title">
    <%= processType %>
  </content>

  <body>

    <div style="position: absolute; top: -9999px; opacity: 0; left: 478px; visibility: hidden;" 
         dojoType="dijit.Dialog" id="updateProcessDialog" widgetid="updateProcessDialog" 
         title="CollabNet Subversion Edge ${message(code:'packagesUpdate.page.installUpdatesStatus.header')}" role="dialog" tabindex="-1"
         class="dijitDialog dijitContentPane" wairole="dialog"
         waistate="labelledby-updateProcessDialog_title">
      <table>
        <tr>
          <td valign="middle">
            <img src="/csvn/images/pkgupdates/roller.gif" id="roller" align="middle">
            <font size="3"><strong><span id="progressStatus_phase">
               <g:message code="packagesUpdate.page.installUpdatesStatus.initialPhase" />&#133;</span></strong>
            </font>
          </td>
        </tr>
        <tr>
          <td>
            <div id="progressStatus_statusMessage" name="progressStatus_statusMessage">
            </div>
          </td>
        </tr>
        <tr>
          <td align="center">
               &nbsp;
          </td>
        </tr>
        <tr>
          <td align="center">
               <span id="progressStatus_overallPercentage" class="progressBar">0%</span>
          </td>
        </tr>
        <tr>
          <td align="center">
               <font size="3"><strong><span id="restartServer"></span></strong></font>
          </td>
        </tr>
        <tr>
          <td align="center">
               &nbsp;
          </td>
        </tr>
        <tr>
          <td>
            <div class="AlignRight">
              <input id="restartButton" type="button" value="${message(code:'packagesUpdate.page.installUpdatesStatus.button.restart')}"
                 class="Button" onClick="javascript:startBackgroundListener();" />
            </div>
          </td>
        </tr>
      </table>
    </div>

    <BR><BR><BR><BR>

  </body>
</html>
