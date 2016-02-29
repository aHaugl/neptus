/*
 * Copyright (c) 2004-2015 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: tsmarques
 * 15 Dec 2015
 */

package pt.lsts.neptus.plugins.mvplanning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.eventbus.Subscribe;

import pt.lsts.imc.PlanDB;
import pt.lsts.imc.PlanSpecification;
import pt.lsts.neptus.comm.IMCSendMessageUtils;
import pt.lsts.neptus.events.NeptusEvents;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.plugins.mvplanning.events.MvPlanningEventAvailableVehicle;
import pt.lsts.neptus.plugins.mvplanning.utils.VehicleAwareness;
import pt.lsts.neptus.plugins.mvplanning.utils.jaxb.Profile;

/* Sends plans to the vehicles.
 Also send 'execution commands'*/
public class PlanAllocator {
    /* Used only to query if a vehicle is available */
    private VehicleAwareness vawareness;

    /** Lists used to maintain a round-robin allocation, per Profile
     * Map<ProfileId, Vehicles List> */
    private Map<String, List<String>> allocationLists;

    /* Plans waiting for an available vehicle to be allocated to */
    private List<PlanTask> queuedPlans;

    /* If there are plans waiting to be allocated */
    private boolean existsQueuedPlans;

    public PlanAllocator(VehicleAwareness vawareness) {
        this.vawareness = vawareness;
        allocationLists = new HashMap<>();
        queuedPlans = new ArrayList<>();
        existsQueuedPlans = false;
        
        NeptusEvents.register(this);
    }

    private boolean unseenProfile(String profileId) {
        return !allocationLists.containsKey(profileId);
    }

    private void fetchProfileVehicles(Profile newProfile) {
        allocationLists.put(newProfile.getId(), newProfile.getProfileVehicles());
    }

    public void allocate(PlanTask pTask) {
        /* allocate plans to vehicle */
        String profileId = pTask.getProfile().getId();

        /* if it's a new profile */
        if(unseenProfile(profileId))
            fetchProfileVehicles(pTask.getProfile());

        int i = 0;
        List<String> profileVehicles = allocationLists.get(profileId);
        boolean allocated = false;

        /* iterate over profile's vehicles and find the first one available */
        while(!allocated || (i < profileVehicles.size())) {
            /* fetch, supposedly, available vehicle of the profile */
            String vehicle = profileVehicles.get(i);

            if(vawareness.isVehicleAvailable(vehicle)) {
                /* allocate plan and then push the vehicle to the back of queue */
                allocated = allocateTo(vehicle, pTask);
                if(allocated) {
                    profileVehicles.remove(i);
                    profileVehicles.add(vehicle);
                    allocationLists.put(profileId, profileVehicles);
                    System.out.println("[mvplanning/PlanAlocater]: Plan " + pTask.getPlanId() + " successfully allocated to " + vehicle);
                }
            }
            i++;
        }

        /* No vehicle is currently available, queue plan and allocate as soon as there's
         * a vehicle available */
        if(!allocated)
            queuePlan(pTask);
    }

    private boolean allocateTo(String vehicle, PlanTask pTask) {
        try {
            int reqId = IMCSendMessageUtils.getNextRequestId();

            PlanDB pdb = new PlanDB();
            PlanSpecification plan = pTask.getPlanSpecification();

            pdb.setType(PlanDB.TYPE.REQUEST);
            pdb.setOp(PlanDB.OP.SET);
            pdb.setRequestId(reqId);
            pdb.setPlanId(pTask.getPlanId());
            pdb.setArg(plan);
            pdb.setInfo("Plan allocated by [mvplanning/PlanAllocator]");

            boolean planSent = IMCSendMessageUtils.sendMessage(pdb, I18n.text("Error sending plan"), vehicle);

            return planSent;
        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("[mvplanning/PlanAllocator]: Failed to allocate plan " + pTask.getPlanId() + " to " + vehicle);
            return false;
        }
  }

    private void queuePlan(PlanTask plan) {
        synchronized(queuedPlans) {
            queuedPlans.add(plan);
            this.existsQueuedPlans = true;
        }
    }

    private boolean existsQueuedPlans() {
        synchronized (queuedPlans) {
            return this.existsQueuedPlans;
        }
    }


    @Subscribe
    public void waitForAvailableVehicle(MvPlanningEventAvailableVehicle event) {
        if(existsQueuedPlans()) {
            /* if there are plans to be allocated, check if this vehicle can execute any of them */
        }
    }
}