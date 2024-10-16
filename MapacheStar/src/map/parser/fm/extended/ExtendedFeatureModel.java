package map.parser.fm.extended;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.carrotsearch.hppc.IntArrayList;

import map.parser.fm.FeatureModel;

public class ExtendedFeatureModel extends FeatureModel{
	
	private IntArrayList numericalFeatures=new IntArrayList();
	private HashMap<Integer,Bounding> boundings=new HashMap<Integer,Bounding>();
	private List<NumericalConstraint> numericalConstraintList=new LinkedList<NumericalConstraint>();
	
	
	
	public int addFeature(String name, int parent, boolean mandatory,Integer min,Integer max) {
		int id=super.addFeature(name, parent, mandatory);
		numericalFeatures.add(id);
		boundings.put(id, new Bounding(min,max));
		return id;
	}
	
	public void addNumericalConstraint(Integer f1,Integer f2,Comparator c) {
		NumericalConstraint nc=new NumericalConstraint(f1,f2, c);
		numericalConstraintList.add(nc);
	}
	
	public void addNumercialConstraint(NumericalConstraint nc) {
		numericalConstraintList.add(nc);
	}
	
	public Boolean isNumerical(int id) {
		return numericalFeatures.contains(id);
	}
	
	public Bounding getBounding(Integer featureID) {
		return boundings.get(featureID);
	}
	
	public List<NumericalConstraint> getNumericalConstraints() {
		return numericalConstraintList;
	}

}
