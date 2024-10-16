/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package map.parser.fm;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 *
 * @author inma
 */
public class MOFeatureModel {

    /**
     * @param args the command line arguments
     */
    public static SxfmParser parser;
    public static void main(String[] args) {
        parser=new SxfmParser();
        try {
            FeatureModel model=parser.parse("D-SensorNetwork.xml");
            System.out.println("Se ha cargado un modelo llamado "+model.getName());
            //LinearConstraintDerivator lcd=new LinearConstraintDerivator();
            //lcd.generateLinearConstraintProblem(model);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(MOFeatureModel.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(MOFeatureModel.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MOFeatureModel.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SxfmParserException ex) {
            Logger.getLogger(MOFeatureModel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
