/*
 * CollabNet Subversion Edge
 * Copyright (C) 2010, CollabNet Inc. All rights reserved.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.collabnet.svnedge.console.services;

import grails.test.GrailsUnitTestCase;

import com.collabnet.svnedge.console.CantBindPortException;
import com.collabnet.svnedge.console.Repository
import com.collabnet.svnedge.console.security.User
import com.collabnet.svnedge.console.ConfigUtil
import com.collabnet.svnedge.console.Server

class LifecycleServiceIntegrationTests extends GrailsUnitTestCase {
    def lifecycleService
    def commandLineService
    def serverConfService
    def csvnAuthenticationProvider
    boolean initialStarted
    def repoParentDir 

    protected void setUp() {
        super.setUp()
        initialStarted = lifecycleService.isStarted()
        // start tests with the server off
        if (initialStarted) {
            lifecycleService.stopServer()
        }

        // Setup a test repository parent
        repoParentDir = createTestDir("repo")
        Server server = lifecycleService.getServer()
        server.repoParentDir = repoParentDir.getCanonicalPath()
        server.save()
    }

    protected void tearDown() {
        super.tearDown()
    }

    void testStartAndStopServer() {
        assertFalse("Should start off with server stopped", 
                    lifecycleService.isStarted())
        def status = lifecycleService.startServer()
        assertServerIsRunning(status)
        Thread.sleep 1000
        status = lifecycleService.stopServer()
        assertServerIsStopped(status)
    }

    private void assertServerIsRunning(int status) {
        assertEquals "startServer exitStatus should be 0", 0, status
        assertTrue "isStarted should return true", lifecycleService.started
        File httpdPidFile = ConfigUtil.httpdPidFile()
        assertTrue "httpd.pid was not found", httpdPidFile.exists()
        assertEquals "Tried to start already started server. Should return -1",
            -1, lifecycleService.startServer()

    }

    private void assertServerIsStopped(int status) {
        assertEquals "stopServer exitStatus should be 0", 0, status
        assertFalse "isStarted should return false", lifecycleService.isStarted()
        File httpdPidFile = ConfigUtil.httpdPidFile()
        assertFalse "httpd.pid was unexpectedly found", httpdPidFile.exists()   
    }

    /**
     * Test setup may be configured to allow low port usage or not, but we
     * can't alter it during the tests, so will just check that it
     * succeeds or fails as would be expected.
     */
    void testPort80() {
        assertFalse("Should start off with server stopped", 
                    lifecycleService.isStarted())
        boolean isPort80Allowed = lifecycleService.isDefaultPortAllowed()
        Server server = lifecycleService.getServer()
        def origPort = server.port
        server.port = 80
        server.save()
        try {
            def status = lifecycleService.startServer()
            if (isPort80Allowed) {
                assertServerIsRunning(status)
                status = lifecycleService.stopServer()
                assertServerIsStopped(status)
            } else {
                assertEquals "startServer exitStatus should be 1", 1, status
                assertFalse "isStarted should return false", 
                    lifecycleService.started
                File httpdPidFile = ConfigUtil.httpdPidFile()
                assertFalse "httpd.pid was unexpectedly found", 
                    httpdPidFile.exists()   
            }
            server.port = origPort
            server.save()

        } catch (Exception e) {
            System.err.println(e)
        }
    }



    void testSetSvnAuth() {
        def testusername = 'testuser'
        def confDir = createTestDir("conf")
        def origConfDirPath = ConfigUtil.confDirPath()
        try {
        ConfigUtil.confDirPath = confDir.absolutePath

        User u = new User(username: testusername, enabled: true)
        lifecycleService.setSvnAuth(u, "mypassword")
        File authFile = new File(confDir, "svn_auth_file")
        assertTrue "auth file does not exist", authFile.exists()
        assertTrue "testuser not found in auth file", 
            authFile.getText().indexOf(testusername) >= 0


        lifecycleService.removeSvnAuth(u)
        assertTrue "testuser still in auth file when removed",
            authFile.getText().indexOf(testusername) < 0
        } finally {
            ConfigUtil.confDirPath = origConfDirPath
        }
    }

    void testSSLInstance() {
        assertFalse("Should start off with server stopped", 
                    lifecycleService.isStarted())
        def confDir = createTestDir("conf")
        def origConfDirPath = ConfigUtil.confDirPath()
        ConfigUtil.confDirPath = confDir.absolutePath
        copyConfFiles(origConfDirPath, confDir)
        Server server = lifecycleService.getServer()
        server.useSsl = true
        server.save()
        def status = lifecycleService.startServer()
        assertEquals "startServer exitStatus should be 0", 0, status
        assertTrue "isStarted should return true", lifecycleService.started
        File httpdPidFile = ConfigUtil.httpdPidFile()
        assertTrue "httpd.pid was not found", httpdPidFile.exists()
        assertEquals "Tried to start already started server. Should return -1",
            -1, lifecycleService.startServer()
        File sslkeyfile = new File(confDir, "server.key")
        assertTrue "server.key was not found", sslkeyfile.exists()
        File sslcrtfile = new File(confDir, "server.crt")
        assertTrue "server.crt was not found", sslkeyfile.exists()

        status = lifecycleService.stopServer()
        assertEquals "stopServer exitStatus should be 0", 0, status
        assertFalse "isStarted should return false", lifecycleService.isStarted()
        assertFalse "httpd.pid was unexpectedly found", httpdPidFile.exists()
        server.useSsl = false
        server.save()
        ConfigUtil.confDirPath = origConfDirPath
    }

    void testLDAPAuth() {
        assertFalse("Should start off with server stopped",
                    lifecycleService.isStarted())
        def confDir = createTestDir("conf")
        def origConfDirPath = ConfigUtil.confDirPath()
        ConfigUtil.confDirPath = confDir.absolutePath

        Server server = lifecycleService.getServer()

        server.ldapEnabled = true
        server.ldapServerHost = "maa.corp.collab.net"
        server.ldapAuthBasedn = "dc=maa, dc=corp, dc=collab, dc=net"
        server.ldapAuthBinddn = "CN=XXX, OU=People, DC=maa, DC=corp, DC=collab, DC=net"
        server.ldapAuthBindPassword = "secret"
        server.ldapLoginAttribute = "sAMAccountName"
        server.ldapSearchScope = "sub"
        server.ldapSecurityLevel = "NONE"

        server.save()

        copyConfFiles(origConfDirPath, confDir)

        def status = lifecycleService.startServer()
        assertEquals "startServer exitStatus should be 0", 0, status
        assertTrue "isStarted should return true", lifecycleService.started
        File httpdPidFile = ConfigUtil.httpdPidFile()
        assertTrue "httpd.pid was not found", httpdPidFile.exists()

        File svnHttpdFile = new File(confDir, "csvn_main_httpd.conf")
        assertTrue "csvn_main_httpd file does not exist", svnHttpdFile.exists()
        def svnHttpdFileContents = svnHttpdFile.getText()

        def expectedDirective = "AuthLDAPUrl \"ldap://${server.ldapServerHost}/${server.ldapAuthBasedn}?${server.ldapLoginAttribute}?${server.ldapSearchScope}\" \"NONE\""
        assertTrue "Ldap configuration AuthLDAPUrl is missing",
            svnHttpdFileContents.indexOf(expectedDirective) >= 0

        expectedDirective = "AuthLDAPBindDN \"${server.ldapAuthBinddn}\""
        assertTrue "Ldap configuration AuthLDAPBindDN is missing",
            svnHttpdFileContents.indexOf(expectedDirective) >= 0

        expectedDirective = "AuthLDAPBindPassword \"${server.ldapAuthBindPassword}\""
        assertTrue "Ldap configuration AuthLDAPBindPassword is missing",
            svnHttpdFileContents.indexOf(expectedDirective) >= 0

        svnHttpdFile = new File(confDir, "svn_viewvc_httpd.conf")
        assertTrue "svn_viewvc_httpd file does not exist", svnHttpdFile.exists()
        svnHttpdFileContents = svnHttpdFile.getText()
        expectedDirective = "AuthBasicProvider csvn-file-users ldap-users"
        assertTrue "Ldap Auth provider configuration is missing ",
            svnHttpdFileContents.indexOf(expectedDirective) >= 0

        assertEquals "Tried to start already started server. Should return -1",
            -1, lifecycleService.startServer()


        log.info "Checking apache status before problematic attempt to stop"
        log.info "Started? " + lifecycleService.isStarted()
        Thread.sleep(3000)
        status = lifecycleService.stopServer()
        assertEquals "stopServer exitStatus should be 0", 0, status

        // wait 3 seconds to let processes wind down, then verify that server is stopped
        Thread.sleep(3000)
        if (lifecycleService.isStarted()) {

            def logfile = getApacheErrorLog()
            echoFileContents(logfile)
            fail "isStarted should return false"
        }

        assertFalse "httpd.pid was unexpectedly found", httpdPidFile.exists()
        ConfigUtil.confDirPath = null

        server.ldapEnabled = false
        server.ldapServerHost = ""
        server.ldapAuthBasedn = ""
        server.ldapAuthBinddn = ""
        server.ldapAuthBindPassword = ""
        server.ldapLoginAttribute = ""
        server.ldapSearchScope = ""

        server.save()
    }
    
    void testAuthHelper() {
        
        assertFalse("Should start off with server stopped",
                    lifecycleService.isStarted())
        
        Server server = lifecycleService.getServer()
        server.ldapEnabled = true
        server.save()
        
        lifecycleService.startServer()
        
        String authHelperUrl = csvnAuthenticationProvider.getAuthHelperUrl(server)
        
        assertTrue("Apache should have verifiable auth helper endpoint",
                    csvnAuthenticationProvider.testAuthListener(authHelperUrl))
    }

    private def copyConfFiles(origConfDirPath, confDir) {
        String s = new File(origConfDirPath, "viewvc.conf.dist").getText()
        new File(confDir, "viewvc.conf.dist").write(s)
        s = new File(origConfDirPath, "mime.types").getText()
        new File(confDir, "mime.types").write(s)
        s = new File(origConfDirPath, "httpd.conf.dist").getText()
        new File(confDir, "httpd.conf.dist").write(s)
    }

    private def echoFileContents(File logfile) {
        if (logfile.exists()) {
            log.info "${logfile.name} contents:\n" + logfile.text
        }
        else {
            log.info "Attempted to print ${logfile.name}, but not found"
        }
    }

    File getApacheErrorLog() {

        File logDir = new File(ConfigUtil.dataDirPath(), "logs")
        def logNameSuffix = serverConfService.getLogFileSuffix()
        return new File(logDir, "error_${logNameSuffix}.log")
    }


    private File createTestDir(String prefix) {
        def testDir = File.createTempFile(prefix + "-test", null)
        log.info("testDir = " + testDir.getCanonicalPath())
        // we want a dir, not a file, so delete and mkdir
        testDir.delete()
        testDir.mkdir()
        // TODO This doesn't seem to work, might need to delete in teardown
        testDir.deleteOnExit()
        return testDir
    }
}
