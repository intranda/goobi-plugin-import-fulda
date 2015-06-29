package de.intranda.goobi.plugins.util;

import org.apache.log4j.Logger;
import org.goobi.beans.Process;

import de.sub.goobi.persistence.managers.ProcessManager;

public class GoobiMetadataUpdate {

    protected static final Logger logger = Logger.getLogger(GoobiMetadataUpdate.class);

    public static boolean checkForExistingProcess(String processname) {
        long anzahl = 0;
        logger.debug("Counting number of processes for " + processname);

        anzahl = ProcessManager.getNumberOfProcessesWithTitle(processname);

        logger.debug("Number of processes: " + anzahl);

        if (anzahl != 0) {
            return true;
        } else {
            return false;
        }
    }

    public static Process loadProcess(String processname) {
        logger.debug("Loading process with name " + processname);
        Process p = ProcessManager.getProcessByTitle(processname);
        return p;
    }
}
