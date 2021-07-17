package org.nrnb.gsoc.enrichment.tasks;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.*;
import org.cytoscape.work.util.ListSingleSelection;
import org.nrnb.gsoc.enrichment.ui.EnrichmentSettings;
import org.nrnb.gsoc.enrichment.utils.ModelUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnrichmentSettingsTask extends AbstractTask {
    final CyServiceRegistrar registrar;
    final CyApplicationManager applicationManager;
    final CyNetwork network;

    @ContainsTunables
    public EnrichmentSettings enrichmentSettings;

    @Tunable(description = "Make these settings the default",
            longDescription = "Unless this is set to true, these settings only apply to the current network",
            tooltip = "<html>Unless this is set to true, these settings only apply to the current network.</html>")
    public boolean makeDefault = false;
    @Tunable(description="Organism",
            longDescription="Default species for network queries.",
            exampleStringValue = "Homo Sapiens",
            params="lookup=begins", groups={"Query Defaults (take effect after restarting Cytoscape)"}, gravity=10.0)
    public ListSingleSelection<String> organism;

    @Tunable(description="Gene ID Column",
            longDescription="Column to choose for getting GeneIDs.",
            exampleStringValue = "LABEL",
            params="lookup=begins", groups={"Query Defaults (take effect after restarting Cytoscape)"}, gravity=10.0)
    public ListSingleSelection<CyColumn> geneID;
    final CyTable nodeTable;
    public Map<String,String> scientificNametoID;
    public EnrichmentSettingsTask(CyServiceRegistrar registrar) {
        this.registrar = registrar;
        applicationManager = registrar.getService(CyApplicationManager.class);
        this.network = applicationManager.getCurrentNetwork();
        nodeTable = network.getDefaultNodeTable();
        enrichmentSettings = new EnrichmentSettings(registrar);
        geneID = new ListSingleSelection<CyColumn>(ModelUtils.getProfilerColumn(nodeTable));
        scientificNametoID = new HashMap<>(ModelUtils.getOrganisms());
        organism   = new ListSingleSelection<String>(ModelUtils.getOrganismsName(scientificNametoID));
    }
    //user sets the cycol -> update default -> the run the query
    @Override
    public void run(TaskMonitor monitor) throws Exception {
        monitor.setTitle("Enrichment settings");
        // TODO: Implement scenario where values must be options value must be stored as default

        // TODO: maybe this is a way to automatically apply settings?
        TaskManager<?, ?> tm = (TaskManager<?, ?>) registrar.getService(TaskManager.class);
    }

    @ProvidesTitle
    public String getTitle() {
        return "Network-specific settings for gProfiler Enrichment table";
    }

}
