//package de.intranda.goobi.plugins.sru;
//
//import java.io.IOException;
//import java.io.StringReader;
//import java.net.MalformedURLException;
//import java.util.List;
//
//import javax.xml.parsers.DocumentBuilder;
//import javax.xml.parsers.DocumentBuilderFactory;
//import javax.xml.parsers.ParserConfigurationException;
//
//import org.jdom.Document;
//import org.jdom.Element;
//import org.jdom.JDOMException;
//import org.jdom.Namespace;
//import org.jdom.input.SAXBuilder;
//import org.w3c.dom.Node;
//import org.w3c.dom.Text;
//
//import ugh.dl.DigitalDocument;
//import ugh.dl.DocStruct;
//import ugh.dl.DocStructType;
//import ugh.dl.Fileformat;
//import ugh.dl.Prefs;
//import ugh.exceptions.PreferencesException;
//import ugh.exceptions.ReadException;
//import ugh.exceptions.TypeNotAllowedForParentException;
//import ugh.fileformats.mets.XStream;
//import ugh.fileformats.opac.PicaPlus;
//
//import com.googlecode.fascinator.redbox.sru.SRUClient;
//
//public final class SRUHelper {
//    private static final Namespace SRW = Namespace.getNamespace("srw", "http://www.loc.gov/zing/srw/");
//
//    // private static final Namespace DC = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
//    // private static final Namespace DIAG = Namespace.getNamespace("diag", "http://www.loc.gov/zing/srw/diagnostic/");
//    // private static final Namespace XCQL = Namespace.getNamespace("xcql", "http://www.loc.gov/zing/cql/xcql/");
//
//    public static Fileformat parsePicaFormat(Node pica, Prefs prefs) throws ReadException, PreferencesException, TypeNotAllowedForParentException {
//
//        PicaPlus pp = new PicaPlus(prefs);
//        pp.read(pica);
//        DigitalDocument dd = pp.getDigitalDocument();
//        Fileformat ff = new XStream(prefs);
//        ff.setDigitalDocument(dd);
//        /* BoundBook hinzufügen */
//        DocStructType dst = prefs.getDocStrctTypeByName("BoundBook");
//        DocStruct dsBoundBook = dd.createDocStruct(dst);
//        dd.setPhysicalDocStruct(dsBoundBook);
//        return ff;
//
//    }
//
//    public static Node parseResult(String resultString) throws IOException, JDOMException, ParserConfigurationException {
//
//        Document doc = new SAXBuilder().build(new StringReader(resultString), "utf-8");
//        // srw:searchRetrieveResponse
//        Element root = doc.getRootElement();
//        // <srw:records>
//        Element srwRecords = root.getChild("records", SRW);
//        // <srw:record>
//        Element srwRecord = srwRecords.getChild("record", SRW);
//        // <srw:recordData>
//        if (srwRecord != null) {
//            Element recordData = srwRecord.getChild("recordData", SRW);
//            Element record = recordData.getChild("record");
//
//            // generate an answer document
//            DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
//            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
//            org.w3c.dom.Document answer = docBuilder.newDocument();
//            org.w3c.dom.Element collection = answer.createElement("collection");
//            answer.appendChild(collection);
//
//            org.w3c.dom.Element picaRecord = answer.createElement("record");
//            collection.appendChild(picaRecord);
//
//            @SuppressWarnings("unchecked")
//            List<Element> data = record.getChildren();
//            for (Element datafield : data) {
//                if (datafield.getAttributeValue("tag") != null) {
//                    org.w3c.dom.Element field = answer.createElement("field");
//                    picaRecord.appendChild(field);
//                    if (datafield.getAttributeValue("occurrence") != null) {
//                        field.setAttribute("occurrence", datafield.getAttributeValue("occurrence"));
//                    }
//                    field.setAttribute("tag", datafield.getAttributeValue("tag"));
//                    @SuppressWarnings("unchecked")
//                    List<Element> subfields = datafield.getChildren();
//                    for (Element sub : subfields) {
//                        org.w3c.dom.Element subfield = answer.createElement("subfield");
//                        field.appendChild(subfield);
//                        subfield.setAttribute("code", sub.getAttributeValue("code"));
//                        Text text = answer.createTextNode(sub.getText());
//                        subfield.appendChild(text);
//                    }
//                }
//            }
//            return answer.getDocumentElement();
//        }
//        return null;
//    }
//
//    public static String search(String ppn) {
//        SRUClient client;
//        try {
//            //            client = new SRUClient("http://gso.gbv.de/sru/DB=2.1/", "pica", null, null);
//            client = new SRUClient("http://sru.gbv.de/hebis", "pica", null, null);
//
//            return client.getSearchResponse("pica.ppn=\"" + ppn + "\"");
//        } catch (MalformedURLException e) {
//        }
//        return "";
//    }
//
//    private SRUHelper() {
//    }
//}
