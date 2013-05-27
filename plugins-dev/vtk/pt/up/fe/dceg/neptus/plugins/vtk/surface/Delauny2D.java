/*
 * Copyright (c) 2004-2013 Universidade do Porto - Faculdade de Engenharia
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
 * Author: hfq
 * May 23, 2013
 */
package pt.up.fe.dceg.neptus.plugins.vtk.surface;

import pt.up.fe.dceg.neptus.NeptusLog;
import pt.up.fe.dceg.neptus.plugins.vtk.pointcloud.PointCloud;
import pt.up.fe.dceg.neptus.plugins.vtk.pointtypes.PointXYZ;
import vtk.vtkCleanPolyData;
import vtk.vtkDelaunay2D;
import vtk.vtkLODActor;
import vtk.vtkPolyData;

/**
 * @author hfq
 *
 */
public class Delauny2D {
    private vtkPolyData polyData;
       
    //public PointCloud<PointXYZ> pointCloud;
    //private PointCloudMesh mesh;
    
    private vtkLODActor delaunyActor;
    private vtkCleanPolyData cleanPolyData;
    
//    public Delauny2D(PointCloud<PointXYZ> intputCloud, PointCloudMesh outputMesh) {
//        this.pointCloud = intputCloud;
//        this.mesh = outputMesh;
//        
//        setDelaunyActor(new vtkLODActor());
//    }
    
    public Delauny2D() {
        
    }
    
    public void performDelauny(PointCloud<PointXYZ> inputCloud) {
        
        NeptusLog.pub().info("Delauny Triangulation time start: " + System.currentTimeMillis());

        NeptusLog.pub().info("cleaning point cloud...");
        cleanPolyData = new vtkCleanPolyData();
        cleanPolyData.SetInputConnection(inputCloud.getPoly().GetProducerPort());
        cleanPolyData.Update();
        
        //mesh.setPolyData(cleanPolyData.GetOutput());
        
        NeptusLog.pub().info("Generate mesh...");
        vtkDelaunay2D delauny = new vtkDelaunay2D();
        delauny.SetInputConnection(cleanPolyData.GetOutputPort());
        //delauny.SetInputConnection(pointCloud.getPoly().GetProducerPort());
        //delauny.SetInput(pointCloud.getPoly());
        //delauny.BoundingTriangulationOn();
        delauny.Update();
        
        setPolyData(delauny.GetOutput());
        
//        mesh.setPolyData(delauny.GetOutput());
//        
//        vtkPolyDataMapper triangulateMapper = new vtkPolyDataMapper();
//        triangulateMapper.SetInputConnection(delauny.GetOutputPort());
//        
//        delaunyActor.SetMapper(triangulateMapper);
//        delaunyActor.Modified();
//        
//        mesh.setMeshCloudLODActor(getDelaunyActor());
        
        NeptusLog.pub().info("Delauny Triangulation time end: " + System.currentTimeMillis());
    }

    /**
     * @return the delaunyActor
     */
    public vtkLODActor getDelaunyActor() {
        return delaunyActor;
    }

    /**
     * @param delaunyActor the delaunyActor to set
     */
    private void setDelaunyActor(vtkLODActor delaunyActor) {
        this.delaunyActor = delaunyActor;
    }

    /**
     * @return the cleanPolyData
     */
    public vtkCleanPolyData getCleanPolyData() {
        return cleanPolyData;
    }

    /**
     * @param cleanPolyData the cleanPolyData to set
     */
    public void setCleanPolyData(vtkCleanPolyData cleanPolyData) {
        this.cleanPolyData = cleanPolyData;
    }

    /**
     * @return the polyData
     */
    public vtkPolyData getPolyData() {
        return polyData;
    }

    /**
     * @param polyData the polyData to set
     */
    public void setPolyData(vtkPolyData polyData) {
        this.polyData = polyData;
    }
}
