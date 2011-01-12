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
package com.collabnet.svnedge.replication


import com.collabnet.svnedge.replica.manager.ApprovalState;

import grails.test.*

class ReplicaCommandsExecutorIntegrationTests extends GrailsUnitTestCase {

    def replicaCommandExecutorService
    def svnNotificationService

    def REPO_NAME = "testproject2"
    def rConf
    
    public ReplicaCommandsExecutorIntegrationTests() {
        // delete the repo directory for the repo we are adding.
        this.rConf = ReplicaConfiguration.getCurrentConfig()
        if (!this.rConf) {
            rConf = new ReplicaConfiguration(svnMasterUrl: null, 
                name: "Test Replica", description: "Super replica", 
                message: "Auto-approved", systemId: "replica1001", 
                svnSyncRate: 5, approvalState: ApprovalState.APPROVED)
            this.rConf.save()
        }
    }

    protected void setUp() {
        super.setUp()

        assertNotNull("The replica instance must exist", this.rConf)

        def repoFileDir = new File(
            svnNotificationService.getReplicaParentDirPath() + "/" + REPO_NAME)
        deleteRecursive(repoFileDir)
    }

    /**
     * Test processing a bad command.
     */
    void testProcessBadCommand() {
        def badCommand = [code: 'Notacommand', id: 0, params: []]
        def result = replicaCommandExecutorService.processCommandRequest(
            badCommand)
        assertNotNull("Processing a bad command should not return null.", 
                      result)
        assertNotNull("Processing a bad command should return an exception.", 
                      result['exception'])
        assertFalse("Processing a bad command should return a false succeeded.",
                    result['succeeded'])
    }

    /**
     * Test processing a good add command.
     */
    void testProcessAddCommand() {
        def cmdParams = [:]
        cmdParams["repoName"] = REPO_NAME

        def command = [code: 'repoAdd', id: 0, params: cmdParams]
        def result = replicaCommandExecutorService.processCommandRequest(
            command)
        assertNotNull("Processing a command should not return null.", 
                      result)
        if (result['exception']) {
            println result['exception']
        }
        assertNull("Processing a command should not return an exception.\n" + 
                   result['exception'], result['exception'])
        assertTrue("Processing a command should return a true succeeded.",
                   result['succeeded'])
    }

    /**
     * Test processing a good remove command.
     */
    void testProcessRemoveCommand() {
        def cmdParams = [:]
        cmdParams["repoName"] = REPO_NAME
        // add first
        def command = [code: 'repoAdd', id: 0, params: cmdParams]
        def result = replicaCommandExecutorService.processCommandRequest(
            command)
        // then remove
        command = [code: 'repoRemove', id: 0, params: cmdParams]
        result = replicaCommandExecutorService.processCommandRequest(command)
        assertNotNull("Processing a command should not return null.", result)
        if (result['exception']) {
            println result['exception']
        }
        assertNull("Processing a command should not return an exception.\n" + 
                   result['exception'], result['exception'])
        assertTrue("Processing a command should return a true succeeded.",
                   result['succeeded'])
    }

    // recursively delete the file/directory
    static void deleteRecursive(file) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory()) {
            // delete contents
            file.listFiles().each { deleteRecursive(it) }
        }
        file.delete()
    }
}