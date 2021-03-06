/*
 * CollabNet Subversion Edge
 * Copyright (C) 2011, CollabNet Inc. All rights reserved.
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
package com.collabnet.svnedge.schema;

import java.sql.SQLException;
import java.util.List;

import org.apache.log4j.Logger;

public class MS014AddMonitorConfiguration implements MigrationScript {
    private Logger log = Logger.getLogger(getClass());

    public boolean migrate(SqlUtil db) throws SQLException {
        
        int[] version = {3, 2, 2};
        if (!db.isSchemaCurrent(version)) {

            String createTable = "CREATE MEMORY TABLE MONITORING_CONFIGURATION(" +
                    "ID BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) " +
                    "  NOT NULL PRIMARY KEY, " +
                    "VERSION BIGINT NOT NULL, " +
                    "NETWORK_ENABLED BOOLEAN default true NOT NULL, " +
                    "NET_INTERFACE VARCHAR(255), " +
                    "IP_ADDRESS VARCHAR(255), " +
                    "REPO_DISK_ENABLED BOOLEAN default true NOT NULL, " +
                    "FREQUENCY VARCHAR(255), " +
                    "HOUR INTEGER default 0 NOT NULL, " +
                    "MINUTE INTEGER default 0 NOT NULL)";
            db.executeUpdateSql(createTable);

            db.executeUpdateSql("insert into MONITORING_CONFIGURATION " +
                    "(VERSION, NETWORK_ENABLED, NET_INTERFACE, IP_ADDRESS, " + 
                    "REPO_DISK_ENABLED, FREQUENCY) select 1, true, " +
                    "s.NET_INTERFACE, s.IP_ADDRESS, true, 'HALF_HOUR' from SERVER s");

            db.executeUpdateSql("alter table SERVER drop column NET_INTERFACE");
            db.executeUpdateSql("alter table SERVER drop column IP_ADDRESS");
        }
        
        db.executeUpdateSql("alter table MONITORING_CONFIGURATION " + 
                "alter column HOUR rename to REPO_DISK_HOUR_OF_DAY");
        db.executeUpdateSql("alter table MONITORING_CONFIGURATION " + 
                "alter column MINUTE rename to REPO_DISK_MINUTE_OF_DAY");
        db.executeUpdateSql("alter table MONITORING_CONFIGURATION " + 
                "add column REPO_DISK_FREQUENCY_HOURS integer default 1 NOT NULL");

        return false;
    }

    public int[] getVersion() {
        return new int[] {3,2,3};
    }
}
