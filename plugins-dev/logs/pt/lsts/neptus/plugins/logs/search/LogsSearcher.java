package pt.lsts.neptus.plugins.logs.search;

import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.ftp.FtpDownloader;
import pt.lsts.neptus.mp.MapChangeEvent;
import pt.lsts.neptus.mra.NeptusMRA;
import pt.lsts.neptus.plugins.*;
import pt.lsts.neptus.types.map.ParallelepipedElement;
import pt.lsts.neptus.util.GuiUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;

import pt.lsts.neptus.plugins.Popup;

/**\
 * @author tsmarques
 * @date 3/14/17
 */
@PluginDescription(name = "Logs Searcher")
@Popup(width = 500, height = 600)
public class LogsSearcher extends ConsolePanel {
    private static final int WIDTH = 500;
    private static final int HEIGHT = 550;

    enum DataOptionEnum {
        ANY("any"),
        MULTIBEAM("multitbeam"),
        SIDESCAN("sidescan"),
        CAMERA("camera"),
        PH("ph"),
        SALINITY("salinity"),
        REDOX("redox");

        private String dataStr;
        DataOptionEnum(String dataStr) {
            this.dataStr = dataStr;
        }

        @Override
        public String toString() {
            return this.dataStr;
        }
    }

    private final String FTP_HOST = "10.0.2.70";
    private final int FTP_PORT = 2121;
    private final String FTP_BASE_DIR = "/home/tsm/ws/lsts/";
    private static final File LOGS_DOWNLOAD_DIR = new File(System.getProperty("user.dir") + "/.cache/logs-searcher/");
    private final LogsDbHandler db = new LogsDbHandler();

    private final JPanel mainPanel = new JPanel();
    private final MigLayout mainLayout = new MigLayout("ins 0, gap 0", "[][grow]", "[top][grow]");
    private final JPanel queryPanel = new JPanel();
    private final JPanel resultsPanel = new JPanel();

    private final JComboBox<String> dataOptions = new JComboBox<>();
    private final JComboBox<String> yearOptions = new JComboBox<>();
    private final JComboBox<String> vehicleOptions = new JComboBox<>();
    private final JComboBox<String>  areaOptions = new JComboBox<>();

    private final HashSet<String> knownMapAreas = new HashSet<>();

    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private final JScrollPane scrollPane = new JScrollPane();

    private final JButton searchButton = new JButton("Search");
    private final JButton clearResultsButton = new JButton("Clear results");
    private final JButton selectAreaButton = new JButton("Select Area");

    private FtpDownloader ftp;
    private FTPClient ftpClient;
    private boolean dbConnectionStatus = false;

    /**
     * Open LogsDataSearcher from MRA
     * */
    public LogsSearcher(ConsoleLayout console) {
        super(console);
        buildGui();

        if(!LOGS_DOWNLOAD_DIR.exists()) {
            NeptusLog.pub().info("Creating logs cache directory at " + LOGS_DOWNLOAD_DIR.getAbsolutePath());
            LOGS_DOWNLOAD_DIR.mkdirs();
        }
    }

    @Override
    public void cleanSubPanel() {

    }

    @Override
    public void initSubPanel() {
        handleConnections();
    }

    private void handleConnections() {
        new Thread(() -> {
            connectFtp();
            dbConnectionStatus = db.connect();
        }).start();
    }

    public void connectFtp() {
        try {
            ftp = new FtpDownloader(FTP_HOST, FTP_PORT);
            ftp.renewClient();
            ftpClient = ftp.getClient();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void buildGui() {
        initMainPanel();
        initQueryPanel();
        initResultsPanel();

        mainPanel.add(queryPanel, "alignx center, w 47px,h 55px, spanx, wrap");
        mainPanel.add(resultsPanel, "w 100%, h 100%");

        this.add(mainPanel);
    }

    private void initMainPanel() {
        this.setSize(new Dimension(WIDTH, HEIGHT));
        mainPanel.setSize(new Dimension(this.getWidth(), this.getHeight()));
        mainPanel.setLayout(mainLayout);
    }

    private void initResultsPanel() {
        tableModel = new DefaultTableModel(new Object[]{"Data Type", "Year", "Vehicle Id", "Log Path"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultsTable = new JTable() {
            @Override
            public String getToolTipText(MouseEvent e) {
                String tip;
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);

                try {
                    tip = getValueAt(rowIndex, colIndex).toString();
                } catch (RuntimeException e1) {
                    return "";
                }

                return tip;
            }
        };

        resultsTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                JTable table =(JTable) me.getSource();
                Point p = me.getPoint();
                int rowIndex = table.rowAtPoint(p);
                if (me.getClickCount() == 2) {
                    if (rowIndex == -1) {
                        GuiUtils.errorMessage(mainPanel, "Log Selection Error", "No log selected");
                        return;
                    }

                    // get log path
                    String logStr = (String) tableModel.getValueAt(rowIndex, 3);
                    if (logStr == null) {
                        GuiUtils.errorMessage(mainPanel, "Log Selection Error", "Error while accessing column" + rowIndex);
                        return;
                    }

                    openLog(logStr);
                }
            }
        });

        resultsTable.setModel(tableModel);

        scrollPane.setViewportView(resultsTable);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);


        resultsPanel.add(scrollPane);
    }

    private void initQueryPanel() {
        queryPanel.setLayout(new GridLayout(2,3));
        queryPanel.add(dataOptions);
        queryPanel.add(vehicleOptions);
        queryPanel.add(yearOptions);
        queryPanel.add(areaOptions);

        queryPanel.add(searchButton);
        queryPanel.add(clearResultsButton);

        initQueryOptions();

        searchButton.addActionListener(e -> {
            String selectedDataTypeStr = String.valueOf(dataOptions.getSelectedItem());
            String selectedYearStr = String.valueOf(yearOptions.getSelectedItem());
            String selectedVehicleStr = String.valueOf(vehicleOptions.getSelectedItem());

            if(selectedDataTypeStr == null || selectedYearStr == null || selectedVehicleStr == null) {
                GuiUtils.errorMessage(mainPanel, "Log Selection Error", "Null option");
                return;
            }

            // perform query adn update resultsTable
            query(selectedDataTypeStr, selectedYearStr, selectedVehicleStr);
            //query(null, null, null);
        });

        clearResultsButton.addActionListener(e -> {
            // clear results table
            for(int i = 0; i < tableModel.getRowCount(); i++)
                tableModel.removeRow(i);
        });

/*        selectAreaButton.addActionListener(e -> {

        });*/
    }

    private void initQueryOptions() {
        DefaultListCellRenderer dlcr = new DefaultListCellRenderer();
        dlcr.setHorizontalAlignment(DefaultListCellRenderer.CENTER);

        // data type options
        dataOptions.setRenderer(dlcr);
        Arrays.stream(DataOptionEnum.values())
                .forEach(opt -> ((DefaultComboBoxModel) dataOptions.getModel()).addElement(opt));

        // available years
        dlcr = new DefaultListCellRenderer();
        dlcr.setHorizontalAlignment(DefaultListCellRenderer.CENTER);
        yearOptions.setRenderer(dlcr);
        ((DefaultComboBoxModel) yearOptions.getModel()).addElement("--any--");
        ((DefaultComboBoxModel) yearOptions.getModel()).addElement("2016");
        ((DefaultComboBoxModel) yearOptions.getModel()).addElement("2017");

        // vehicles' ids
        dlcr = new DefaultListCellRenderer();
        dlcr.setHorizontalAlignment(DefaultListCellRenderer.CENTER);
        vehicleOptions.setRenderer(dlcr);
        ((DefaultComboBoxModel) vehicleOptions.getModel()).addElement("--any--");
        ((DefaultComboBoxModel) vehicleOptions.getModel()).addElement("lauv-xplore-1");
        ((DefaultComboBoxModel) vehicleOptions.getModel()).addElement("lauv-noptilus-1");
        ((DefaultComboBoxModel) vehicleOptions.getModel()).addElement("lauv-noptilus-2");
        ((DefaultComboBoxModel) vehicleOptions.getModel()).addElement("lauv-noptilus-3");
        ((DefaultComboBoxModel) vehicleOptions.getModel()).addElement("lauv-xtreme-2");
        ((DefaultComboBoxModel) vehicleOptions.getModel()).addElement("lauv-arpao");

        dlcr = new DefaultListCellRenderer();
        dlcr.setHorizontalAlignment(DefaultListCellRenderer.CENTER);
        areaOptions.setRenderer(dlcr);
        ((DefaultComboBoxModel) areaOptions.getModel()).addElement("--none--");
    }

    @Subscribe
    public void mapChanged(MapChangeEvent event) {
        if(event == null || event.getChangedObject() == null)
            return;

        // only care about areas
        if(!(event.getChangedObject() instanceof ParallelepipedElement))
            return;

        if(event.getEventType() == MapChangeEvent.OBJECT_ADDED) {
            String objectId = event.getChangedObject().getId();
            if(knownMapAreas.contains(objectId))
                return;

            knownMapAreas.add(objectId);
            ((DefaultComboBoxModel) areaOptions.getModel()).addElement(objectId);
        }
    }

    private void query(String payload, String year, String vehicleId) {
        // query db for data with the given characteristics
        if(ftp == null)
            connectFtp();

        if(dbConnectionStatus == false)
            db.connect();

        String query = buildQuery(payload, year, vehicleId);
        updateEntries(db.doQuery(query));
    }

    /**
     * Build query string based on user's selected options
     * */
    public String buildQuery(String payload, String year, String vehicleId) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM " + LogsDbHandler.DbTableName.LOGS.toString() + " WHERE ");

        boolean searchByPayload = false;
        boolean searchByYear = false;

        if(!payload.equals(LogsSearcher.DataOptionEnum.ANY.toString())) {
            sb.append(LogsDbHandler.LogTableColumnName.DATA_TYPE.toString() + "=" + "\"" + payload + "\"");
            searchByPayload = true;
        }

        if(!year.equals(LogsSearcher.DataOptionEnum.ANY.toString())) {
            if(searchByPayload)
                sb.append(" AND ");
            sb.append(LogsDbHandler.LogTableColumnName.DATE.toString() + "=" + "\"" + year + "\"");
            searchByYear = true;
        }

        if(!vehicleId.equals(LogsSearcher.DataOptionEnum.ANY.toString())) {
            if(searchByPayload || searchByYear)
                sb.append(" AND ");
            sb.append(LogsDbHandler.LogTableColumnName.VEHICLE_ID.toString() + "=" + "\"" + vehicleId + "\"");
        }

        return sb.toString();
    }

    private void updateEntries(ResultSet res) {
        try {
            boolean isEmpty = true;
            tableModel.setRowCount(0);
            while(res.next()) {
                isEmpty = false;
                String path = res.getString(LogsDbHandler.LogTableColumnName.PATH.toString());
                String year = res.getString(LogsDbHandler.LogTableColumnName.DATE.toString());
                String vehicle = res.getString(LogsDbHandler.LogTableColumnName.VEHICLE_ID.toString());
                String dataType = res.getString(LogsDbHandler.LogTableColumnName.DATA_TYPE.toString());

                tableModel.addRow(new Object[]{dataType, year, vehicle, path});
            }

            if(isEmpty)
                GuiUtils.infoMessage(mainPanel, "Query Results", "No results found");
        } catch (SQLException e) {
            GuiUtils.errorMessage(mainPanel, "Error", "Error while parsing results");
            e.printStackTrace();
        }
    }

    /**
     * Open a selected log in MRA
     * */
    private void openLog(String logAbsolutePathStr) {
        // Fetch file from remote (FTP?)
        File logFile = null;
        try {
            logFile = fetchLog(logAbsolutePathStr);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(logFile == null) {
            GuiUtils.errorMessage(mainPanel, "Log Selection Error", "Couldn't download file");
            return;
        }

        NeptusMRA.showApplication().getMraFilesHandler().openLog(logFile);
    }

    /**
     * Fetch remote logs
     * */
    private File fetchLog(String logRemoteAbsolutePath) throws IOException {
        File logParentRemoteDir = new File(new File(logRemoteAbsolutePath).getParent());
        File logParentLocalDir = new File(LOGS_DOWNLOAD_DIR + "/" + logParentRemoteDir.getAbsolutePath().split(FTP_BASE_DIR)[1]);
        File localLogAbsolutePath = new File(logParentLocalDir.getAbsolutePath() + "/Data.lsf.gz");

        // log already exists, no need to re-download
        // not using checksum
        if(localLogAbsolutePath.exists()) {
            NeptusLog.pub().info("Log " + logParentLocalDir.getAbsolutePath() + " already exists");
            return localLogAbsolutePath;
        }

        logParentLocalDir.mkdirs();

        String ftpRootDir = ftpClient.printWorkingDirectory();
        String baseDir = logParentRemoteDir.getAbsolutePath().split(FTP_BASE_DIR)[1];
        ftpClient.changeWorkingDirectory(baseDir);
        StringBuilder sb = null;

        for(FTPFile ftpFile : ftpClient.listFiles()) {
            if(ftpFile.getName().equals("mra"))
                continue;
            File downloadedFile = new File(LOGS_DOWNLOAD_DIR + "/" + baseDir + "/" + ftpFile.getName());
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(downloadedFile));
            boolean success = ftpClient.retrieveFile(ftpFile.getName(), outputStream);
            outputStream.close();

            if(!success) {
                if(sb == null)
                    sb = new StringBuilder();
                sb.append(ftpFile.getName() + "\n");
            }
        }
        // reset ftp root dir
        ftpClient.changeWorkingDirectory(ftpRootDir);

        if(sb != null)
            GuiUtils.errorMessage(mainPanel, "Download Error", "The following files couldn't failed to download: \n"
                    + sb.toString());

        return localLogAbsolutePath;
    }
}
