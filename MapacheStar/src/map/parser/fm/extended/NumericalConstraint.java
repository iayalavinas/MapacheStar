package map.parser.fm.extended;

import java.util.Objects;

import map.ilp.LinearConstraint;
import map.ilp.Term;

public class NumericalConstraint {
	private Integer feature_1,feature_2;
	private Comparator comp;
	
	public NumericalConstraint(Integer f1,Integer f2,Comparator c) {
		feature_1=f1;
		feature_2=f2;
		comp=c;
	}

	public Integer getFeature_1() {
		return feature_1;
	}

	public void setFeature_1(Integer feature_1) {
		this.feature_1 = feature_1;
	}

	public Integer getFeature_2() {
		return feature_2;
	}

	public void setFeature_2(Integer feature_2) {
		this.feature_2 = feature_2;
	}

	public Comparator getComp() {
		return comp;
	}

	public void setComp(Comparator comp) {
		this.comp = comp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(comp, feature_1, feature_2);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NumericalConstraint other = (NumericalConstraint) obj;
		return comp == other.comp && Objects.equals(feature_1, other.feature_1)
				&& Objects.equals(feature_2, other.feature_2);
	}

	@Override
	public String toString() {
		String comparatorSymbol="?";
		switch (comp) {
		case LowerOrEqual:
			comparatorSymbol="<=";
			break;
		case Lower:
			comparatorSymbol="<";
			break;
		case Equal:
			comparatorSymbol="==";
			break;
		case HigherOrEqual:
			comparatorSymbol=">=";
			break;
		case Higher:
			comparatorSymbol=">";
			break;
		}
		return "X_" + feature_1 +comparatorSymbol+ "X_" + feature_2;
	}
	
	

}
