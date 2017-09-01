package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.goobi.beans.Process;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import de.intranda.goobi.plugins.util.GoobiMetadataUpdate;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import de.sub.goobi.helper.exceptions.SwapException;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class FuldaNewspaperImport implements IImportPlugin, IPlugin {

    protected static final Logger logger = Logger.getLogger(FuldaNewspaperImport.class);

    private static final String PLUGIN_TITLE = "FuldaNewspaperImport";
    private Prefs prefs;
    private String importFolder = "";
    private String currentIdentifier = "";
    private String volumeNumber = "";

    private static SimpleDateFormat dateformatFilesystem = new SimpleDateFormat("yyyyMMdd");

    private Fileformat fileformat = null;

    private MetadataType mainTitleType;
    private MetadataType dateType;
    private DocStructType issueType;

    private MassImportForm form = null;
    
    @Override
    public void setData(Record r) {
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        return null;
    }

    
    public String getProcessTitle() {
        return currentIdentifier + "_" + volumeNumber;
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<ImportObject>();
        for (Record currentRecord : records) {
            if (form != null) {
                form.addProcessToProgressBar();
            }
            currentIdentifier = currentRecord.getData();

            String basedir = ConfigPlugins.getPluginConfig(this).getString("basedir", "/opt/digiverso/goobi/import/");
            File newspaper = new File(basedir, currentIdentifier);

            String[] volumes = newspaper.list(directoryFilter);
            if ((volumes == null) || (volumes.length == 0)) {
                break;
            }
            for (String volumefolder : volumes) {
                
                volumeNumber = volumefolder;
                if (!GoobiMetadataUpdate.checkForExistingProcess(getProcessTitle())) {

                   
                    ImportObject io = new ImportObject();

                    io.setImportFileName(currentIdentifier + "_" + volumeNumber);
                    io.setErrorMessage("Vorgang " + currentIdentifier + "_" + volumeNumber + " existiert nicht.");

                    io.setImportReturnValue(ImportReturnValue.InvalidData);

                    answer.add(io);

                } else {
                    Process prozess = GoobiMetadataUpdate.loadProcess(getProcessTitle());
                    logger.debug("found Prozess " + prozess.getTitel() + " in goobi");
                    prefs = prozess.getRegelsatz().getPreferences();
                    mainTitleType = prefs.getMetadataTypeByName("TitleDocMain");
                    dateType = prefs.getMetadataTypeByName("DateIssued");
                    issueType = prefs.getDocStrctTypeByName("NewspaperIssue");
                    try {
                        fileformat = prozess.readMetadataFile();
                        logger.debug("loaded metadata");
                        updateData(prozess);
                        fileformat.getDigitalDocument().addAllContentFiles();
                        prozess.writeMetadataFile(fileformat);

                        Helper.setMeldung("Prozess " + prozess.getTitel() + " wurde erfolgreich aktualisiert");
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            }
        }
        return answer;
    }

    private void updateData(Process prozess) throws PreferencesException, ParseException, TypeNotAllowedForParentException,
            TypeNotAllowedAsChildException, MetadataTypeNotAllowedException, ImportPluginException {

        // nachträgliches Einfügen von Strukturelementen

        logger.debug("loading mets file");

        DigitalDocument dd = fileformat.getDigitalDocument();
        DocStruct logical = dd.getLogicalDocStruct();
        DocStruct volume = logical.getAllChildren().get(0);
        List<File> issueList = findAllIsssues();

        logger.debug("found " + issueList.size() + " issues.");

        int imageOrderNumber = 1;
        for (File issueFolder : issueList) {
            //            Date newIssueDate = dateformatFilesystem.parse(dateString);
            if (dd.getPhysicalDocStruct().getAllChildren() != null) {
                imageOrderNumber = dd.getPhysicalDocStruct().getAllChildren().size() + 1;
            }
            logger.debug("first image number of issue " + issueFolder.getName() + " is " + imageOrderNumber);
            createIssue(dd, volume, imageOrderNumber, issueFolder, true, prozess);
        }

    }

    private int createIssue(DigitalDocument dd, DocStruct volume, int imageOrderNumber, File issueFolder, boolean isUpdate, Process prozess)
            throws TypeNotAllowedForParentException, TypeNotAllowedAsChildException, ParseException, MetadataTypeNotAllowedException,
            ImportPluginException {

        // only 1 issue per day, no supplement - no subfolder, images in folder

        if ((issueFolder.listFiles(directoryFilter) == null) || (issueFolder.listFiles(directoryFilter).length == 0)) {
            File[] imageArray = issueFolder.listFiles(imageFilter);
            List<File> imageList = new ArrayList<File>();
            imageList.addAll(Arrays.asList(imageArray));
            Collections.sort(imageList);

            DocStruct issue = createSingleIssue(dd, volume, issueFolder);

            imageOrderNumber = linkImages(issue, dd, imageList, imageOrderNumber);
            // completeImageList.addAll(imageList);
            if (isUpdate) {
                moveImagesToFolder(imageList, issueFolder.getName(), prozess);
            } else {
                moveImages(imageList, issueFolder.getName());
            }
        }
      
        return imageOrderNumber;
    }

    private DocStruct createSingleIssue(DigitalDocument dd, DocStruct volume, File issueFolder) throws TypeNotAllowedForParentException,
            TypeNotAllowedAsChildException, ParseException, MetadataTypeNotAllowedException {
        DocStruct issue = dd.createDocStruct(issueType);
        volume.addChild(issue);
        // parse current date

        String dateString = "";
        String issueNumber = "";
        if (issueFolder.getName().matches("\\d+")) {
            dateString = issueFolder.getName();
        } else {
            dateString = issueFolder.getName().substring(0, 8);
            issueNumber = issueFolder.getName().substring(9);
            // date with letter at the end                
        }

        Date issued = dateformatFilesystem.parse(dateString);
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(issued);

        // creating title for issue
        String title = createIssueTitle(cal, issueNumber);
        Metadata issueTitle = new Metadata(mainTitleType);
        issueTitle.setValue(title);
        issue.addMetadata(issueTitle);
        // creating date for issue
        Metadata issueDate = new Metadata(dateType);
        issueDate.setValue(createW3CDateIssued(cal));
        issue.addMetadata(issueDate);
        return issue;
    }

    private static String createIssueTitle(GregorianCalendar cal, String type) {
        String title = "";
        switch (type) {
            case "a":
                title += "Erste Ausgabe vom ";
                break;
            case "b":
                title += "Zweite Ausgabe vom ";
                break;
            case "c":
                title += "Dritte Ausgabe vom ";
                break;
            case "d":
                title += "Vierte Ausgabe vom ";
                break;
            case "e":
                title += "Fünfte Ausgabe vom ";
                break;
            case "f":
                title += "Sechste Ausgabe vom ";
                break;
            case "g":
                title += "Siebte Ausgabe vom ";
                break;
            case "h":
                title += "Achte Ausgabe vom ";
                break;
            case "i":
                title += "Neunte Ausgabe vom ";
                break;
            default:
                title += "Ausgabe vom ";
                break;
        }
        switch (cal.get(Calendar.DAY_OF_WEEK)) {
            case 2:
                title += "Montag, den ";
                break;
            case 3:
                title += "Dienstag, den ";
                break;
            case 4:
                title += "Mittwoch, den ";
                break;
            case 5:
                title += "Donnerstag, den ";
                break;
            case 6:
                title += "Freitag, den ";
                break;
            case 7:
                title += "Samstag, den ";
                break;
            default:
                title += "Sonntag, den ";
                break;
        }
        switch (cal.get(Calendar.DAY_OF_MONTH)) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                title += "0" + cal.get(Calendar.DAY_OF_MONTH) + ". ";
                break;
            default:
                title += cal.get(Calendar.DAY_OF_MONTH) + ". ";
                break;
        }

        int i = cal.get(Calendar.MONTH);
        if (i == 0) {
            title += "Januar ";
        } else if (i == 1) {
            title += "Februar ";
        } else if (i == 2) {
            title += "März ";
        } else if (i == 3) {
            title += "April ";
        } else if (i == 4) {
            title += "Mai ";
        } else if (i == 5) {
            title += "Juni ";
        } else if (i == 6) {
            title += "Juli ";
        } else if (i == 7) {
            title += "August ";
        } else if (i == 8) {
            title += "September ";
        } else if (i == 9) {
            title += "Oktober ";
        } else if (i == 10) {
            title += "November ";
        } else if (i == 11) {
            title += "Dezember ";
        }
        title += cal.get(Calendar.YEAR) + ".";
        return title;
    }

    private static String createW3CDateIssued(GregorianCalendar cal) {
        String w3cdtf = String.valueOf(cal.get(Calendar.YEAR)) + "-";
        int i = cal.get(Calendar.MONTH);
        if (i == 0) {
            w3cdtf += "01-";
        } else if (i == 1) {
            w3cdtf += "02-";
        } else if (i == 2) {
            w3cdtf += "03-";
        } else if (i == 3) {
            w3cdtf += "04-";
        } else if (i == 4) {
            w3cdtf += "05-";
        } else if (i == 5) {
            w3cdtf += "06-";
        } else if (i == 6) {
            w3cdtf += "07-";
        } else if (i == 7) {
            w3cdtf += "08-";
        } else if (i == 8) {
            w3cdtf += "09-";
        } else if (i == 9) {
            w3cdtf += "10-";
        } else if (i == 10) {
            w3cdtf += "11-";
        } else if (i == 11) {
            w3cdtf += "12-";
        }

        switch (cal.get(Calendar.DAY_OF_MONTH)) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                w3cdtf += "0" + cal.get(Calendar.DAY_OF_MONTH);
                break;
            default:
                w3cdtf += cal.get(Calendar.DAY_OF_MONTH);
                break;
        }
        return w3cdtf;
    }

    private int linkImages(DocStruct current, DigitalDocument dd, List<File> imageList, int pyhsicalPageCounter)
            throws TypeNotAllowedForParentException, TypeNotAllowedAsChildException, MetadataTypeNotAllowedException {

        DocStruct physical = dd.getPhysicalDocStruct();
        DocStruct firstchild = dd.getLogicalDocStruct().getAllChildren().get(0);
        int logicalPageCounter = 1;
        DocStructType dsTypePage = prefs.getDocStrctTypeByName("page");
        for (@SuppressWarnings("unused")
        File filename : imageList) {

            DocStruct dsPage = dd.createDocStruct(dsTypePage);
            physical.addChild(dsPage);
            Metadata metaPhysPageNumber = new Metadata(prefs.getMetadataTypeByName("physPageNumber"));
            dsPage.addMetadata(metaPhysPageNumber);
            metaPhysPageNumber.setValue(String.valueOf(pyhsicalPageCounter));

            Metadata metaLogPageNumber = new Metadata(prefs.getMetadataTypeByName("logicalPageNumber"));

            metaLogPageNumber.setValue(String.valueOf(logicalPageCounter++));

            dsPage.addMetadata(metaLogPageNumber);
            current.addReferenceTo(dsPage, "logical_physical");
            firstchild.addReferenceTo(dsPage, "logical_physical");
            pyhsicalPageCounter++;
        }
        return pyhsicalPageCounter;
    }

    private void moveImages(List<File> imageList, String prefix) throws ImportPluginException {
        File destinationRoot = new File(importFolder, getProcessTitle());
        if (!destinationRoot.exists()) {
            destinationRoot.mkdir();
        }

        try {
            for (File file : imageList) {

                File destinationImages = new File(destinationRoot, "images");
                if (!destinationImages.exists()) {
                    destinationImages.mkdir();
                }
                File destinationTif = new File(destinationImages, getProcessTitle() + "_tif");
                if (!destinationTif.exists()) {
                    destinationTif.mkdir();
                }
                FileUtils.copyFile(file, new File(destinationTif, prefix + "_" + file.getName()));

            }
        } catch (IOException e) {
            logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        }

    }

    private void moveImagesToFolder(List<File> imageList, String prefix, Process prozess) throws ImportPluginException {

        try {
            File destinationFolder = new File(prozess.getImagesOrigDirectory(false));
            for (File file : imageList) {
                FileUtils.copyFile(file, new File(destinationFolder, prefix + "_" + file.getName()));
            }
        } catch (IOException e) {
            logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        } catch (SwapException e) {
            logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        } catch (DAOException e) {
            logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        } catch (InterruptedException e) {
            logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        }
    }

    private List<File> findAllIsssues() {
        String basedir = ConfigPlugins.getPluginConfig(this).getString("basedir", "/opt/digiverso/goobi/import/");
        File newspaper = new File(basedir, currentIdentifier);
        File newspaperVolume = new File(newspaper, volumeNumber);

        File[] issueArray = newspaperVolume.listFiles(directoryFilter);
        if ((issueArray == null) || (issueArray.length == 0)) {
            return null;
        }
        List<File> issueList = new ArrayList<File>();
        issueList.addAll(Arrays.asList(issueArray));
        Collections.sort(issueList);
        return issueList;
    }

    @Override
    public List<Record> splitRecords(String records) {
        return null;
    }

    @Override
    public List<Record> generateRecordsFromFile() {
        return null;
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        List<Record> records = new ArrayList<Record>();
        for (String filename : filenames) {
            Record rec = new Record();
            rec.setData(filename);
            rec.setId(filename);
            records.add(rec);
        }
        return records;
    }

    @Override
    public void setFile(File importFile) {
    }

    @Override
    public List<String> splitIds(String ids) {
        return null;
    }

    @Override
    public List<ImportType> getImportTypes() {
        List<ImportType> answer = new ArrayList<>();
        answer.add(ImportType.FOLDER);
        return answer;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
    }

    @Override
    public List<String> getAllFilenames() {
        String basedir = ConfigPlugins.getPluginConfig(this).getString("basedir", "/opt/digiverso/goobi/import/");
        List<String> subfolderInImportfolder = new ArrayList<String>();
        File folder = new File(basedir);
        if (folder.exists() && folder.isDirectory()) {
            subfolderInImportfolder.addAll(Arrays.asList(folder.list(directoryFilter)));
        }
        Collections.sort(subfolderInImportfolder);
        return subfolderInImportfolder;
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
        // String basedir = ConfigPlugins.getPluginConfig(this).getString("basedir", "/opt/digiverso/goobi/import/");
        // for (String filename : selectedFilenames) {
        // File folder = new File(basedir, filename);
        // try {
        // FileUtils.deleteDirectory(folder);
        // } catch (IOException e) {
        // logger.warn("Error during deletion", e);
        // }
        // }

    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
    }

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return PLUGIN_TITLE;
    }

    
    public String getDescription() {
        return PLUGIN_TITLE;
    }

    @Override
    public void setPrefs(Prefs prefs) {
        this.prefs = prefs;
    }

    @Override
    public String getImportFolder() {
        return importFolder;
    }

    @Override
    public void setImportFolder(String folder) {
        this.importFolder = folder;
    }

    private static FilenameFilter directoryFilter = new FilenameFilter() {
        @Override
        public boolean accept(final File dir, final String name) {
            File toTest = new File(dir, name);
            return toTest.exists() && toTest.isDirectory();
        }
    };

    private static FilenameFilter imageFilter = new FilenameFilter() {
        @Override
        public boolean accept(final File dir, final String name) {
            return name.endsWith("tif") || name.endsWith("TIF") || name.endsWith("jpg");
        }
    };

    @Override
    public void setForm(MassImportForm form) {
       this.form = form;
        
    }
}
