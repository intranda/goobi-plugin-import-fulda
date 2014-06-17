package de.intranda.goobi.plugins.util;

import java.util.List;

import org.apache.log4j.Logger;

import de.sub.goobi.Beans.Prozess;
import de.sub.goobi.Persistence.ProzessDAO;
import de.sub.goobi.helper.exceptions.DAOException;

public class GoobiMetadataUpdate {

    protected static final Logger logger = Logger.getLogger(GoobiMetadataUpdate.class);

    
    public static boolean checkForExistingProcess(String processname) {
        long anzahl = 0;
        logger.debug("Counting number of processes for " + processname);
        ProzessDAO dao = new ProzessDAO();
        try {
            anzahl = dao.count("from Prozess where titel LIKE '%" + processname + "%'");
        } catch (DAOException e) {
            logger.error(e);
            return false;
        }
//        ProcessManager pm = new ProcessManager();
//        try {
//            anzahl = pm.getHitSize(null, "prozesse.titel LIKE '%" + processname + "%'");
//        } catch (DAOException e) {
//            logger.error(e);
//            return false;
//        }

        logger.debug("Number of processes: " + anzahl);
        
        if (anzahl != 0) {
            return true;
        } else {
            return false;
        }
    }

    public static Prozess loadProcess(String processname) {
        logger.debug("Loading process with name " + processname);
        try {
            List<Prozess> processlist = new ProzessDAO().search("from Prozess where titel LIKE '%" + processname + "%'");
            if (!processlist.isEmpty()) {
              return processlist.get(0);
          }
        } catch (DAOException e) {
            logger.error(e);
        }
//       List<Process> processlist = ProcessManager.getProcesses(null, "prozesse.titel LIKE '%" + processname + "%'",0, 10);
//        if (!processlist.isEmpty()) {
//            return processlist.get(0);
//        }
        
        return null;
    }
}
