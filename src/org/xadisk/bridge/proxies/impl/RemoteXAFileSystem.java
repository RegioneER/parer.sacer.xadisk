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

package org.xadisk.bridge.proxies.impl;

import org.xadisk.bridge.proxies.facilitators.RemoteMethodInvoker;
import org.xadisk.bridge.proxies.facilitators.RemoteObjectProxy;
import java.io.IOException;
import java.io.Serializable;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.xadisk.bridge.proxies.facilitators.RemoteXADiskActivationSpecImpl;
import org.xadisk.bridge.proxies.interfaces.XAFileSystem;
import org.xadisk.bridge.proxies.interfaces.XASession;
import org.xadisk.connector.inbound.EndPointActivation;
import org.xadisk.bridge.proxies.interfaces.Session;
import org.xadisk.bridge.server.conversation.HostedContext;
import org.xadisk.connector.XAResourceImpl;
import org.xadisk.filesystem.NativeXAFileSystem;
import org.xadisk.filesystem.XAFileSystemCommonness;

public class RemoteXAFileSystem extends RemoteObjectProxy implements XAFileSystemCommonness {

    private static final long serialVersionUID = 1L;
    
    private NativeXAFileSystem localXAFileSystem;
    
    public RemoteXAFileSystem(String serverAddress, int serverPort, NativeXAFileSystem localXAFileSystem) {
        super(0, new RemoteMethodInvoker(serverAddress, serverPort));
        this.localXAFileSystem = localXAFileSystem;
    }

    public RemoteXAFileSystem(String serverAddress, int serverPort) {
        super(0, new RemoteMethodInvoker(serverAddress, serverPort));
    }

    public RemoteXAFileSystem(long objectId, RemoteMethodInvoker invoker, NativeXAFileSystem localXAFileSystem) {
        super(objectId, invoker);
        this.localXAFileSystem = localXAFileSystem;
    }

    public boolean pointToSameXAFileSystem(XAFileSystem xaFileSystem) {
        if(xaFileSystem instanceof RemoteXAFileSystem) {
            RemoteXAFileSystem that = (RemoteXAFileSystem) xaFileSystem;
            return this.invoker.getServerAddress().equalsIgnoreCase(that.invoker.getServerAddress())
                    && this.invoker.getServerPort() == that.invoker.getServerPort();
        } else {
            return false;
        }
    }
    
    public Session createSessionForLocalTransaction() {
        try {
            return (Session) invokeRemoteMethod("createSessionForLocalTransaction");
        } catch (Throwable t) {
            throw assertExceptionHandling(t);
        }
    }

    public Session createSessionForXATransaction(Xid xid) {
        try {
            return (Session) invokeRemoteMethod("createSessionForXATransaction", (Serializable) xid);
        } catch (Throwable t) {
            throw assertExceptionHandling(t);
        }
    }

    public XASession createSessionForXATransaction() {
        //what about "checkIfContinue" check here, as in native xafs case?
        return new RemoteXASession(this);
    }

    public XAResource getXAResourceForRecovery() {
        return new XAResourceImpl(new RemoteXASession(this));
    }

    public int getDefaultTransactionTimeout() {
        try {
            return (Integer) invokeRemoteMethod("getDefaultTransactionTimeout");
        } catch (Throwable t) {
            throw assertExceptionHandling(t);
        }
    }

    public Session getSessionForTransaction(Xid xid) {
        try {
            return (Session) invokeRemoteMethod("getSessionForTransaction", (Serializable) xid);
        } catch (Throwable t) {
            throw assertExceptionHandling(t);
        }
    }

    public void notifySystemFailureAndContinue(Throwable t) {
        try {
            invokeRemoteMethod("notifySystemFailureAndContinue", t);
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }

    public Xid[] recover(int flag) throws XAException {
        try {
            return (Xid[]) invokeRemoteMethod("recover", flag);
        } catch (XAException xae) {
            throw xae;
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }

    public byte[][] getIdentifiersForFailedTransactions() {
        try {
            return (byte[][]) invokeRemoteMethod("getIdentifiersForFailedTransactions");
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }

    public void declareTransactionAsComplete(byte[] transactionIdentifier) {
        try {
            invokeRemoteMethod("declareTransactionAsComplete", transactionIdentifier);
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }

    public void shutdown() {
        disconnect();
    }

    public void waitForBootup(long timeout) throws InterruptedException {
        try {
            invokeRemoteMethod("waitForBootup", timeout);
        } catch (InterruptedException ie) {
            throw ie;
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }

    public void registerEndPointActivation(EndPointActivation epActivation) throws IOException {
        try {
            MessageEndpointFactory messageEndpointFactory = epActivation.getMessageEndpointFactory();
            HostedContext globalCallbackContext = localXAFileSystem.getGlobalCallbackContext();
            long objectId = globalCallbackContext.hostObject(messageEndpointFactory);
            String xaDiskSystemId = localXAFileSystem.getXADiskSystemId();
            RemoteMessageEndpointFactory remoteMessageEndpointFactory = new RemoteMessageEndpointFactory(objectId, xaDiskSystemId,
                    localXAFileSystem.createRemoteMethodInvokerToSelf());

            RemoteXADiskActivationSpecImpl ras = new RemoteXADiskActivationSpecImpl(epActivation.getActivationSpecImpl());
            EndPointActivation callbackEndPointActivation = new EndPointActivation(remoteMessageEndpointFactory,
                    ras);
            invokeRemoteMethod("registerEndPointActivation", callbackEndPointActivation);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }

    public void deRegisterEndPointActivation(EndPointActivation epActivation) throws IOException {
        try {
            MessageEndpointFactory messageEndpointFactory = epActivation.getMessageEndpointFactory();
            HostedContext globalCallbackContext = localXAFileSystem.getGlobalCallbackContext();
            long objectId = globalCallbackContext.deHostObject(messageEndpointFactory);
            String xaDiskSystemId = localXAFileSystem.getXADiskSystemId();
            RemoteMessageEndpointFactory remoteMessageEndpointFactory =
                    new RemoteMessageEndpointFactory(objectId, xaDiskSystemId, null);

            RemoteXADiskActivationSpecImpl ras = new RemoteXADiskActivationSpecImpl(epActivation.getActivationSpecImpl());
            EndPointActivation callbackEndPointActivation = new EndPointActivation(remoteMessageEndpointFactory, ras);
            invokeRemoteMethod("deRegisterEndPointActivation", callbackEndPointActivation);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }

    public XAResource getEventProcessingXAResourceForRecovery() {
        try {
            return (XAResource) invokeRemoteMethod("getEventProcessingXAResourceForRecovery");
        } catch (Throwable th) {
            throw assertExceptionHandling(th);
        }
    }
}
