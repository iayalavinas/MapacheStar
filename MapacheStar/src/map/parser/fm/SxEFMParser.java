package map.parser.fm;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.carrotsearch.hppc.ObjectArrayList;

import map.parser.fm.extended.ExtendedFeatureModel;

public class SxEFMParser extends SxfmParser {
	
	/**
     * Parses an SXFM file
     *
     * @param xmlFile
     * @return the feature model
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     * @throws SxfmParserException
     */
    public static ExtendedFeatureModel parseToEFM(String xmlFile) throws ParserConfigurationException,
            SAXException, IOException, SxfmParserException {

        ExtendedFeatureModel fm = new ExtendedFeatureModel();

        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(xmlFile);

        Element root = doc.getDocumentElement();

        // Check root
        if (!root.getNodeName().equals(ELEMENT_FEATURE_MODEL)) {
            throw new SxfmParserException(EXCEPTION_UNEXPECTED_ELEMENT + root.getNodeName());
        }

        // Get name attribute, which is mandatory
        if (!root.hasAttribute(ATTRIBUTE_NAME)) {
            throw new SxfmParserException(EXCEPTION_MANDATORY_ATTRIBUTE_NOT_FOUND
                    + ATTRIBUTE_NAME);
        }
        fm.setName(root.getAttribute(ATTRIBUTE_NAME));

        // Get metadata, which is optional
        NodeList nl = root.getElementsByTagName(ELEMENT_META);
        if (nl.getLength() > 1) {
            throw new SxfmParserException(EXCEPTION_REPEATED_DEFINITION + ELEMENT_META);
        } else if (nl.getLength() == 1) {
            // Generate list of allowed data
            ObjectArrayList<String> validData = new ObjectArrayList<String>();
            validData.add(METADATA);

            nl = ((Element) nl.item(0)).getElementsByTagName(ELEMENT_DATA);
            int length = nl.getLength();
            if (length == 0) {
                throw new SxfmParserException(EXCEPTION_EMPTY_METADATA);
            }
            for (int i = 0; i < length; i++) {
                Element dataElement = (Element) nl.item(i);
                if (!dataElement.hasAttribute(ATTRIBUTE_NAME)) {
                    throw new SxfmParserException(EXCEPTION_MANDATORY_ATTRIBUTE_NOT_FOUND
                            + ATTRIBUTE_NAME);
                }
                String name = dataElement.getAttribute(ATTRIBUTE_NAME);

                // Check validity
                if (!validData.contains(name)) {
                    throw new SxfmParserException(EXCEPTION_UNKNOWN_METADATA + name);
                }

                // Add data to the FM
                String value = dataElement.getTextContent();
                if (name.equalsIgnoreCase(DATA_DESCRIPTION)) {
                    fm.setDescription(value);
                } else if (name.equalsIgnoreCase(DATA_CREATOR)) {
                    fm.setCreator(value);
                } else if (name.equalsIgnoreCase(DATA_EMAIL)) {
                    fm.setEmail(value);
                } else if (name.equalsIgnoreCase(DATA_DATE)) {
                    fm.setDate(value);
                } else if (name.equalsIgnoreCase(DATA_DEPARTMENT)) {
                    fm.setDepartment(value);
                } else if (name.equalsIgnoreCase(DATA_ORGANIZATION)) {
                    fm.setOrganization(value);
                } else if (name.equalsIgnoreCase(DATA_ADDRESS)) {
                    fm.setAddress(value);
                } else if (name.equalsIgnoreCase(DATA_PHONE)) {
                    fm.setPhone(value);
                } else if (name.equalsIgnoreCase(DATA_WEBSITE)) {
                    fm.setWebsite(value);
                } else if (name.equalsIgnoreCase(DATA_REFERENCE)) {
                    fm.setReference(value);
                }
            }
        } // End of metadata

        // Parse feature tree
        nl = root.getElementsByTagName(ELEMENT_FEATURE_TREE);
        if (nl.getLength() == 0) {
            throw new SxfmParserException(EXCEPTION_MANDATORY_ELEMENT_NOT_FOUND
                    + ELEMENT_FEATURE_TREE);
        } else if (nl.getLength() > 1) {
            throw new SxfmParserException(EXCEPTION_REPEATED_DEFINITION
                    + ELEMENT_FEATURE_TREE);
        }

        if (!nl.item(0).hasChildNodes()) {
            throw new SxfmParserException(EXCEPTION_EMPTY_FEATURE_TREE);
        }

        nl = nl.item(0).getChildNodes();
        StringBuffer featureTree = new StringBuffer();
        int length = nl.getLength();
        for (int i = 0; i < length; i++) {
            if (nl.item(i).getNodeType() == Node.TEXT_NODE) {
                featureTree.append(nl.item(i).getNodeValue() + "\n");
            }
        }

        parseFeatureTree(featureTree.toString(), fm);

        // Parse cross-tree constraints (the tag is mandatory)
        nl = root.getElementsByTagName(ELEMENT_CONSTRAINTS);
        if (nl.getLength() == 0) {
            throw new SxfmParserException(EXCEPTION_MANDATORY_ELEMENT_NOT_FOUND
                    + ELEMENT_CONSTRAINTS);
        } else if (nl.getLength() > 1) {
            throw new SxfmParserException(EXCEPTION_REPEATED_DEFINITION
                    + ELEMENT_CONSTRAINTS);
        }

        if (nl.item(0).hasChildNodes()) {
            nl = nl.item(0).getChildNodes();
            StringBuffer constraints = new StringBuffer();
            length = nl.getLength();
            for (int i = 0; i < length; i++) {
                if (nl.item(i).getNodeType() == Node.TEXT_NODE) {
                    constraints.append(nl.item(i).getNodeValue() + "\n");
                }
            }
            parseConstraints(constraints.toString(), fm);
        }

        return fm;
    }

}
