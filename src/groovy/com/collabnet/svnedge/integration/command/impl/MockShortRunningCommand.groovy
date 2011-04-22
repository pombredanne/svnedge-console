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
package com.collabnet.svnedge.integration.command.impl

import com.collabnet.svnedge.integration.command.AbstractCommand
import org.apache.log4j.Logger

/**
 * This command simulates a long running Replica command for testing purposes
 */
public class MockShortRunningCommand extends AbstractCommand 
        implements com.collabnet.svnedge.integration.command.LongRunningCommand {

    private Logger log = Logger.getLogger(getClass())

    def constraints() {
        log.debug("Constraints...")
    }

    def execute() {
        log.debug("Executing short-running command with 5 second wait...")
        Thread.sleep 5000
    }

    def undo() {
        log.debug("Undoing...")
    }
}
