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
package com.collabnet.svnedge

/**
 * An exception which may be thrown within a service to indicate an invalid input.
 */
class ValidationException extends RuntimeException {
    
    String field
    
    /**
     * Creates a ValidationException with the given errorKey.
     * @param errorKey a message resource key
     */
    public ValidationException(String errorKey) {
        super(errorKey)
    }

    /**
    * Creates a ValidationException with the given errorKey.
    * @param errorKey a message resource key
    */
   public ValidationException(String errorKey, String field) {
       super(errorKey)
       this.field = field
   }
}
