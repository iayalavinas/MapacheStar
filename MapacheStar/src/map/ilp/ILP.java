package map.ilp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntOpenHashSet;
import com.carrotsearch.hppc.ObjectIntOpenHashMap;

import map.parser.fm.CrossTreeConstraint;
import map.parser.fm.FeatureModel;
import map.parser.fm.extended.Bounding;
import map.parser.fm.extended.Comparator;
import map.parser.fm.extended.ExtendedFeatureModel;
import map.parser.fm.extended.NumericalConstraint;
import uma.istar.IStarModel;
import uma.istar.entities.Actor;
import uma.istar.entities.Agent;
import uma.istar.entities.Contribution;
import uma.istar.entities.Dependency;
import uma.istar.entities.GoalTaskElement;
import uma.istar.entities.IStarEntity;
import uma.istar.entities.IntentionalElement;
import uma.istar.entities.Refinement;

public class ILP {

	private IntArrayList variables;
	private HashMap<Object, Integer> mNamesToID;
	private HashMap<Integer, Object> mIDToName;
	private ObjectiveFunction objectiveFunction;
	private HashMap<Integer, Bounding> boundingConstraints;
	private List<LinearConstraint> constraints, equalityConstraints;
	private AtomicInteger mIDFactory = new AtomicInteger(1);
	private Double bigM = 1000000000000000d;
	private ExtendedFeatureModel featureModel;
	private IStarModel goalModel;

	public ILP() {
		variables = new IntArrayList();
		mNamesToID = new HashMap<Object, Integer>();
		mIDToName = new HashMap<Integer, Object>();
		constraints = new LinkedList<LinearConstraint>();
		boundingConstraints = new HashMap<Integer, Bounding>();
		equalityConstraints = new LinkedList<LinearConstraint>();
		objectiveFunction = new ObjectiveFunction();
	}

	public void printProblem() {
		System.out.println("Your ILP problem has " + variables.size() + " variables, " + "" + constraints.size()
				+ " linear constraints, " + equalityConstraints.size() + " equality constraints " + "and "
				+ boundingConstraints.size() + " bounding constraints.");
		// función objetivo
		if (objectiveFunction != null) {
			System.out.println("La función objetivo es: " + objectiveFunction.toString());
		}
		// Hay una variable que está en bounding constraint pero no está en variables
		System.out.println("The problem constraints are: ");
		for (int i = 0; i < constraints.size(); i++) {
			System.out.println(constraints.get(i).toString());
		}
		System.out.println("The problem equality constraints are: ");
		for (int i = 0; i < equalityConstraints.size(); i++) {
			System.out.println(equalityConstraints.get(i).toString());
		}
		System.out.println("The problem bounding constraints are: ");
		Iterator<Integer> iter = boundingConstraints.keySet().iterator();
		while (iter.hasNext()) {
			Integer aux = iter.next();
			System.out.println(aux + ":" + boundingConstraints.get(aux).toString());
		}
	}

	private int getNextID() {
		return mIDFactory.getAndIncrement();
	}

	public int getID(Object name) {
		return mNamesToID.get(name);
	}

	public int getIndex(Object var) {
		int res = -1;
		if (mNamesToID.containsKey(var)) {
			res = mNamesToID.get(var);
		} else {
			if (!(var instanceof String)) {
				if ((Integer) var == 0) {
					System.out.println("Añadimos el nombre 0");
				}
			}
			res = getNextID();
			mIDToName.put(res, var);
			mNamesToID.put(var, res);
			variables.add(res);
		}
		return res;
	}

	public void loadConstraints(MappingFunction mf) {
		equalityConstraints.add(mf.getLinearConstraint());
	}

	public void loadConstraints(List<MappingFunction> mf) {
		for (int i = 0; i < mf.size(); i++) {
			equalityConstraints.add(mf.get(i).getLinearConstraint());
		}
	}

	public void loadConstraints(IStarModel ism) {
		goalModel = ism;
		// add actors
		HashMap<String, Actor> actorList = ism.getActorList();
		Iterator<Actor> actorIterator = actorList.values().iterator();
		while (actorIterator.hasNext()) {
			Actor actor = actorIterator.next();
			// we add actor to the log structures
			Integer id = getIndex(actor.getId());
			addBoundingConstraint(id, 0, 100);
			// refinements
			addRefinements(actor);
			// contributions
			addContributions(actor);
			// actor constraint
			addActorConstraint(actor);
		}
		// add social dependencies
		addDependencies(ism);
	}

	public void loadConstraints(ExtendedFeatureModel efm) {
		featureModel = efm;
		// groups
		IntArrayList features = efm.getFeatures();
		IntArrayList addedToAGroup = new IntArrayList();
		Boolean isNumerical = false;

		// se añaden las características
		for (int i = 0; i < features.size(); i++) {
			// se obtiene su índice y se añade si es necesiario.
			int index = getIndex(features.get(i));
			// System.out.println("Se añade la característica con nombre
			// "+efm.getName(features.get(i))+" con índice "+index);
			// es numerica
			if (efm.isNumerical(features.get(i))) {
				// System.out.println("Se añade la característica numérica
				// "+efm.getName(features.get(i))+" con index "+index);
				addBoundingConstraints(efm, index, features.get(i));
				isNumerical = true;
			} else {
				addBoundingConstraint(index, 0, 1);
			}
			if (!addedToAGroup.contains(index)) {
				// es miembro de un grupo OR
				if (efm.getORGroupMembers(features.get(i)).size() > 0 && !addedToAGroup.contains(features.get(i))) {
					addedToAGroup.add(features.get(i));
					addedToAGroup.addAll(efm.getORGroupMembers(features.get(i)));
					addGroupConstraint(efm, efm.getORGroupMembers(features.get(i)), FeatureModel.TYPE_OR);
					// es miembro de un grupo XOR
				} else if (efm.getXORGroupMembers(features.get(i)).size() > 0
						&& !addedToAGroup.contains(features.get(i))) {
					addedToAGroup.add(features.get(i));
					addedToAGroup.addAll(efm.getXORGroupMembers(features.get(i)));
					addGroupConstraint(efm, efm.getXORGroupMembers(features.get(i)), FeatureModel.TYPE_XOR);
				}
			}
			// paternity
			addChildren(efm, features.get(i), index);
			// mandatory
			if (efm.isMandatory(features.get(i))) {
				List<Term> listTerm = new LinkedList<Term>();
				if (efm.getRootFeature() == features.get(i)) {
					// hay que poner esa constraint a 1 de manera obligatoria
					listTerm.add(new Term(1d, index));
					equalityConstraints.add(new LinearConstraint(listTerm, 1, Comparator.Equal));
				} else {
					// up-uc<=0
					// hay que buscar al padre
					int parentID = efm.getParent(features.get(i));
					int parentIndex = getIndex(parentID);
					Double childRange, parentRange;
					if (efm.isNumerical(parentID)) {
						parentRange = efm.getBounding(parentID).getRange();
						if (!boundingConstraints.containsKey(parentIndex)) {
							addBoundingConstraints(efm, parentIndex, parentID);
						}
					} else {
						parentRange = 1d;
						if (!boundingConstraints.containsKey(parentIndex)) {
							addBoundingConstraint(parentIndex, 0, 1);
						}
					}
					if (isNumerical) {
						childRange = efm.getBounding(features.get(i)).getRange();
					} else {
						childRange = 1d;
					}
					if (parentRange > childRange) {
						// up-(UBp-LBp+1)*uc<=0
						listTerm.add(new Term(1d, parentIndex));
						listTerm.add(new Term((parentRange + 1) * (-1), index));
					} else {
						// up-uc<=0
						listTerm.add(new Term(1d, parentIndex));
						listTerm.add(new Term(-1d, index));
					}
					constraints.add(new LinearConstraint(listTerm, 0, Comparator.LowerOrEqual));
				}
			}
		}

		// se añaden las restricciones entre hojas
		addCrosstreeConstraint(efm);
		addNumericalConstraint(efm);
		// añadimos que la característica raíz siempre tiene que estar seleccionada.
		Integer rootId = efm.getRootFeature();
		Integer rootIndex = getIndex(rootId);
		List<Term> termList = new LinkedList<Term>();
		termList.add(new Term(1d, rootIndex));
		LinearConstraint rootLC = new LinearConstraint(termList, 1, Comparator.Equal);
		equalityConstraints.add(rootLC);
	}

	private void addChildren(ExtendedFeatureModel efm, Integer parentID, Integer parentIndex) {
		IntArrayList children = efm.getChildren(parentID);
		Boolean isNumerical = efm.isNumerical(parentID);
		// System.out.println("La característica "+efm.getName(parentID)+" tiene
		// "+children.size()+" hijos.");

		for (int i = 0; i < children.size(); i++) {
			// si el hijo no pertenece a ningún grupo
			if (efm.getORGroupMembers(children.get(i)).size() == 0
					&& efm.getXORGroupMembers(children.get(i)).size() == 0) {
				Double parentRange, childRange;
				List<Term> listTerm;
				int childIndex = getIndex(children.get(i));
				if (isNumerical) {
					parentRange = efm.getBounding(parentID).getRange();
				} else {
					parentRange = 1d;
				}
				if (efm.isNumerical(children.get(i))) {
					childRange = efm.getBounding(children.get(i)).getRange();
					if (!boundingConstraints.containsKey(childIndex)) {
						addBoundingConstraints(efm, childIndex, children.get(i));
					}
				} else {
					childRange = 1d;
					if (!boundingConstraints.containsKey(childIndex)) {
						addBoundingConstraint(childIndex, 0, 1);
					}
				}
				if (childRange > parentRange) {
					// uc-(UBc-LBc+1)*up<=0
					listTerm = new LinkedList<Term>();
					listTerm.add(new Term(1d, childIndex));
					listTerm.add(new Term((-1) * (childRange + 1), parentIndex));
				} else {
					// uc-up<=0
					listTerm = new LinkedList<Term>();
					listTerm.add(new Term(1d, childIndex));
					listTerm.add(new Term(-1d, parentIndex));
				}
				constraints.add(new LinearConstraint(listTerm, 0, Comparator.LowerOrEqual));
			}
		}

	}

	private void addNumericalConstraint(ExtendedFeatureModel efm) {
		List<NumericalConstraint> numericalConst = efm.getNumericalConstraints();
		List<Term> termList = new LinkedList<Term>();

		for (int i = 0; i < numericalConst.size(); i++) {
			Integer index_1 = getIndex(numericalConst.get(i).getFeature_1());
			Integer index_2 = getIndex(numericalConst.get(i).getFeature_2());
			// las constraints que tenemos que transformar son de la forma. f1 comparador f2
			// Analizamos que tipo de comparador tenemos
			switch (numericalConst.get(i).getComp()) {
			case LowerOrEqual:
				termList.add(new Term(1d, index_1));
				termList.add(new Term(-1d, index_2));
				constraints.add(new LinearConstraint(termList, 0, Comparator.LowerOrEqual));
				break;
			case Lower:
				termList.add(new Term(1d, index_1));
				termList.add(new Term(-1d, index_2));
				constraints.add(new LinearConstraint(termList, -1, Comparator.LowerOrEqual));
				break;
			case Equal:
				termList.add(new Term(1d, index_1));
				termList.add(new Term(-1d, index_2));
				equalityConstraints.add(new LinearConstraint(termList, 0, Comparator.Equal));
				break;
			case HigherOrEqual:
				termList.add(new Term(-1d, index_1));
				termList.add(new Term(1d, index_2));
				constraints.add(new LinearConstraint(termList, 0, Comparator.LowerOrEqual));
				break;
			case Higher:
				termList.add(new Term(1d, index_1));
				termList.add(new Term(-1d, index_2));
				constraints.add(new LinearConstraint(termList, -1, Comparator.LowerOrEqual));
				break;
			}
		}
	}

	private void addCrosstreeConstraint(ExtendedFeatureModel efm) {
		List<CrossTreeConstraint> crossTreeList = efm.getCrossTreeConstraints();
		List<Term> termList = new LinkedList<Term>();
		for (int i = 0; i < crossTreeList.size(); i++) {
			// tenemos que cambiarles el signo.
			IntArrayList positive = crossTreeList.get(i).getPositiveFeatures();
			for (int p = 0; p < positive.size(); p++) {
				int index = getIndex(positive.get(p));
				termList.add(new Term(-1d, index));
			}
			IntArrayList negative = crossTreeList.get(i).getNegativeFeatures();
			for (int n = 0; n < negative.size(); n++) {
				int index = getIndex(negative.get(n));
				termList.add(new Term(-1d, index));
			}
			constraints.add(new LinearConstraint(termList, 1, Comparator.LowerOrEqual));
			termList = new LinkedList<Term>();
		}
	}

	private void addBoundingConstraint(int index, int lower, int upper) {
		// System.out.println("2: Se añade el bounding " + lower + " y " + upper + "
		// para" + index);
		boundingConstraints.put(index, new Bounding(lower, upper));
	}

	private void addGroupConstraint(ExtendedFeatureModel efm, IntOpenHashSet group, int type) {
		// System.out.println("Se llama a addGroupConstraint");
		List<Integer> groupVariables = new LinkedList<Integer>();
		int[] keys = group.keys;
		boolean[] allocated = group.allocated;
		// obtenemos los índices de todas las características que formarán parte de la
		// restricción.
		for (int i = 0; i < allocated.length; i++) {
			if (allocated[i]) {

				int uIndex = getIndex(keys[i]);
				if (!boundingConstraints.containsKey(uIndex)) {
					addBoundingConstraint(uIndex, 0, 1);
				}
				List<Term> termList = new LinkedList<Term>();
				if (efm.isNumerical(keys[i])) {
					// se añade una variable ficticia
					int zIndex = getIndex(efm.getName(keys[i]) + "_z");
					groupVariables.add(zIndex);
					// se añade una constraint que relaciona la variable ficticia con la real.
					// real-M*ficticia<=0
					termList = new LinkedList<Term>();
					termList.add(new Term(1d, keys[i]));
					termList.add(new Term(-bigM, zIndex));
					LinearConstraint lc = new LinearConstraint(termList, 0, Comparator.LowerOrEqual);
					constraints.add(lc);
					// se añaden los boundings de la variabiles ficticia
					// 0 <= z <= bigM
					if (!boundingConstraints.containsKey(zIndex))
						addBoundingConstraint(zIndex, 0, bigM.intValue());
				} else {
					groupVariables.add(uIndex);
				}
			}
		}
		// en variables tenemos los índices de las variables que forman la constraint
		// grupal
		//
		List<Term> termN = new LinkedList<Term>();
		List<Term> termM = new LinkedList<Term>();
		for (int i = 0; i < groupVariables.size(); i++) {
			termN.add(new Term(-1d, groupVariables.get(i)));
			termM.add(new Term(1d, groupVariables.get(i)));
		}
		// se añade el padre a la constraint
		int parentID = efm.getParent((Integer) mIDToName.get(groupVariables.get(0)));
		int parentIndex = getIndex(parentID);
		if (efm.isNumerical(parentID)) {
			if (!boundingConstraints.containsKey(parentIndex)) {
				addBoundingConstraints(efm, parentIndex, parentID);
			}
		} else {
			if (!boundingConstraints.containsKey(parentIndex)) {
				addBoundingConstraint(parentIndex, 0, 1);
			}
		}
		Double upperBound;
		if (type == FeatureModel.TYPE_OR) {
			upperBound = Double.parseDouble(groupVariables.size() + "");
		} else {
			upperBound = 1d;
		}
		// no tengo claro que la numérica esté bien modelada.
		if (efm.isNumerical(parentID)) {
			// se añade una variable ficticia
			// System.out.println("SE crea una variable ficticia B");
			int zIndex = getIndex(efm.getName(parentID) + "_z");
			groupVariables.add(zIndex);
			// se añade la restricción que relaciona la variable ficticia con la real
			// asociada.
			List<Term> termList = new LinkedList<Term>();
			termList.add(new Term(1d, parentIndex));
			termList.add(new Term(-bigM, zIndex));
			constraints.add(new LinearConstraint(termList, 0, Comparator.LowerOrEqual));
			// se añade la variable a la lista de las restricciones de grupo.
			termM.add(new Term(-1 * upperBound, zIndex));
		} else {
			termM.add(new Term(-1 * upperBound, parentIndex));
			termN.add(new Term(1d, parentIndex));
		}

		// sum u_i >= n
		LinearConstraint lcN = new LinearConstraint(termN, 0, Comparator.LowerOrEqual);
		constraints.add(lcN);
		LinearConstraint lcM = new LinearConstraint(termM, 0, Comparator.LowerOrEqual);
		constraints.add(lcM);
		/*
		 * System.out.println("Se modela la group constraint con: ");
		 * System.out.println("Constraint n:"+lcN.toString());
		 * System.out.println("Constraint m:"+lcM.toString());
		 */
	}

	// no sé si esto lo debería de añadir en su propia estructura
	private void addBoundingConstraints(ExtendedFeatureModel efm, Integer featureIndex, Integer featureID) {
		// se obtienen los boundings
		Bounding bound = efm.getBounding(featureID);
		int upperBound = bound.getMax();
		if (bound.getMin() != 0) {
			upperBound = bound.getMax() - bound.getMin() + 1;
		}
		// con nueva estructura
		boundingConstraints.put(featureIndex, new Bounding(0, upperBound));
	}

	private void addDependencies(IStarModel ism) {
		Integer dependerEle, dependum, dependeeEle;

		for (int i = 0; i < ism.getDependencyList().size(); i++) {
			Dependency dep = ism.getDependencyList().get(i);
			// se asignan los índices
			// depender element
			dependerEle = getIndex(dep.getDependerEle().getId());
			dependeeEle = getIndex(dep.getDependeeEle().getId());
			dependum = getIndex(dep.getDependum().getId());
			// se crean las constraints
			List<Term> termList = new LinkedList<Term>();
			termList.add(new Term(1d, dependerEle));
			termList.add(new Term(-1d, dependum));
			LinearConstraint lc1 = new LinearConstraint(termList, 0, Comparator.LowerOrEqual);
			// System.out.println("LC1: "+lc1.toString());
			constraints.add(lc1);
			termList = new LinkedList<Term>();
			termList.add(new Term(1d, dependum));
			termList.add(new Term(-1d, dependeeEle));
			LinearConstraint lc2 = new LinearConstraint(termList, 0, Comparator.LowerOrEqual);
			// System.out.println("LC2: "+lc2.toString());
			constraints.add(lc2);
			// si los elementos no están en la lista de boudings se actualizan
			if (!boundingConstraints.containsKey(dependerEle)) {
				addBoundingConstraint(dependerEle, 0, 100);
			}
			if (!boundingConstraints.containsKey(dependeeEle)) {
				addBoundingConstraint(dependeeEle, 0, 100);
			}
			if (!boundingConstraints.containsKey(dependum)) {
				addBoundingConstraint(dependum, 0, 100);
			}
		}
	}

	private void addActorConstraint(Actor act) {
		// se obtienen los elementos ra�ces
		Iterator<String> keyIterator = act.getWants().keySet().iterator();
		List<IntentionalElement> rootElements = new LinkedList<IntentionalElement>();
		while (keyIterator.hasNext()) {
			IntentionalElement ie = act.getWants().get(keyIterator.next());
			if (act.isRootElement(ie.getId())) {
				rootElements.add(ie);
			}
		}
		// se genera el peso
		Double weight = -1d / rootElements.size();
		List<Term> termList = new LinkedList<Term>();
		// se obtiene el indice del agente
		int actorIndex = getIndex(act.getId());
		if (!boundingConstraints.containsKey(actorIndex)) {
			addBoundingConstraint(actorIndex, 0, 100);
		}
		termList.add(new Term(1d, actorIndex));
		for (int i = 0; i < rootElements.size(); i++) {
			int index = getIndex(rootElements.get(i).getId());
			if (!boundingConstraints.containsKey(index)) {
				addBoundingConstraint(index, 0, 100);
			}
			termList.add(new Term(weight, index));
		}
		LinearConstraint actorConstraint = new LinearConstraint(termList, 0, Comparator.Equal);
		// System.out.println(act.getText()+":"+ actorConstraint.toString());
		equalityConstraints.add(actorConstraint);
	}

	private Double getMaxQuality(List<Term> termList) {
		Double res = 0d;
		//System.out.println("termList.size() " + termList.size());
		for (int i = 0; i < termList.size(); i++) {
			res = res + Math.abs(termList.get(i).getWeight()) * 100;
		}
		return res;
	}

	private void addContributions(Actor act) {
		Iterator<String> keyContIterator = act.getContributionList().keySet().iterator();
		List<LinearConstraint> res = new LinkedList<LinearConstraint>();
		List<Term> termList = new LinkedList<Term>();
		while (keyContIterator.hasNext()) {
			Contribution contribution = act.getContributionList().get(keyContIterator.next());
			// se busca el índice del objetivo contribución, si es que existe.
			int qualityIndex = getIndex(contribution.getTarget().getId());
			//System.out.println("Indice quality: " + qualityIndex + " " + contribution.getTarget().getText());
			if (!boundingConstraints.containsKey(qualityIndex)) {
				addBoundingConstraint(qualityIndex, 0, 100);
			}
			int index;
			// make
			List<IntentionalElement> contList = contribution.getMakeList();
			for (int i = 0; i < contList.size(); i++) {
				index = getIndex(contList.get(i).getId());
				if (!boundingConstraints.containsKey(index)) {
					addBoundingConstraint(index, 0, 100);
				}
				termList.add(new Term(-1d, index));
			}
			// help
			contList = contribution.getHelpList();
			for (int i = 0; i < contList.size(); i++) {
				index = getIndex(contList.get(i).getId());
				if (!boundingConstraints.containsKey(index)) {
					addBoundingConstraint(index, 0, 100);
				}
				termList.add(new Term(-0.75d, index));
			}
			// hurt
			contList = contribution.getHurtList();
			for (int i = 0; i < contList.size(); i++) {
				index = getIndex(contList.get(i).getId());
				if (!boundingConstraints.containsKey(index)) {
					addBoundingConstraint(index, 0, 100);
				}
				termList.add(new Term(-0.5d, index));
			}
			// normalización de valores
			Double maxQuality = getMaxQuality(termList);
			//System.out.println(maxQuality);
			Double factor = 100 / maxQuality;
			for (int i = 0; i < termList.size(); i++) {
				termList.get(i).setWeight(termList.get(i).getWeight() * factor);
			}
			termList.add(new Term(1d, qualityIndex));
			LinearConstraint toAdd = new LinearConstraint(termList, 0, Comparator.Equal);
			res.add(toAdd);
			termList = new LinkedList<Term>();
		}
		equalityConstraints.addAll(res);
	}

	private void addContributions2(Actor act) {
		Iterator<String> keyContIterator = act.getContributionList().keySet().iterator();
		List<LinearConstraint> res = new LinkedList<LinearConstraint>();
		List<Term> termList = new LinkedList<Term>();
		while (keyContIterator.hasNext()) {
			Contribution contribution = act.getContributionList().get(keyContIterator.next());
			// se busca el índice del objetivo contribución, si es que existe.
			int qualityIndex = getIndex(contribution.getTarget().getId());
			System.out.println("Indice quality: " + qualityIndex + " " + contribution.getTarget().getText());
			if (!boundingConstraints.containsKey(qualityIndex)) {
				addBoundingConstraint(qualityIndex, 0, 100);
			}
			// se crea la variable falsa Q
			IntentionalElement fakeQ = new IntentionalElement(qualityIndex + "_fake_Q", "fake_Q_" + qualityIndex);
			int index = getIndex(fakeQ.getId());
			System.out.println("Indice de q: " + index);
			if (!boundingConstraints.containsKey(index)) {
				addBoundingConstraint(index, 0, Integer.MAX_VALUE);
			}
			// voy por aquí-> ahora no funciona con esto
			// se crean las restricciones para modelar quality=min(100,fakeQ)
			List<LinearConstraint> min100Constraints = getMin100Constraint(qualityIndex, index);
			constraints.addAll(min100Constraints);

			termList.add(new Term(1d, index));
			// make
			List<IntentionalElement> contList = contribution.getMakeList();
			for (int i = 0; i < contList.size(); i++) {
				index = getIndex(contList.get(i).getId());
				if (!boundingConstraints.containsKey(index)) {
					addBoundingConstraint(index, 0, 100);
				}
				termList.add(new Term(-1d, index));
			}
			// help
			contList = contribution.getHelpList();
			for (int i = 0; i < contList.size(); i++) {
				index = getIndex(contList.get(i).getId());
				if (!boundingConstraints.containsKey(index)) {
					addBoundingConstraint(index, 0, 100);
				}
				termList.add(new Term(-0.75d, index));
			}
			// hurt
			contList = contribution.getHurtList();
			for (int i = 0; i < contList.size(); i++) {
				index = getIndex(contList.get(i).getId());
				if (!boundingConstraints.containsKey(index)) {
					addBoundingConstraint(index, 0, 100);
				}
				termList.add(new Term(-0.5d, index));
			}
			LinearConstraint toAdd = new LinearConstraint(termList, 0, Comparator.Equal);
			res.add(toAdd);
			termList = new LinkedList<Term>();
		}
		equalityConstraints.addAll(res);
	}

	private List<LinearConstraint> getMin100Constraint(int xIndex, int qIndex) {
		List<LinearConstraint> res = new LinkedList<LinearConstraint>();
		Random rnd = new Random(10000);
		// se crea la variable ficticia y
		IntentionalElement fakeY = new IntentionalElement(xIndex + "_fake_y" + rnd.nextInt(), "fake_y_" + xIndex);
		int yIndex = getIndex(fakeY.getId());
		if (!boundingConstraints.containsKey(yIndex))
			addBoundingConstraint(yIndex, 0, 1);
		// -My-q<=-100
		List<Term> terms = new LinkedList<Term>();
		terms.add(new Term(-bigM, yIndex));
		terms.add(new Term(-1d, qIndex));
		res.add(new LinearConstraint(terms, -100, Comparator.LowerOrEqual));
		// My+q<=M
		terms = new LinkedList<Term>();
		terms.add(new Term(bigM, yIndex));
		terms.add(new Term(1d, qIndex));
		res.add(new LinearConstraint(terms, bigM.intValue(), Comparator.LowerOrEqual));
		// x-q<=0
		terms = new LinkedList<Term>();
		terms.add(new Term(1d, xIndex));
		terms.add(new Term(-1d, qIndex));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		// -x+q+My<=M
		terms = new LinkedList<Term>();
		terms.add(new Term(-1d, xIndex));
		terms.add(new Term(1d, qIndex));
		terms.add(new Term(bigM, yIndex));
		res.add(new LinearConstraint(terms, bigM.intValue(), Comparator.LowerOrEqual));
		// -x-My<=-100
		terms = new LinkedList<Term>();
		terms.add(new Term(-1d, xIndex));
		terms.add(new Term(-bigM, yIndex));
		res.add(new LinearConstraint(terms, -100, Comparator.LowerOrEqual));
		/*
		 * for(int i=0;i<res.size();i++) { System.out.println(res.get(i)); }
		 */
		return res;
	}

	private void addRefinements(Actor act) {
		Iterator<Refinement> refinementIterator = act.getRefinementList().values().iterator();
		// System.out.println("Refinements para "+act.getText());
		List<LinearConstraint> aux;

		while (refinementIterator.hasNext()) {
			Refinement ref = refinementIterator.next();
			if (!ref.getAndRefinements().isEmpty()) {
				List<GoalTaskElement> children = ref.getAndRefinements();
				// se consigue el identificador del primer elemento
				Integer x_1Index = getIndex(children.get(0).getId());
				if (!boundingConstraints.containsKey(x_1Index)) {
					addBoundingConstraint(x_1Index, 0, 100);
				}
				for (int i = 0; i < children.size() - 1; i++) {
					IntentionalElement alfa = new IntentionalElement(ref.getParent().getId() + "_alfa_" + i, "alfa");
					IntentionalElement fakeY = new IntentionalElement(ref.getParent().getId() + "_y_" + i, "alfa");
					// se añaden a la lista de identificadores
					int alfaIndex = getIndex(alfa.getId());
					if (!boundingConstraints.containsKey(alfaIndex)) {
						addBoundingConstraint(alfaIndex, 0, 100);
					}
					int fakeYIndex = getIndex(fakeY.getId());
					// se mete en la lista de variables binarias
					addBoundingConstraint(fakeYIndex, 0, 1);
					// cogemos el índice de la siguiente variable
					GoalTaskElement x2 = children.get(i + 1);
					int x2Index = getIndex(x2.getId());
					if (!boundingConstraints.containsKey(x2Index)) {
						addBoundingConstraint(x2Index, 0, 100);
					}
					// se añaden las constraints al problema
					aux = getAndConstraint(alfaIndex, fakeYIndex, x_1Index, x2Index);
					constraints.addAll(aux);
					// se asigna alfa al al siguiente x1
					x_1Index = alfaIndex;
					// for(int j=0;j<aux.size();j++) { System.out.println(aux.get(j).toString()); }
				}
				// se a�aden las restricciones asociadas a la variable real
				IntentionalElement fakeY = new IntentionalElement(ref.getParent().getId() + "_y_last", "alfa");
				int fakeYIndex = getIndex(fakeY.getId());
				// se mete en la lista de variables binarias
				addBoundingConstraint(fakeYIndex, 0, 1);
				int xIndex = getIndex(ref.getParent().getId());
				int x_2Index = getIndex(children.get(children.size() - 1).getId());
				if (!boundingConstraints.containsKey(xIndex)) {
					addBoundingConstraint(xIndex, 0, 100);
				}
				if (!boundingConstraints.containsKey(x_2Index)) {
					addBoundingConstraint(x_2Index, 0, 100);
				}
				aux = getAndConstraint(xIndex, fakeYIndex, x_1Index, x_2Index);

				// for(int j=0;j<aux.size();j++) { System.out.println(aux.get(j).toString()); }

				constraints.addAll(aux);
				// System.out.println("**********************************");
			} else {
				List<GoalTaskElement> children = ref.getOrRefinements();
				int x1Index = getIndex(children.get(0).getId());
				if (!boundingConstraints.containsKey(x1Index)) {
					addBoundingConstraint(x1Index, 0, 100);
				}
				for (int i = 0; i < children.size() - 1; i++) {
					IntentionalElement alfa = new IntentionalElement(ref.getParent().getId() + "_alfa_" + i, "alfa");
					IntentionalElement fakeY = new IntentionalElement(ref.getParent().getId() + "_y_" + i, "alfa");
					// se añaden a la lista de identificadores
					int alfaIndex = getIndex(alfa.getId());
					if (!boundingConstraints.containsKey(alfaIndex)) {
						addBoundingConstraint(alfaIndex, 0, 100);
					}
					int fakeYIndex = getIndex(fakeY.getId());
					// se mete en la lista de variables binarias
					addBoundingConstraint(fakeYIndex, 0, 1);
					// cogemos el índice de la siguiente variable
					GoalTaskElement x2 = children.get(i + 1);
					int x2Index = getIndex(x2.getId());
					if (!boundingConstraints.containsKey(x2Index)) {
						addBoundingConstraint(x2Index, 0, 100);
					}
					// se añaden las constraints al problema
					aux = getOrConstraints(alfaIndex, fakeYIndex, x1Index, x2Index);
					/*
					 * for(int j=0;j<aux.size();j++) { System.out.println(aux.get(j).toString()); }
					 */
					constraints.addAll(aux);
					// se asigna alfa al al siguiente x1
					x1Index = alfaIndex;
				}
				// se a�aden las restricciones asociadas a la variable real
				IntentionalElement fakeY = new IntentionalElement(ref.getParent().getId() + "_y_last", "alfa");
				int fakeYIndex = getIndex(fakeY.getId());
				// se mete en la lista de variables binarias
				addBoundingConstraint(fakeYIndex, 0, 1);
				int xIndex = getIndex(ref.getParent().getId());
				int x2Index = getIndex(children.get(children.size() - 1).getId());

				if (!boundingConstraints.containsKey(xIndex)) {
					addBoundingConstraint(xIndex, 0, 100);
				}
				if (!boundingConstraints.containsKey(x2Index)) {
					addBoundingConstraint(x2Index, 0, 100);
				}
				aux = getOrConstraints(xIndex, fakeYIndex, x1Index, x2Index);
				constraints.addAll(aux);
				/*
				 * for(int j=0;j<aux.size();j++) { System.out.println(aux.get(j).toString()); }
				 * System.out.println("**********************************");
				 */
			}
		}
	}

	private List<LinearConstraint> getAndConstraint(int alfaIndex, int yIndex, int x1Index, int x2Index) {
		List<LinearConstraint> res = new LinkedList<LinearConstraint>();
		// System.out.println("ANDConstraint indexes: "+alfaIndex+" "+yIndex+"
		// "+x1Index+" "+x2Index);
		// generamos las restricciones.
		// R1: -x1+x2-My<=0
		List<Term> terms = new LinkedList<Term>();
		terms.add(new Term(-1d, x1Index));
		terms.add(new Term(1d, x2Index));
		terms.add(new Term(-bigM, yIndex));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		// R2: x1-x2+My<=M
		terms = new LinkedList<Term>();
		terms.add(new Term(1d, x1Index));
		terms.add(new Term(-1d, x2Index));
		terms.add(new Term(bigM, yIndex));
		res.add(new LinearConstraint(terms, bigM.intValue(), Comparator.LowerOrEqual));
		// R3: alfa-x1 <=0
		terms = new LinkedList<Term>();
		terms.add(new Term(1d, alfaIndex));
		terms.add(new Term(-1d, x1Index));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		// R4: alfa-x2<=0
		terms = new LinkedList<Term>();
		terms.add(new Term(1d, alfaIndex));
		terms.add(new Term(-1d, x2Index));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		// R5:-alfa+x1+My<=M
		terms = new LinkedList<Term>();
		terms.add(new Term(-1d, alfaIndex));
		terms.add(new Term(1d, x1Index));
		terms.add(new Term(bigM, yIndex));
		res.add(new LinearConstraint(terms, bigM.intValue(), Comparator.LowerOrEqual));
		// R6: -alfa+x2-My<=0
		terms = new LinkedList<Term>();
		terms.add(new Term(-1d, alfaIndex));
		terms.add(new Term(1d, x2Index));
		terms.add(new Term(-bigM, yIndex));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		return res;
	}

	private List<LinearConstraint> getOrConstraints(int alfaIndex, int yIndex, int x1Index, int x2Index) {
		List<LinearConstraint> res = new LinkedList<LinearConstraint>();
		// System.out.println("ORConstraint indexes: "+alfaIndex+" "+yIndex+"
		// "+x1Index+" "+x2Index);
		// generamos las restricciones.
		// R1: x1-x2-My<=0
		List<Term> terms = new LinkedList<Term>();
		terms.add(new Term(1d, x1Index));
		terms.add(new Term(-1d, x2Index));
		terms.add(new Term(-bigM, yIndex));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		// R2: -x1+x2+My<=M
		terms = new LinkedList<Term>();
		terms.add(new Term(-1d, x1Index));
		terms.add(new Term(1d, x2Index));
		terms.add(new Term(bigM, yIndex));
		res.add(new LinearConstraint(terms, bigM.intValue(), Comparator.LowerOrEqual));
		// R3: -alfa+x1 <=0
		terms = new LinkedList<Term>();
		terms.add(new Term(-1d, alfaIndex));
		terms.add(new Term(1d, x1Index));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		// R4: -alfa+x2<=0
		terms = new LinkedList<Term>();
		terms.add(new Term(-1d, alfaIndex));
		terms.add(new Term(1d, x2Index));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		// R5:alfa-x1+My<=M
		terms = new LinkedList<Term>();
		terms.add(new Term(1d, alfaIndex));
		terms.add(new Term(-1d, x1Index));
		terms.add(new Term(bigM, yIndex));
		res.add(new LinearConstraint(terms, bigM.intValue(), Comparator.LowerOrEqual));
		// R6: alfa-x2-My<=0
		terms = new LinkedList<Term>();
		terms.add(new Term(1d, alfaIndex));
		terms.add(new Term(-1d, x2Index));
		terms.add(new Term(-bigM, yIndex));
		res.add(new LinearConstraint(terms, 0, Comparator.LowerOrEqual));
		return res;
	}

	public void generateIntLinProg(String fileName) {
		try {
			// System.out.println("Vamos a imprimir "+objectiveFunction.toString());
			PrintWriter pw = new PrintWriter(new FileWriter(fileName + ".m"));
			pw.println("% This is a automatically generated file");
			printObjectiveFunction(pw);
			// especificamos que todas las variables son enteras.
			String intConString = "intcon = [";
			for (int i = 0; i < variables.size(); i++) {
				intConString = intConString + (i + 1) + ",";
			}
			// se modifica el final de la cadena
			intConString = intConString.substring(0, intConString.length() - 1) + "];";
			pw.println(intConString);
			printConstraints(pw, true);
			printConstraints(pw, false);
			printBoundings(pw);
			pw.println("x = intlinprog(f,intcon,A,b,Aeq,beq,lb,ub);");
			pw.println("writematrix(x,\""+fileName+"-res.txt\")");
			pw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void printBoundings(PrintWriter pw) {
		pw.println("lb = zeros(" + variables.size() + ",1);");
		String upperBoundString = "ub = [";
		for (int i = 0; i < variables.size(); i++) {
			Bounding bounding = boundingConstraints.get(variables.get(i));
			if (bounding == null) {
				System.out.println("Null bouding for variable " + variables.get(i));
			}
			int upperBound = bounding.getMax();
			upperBoundString = upperBoundString + upperBound + ";";
		}
		upperBoundString = upperBoundString.substring(0, upperBoundString.length() - 1) + "];";
		pw.println(upperBoundString);
	}

	private void printConstraints(PrintWriter pw, Boolean inequality) {
		String aVector, bVector;
		List<LinearConstraint> toPrint;
		if (inequality) {
			aVector = "A = [";
			bVector = "b = [";
			toPrint = constraints;
		} else {
			aVector = "Aeq = [";
			bVector = "beq = [";
			toPrint = equalityConstraints;
		}
		for (int pIndex = 0; pIndex < toPrint.size(); pIndex++) {
			for (int vIndex = 0; vIndex < variables.size(); vIndex++) {
				if (toPrint.get(pIndex).containsIndex(variables.get(vIndex))) {
					// se imprime el peso
					aVector = aVector + toPrint.get(pIndex).getWeight(variables.get(vIndex)) + ",";
					// System.out.println("Se añade a A
					// "+toPrint.get(pIndex).getWeight(variables.get(vIndex)));
				} else {
					// 0
					aVector = aVector + "0,";
					// System.out.println("Se añade un 0");
				}
			}
			aVector = aVector.substring(0, aVector.length() - 1) + ";";
			bVector = bVector + toPrint.get(pIndex).getConstant() + ";";
		}
		aVector = aVector.substring(0, aVector.length() - 1) + "];";
		bVector = bVector.substring(0, bVector.length() - 1) + "];";
		pw.println(aVector);
		pw.println(bVector);
	}

	// los problemas de intlinprog son de minimización, por lo tanto la función
	// objetivos
	// tiene que estar multiplicada por -1.
	private void printObjectiveFunction(PrintWriter pw) {
		String objFuncString = "f = [";
		for (int i = 0; i < variables.size(); i++) {
			if (objectiveFunction.containsIndex(variables.get(i))) {
				objFuncString = objFuncString + objectiveFunction.getWeight(variables.get(i)) * (-1) + ";";
			} else {
				objFuncString = objFuncString + "0;";
			}
		}
		// modificamos el final de la cadena
		objFuncString = objFuncString.substring(0, objFuncString.length() - 1) + "];";
		//System.out.println(objFuncString);
		pw.println(objFuncString);
	}

	public void readResults(String file) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String texto = br.readLine();
			// cada posición de la lista resList se corresponde al valor de la variable
			// guardada en esa posición.
			HashMap<Integer, Integer> resList = new HashMap<Integer, Integer>();// <Index,Value>
			int index = 0;
			while (texto != null && index < variables.size()) {
				if (texto.startsWith("-")) {
					texto = texto.substring(1);
				}
				Double number = Double.parseDouble(texto);
				resList.put(variables.get(index), number.intValue());
				// System.out.println(variables.get(i)+":"+texto);
				texto = br.readLine();
				index++;
			}
			for (int j = 0; j < variables.size(); j++) {
				// Se extrae el nombre del objeto en su estructura.
				Object name = mIDToName.get(variables.get(j));
				if (name instanceof String) {
					// se obtiene el elemento intencional
					IStarEntity entity = goalModel.getEntity((String) name);
					if (entity != null) {
						System.out.println(
								entity.getText() + "(" + variables.get(j) + ")" + ":" + resList.get(variables.get(j)));
					}
				} else {
					String featureName = featureModel.getName((Integer) name);
					if (featureName != null) {
						System.out.println(
								featureName + "(" + variables.get(j) + ")" + ":" + resList.get(variables.get(j)));
					} else {
						System.out.println("Index de la variable ficticia " + variables.get(j) + " y nombre "
								+ mIDToName.get(variables.get(j)));

					}
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// Devuelve el nivel de satisfacción del elemento intencional identificado con
	// el String
	private Map<String, Integer> getGoalModelRes(String file) {
		HashMap<String, Integer> res = new HashMap<String, Integer>();
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(file));
			String texto = br.readLine();
			// cada posición de la lista resList se corresponde al valor de la variable
			// guardada en esa posición.
			HashMap<Integer, Integer> resList = new HashMap<Integer, Integer>();// <Index,Value>
			int index = 0;
			while (texto != null && index < variables.size()) {
				if (texto.startsWith("-")) {
					texto = texto.substring(1);
				}
				Double number = Double.parseDouble(texto);
				resList.put(variables.get(index), number.intValue());
				// System.out.println(variables.get(i)+":"+texto);
				texto = br.readLine();
				index++;
			}
			for (int j = 0; j < variables.size(); j++) {
				// Se extrae el nombre del objeto en su estructura.
				Object name = mIDToName.get(variables.get(j));
				if (name instanceof String) {
					// se obtiene el elemento intencional
					IStarEntity entity = goalModel.getEntity((String) name);
					if (entity != null) {
						res.put(entity.getId(), resList.get(variables.get(j)));
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}
	
	public void writeConfigurationToFile(String resFile,String conFile) {
		try {
			PrintWriter printer=new PrintWriter(conFile);
			BufferedReader br = new BufferedReader(new FileReader(resFile));
			String texto = br.readLine();
			// cada posición de la lista resList se corresponde al valor de la variable
			// guardada en esa posición.
			HashMap<Integer, Integer> resList = new HashMap<Integer, Integer>();// <Index,Value>
			int index = 0;
			while (texto != null && index < variables.size()) {
				if (texto.startsWith("-")) {
					texto = texto.substring(1);
				}
				Double number = Double.parseDouble(texto);
				resList.put(variables.get(index), number.intValue());
				// System.out.println(variables.get(i)+":"+texto);
				texto = br.readLine();
				index++;
			}
			for (int j = 0; j < variables.size(); j++) {
				// Se extrae el nombre del objeto en su estructura.
				Object name = mIDToName.get(variables.get(j));
				if (name instanceof String) {
					// se obtiene el elemento intencional
					/*IStarEntity entity = goalModel.getEntity((String) name);
					if (entity != null) {
						System.out.println(
								entity.getText() + "(" + variables.get(j) + ")" + ":" + resList.get(variables.get(j)));
					}*/
				} else {
					String featureName = featureModel.getName((Integer) name);
					if (featureName != null) {
						printer.println(
								featureName + "(" + variables.get(j) + ")" + ":" + resList.get(variables.get(j)));
					} else {
						printer.println("Index de la variable ficticia " + variables.get(j) + " y nombre "
								+ mIDToName.get(variables.get(j)));

					}
				}
			}
			br.close();
			printer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void readConfiguration(String resFile) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(resFile));
			String texto = br.readLine();
			// cada posición de la lista resList se corresponde al valor de la variable
			// guardada en esa posición.
			HashMap<Integer, Integer> resList = new HashMap<Integer, Integer>();// <Index,Value>
			int index = 0;
			while (texto != null && index < variables.size()) {
				if (texto.startsWith("-")) {
					texto = texto.substring(1);
				}
				Double number = Double.parseDouble(texto);
				resList.put(variables.get(index), number.intValue());
				// System.out.println(variables.get(i)+":"+texto);
				texto = br.readLine();
				index++;
			}
			for (int j = 0; j < variables.size(); j++) {
				// Se extrae el nombre del objeto en su estructura.
				Object name = mIDToName.get(variables.get(j));
				if (name instanceof String) {
					// se obtiene el elemento intencional
					/*IStarEntity entity = goalModel.getEntity((String) name);
					if (entity != null) {
						System.out.println(
								entity.getText() + "(" + variables.get(j) + ")" + ":" + resList.get(variables.get(j)));
					}*/
				} else {
					String featureName = featureModel.getName((Integer) name);
					if (featureName != null) {
						System.out.println(
								featureName + "(" + variables.get(j) + ")" + ":" + resList.get(variables.get(j)));
					} else {
						System.out.println("Index de la variable ficticia " + variables.get(j) + " y nombre "
								+ mIDToName.get(variables.get(j)));

					}
				}
			}
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void generateLabelledGoalModel(String resFile, String inputGoalModel, String labelledGoalModel) {
		Map<String, Integer> satLevelMap = getGoalModelRes(resFile);
		BufferedReader reader = null;
		PrintWriter pw = null;

		try {
			File inputFile = new File(inputGoalModel);
			reader = new BufferedReader(new FileReader(inputFile));
			pw = new PrintWriter(new FileWriter(new File(labelledGoalModel)));
			String line;
			Integer satLevel = null;
			while ((line = reader.readLine()) != null) {
				// se escribe la línea en el fichero de salida
				pw.println(line);
				if (line.contains("\"id\":")) {
					// obtenemos el identificador
					StringTokenizer tokenizer = new StringTokenizer(line);
					// el primer toke es "id":
					tokenizer.nextToken();
					// el segunto token es el id rodeado de comillas
					String id = tokenizer.nextToken();
					// le quitamos el caracter del principio y del final.ç
					id = id.substring(1, id.length() - 2);
					// hay registrado algún nivel de satisfacción para este elemento intecional
					if (satLevelMap.containsKey(id)) {
						satLevel = satLevelMap.get(id);
					} else {
						satLevel = null;
					}
				} else if (line.contains("\"customProperties\"") && satLevel != null) {
					line = reader.readLine();
					while (line.endsWith(",")) {
						pw.println(line);
						line = reader.readLine();
					}
					pw.println(line + ",");
					pw.println("\t\t    \"satLevel\": " + satLevel);
					// coloreamos el documento
				} else if (line.contains("display")) {
					modifyDisplayInformation(reader, pw, satLevelMap);
					// cuando el programa vuelve de esta rutina ha pintado la línea "tool":
					// "pistar.2.1.0",
				}

			}
			pw.println("");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null)
				try {
					reader.close();
					pw.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}

	private String generateColorLabel(Integer satLevel) {
		String res = "";
		if (satLevel == 0) {
			res = "\t \"backgroundColor\": \"#FA1805\"";// rojo
		} else if (satLevel > 0 && satLevel <= 25) {
			res = "\t \"backgroundColor\": \"#BC5722\"";// naranja oscuro
		} else if (satLevel > 25 && satLevel < 50) {
			res = "\t \"backgroundColor\": \"#FF762E\"";// naranja claro
		} else if (satLevel == 50) {
			res = "\t \"backgroundColor\": \"#FFFFFF\"";
		} else if (satLevel > 50 && satLevel <= 75) {
			res = "\t \"backgroundColor\": \"#F5FF9F\"";// amarillo claro
		} else if (satLevel > 75 && satLevel <= 99) {
			res = "\t \"backgroundColor\": \"#D8FF22\"";// amarillo oscuro
		} else {
			res = "\t \"backgroundColor\": \"#77FF12 \"";// verde
		}
		return res;
	}

	private void modifyDisplayInformation(BufferedReader reader, PrintWriter printer,
			Map<String, Integer> satLevelMap) {
		Integer satLevel = null;
		List<String> custElem = new LinkedList<String>();
		try {
			// lo primero que lee esta función es siempre un identificador
			String line = reader.readLine();
			String prevLine = new StringTokenizer(line).nextToken();
			while (!prevLine.equals("}") || !line.contains("},")) {
				if (line.contains("}")) {
					if (prevLine.contains("\"y\":")) {
						printer.println(line);
					} else {
						printer.println("\t },");
					}
				} else {
					if (!line.contains("backgroundColor"))
						printer.println(line);
				}
				if (line.contains(": {")) {
					// se extrae el identificador.
					StringTokenizer tokenizer = new StringTokenizer(line);
					String id = tokenizer.nextToken();
					id = id.substring(1, id.length() - 2);
					// ¿Hay un valor de satisfacción asociado a este id?
					if (satLevelMap.containsKey(id)) {
						custElem.add(id);
						satLevel = satLevelMap.get(id);
						printer.println(generateColorLabel(satLevel) + ",");
					}
				}
				prevLine = new StringTokenizer(line).nextToken();
				line = reader.readLine();
			}
			// hemos llegado al final de la sección display, pero aún no hemos escrito el
			// caracter que la cierra
			// añadimos los elementos de los que no hemos proporcionado información de
			// representación.
			List<String> keyList = new LinkedList<String>();
			keyList.addAll(satLevelMap.keySet());
			List<String> pendingKeys = new LinkedList<String>();// Lista de elementos que tenemos que colorear
			for (int i = 0; i < keyList.size(); i++) {
				if (!custElem.contains(keyList.get(i))) {
					pendingKeys.add(keyList.get(i));
				}
			}
			for (int i = 0; i < pendingKeys.size() - 1; i++) {
				printer.println('"' + pendingKeys.get(i) + '"' + ": {");
				// System.out.println(pendingKeys.get(i));
				printer.println(generateColorLabel(satLevelMap.get(pendingKeys.get(i))));
				printer.println("},");
			}
			// se imprime el último elemento, que tiene que tener un cierre sin ,
			printer.println('"' + pendingKeys.get(pendingKeys.size() - 1) + '"' + ": {");
			printer.println(generateColorLabel(satLevelMap.get(pendingKeys.get(pendingKeys.size() - 1))));
			printer.println("}");
			// se cierra la sección display
			printer.println("},");
			// se pinta la línea de tool y el antes de salir.
			line = reader.readLine();
			printer.println(line);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	// El nivel de satisfacción de un agente se mide como la suma ponderada de sus
	// elementos raíz
	public void setObjectiveFunctionForAgent(String id) {
		// 1. localizar al agente
		Actor actor = goalModel.getActor(id);
		// 2. Localizar el índice de la variable que representa su satisfacción en el
		// modelo
		Integer actorIndex = mNamesToID.get(actor.getId());
		List<Term> terms = new LinkedList<Term>();
		terms.add(new Term(1d, actorIndex));
		// 3. Generamos la función objetivo
		objectiveFunction = new ObjectiveFunction();
		objectiveFunction.setTerms(terms);
	}

	public void setEveryActorObjectiveFunction() {
		HashMap<String, Actor> actorList = goalModel.getActorList();
		Iterator<String> actorIterator = actorList.keySet().iterator();
		List<Integer> actorIndexList = new LinkedList<Integer>();

		while (actorIterator.hasNext()) {
			Actor actor = actorList.get(actorIterator.next());
			if (actor instanceof Agent) {
				// no hacemos nada...
			} else {
				Integer actorIndex = mNamesToID.get(actor.getId());
				actorIndexList.add(actorIndex);
			}
		}
		Double weight = (1d / actorIndexList.size());
		// se generan los términos
		List<Term> terms = new LinkedList<Term>();
		for (int i = 0; i < actorIndexList.size(); i++) {
			terms.add(new Term(weight, actorIndexList.get(i)));
		}
		// se genera la función objetivo
		objectiveFunction = new ObjectiveFunction();
		objectiveFunction.setTerms(terms);
		// System.out.println("Función objetivo: " + objectiveFunction.toString());
	}

	public Boolean addConstraint(String id, Integer value) {
		Boolean res = false;
		int index = -1;
		// buscamos el índice de la variable.
		// puede ser un identificador del modelo de objetivos o del feature model
		if (mNamesToID.containsKey(id)) {
			// es de un modelo de objetivos
			System.out.println(goalModel.getEntity(id).getText());
			index = mNamesToID.get(id);
		} else {
			// ¿Es un elemento del feature model?
			Integer featureModelIndex = featureModel.getID(id);
			if (featureModelIndex > 0) {
				index = mNamesToID.get(featureModelIndex);
			}
		}
		// se ha encontrado un identificador satisfactorio
		if (index > 0) {
			System.out.println("The index is: " + index);
			// comprobamos los boundings
			Bounding bound = boundingConstraints.get(index);
			if (value >= bound.getMin() && value <= bound.getMax()) {
				List<Term> termList = new LinkedList<Term>();
				termList.add(new Term(1d, index));
				equalityConstraints.add(new LinearConstraint(termList, value, Comparator.Equal));
				res = true;
			}
		}
		return res;
	}

}
