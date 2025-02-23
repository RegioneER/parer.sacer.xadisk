/*
 * Engineering Ingegneria Informatica S.p.A.
 *
 * Copyright (C) 2023 Regione Emilia-Romagna
 * <p/>
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package org.xadisk.filesystem.exceptions;

import org.xadisk.bridge.proxies.interfaces.Session;
import org.xadisk.bridge.proxies.interfaces.XADiskBasicIOOperations;
import org.xadisk.bridge.proxies.interfaces.XASession;
import org.xadisk.connector.outbound.XADiskConnection;
import org.xadisk.filesystem.FileSystemConfiguration;

/**
 * This exception is thrown by the i/o operation methods in
 * {@link XADiskBasicIOOperations} when a lock over a relevant file/directory could
 * not be acquired within the lock wait timeout period. Waiting for a lock
 * allows some space for other transactions holding the lock to complete and release
 * the locks.
 * <p> Note that the value of lock wait timeout period defaults to the
 * {@link FileSystemConfiguration#getLockTimeOut() global-configuration}, and can be overridden
 * at a {@link Session}/{@link XASession}/{@link XADiskConnection} level by
 * {@link XADiskBasicIOOperations#setFileLockWaitTimeout(long) setFileLockWaitTimeout}.
 *
 * @since 1.0
 */

public class LockingTimedOutException extends LockingFailedException {

    private static final long serialVersionUID = 1L;
    
    public LockingTimedOutException(String path) {
        super(path);
    }

    @Override
    public String getMessage() {
        return super.getGenericMessage() + " The reason is : "
                + "An attempt to acquire the lock has timed-out.";
    }
}
