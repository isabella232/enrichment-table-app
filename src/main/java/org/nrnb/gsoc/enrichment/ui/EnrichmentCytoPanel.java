package org.nrnb.gsoc.enrichment.ui;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelComponent2;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.*;
import org.cytoscape.model.events.*;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.util.swing.CyColorPaletteChooserFactory;
import org.cytoscape.util.swing.IconManager;
import org.cytoscape.util.swing.TextIcon;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.model.events.NetworkAboutToBeDestroyedEvent;
import org.cytoscape.model.events.NetworkAboutToBeDestroyedListener;
import org.cytoscape.application.events.SetCurrentNetworkEvent;
import org.cytoscape.application.events.SetCurrentNetworkListener;
import org.cytoscape.session.events.SessionLoadedEvent;
import org.cytoscape.session.events.SessionLoadedListener;
import org.json.simple.JSONObject;
import org.nrnb.gsoc.enrichment.model.EnrichmentTerm;
import org.nrnb.gsoc.enrichment.model.EnrichmentTerm.TermSource;
import org.nrnb.gsoc.enrichment.tasks.EnrichmentAdvancedOptionsTask;
import org.nrnb.gsoc.enrichment.tasks.EnrichmentTask;
import org.nrnb.gsoc.enrichment.tasks.FilterEnrichmentTableTask;
import org.nrnb.gsoc.enrichment.tasks.ExportEnrichmentTableTask;
import org.nrnb.gsoc.enrichment.utils.ModelUtils;
import org.cytoscape.application.swing.CytoPanelState;
import org.cytoscape.property.CyProperty;
import org.cytoscape.property.CyProperty.SavePolicy;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;


/**
 * @author ighosh98
 * @description Result Panel which stores the result of the gProfiler querying task and provides other tools to modify the querying tasks
 */
public class EnrichmentCytoPanel extends JPanel
        implements CytoPanelComponent2, ActionListener, RowsSetListener, TableModelListener, SelectedNodesAndEdgesListener, NetworkAboutToBeDestroyedListener, SessionLoadedListener, SetCurrentNetworkListener {

    private CyTable enrichmentTable;
    EnrichmentTableModel tableModel;
    Map<String, JTable> enrichmentTables;
    JPanel topPanel;
    JPanel mainPanel;
    JScrollPane scrollPane;
    List<String> availableTables;
    final CyColorPaletteChooserFactory colorChooserFactory;
    public final static String showTable = TermSource.ALL.getTable();
    JLabel labelRows;
    JButton butAdvancedOptions;
    JButton butExportTable;
    JButton butRunProfiler;
    JButton butFilter;
    TableColumnModel columnModel;
    CyTable filteredEnrichmentTable = null;
    boolean clearSelection = false;
    private String[] columnToolTips = {
    "the full name of the datasource for the term",
    "term ID in its native namespace. For non-GO terms, the ID is prefixed with the datasource abbreviation",
    "term name",
    "term description if available. If not available, repeats the term name",
    "hypergeometric p-value after correction for multiple testing",
    "the number of genes that were included in the query",
    "the total number of genes 'in the universe' which is used as one of the four parameters for the hypergeometric probability function of statistical significance",
    "the number of genes that are annotated to the term",
    "the number of genes in the query that are annotated to the corresponding term",
    "the proportion of genes in the input list that are annotated to the function, defined as intersection_size/query_size",
    "the proportion of functionally annotated genes that the query recovers, defined as intersection_size/term_size",
    "llist of query genes intersecting with terms"
  };

     // TODO: Quick settings options -> Drop down to select column and auto complete species

    private static final Icon chartIcon = new ImageIcon(
            EnrichmentCytoPanel.class.getResource("/images/chart20.png"));
    final Font iconFont;
    final CyServiceRegistrar registrar;
    private static final Icon icon = new ImageIcon(
            EnrichmentCytoPanel.class.getResource("/images/enrichment-table14.png"));
    final CyApplicationManager applicationManager;

    final String butFilterName = "Filter enrichment table";
    final String butSettingsName = "Network-specific enrichment panel settings";
    final String butExportTableDescr = "Export enrichment table";
    final String butRunProfilerName = "Perform Gene Enrichment";
    private boolean noSignificant;
    private JSONObject result;
    CyTableFactory tableFactory;
    CyTableManager tableManager;
    private CyProperty<Properties> sessionProperties;


    public EnrichmentCytoPanel(CyServiceRegistrar registrar, boolean noSignificant, JSONObject result) {
        this.registrar = registrar;
        this.result = result;
        this.setLayout(new BorderLayout());
        this.colorChooserFactory = registrar.getService(CyColorPaletteChooserFactory.class);
        IconManager iconManager = registrar.getService(IconManager.class);
        this.iconFont = iconManager.getIconFont(22.0f);
        applicationManager = registrar.getService(CyApplicationManager.class);
        enrichmentTables = new HashMap<String, JTable>();
        this.noSignificant = noSignificant;
        initPanel(this.noSignificant);
    }

    public void setEnrichmentTable(CyTable enrichmentTable){
        this.enrichmentTable = enrichmentTable;
        initPanel(this.noSignificant);
    }

    @Override
    public String getIdentifier() {
        return "org.nrnb.gsoc.enrichment";
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public CytoPanelName getCytoPanelName() {
        return CytoPanelName.SOUTH;
    }

    @Override
    public String getTitle() {
        return "Enrichment Table";
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    public EnrichmentTableModel getTableModel() { return tableModel; }

    @Override
    public void actionPerformed(ActionEvent e) {
        CyNetwork network = applicationManager.getCurrentNetwork();
        TaskManager<?, ?> tm = registrar.getService(TaskManager.class);
        if (e.getSource().equals(butRunProfiler)) {
            tm.execute(new TaskIterator(new EnrichmentTask(registrar,this)));
        }
        else if (e.getSource().equals(butFilter)) {
            tm.execute(new TaskIterator(new FilterEnrichmentTableTask(registrar,this)));
        }
        else if (e.getSource().equals(butExportTable)) {
            if (network != null) {
                if(enrichmentTable!=null) {
                    tm.execute(new TaskIterator(new ExportEnrichmentTableTask(registrar, network, this, enrichmentTable)));
                }
            }
        } else if (e.getSource().equals(butAdvancedOptions)) {
            if (network != null) {
                tm.execute(new TaskIterator(new EnrichmentAdvancedOptionsTask(registrar)));
            }
        }
    }

    public CyTable getFilteredTable() {
        //Map<EnrichmentTerm, String> selectedTerms = new LinkedHashMap<EnrichmentTerm, String>();
        CyNetwork network = applicationManager.getCurrentNetwork();
        if (network == null || tableModel == null)
            //return selectedTerms;
            return null;

        if (filteredEnrichmentTable != null) return filteredEnrichmentTable;

        CyTable currTable = ModelUtils.getEnrichmentTable(registrar, network, TermSource.ALL.getTable());

        if (currTable == null || currTable.getRowCount() == 0) {
            return null;
        }
        CyTableFactory tableFactory = registrar.getService(CyTableFactory.class);
    		CyTableManager tableManager = registrar.getService(CyTableManager.class);
    		filteredEnrichmentTable = tableFactory.createTable(TermSource.ALLFILTERED.getTable(),
    		                                                   EnrichmentTerm.colID, Long.class, false, true);
    		filteredEnrichmentTable.setTitle("Enrichment: filtered");
    		//filteredEnrichmentTable.setSavePolicy(SavePolicy.DO_NOT_SAVE);
    		tableManager.addTable(filteredEnrichmentTable);
    		ModelUtils.setupEnrichmentTable(filteredEnrichmentTable);
        updateFilteredEnrichmentTable();
        return filteredEnrichmentTable;
    }

    /**
     * @description Initialises the panel design
     * @param noSignificant
     */
    public void initPanel(boolean noSignificant) {
        CyNetwork network = applicationManager.getCurrentNetwork();
        /**
         * Initialise the top panel design
         */
        JPanel buttonsPanelLeft = new JPanel();
        BoxLayout layoutLeft = new BoxLayout(buttonsPanelLeft, BoxLayout.LINE_AXIS);
        buttonsPanelLeft.setLayout(layoutLeft);

        butRunProfiler = new JButton(IconManager.ICON_REFRESH);
        butRunProfiler.setFont(iconFont);
        butRunProfiler.addActionListener(this);
        butRunProfiler.setToolTipText(butRunProfilerName);
        butRunProfiler.setBorderPainted(false);
        butRunProfiler.setContentAreaFilled(false);
        butRunProfiler.setFocusPainted(false);
        butRunProfiler.setBorder(BorderFactory.createEmptyBorder(2,10,2,10));

        butFilter = new JButton(IconManager.ICON_FILTER);
        butFilter.setFont(iconFont);
        butFilter.addActionListener(this);
        butFilter.setToolTipText(butFilterName);
        butFilter.setBorderPainted(false);
        butFilter.setContentAreaFilled(false);
        butFilter.setFocusPainted(false);
        butFilter.setBorder(BorderFactory.createEmptyBorder(2,10,2,10));


        /**
         * JComboBox for setting the default value of the node to be chosen for performing the query
         */

        // Add enrichment map button here if EnrichmentMap is loaded

        buttonsPanelLeft.add(butRunProfiler);
        buttonsPanelLeft.add(butFilter);

        // JPanel buttonsPanelRight = new JPanel(new GridLayout(1, 3));
        JPanel buttonsPanelRight = new JPanel();
        BoxLayout layoutRight = new BoxLayout(buttonsPanelRight, BoxLayout.LINE_AXIS);
        buttonsPanelRight.setLayout(layoutRight);

        butExportTable = new JButton(IconManager.ICON_SAVE);
        butExportTable.addActionListener(this);
        butExportTable.setFont(iconFont);
        butExportTable.setToolTipText(butExportTableDescr);
        butExportTable.setBorderPainted(false);
        butExportTable.setContentAreaFilled(false);
        butExportTable.setFocusPainted(false);
        butExportTable.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 10));
        butExportTable.setEnabled(false);
        butFilter.setEnabled(false);

        butAdvancedOptions = new JButton(IconManager.ICON_COG);
        butAdvancedOptions.setFont(iconFont);
        butAdvancedOptions.addActionListener(this);
        butAdvancedOptions.setToolTipText(butSettingsName);
        butAdvancedOptions.setBorderPainted(false);
        butAdvancedOptions.setContentAreaFilled(false);
        butAdvancedOptions.setFocusPainted(false);
        butAdvancedOptions.setBorder(BorderFactory.createEmptyBorder(2,4,2,10));

        buttonsPanelRight.add(butExportTable);
        buttonsPanelRight.add(butAdvancedOptions);
        topPanel = new JPanel(new BorderLayout());
        topPanel.add(buttonsPanelLeft, BorderLayout.WEST);
        topPanel.add(buttonsPanelRight, BorderLayout.EAST);
        // topPanel.add(boxTables, BorderLayout.EAST);
        this.add(topPanel, BorderLayout.NORTH);
        if (network == null)
            return;
        initPanel(network, noSignificant);
    }

    /**
     * @description Initialises the panel design
     * @param network
     * @param noSignificant
     */
    public void initPanel(CyNetwork network, boolean noSignificant) {
        this.removeAll();
        availableTables = new ArrayList<String>();
        Set<CyTable> currTables = ModelUtils.getEnrichmentTables(registrar, network);
        for (CyTable currTable : currTables) {
            enrichmentTable = currTable;
            availableTables.add(enrichmentTable.getTitle());
        }
        JPanel buttonsPanelLeft = new JPanel();
        BoxLayout layoutLeft = new BoxLayout(buttonsPanelLeft, BoxLayout.LINE_AXIS);
        buttonsPanelLeft.setLayout(layoutLeft);

        buttonsPanelLeft.add(butRunProfiler);
        buttonsPanelLeft.add(butFilter);

        // JPanel buttonsPanelRight = new JPanel(new GridLayout(1, 3));
        JPanel buttonsPanelRight = new JPanel();
        BoxLayout layoutRight = new BoxLayout(buttonsPanelRight, BoxLayout.LINE_AXIS);
        buttonsPanelRight.setLayout(layoutRight);

        butExportTable = new JButton(IconManager.ICON_SAVE);
        butExportTable.addActionListener(this);
        butExportTable.setFont(iconFont);
        butExportTable.setToolTipText(butExportTableDescr);
        butExportTable.setBorderPainted(false);
        butExportTable.setContentAreaFilled(false);
        butExportTable.setFocusPainted(false);
        butExportTable.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 10));
        butExportTable.setEnabled(false);
        butFilter.setEnabled(false);


        butAdvancedOptions = new JButton(IconManager.ICON_COG);
        butAdvancedOptions.setFont(iconFont);
        butAdvancedOptions.addActionListener(this);
        butAdvancedOptions.setToolTipText(butSettingsName);
        butAdvancedOptions.setBorderPainted(false);
        butAdvancedOptions.setContentAreaFilled(false);
        butAdvancedOptions.setFocusPainted(false);
        butAdvancedOptions.setBorder(BorderFactory.createEmptyBorder(2,4,2,10));

        buttonsPanelRight.add(butExportTable);
        buttonsPanelRight.add(butAdvancedOptions);
        butExportTable.setEnabled(true);
        butFilter.setEnabled(true);
        topPanel = new JPanel(new BorderLayout());
        topPanel.add(buttonsPanelLeft, BorderLayout.WEST);
        topPanel.add(buttonsPanelRight, BorderLayout.EAST);
        // topPanel.add(boxTables, BorderLayout.EAST);
        this.add(topPanel, BorderLayout.NORTH);

        if (noSignificant) {
            mainPanel = new JPanel(new BorderLayout());
            JLabel label = new JLabel("Enrichment retrieval returned no results that met the criteria.",
                    SwingConstants.CENTER);
            mainPanel.add(label, BorderLayout.CENTER);
            this.add(mainPanel, BorderLayout.CENTER);
        } else if (availableTables.size() == 0) {
            mainPanel = new JPanel(new BorderLayout());
            JLabel label = new JLabel("No enrichment has been retrieved for this network.",
                    SwingConstants.CENTER);
            mainPanel.add(label, BorderLayout.CENTER);
            this.add(mainPanel, BorderLayout.CENTER);
        } else {


            if(enrichmentTable==null){
                CyTableManager tableManager = registrar.getService(CyTableManager.class);
                tableFactory = registrar.getService(CyTableFactory.class);
                enrichmentTable = tableFactory.createTable(TermSource.ALL.getTable(),EnrichmentTerm.colID,Long.class,false, true);
                tableManager.addTable(enrichmentTable);
            }
            createJTable(enrichmentTable);
    //        System.out.println("Table model: "+ tableModel.getColumnCount());
            // Check if values are git mbeing received correctly
            List<CyRow> rows = enrichmentTable.getAllRows();
            availableTables.add(enrichmentTable.getTitle());
            /**
             * Initialise the top panel design
             */
            JTable currentTable = enrichmentTables.get(enrichmentTable.getTitle());
            if (tableModel != null) {
                updateFilteredEnrichmentTable();
            }
            labelRows = new JLabel("");
            updateLabelRows();
            labelRows.setHorizontalAlignment(JLabel.RIGHT);
            Font labelFont = labelRows.getFont();
            labelRows.setFont(labelFont.deriveFont((float)(labelFont.getSize() * 0.8)));

            mainPanel = new JPanel(new BorderLayout());
            scrollPane = new JScrollPane(currentTable);
            mainPanel.setLayout(new GridLayout(1, 1));
            mainPanel.add(scrollPane, BorderLayout.CENTER);
            this.add(mainPanel, BorderLayout.CENTER);
        }
        this.revalidate();
        this.repaint();
    }

    /**
     * @description Creates the settings table
     * @param cyTable
     */
    private void createJTable(CyTable cyTable) {
        tableModel = new EnrichmentTableModel(enrichmentTable, EnrichmentTerm.swingColumnsEnrichment);
        JTable jTable = new JTable(tableModel){
          //Implement table header tool tips.
          protected JTableHeader createDefaultTableHeader() {
            return new JTableHeader(columnModel) {
              public String getToolTipText(MouseEvent e) {
                String tip = null;
                java.awt.Point p = e.getPoint();
                int index = columnModel.getColumnIndexAtX(p.x);
                int realIndex =
                  columnModel.getColumn(index).getModelIndex();
                return columnToolTips[realIndex];
              }
            };
          }
        };
        jTable.getColumnModel().getColumn(12).setMinWidth(0);
        jTable.getColumnModel().getColumn(12).setMaxWidth(0);
        jTable.getColumnModel().getColumn(12).setWidth(0);
        jTable.setFillsViewportHeight(true);
        jTable.setAutoCreateRowSorter(true);
        jTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        jTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        jTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                enrichmentTables.put(enrichmentTable.getTitle(), jTable);
                int rows = jTable.getSelectedRow();
                int columnCount = jTable.getSelectedColumnCount();

                if (e.getValueIsAdjusting())
                    return;

                CyNetwork network = applicationManager.getCurrentNetwork();
                if (network == null)
                    return;

                if(jTable==null){
                    return;
                }

                if (columnCount == 1 && rows > -1) {
                  if (jTable.getSelectedRowCount() == 1)
                      clearNetworkSelection(network);
                        for (int row: jTable.getSelectedRows()) {
                            Object cellContent =
                                    jTable.getModel().getValueAt(jTable.convertRowIndexToModel(row),
                                            EnrichmentTerm.nodeSUIDColumn);
                            if (cellContent instanceof java.util.List) {
                                java.util.List<Long> nodeIDs = (List<Long>) cellContent;
                                for (Long nodeID : nodeIDs) {
                                    network.getDefaultNodeTable().getRow(nodeID).set(CyNetwork.SELECTED, true);
                                }
                            }
                        }
                }
            }
        });
        jTable.getModel().addTableModelListener(this);
        jTable.setDefaultRenderer(Color.class, new ColorRenderer(true));
        //jTable.setDefaultEditor(Color.class, new ColorEditor(registrar, this, colorChooserFactory, network));
        jTable.addMouseListener(new MouseAdapter() {

            public void mousePressed(MouseEvent e) {
                    JTable source = (JTable) e.getSource();
                    int row = source.rowAtPoint(e.getPoint());
                    int column = source.columnAtPoint(e.getPoint());
                    if (!source.isRowSelected(row)) {
                        source.changeSelection(row, column, false, false);
                    }

            }

            public void mouseReleased(MouseEvent e) {
                    JTable source = (JTable) e.getSource();
                    int row = source.rowAtPoint(e.getPoint());
                    int column = source.columnAtPoint(e.getPoint());
                    if (!source.isRowSelected(row)) {
                        source.changeSelection(row, column, false, false);
                    }

            }
        });
        enrichmentTables.put(enrichmentTable.getTitle(), jTable);

    }



    public CyTable updateFilteredEnrichmentTable() {
        if (filteredEnrichmentTable == null)
            getFilteredTable();

        CyNetwork network = applicationManager.getCurrentNetwork();
        if (network == null || tableModel == null)
            return null;

        CyTable currTable = ModelUtils.getEnrichmentTable(registrar, network, TermSource.ALL.getTable());
        if (currTable == null) return null;

        filteredEnrichmentTable.deleteRows(filteredEnrichmentTable.getPrimaryKey().getValues(Long.class));

        Long[] rowNames = tableModel.getRowNames();
        for (int i = 0; i < rowNames.length; i++) {
            CyRow row = currTable.getRow(rowNames[i]);
            CyRow filtRow = filteredEnrichmentTable.getRow(rowNames[i]);
            filtRow.set(EnrichmentTerm.colName, row.get(EnrichmentTerm.colName, String.class));
            filtRow.set(EnrichmentTerm.colDescription, row.get(EnrichmentTerm.colDescription, String.class));
            filtRow.set(EnrichmentTerm.colPvalue, row.get(EnrichmentTerm.colPvalue, String.class));
            filtRow.set(EnrichmentTerm.colGenes, row.getList(EnrichmentTerm.colGenes, String.class));
            filtRow.set(EnrichmentTerm.colGenesSUID, row.getList(EnrichmentTerm.colGenesSUID, Long.class));
            filtRow.set(EnrichmentTerm.colNetworkSUID, row.get(EnrichmentTerm.colNetworkSUID, Long.class));
        }
        return filteredEnrichmentTable;
    }

    public void resetColor(int currentRow) {
        JTable currentTable = enrichmentTables.get(showTable);
        // currentRow = currentTable.getSelectedRow();
        CyNetwork network = applicationManager.getCurrentNetwork();
        if (network == null || tableModel == null)
            return;
        CyTable enrichmentTable = ModelUtils.getEnrichmentTable(registrar, network,
                TermSource.ALL.getTable());
        Color color = (Color)currentTable.getModel().getValueAt(
                currentTable.convertRowIndexToModel(currentRow),
                EnrichmentTerm.chartColumnCol);
        String termName = (String)currentTable.getModel().getValueAt(
                currentTable.convertRowIndexToModel(currentRow),
                EnrichmentTerm.nameColumn);
        if (color == null || termName == null)
            return;

        for (CyRow row : enrichmentTable.getAllRows()) {
            if (enrichmentTable.getColumn(EnrichmentTerm.colName) != null
                    && row.get(EnrichmentTerm.colName, String.class) != null
                    && row.get(EnrichmentTerm.colName, String.class).equals(termName)) {
             //   row.set(EnrichmentTerm.colChartColor, "");
            }
        }
        tableModel.fireTableDataChanged();

    }



    public void updateLabelRows() {
        if (tableModel == null)
            return;
        String labelTxt = "";
        if (tableModel.getAllRowCount() != tableModel.getRowCount()) {
            labelTxt = tableModel.getRowCount() + " rows ("+tableModel.getAllRowCount()+" before filtering)";
        } else {
            labelTxt = tableModel.getAllRowCount() + " rows";
        }
        if (labelRows != null)
            labelRows.setText(labelTxt);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        CyNetwork network = applicationManager.getCurrentNetwork();
        if (network == null)
            return;

        updateLabelRows();
        JTable currentTable = enrichmentTables.get(showTable);
        if (currentTable == null){
          currentTable = enrichmentTables.get(enrichmentTable.getTitle());
        }
        currentTable.tableChanged(e);
    }

    private void clearNetworkSelection(CyNetwork network) {
  		List<CyNode> nodes = network.getNodeList();
  		clearSelection = true;
  		for (CyNode node : nodes) {
  			if (network.getRow(node).get(CyNetwork.SELECTED, Boolean.class)) {
  				network.getRow(node).set(CyNetwork.SELECTED, false);
  			}
  		}
  		clearSelection = false;
  	}

    @Override
    public void handleEvent(RowsSetEvent rse) {
        CyNetworkManager networkManager = registrar.getService(CyNetworkManager.class);
        CyNetwork selectedNetwork = null;
        if (rse.containsColumn(CyNetwork.SELECTED)) {
            Collection<RowSetRecord> columnRecords = rse.getColumnRecords(CyNetwork.SELECTED);
            for (RowSetRecord rec : columnRecords) {
                CyRow row = rec.getRow();
                if (row.toString().indexOf("FACADE") >= 0)
                    continue;
                Long networkID = row.get(CyNetwork.SUID, Long.class);
                Boolean selectedValue = (Boolean) rec.getValue();
                if (selectedValue && networkManager.networkExists(networkID)) {
                    selectedNetwork = networkManager.getNetwork(networkID);
                }
            }
        }
        if (selectedNetwork != null) {
            initPanel(selectedNetwork, false);
            return;
        }
        CyNetwork network = applicationManager.getCurrentNetwork();
		      JTable currentTable = enrichmentTables.get(showTable);
		        if (!clearSelection && network != null && currentTable != null) {
			           List<CyNode> nodes = network.getNodeList();
			              for (CyNode node : nodes) {
				                  if (network.getRow(node).get(CyNetwork.SELECTED, Boolean.class)) {
					                       return;
				                           }
			                              }
			                                 currentTable.clearSelection();
		                                   }
    }

    @Override
    public void handleEvent(SelectedNodesAndEdgesEvent event) {
        JTable table = enrichmentTables.get(showTable);
        if (table!=null && table.getSelectedRow() > -1 &&
                table.getSelectedColumnCount() == 1 &&
                table.getSelectedColumn() != EnrichmentTerm.chartColumnCol)
            return;

        CyNetwork network = applicationManager.getCurrentNetwork();
        if (network == null)
            return;
        List<Long> nodesToFilterSUID = new ArrayList<Long>();
        for (final CyNode node : event.getSelectedNodes()) {
            nodesToFilterSUID.add(node.getSUID());
        }
        updateLabelRows();
    }

    @Override
    public void handleEvent(NetworkAboutToBeDestroyedEvent e) {
      CyNetwork network = e.getNetwork();
      // delete enrichment tables
      CyTableManager tableManager = registrar.getService(CyTableManager.class);
      Set<CyTable> oldTables = ModelUtils.getEnrichmentTables(registrar, network);
      for (CyTable table : oldTables) {
        tableManager.deleteTable(table.getSUID());
      }

      CySwingApplication swingApplication = registrar.getService(CySwingApplication.class);
      CytoPanel cytoPanel = swingApplication.getCytoPanel(CytoPanelName.SOUTH);

      if (cytoPanel.indexOfComponent("org.nrnb.gsoc.enrichment") >= 0) {
        int compIndex = cytoPanel.indexOfComponent("org.nrnb.gsoc.enrichment");
        Component panel = cytoPanel.getComponentAt(compIndex);
        if (panel instanceof CytoPanelComponent2) {
          registrar.unregisterService(panel, CytoPanelComponent.class);
          registrar.unregisterService(panel, RowsSetListener.class);
          registrar.unregisterService(panel, SelectedNodesAndEdgesListener.class);
          }
      }

      CytoPanelComponent2 panel = new EnrichmentCytoPanel(registrar, noSignificant, null);
			registrar.registerService(panel, CytoPanelComponent.class, new Properties());
			registrar.registerService(panel, RowsSetListener.class, new Properties());
			registrar.registerService(panel, SelectedNodesAndEdgesListener.class, new Properties());
			if (cytoPanel.getState() == CytoPanelState.HIDE)
				cytoPanel.setState(CytoPanelState.DOCK);
			cytoPanel.setSelectedIndex(
					cytoPanel.indexOfComponent("org.nrnb.gsoc.enrichment"));
        }

    public void handleEvent(SessionLoadedEvent arg0) {
		CySwingApplication swingApplication = registrar.getService(CySwingApplication.class);
		CytoPanel cytoPanel = swingApplication.getCytoPanel(CytoPanelName.SOUTH);
    if (cytoPanel.indexOfComponent("org.nrnb.gsoc.enrichment") >= 0) {
      int compIndex = cytoPanel.indexOfComponent("org.nrnb.gsoc.enrichment");
      Component panel = cytoPanel.getComponentAt(compIndex);
      if (panel instanceof CytoPanelComponent2) {
        registrar.unregisterService(panel, CytoPanelComponent.class);
        registrar.unregisterService(panel, RowsSetListener.class);
        registrar.unregisterService(panel, SelectedNodesAndEdgesListener.class);
        }
    }
    //if (cytoPanel.indexOfComponent("org.nrnb.gsoc.enrichment") < 0) {
  			CytoPanelComponent2 panel =  new EnrichmentCytoPanel(registrar, noSignificant, null);
  			registrar.registerService(panel, CytoPanelComponent.class, new Properties());
        registrar.registerService(panel, RowsSetListener.class, new Properties());
        registrar.registerService(panel, SelectedNodesAndEdgesListener.class, new Properties());
  			if (cytoPanel.getState() == CytoPanelState.HIDE)
  				cytoPanel.setState(CytoPanelState.DOCK);
  		//} else {
  			//int compIndex = cytoPanel.indexOfComponent("org.nrnb.gsoc.enrichment");
  			//Component panel = cytoPanel.getComponentAt(compIndex);
  			//if (panel instanceof CytoPanelComponent2) {
  				//registrar.unregisterService(panel, CytoPanelComponent.class);
  			//}
  		//}
		}


    public void handleEvent(SetCurrentNetworkEvent event) {
      CyNetwork network = event.getNetwork();
    }
}
