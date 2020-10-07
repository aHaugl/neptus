/*
 * Copyright (c) 2004-2020 Universidade do Porto - Faculdade de Engenharia
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
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Andreas Haugland
 * Oct 7, 2020
 */

package pt.lsts.neptus.plugins.ho4;

import java.awt.Color;
import java.awt.Graphics2D;
import java.text.DecimalFormat;
import java.util.ArrayList;

import com.google.common.eventbus.Subscribe;

import pt.lsts.imc.IMCAddressResolver;
import pt.lsts.imc.PathControlState;
import pt.lsts.imc.PlanDB;
import pt.lsts.imc.PlanDBState;

import pt.lsts.imc.Rpm;
import pt.lsts.imc.Temperature;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayer;
import pt.lsts.neptus.console.events.ConsoleEventMainSystemChange;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.renderer2d.StateRenderer2D;
import pt.lsts.neptus.types.vehicle.VehicleType;
import pt.lsts.neptus.types.vehicle.VehiclesHolder;

@PluginDescription(name = "MyPlugin", version = "0.1", description = "First plugin")
public class MyFirstPlugin extends ConsoleLayer {

    private short rpmValue = -1;
    private double temperatureValue = Double.MAX_VALUE;
    private VehicleType vehicle;
    private IMCAddressResolver res = new IMCAddressResolver();
    private int mainVehicleId;
    private double endLatitude = Double.MAX_VALUE;
    private double endLongitude = Double.MAX_VALUE;
    private ArrayList<String> planIds = new ArrayList<String>();

    public MyFirstPlugin() {
        NeptusLog.pub().info("My First Plugin is Loaded!");

    }

    @Override
    public void paint(Graphics2D g, StateRenderer2D renderer) {
        super.paint(g, renderer);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.BLACK);
        StringBuilder str0 = new StringBuilder();
        str0.append("Selected vehicle: ");
        str0.append(getConsole().getMainSystem());
        g2.drawString(str0.toString(), 15, 15);
        
        //String for RPM
        StringBuilder str1 = new StringBuilder();
        str1.append("RPM: ");
        if (rpmValue != -1) {
            str1.append(rpmValue);
        }
        else {
            str1.append("N/A");
        }
        g2.drawString(str1.toString(), 15, 30);

        DecimalFormat df2 = new DecimalFormat("*.**");
        
        //String for temperaturevalue
        StringBuilder str2 = new StringBuilder();
        str2.append("Temp: ");
        if (temperatureValue != Double.MAX_VALUE) {
            str2.append(df2.format(temperatureValue));
        }
        else {
            str2.append("N/A");
        }

        g2.drawString(str2.toString(), 15, 45);

        //String for final Latitude    
        StringBuilder str3 = new StringBuilder();
        str3.append("endLatitude: ");
        if (endLatitude != Double.MAX_VALUE) {
            str3.append(endLatitude);

        }
        else {
            str3.append("N/A");
        }
        g2.drawString(str3.toString(), 15, 60);
        
        //String for final longitude
        StringBuilder str4 = new StringBuilder();
        str4.append("endLongitude: ");
        if (endLongitude != Double.MAX_VALUE) {
            str4.append(endLongitude);

        }
        else {
            str4.append("N/A");
        }
        g2.drawString(str4.toString(), 15, 75);
        
        //String for plan-ID:
        StringBuilder str5 = new StringBuilder();
        str5.append("Plan IDs: ");
        if (!planIds.isEmpty()) {
            for (String planId : planIds) {
                str5.append(planId);
                str5.append(", ");
            }
        }
        else {
            str5.append("N/A");
        }
        g2.drawString(str5.toString(), 15, 90);
    }

    @Subscribe
    public void mainVehicleChangeIdentification(ConsoleEventMainSystemChange evnt) {
        vehicle = VehiclesHolder.getVehicleById(evnt.getCurrent());
        System.out.println("Main vehicle changed to " + vehicle);
        mainVehicleId = res.resolve(vehicle.getId());
        System.out.println("Main vehicle id is :" + mainVehicleId);

    }

    @Subscribe
    public void consume(Rpm rpm) {
        if (rpm.getSrc() == mainVehicleId) {
            rpmValue = rpm.getValue();
        }

    }

    @Subscribe
    public void consume(Temperature temp) {
        if (temp.getSrc() == mainVehicleId) {
            temperatureValue = temp.getValue();
        }

    }

    @Subscribe
    public void consume(PathControlState pathControlState) {
        if (pathControlState.getSrc() == mainVehicleId) {
            endLatitude = Math.toDegrees(pathControlState.getEndLat());
            endLongitude = Math.toDegrees(pathControlState.getEndLon());
        }
    }

    @Subscribe
    public void consume(PlanDB planDB) {
        if (planDB.getSrc() == mainVehicleId) {
            ((PlanDBState) planDB.getArg()).getPlansInfo().stream().forEach(x -> planIds.add(x.getPlanId()));
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.console.ConsoleLayer#userControlsOpacity()
     */
    @Override
    public boolean userControlsOpacity() {
        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.console.ConsoleLayer#initLayer()
     */
    @Override
    public void initLayer() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.console.ConsoleLayer#cleanLayer()
     */
    @Override
    public void cleanLayer() {
        // TODO Auto-generated method stub

    }
}