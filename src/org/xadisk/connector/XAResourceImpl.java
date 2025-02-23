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

package org.xadisk.connector;

import org.xadisk.bridge.proxies.interfaces.Session;
import java.util.concurrent.ConcurrentHashMap;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.xadisk.filesystem.NativeXASession;
import org.xadisk.filesystem.SessionCommonness;
import org.xadisk.filesystem.XAFileSystemCommonness;
import org.xadisk.filesystem.TransactionInformation;
import org.xadisk.filesystem.exceptions.NoTransactionAssociatedException;
import org.xadisk.filesystem.exceptions.XASystemException;
import org.xadisk.filesystem.utilities.MiscUtils;

public class XAResourceImpl implements XAResource {

    private final NativeXASession xaSession;
    private final XAFileSystemCommonness xaFileSystem;
    private final ConcurrentHashMap<Xid, TransactionInformation> internalXids = new ConcurrentHashMap<Xid, TransactionInformation>(1000);
    private volatile int transactionTimeout;

    public XAResourceImpl(NativeXASession xaSession) {
        this.xaSession = xaSession;
        this.xaFileSystem = (XAFileSystemCommonness) xaSession.getUnderlyingXAFileSystem();
        transactionTimeout = xaFileSystem.getDefaultTransactionTimeout();
    }

    public void start(Xid xid, int flag) throws XAException {
        TransactionInformation internalXid = mapToInternalXid(xid);
        if (flag == XAResource.TMNOFLAGS) {
            try {
                Session session = xaSession.refreshSessionForNewXATransaction(internalXid);
                session.setTransactionTimeout(transactionTimeout);
            } catch (XASystemException xase) {
                throw MiscUtils.createXAExceptionWithCause(XAException.XAER_RMFAIL, xase);
            }
            xaSession.setTypeOfCurrentTransaction(NativeXASession.XA_TRANSACTION);
        }
        if (flag == XAResource.TMJOIN) {
            Session sessionOfTransaction = xaFileSystem.getSessionForTransaction(internalXid);
            if (sessionOfTransaction == null) {
                throw MiscUtils.createXAExceptionWithCause(XAException.XAER_INVAL, null);
            }
            xaSession.setSessionOfExistingXATransaction(sessionOfTransaction);
            xaSession.setTypeOfCurrentTransaction(NativeXASession.XA_TRANSACTION);
        }
        if (flag == XAResource.TMRESUME) {
            Session sessionOfTransaction = xaFileSystem.getSessionForTransaction(internalXid);
            if (sessionOfTransaction == null) {
                throw MiscUtils.createXAExceptionWithCause(XAException.XAER_INVAL, null);
            }
            xaSession.setSessionOfExistingXATransaction(sessionOfTransaction);
            xaSession.setTypeOfCurrentTransaction(NativeXASession.XA_TRANSACTION);
        }
    }

    public void end(Xid xid, int flag) throws XAException {
        TransactionInformation internalXid = mapToInternalXid(xid);
        Session sessionOfTransaction = xaFileSystem.getSessionForTransaction(internalXid);
        if (sessionOfTransaction == null) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_INVAL, null);
        }
        if (flag == XAResource.TMSUCCESS) {
            xaSession.setTypeOfCurrentTransaction(NativeXASession.NO_TRANSACTION);
        }
        if (flag == XAResource.TMFAIL) {
            xaSession.setTypeOfCurrentTransaction(NativeXASession.NO_TRANSACTION);
        }
        if (flag == XAResource.TMSUSPEND) {
            xaSession.setTypeOfCurrentTransaction(NativeXASession.NO_TRANSACTION);
        }
    }

    public int prepare(Xid xid) throws XAException {
        TransactionInformation internalXid = mapToInternalXid(xid);
        Session sessionOfTransaction = xaFileSystem.getSessionForTransaction(internalXid);
        if (sessionOfTransaction == null) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_INVAL, null);
        }
        try {
            if(((SessionCommonness)sessionOfTransaction).isUsingReadOnlyOptimization()) {
                ((SessionCommonness)sessionOfTransaction).completeReadOnlyTransaction();
                return XAResource.XA_RDONLY;
            } else {
                ((SessionCommonness)sessionOfTransaction).prepare();
                return XAResource.XA_OK;
            }
        } catch (NoTransactionAssociatedException note) {
            releaseFromInternalXidMap(xid);
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_OUTSIDE, note);
        } catch (XASystemException xase) {
            releaseFromInternalXidMap(xid);
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_RMFAIL, xase);
        }
    }

    public void rollback(Xid xid) throws XAException {
        TransactionInformation internalXid = mapToInternalXid(xid);
        Session sessionOfTransaction = xaFileSystem.getSessionForTransaction(internalXid);
        if (sessionOfTransaction == null) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_INVAL, null);
        }
        try {
            sessionOfTransaction.rollback();
        } catch (NoTransactionAssociatedException note) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_OUTSIDE, note);
        } catch (XASystemException xase) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_RMFAIL, xase);
        } finally {
            releaseFromInternalXidMap(xid);
        }
        xaSession.setTypeOfCurrentTransaction(NativeXASession.NO_TRANSACTION);
    }

    public void commit(Xid xid, boolean onePhase) throws XAException {
        TransactionInformation internalXid = mapToInternalXid(xid);
        Session sessionOfTransaction = xaFileSystem.getSessionForTransaction(internalXid);
        if (sessionOfTransaction == null) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_INVAL, null);
        }
        try {
            ((SessionCommonness)sessionOfTransaction).commit(onePhase);
        } catch (NoTransactionAssociatedException note) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_OUTSIDE, note);
        } catch (XASystemException xase) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_RMFAIL, xase);
        } finally {
            releaseFromInternalXidMap(xid);
        }
        xaSession.setTypeOfCurrentTransaction(NativeXASession.NO_TRANSACTION);
    }

    public Xid[] recover(int flag) throws XAException {
        return xaFileSystem.recover(flag);
    }

    public void forget(Xid xid) throws XAException {
        //Xid internalXid = mapToInternalXid(xid);
    }

    public boolean isSameRM(XAResource xar) throws XAException {
        if (xar instanceof XAResourceImpl) {
            XAResourceImpl that = (XAResourceImpl) xar;
            return this.xaFileSystem.pointToSameXAFileSystem(that.xaFileSystem);
        } else {
            return false;
        }
    }

    public int getTransactionTimeout() throws XAException {
        return transactionTimeout;
    }

    public boolean setTransactionTimeout(int transactionTimeout) throws XAException {
        if (transactionTimeout < 0) {
            throw MiscUtils.createXAExceptionWithCause(XAException.XAER_INVAL, null);
        }
        if (transactionTimeout == 0) {
            this.transactionTimeout = xaFileSystem.getDefaultTransactionTimeout();
        } else if (transactionTimeout > 0) {
        this.transactionTimeout = transactionTimeout;
        }
        return true;
    }

    private TransactionInformation mapToInternalXid(Xid xid) {
        TransactionInformation internalXid = internalXids.get(xid);
        if (internalXid == null) {
            internalXid = new TransactionInformation(xid);
            internalXids.put(xid, internalXid);
        }
        return internalXid;
    }

    private void releaseFromInternalXidMap(Xid xid) {
        internalXids.remove(xid);
    }
}
