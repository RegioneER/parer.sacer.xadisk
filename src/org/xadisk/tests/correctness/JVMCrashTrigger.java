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

package org.xadisk.tests.correctness;

import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import com.sun.jdi.request.ThreadStartRequest;
import java.util.ArrayList;
import java.util.HashMap;

public class JVMCrashTrigger implements Runnable {

    private VirtualMachine vm;
    private EventQueue queue;
    private EventRequestManager erManager;
    private ThreadReference mainThread = null;
    private String transactionDemarcatingThread;
    private int crashAtBreakpointNumber;
    private ArrayList<Method> interestingMethods = new ArrayList<Method>();
    private HashMap<String, ArrayList<String>> interestingClassesAndMethodNames =
            new HashMap<String, ArrayList<String>>();

    public JVMCrashTrigger(VirtualMachine vm, String transactionDemarcatingThread,
            int crashAtBreakpointNumber) {
        this.vm = vm;
        this.queue = vm.eventQueue();
        this.erManager = vm.eventRequestManager();
        this.transactionDemarcatingThread = transactionDemarcatingThread;
        this.crashAtBreakpointNumber = crashAtBreakpointNumber;
        String class1 = org.xadisk.filesystem.workers.GatheringDiskWriter.class.getName();
        ArrayList<String> methods1 = new ArrayList<String>();
        methods1.add("forceLog");
        methods1.add("forceUndoLogAndData");
        methods1.add("forceWrite");
        interestingClassesAndMethodNames.put(class1, methods1);

        String class2 = org.xadisk.filesystem.NativeSession.class.getName();
        ArrayList<String> methods2 = new ArrayList<String>();
        java.lang.reflect.Method allMethods2[] = org.xadisk.filesystem.NativeSession.class.getDeclaredMethods();
        for (int i = 0; i < allMethods2.length; i++) {
            String methodName = allMethods2[i].getName();
            if (methodName.startsWith("commit")) {
                methods2.add(methodName);
            }
        }
        methods2.add("commit");
        methods2.add("rollback");
        interestingClassesAndMethodNames.put(class2, methods2);
    }

    public void run() {
        int breakPointsEncountered = 0;
        thisRunnable:
        while (true) {
            try {
                EventSet eventSet = queue.remove();
                EventIterator eventIter = eventSet.eventIterator();
                while (eventIter.hasNext()) {
                    Event event = eventIter.next();
                    if (event instanceof BreakpointEvent) {
                        breakPointsEncountered++;
                        BreakpointEvent bpEvent = (BreakpointEvent) event;
                        //refreshStepRequest();
                        System.out.println(bpEvent);
                        if (breakPointsEncountered == crashAtBreakpointNumber) {
                            vm.exit(101);
                        }
                    } else if (event instanceof ClassPrepareEvent) {
                        ClassPrepareEvent cpEvent = (ClassPrepareEvent) event;
                        ReferenceType classRef = cpEvent.referenceType();
                        ArrayList<String> interestingMethodsInClass = interestingClassesAndMethodNames.get(classRef.name());
                        if (interestingMethodsInClass != null) {
                            for (String methodName : interestingMethodsInClass) {
                                interestingMethods.add(classRef.methodsByName(methodName).get(0));
                                erManager.deleteEventRequest(cpEvent.request());
                            }
                        }
                    } else if (event instanceof StepEvent) {
                        StepEvent stepEvent = (StepEvent) event;
                        //refreshStepRequest();
                        System.out.println(stepEvent);
                    } else if (event instanceof ThreadStartEvent) {
                        ThreadStartEvent tsEvent = (ThreadStartEvent) event;
                        if (tsEvent.thread().name().equals(transactionDemarcatingThread)) {
                            mainThread = tsEvent.thread();
                            for (Method method : interestingMethods) {
                                BreakpointRequest bpRequest = erManager.createBreakpointRequest(method.location());
                                bpRequest.addThreadFilter(mainThread);
                                bpRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                                bpRequest.enable();
                            }
                            erManager.deleteEventRequest(tsEvent.request());
                        }
                    } else if (event instanceof VMStartEvent) {
                        for (String interestingClass : interestingClassesAndMethodNames.keySet()) {
                            ClassPrepareRequest cpRequest = erManager.createClassPrepareRequest();
                            cpRequest.addClassFilter(interestingClass);
                            cpRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                            cpRequest.enable();
                        }
                        ThreadStartRequest tsRequest = erManager.createThreadStartRequest();
                        tsRequest.enable();
                        System.out.println("VM started.");
                    } else if (event instanceof VMDeathEvent) {
                        System.out.println("VM terminated.");
                        break thisRunnable;
                    } else if (event instanceof VMDisconnectEvent) {
                        System.out.println("VM got disconnected.");
                        break thisRunnable;
                    }
                }
                eventSet.resume();
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

    /*private void refreshStepRequest() {
        if (erManager.stepRequests().size() > 0) {
            erManager.deleteEventRequest(erManager.stepRequests().get(0));
        }
        StepRequest stepRequest = erManager.createStepRequest(
                mainThread, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
        stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        //stepRequest.addClassFilter(null);//what to do here for more than one interesting classes??
        //why no threadFilter can be added to StepRequest??
        stepRequest.enable();
    }*/
}
