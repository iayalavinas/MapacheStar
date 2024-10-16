package map.ilp;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import map.parser.fm.extended.Comparator;

public class MappingFunction {
	private Integer intentionalElement;
	private List<Term> features=new LinkedList<Term>();
	
	public Integer getEntity() {
		return intentionalElement;
	}
	public void setEntity(Integer entity) {
		this.intentionalElement = entity;
	}
	public List<Term> getFeatures() {
		return features;
	}
	public void setFeatures(List<Term> features) {
		this.features = features;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(intentionalElement, features);
	}
	
	@Override
	public String toString() {
		String res="x_"+intentionalElement+" = ";
		for(int i=0;i<features.size();i++) {
			res=res+features.get(i).toString();
		}
		return res;
	}
	
	// -intentional_element + features = 0
	public LinearConstraint getLinearConstraint() {
		List<Term> listTerm=new LinkedList<Term>();
		listTerm.addAll(features);
		listTerm.add(new Term(-1d,intentionalElement));
		LinearConstraint lc=new LinearConstraint(listTerm, 0, Comparator.Equal);
		return lc;
	}

}
