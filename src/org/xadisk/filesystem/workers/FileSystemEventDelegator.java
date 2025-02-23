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

package org.xadisk.filesystem.workers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkManager;
import org.xadisk.bridge.proxies.impl.RemoteMessageEndpointFactory;
import org.xadisk.connector.inbound.EndPointActivation;
import org.xadisk.filesystem.FileSystemStateChangeEvent;
import org.xadisk.filesystem.NativeXAFileSystem;
import org.xadisk.filesystem.workers.observers.EventDispatchListener;

public class FileSystemEventDelegator implements Work {

    private final NativeXAFileSystem xaFileSystem;
    private final LinkedBlockingQueue<FileSystemStateChangeEvent> eventQueue;
    private final WorkManager workManager;
    private final CopyOnWriteArrayList<EndPointActivation> registeredActivations =
            new CopyOnWriteArrayList<EndPointActivation>();
    private final int maximumConcurrentEventDeliveries;
    private final EventDispatchListener eventDispatchListener;
    private volatile boolean released = false;

    public FileSystemEventDelegator(NativeXAFileSystem xaFileSystem, int maximumConcurrentEventDeliveries) {
        this.xaFileSystem = xaFileSystem;
        this.eventQueue = xaFileSystem.getFileSystemEventQueue();
        this.workManager = xaFileSystem.getWorkManager();
        this.maximumConcurrentEventDeliveries = maximumConcurrentEventDeliveries;
        this.eventDispatchListener = new EventDispatchListener();
    }

    public boolean registerActivation(EndPointActivation activation) {
        if (registeredActivations.contains(activation)) {
            return false;
        }
        registeredActivations.add(activation);
        return true;
    }

    public void deRegisterActivation(EndPointActivation activation) {
        MessageEndpointFactory mef = activation.getMessageEndpointFactory();
        if (mef instanceof RemoteMessageEndpointFactory) {
            int registeredAt = registeredActivations.indexOf(activation);
            if (registeredAt != -1) {
                RemoteMessageEndpointFactory remoteMEF = (RemoteMessageEndpointFactory) registeredActivations.get(registeredAt).getMessageEndpointFactory();
                remoteMEF.shutdown();
            }
        }
        registeredActivations.remove(activation);
    }

    public ArrayList<EndPointActivation> getAllActivations() {
        return new ArrayList<EndPointActivation>(registeredActivations);
    }

    public void run() {
        try {
            while (!released) {
                if (eventDispatchListener.getOngoingConcurrentDeliveries()
                        >= maximumConcurrentEventDeliveries) {
                    Thread.sleep(100);
                    continue;
                }
                FileSystemStateChangeEvent event = eventQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }
                Iterator<EndPointActivation> activations = registeredActivations.iterator();
                EndPointActivation interestedActivationPicked = null;
                while (activations.hasNext()) {
                    EndPointActivation current = activations.next();
                    if (current.getActivationSpecImpl().isEndpointInterestedIn(event)) {
                        interestedActivationPicked = current;
                        break;
                    }
                }
                try {
                    if (interestedActivationPicked != null) {
                        workManager.startWork(new FileSystemEventProcessor(interestedActivationPicked.getMessageEndpointFactory(),
                                event, xaFileSystem), WorkManager.INDEFINITE, null, eventDispatchListener);
                        //eventDispatchListener.workStarted(null);found glassfish sending
                        //an event on work start already.
                    }
                } catch (WorkException we) {
                    eventQueue.put(event);//but this will disrupt the order of events in the queue. Any other
                    //way? Rejection? Dead-letter file where we can put this info?
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        } catch (Throwable t) {
            xaFileSystem.notifySystemFailure(t);
        }
    }

    public void release() {
        released = true;
    }

    public ArrayList<FileSystemStateChangeEvent> retainOnlyInterestingEvents(ArrayList<FileSystemStateChangeEvent> fileStateChangeEventsToRaise) {
        ArrayList<FileSystemStateChangeEvent> eventsToRetain = new ArrayList<FileSystemStateChangeEvent>();
        for (FileSystemStateChangeEvent event : fileStateChangeEventsToRaise) {
            for (EndPointActivation activation : registeredActivations) {
                if (activation.getActivationSpecImpl().isEndpointInterestedIn(event)) {
                    eventsToRetain.add(event);
                    break;
                }
            }
        }
        return eventsToRetain;
    }
}
