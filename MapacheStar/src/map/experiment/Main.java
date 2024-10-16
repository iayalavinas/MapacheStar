package map.experiment;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.carrotsearch.hppc.IntArrayList;

import map.ilp.ILP;
import map.ilp.MappingFunction;
import map.ilp.Term;
import map.parser.fm.SxEFMParser;
import map.parser.fm.SxfmParserException;
import map.parser.fm.extended.Comparator;
import map.parser.fm.extended.ExtendedFeatureModel;
import map.parser.fm.extended.NumericalConstraint;
import uma.istar.IStarModel;

public class Main {

	public static SxEFMParser parser;
	private static ILP problem;
	private static ExtendedFeatureModel featureModel;
	private static IStarModel goalModel;


	public static void main(String[] args) {
		String goalModelFile_paper = args[0];
		String featureModelFile_paper = args[1];
		loadExperiment(goalModelFile_paper, featureModelFile_paper);
		System.out.println(
				"This is a tool to illustrate the MapacheStar approach presented to Requirements Engineering Conference");
		System.out.println("What ILP problem do you want to generate?");
		System.out.println(
				"Optimisation for the user (1), optimisation for a group of users (2) or optimisation for the user and constraints (3).");
		System.out.println("You can type q to quit.");
		Scanner scanner = new Scanner(System.in);
		String input = scanner.nextLine();
		if (!input.startsWith("q")) {
			System.out.println("Open the generated file in Matlab and execute it.");
			if (input.startsWith("1")) {
				generateUser();
			} else if (input.startsWith("2")) {
				generateAllActors();
			} else {
				generateUserConstraints();
			}
		}
		System.out.println(
				"If you have obtained the results from Matlab, its time to visualize the results. What problem do you want to visualize?");
		System.out.println(
				"Optimisation for the user (1), optimisation for a group of users (2) or optimisation for the user and constraints (3).");
		System.out.println("You can type q to quit.");
		input = scanner.nextLine();
		if (!input.startsWith("q")) {
			System.out.println("Open the generated file in Matlab and execute it.");
			if (input.startsWith("1")) {
				visualizeResultsForUser(goalModelFile_paper);
			} else if (input.startsWith("2")) {
				visualizeResultsForAllActors(goalModelFile_paper);
			} else {
				visualizeResultsForUserConstraints(goalModelFile_paper);
			}
		}
	}

	private static void generateUser() {
		// probemos con distintas funciones objetivo
		problem.setObjectiveFunctionForAgent("84cf1520-22b2-45e2-9806-80f3c8286472");
		problem.generateIntLinProg("user");
		System.out.println(
				"We have generated user.m file. Takes the results from matlab typing in its console writematrix(x,'res-user.txt').");
	}

	private static void visualizeResultsForUser(String goalModelFile) {
		problem.generateLabelledGoalModel("res-user.txt", goalModelFile, "goalModel-user.txt");
		problem.writeConfigurationToFile("res-user.txt", "conf-user.txt");
		System.out.println("We have generated the files goalModel-user.txt (for piStar) and conf-user.txt");
		
	}

	private static void generateAllActors() {
		// función objetivo que pondera todos los usuarios
		problem.setEveryActorObjectiveFunction();
		problem.generateIntLinProg("everyActor");
		System.out.println(
				"We have generated everyActor.m file. Takes the results from matlab typing in its console writematrix(x,'res-everyagent.txt').");

	}

	private static void visualizeResultsForAllActors(String goalModelFile) {
		problem.generateLabelledGoalModel("res-everyagent.txt", goalModelFile, "goalModel-everyagent.txt");
		problem.writeConfigurationToFile("res-everyagent.txt", "conf-everyagent.txt");
		System.out.println("We have generated the files goalModel-everyagent.txt (for piStar) and conf-everyagent.txt");
	}

	private static void generateUserConstraints() {
		// función objetivo user y restricciones
		problem.setObjectiveFunctionForAgent("84cf1520-22b2-45e2-9806-80f3c8286472");// user
		problem.addConstraint("access_code_released", 3);
		problem.addConstraint("remote_control_granted", 3);
		problem.addConstraint("intelligent_key-chain_granted", 3);
		problem.addConstraint("_r_24", 1);// sentinel system
		problem.generateIntLinProg("userConstraint");
		System.out.println(
				"We have generated userConstraint.m file. Takes the results from matlab typing in its console writematrix(x,'res-userconstraint.txt').");
	}

	private static void visualizeResultsForUserConstraints(String goalModelFile) {
		problem.generateLabelledGoalModel("res-userconstraint.txt", goalModelFile, "goalModel-userconstraint.txt");
		problem.writeConfigurationToFile("res-userconstraint.txt", "conf-userconstraint.txt");
		System.out.println("We have generated the files goalModel-userconstraint.txt (for piStar) and conf-userconstraint.txt");
	}

	private static void loadExperiment(String goalModelFile, String featureModelFile) {
		try {
			featureModel = parser.parseToEFM(featureModelFile);
			// we add numerical features not supported by SPLOT
			// we need the ID of numercial features' ancestor
			int parentID = featureModel.getID("_r_11_12");
			featureModel.addFeature("Access_code_released", parentID, true, 0, 10);
			parentID = featureModel.getID("_r_11_13");
			featureModel.addFeature("Remote_control_granted", parentID, true, 0, 10);
			parentID = featureModel.getID("_r_11_14");
			featureModel.addFeature("Intelligent_key-chain_granted", parentID, true, 0, 10);
			parentID = featureModel.getID("_r_25");
			Integer windowsId = featureModel.addFeature("Windows", parentID, true, 0, 10);
			parentID = featureModel.getID("_r_25_29_32");
			Integer shockId = featureModel.addFeature("Number_of_shock_sensors", parentID, true, 0, 10);
			// we add the numerical constraint of the problem
			NumericalConstraint nc = new NumericalConstraint(windowsId, shockId, Comparator.LowerOrEqual);
			featureModel.addNumercialConstraint(nc);
			// we add additional crosstree constraints
			// ¬Infrarred V Photodetector
			IntArrayList positiveList = new IntArrayList();
			positiveList.add(featureModel.getID("_r_25_29_31"));
			IntArrayList negativeList = new IntArrayList();
			negativeList.add(featureModel.getID("_r_15_21_22"));
			featureModel.addCrossTreeConstraint(positiveList, negativeList);
			// ¬ Real Image V Internal Security Camera V External security Camera
			positiveList = new IntArrayList();
			positiveList.add(featureModel.getID("_r_25_26_27"));// external security camera
			positiveList.add(featureModel.getID("_r_25_29_30"));// internal security camera
			negativeList = new IntArrayList();
			negativeList.add(featureModel.getID("_r_15_21_23"));// real image
			featureModel.addCrossTreeConstraint(positiveList, negativeList);

			// loading of goal model.
			goalModel = new IStarModel();
			goalModel.parseModel(goalModelFile);

			// adding constrints from models
			problem = new ILP();
			problem.loadConstraints(goalModel);
			problem.loadConstraints(featureModel);

			// adding constraints from mapping function
			// ThreatsResolved = 100 * ThreatResolutionSystem
			MappingFunction mappingFunction = new MappingFunction();
			int index_tr = problem.getIndex("f21c9b6a-1775-489a-9b0f-955d5f2af4e2");
			Integer id = featureModel.getID("_r_15");
			int index_trs = problem.getIndex(id);
			mappingFunction.setEntity(index_tr);
			List<Term> listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, index_trs));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// access_code=10*access_code_released
			mappingFunction = new MappingFunction();
			int index_ac = problem.getIndex("92275226-a0bc-4cd7-bba2-37c66dca6e67");
			mappingFunction.setEntity(index_ac);
			id = featureModel.getID("access_code_released");
			int index_acr = problem.getIndex(id);
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(10d, index_acr));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// security camer=50*internal security camera+50*external security camera
			mappingFunction = new MappingFunction();
			int index_sc = problem.getIndex("722514cb-3230-4cc1-b7b5-884364f3b01a");
			mappingFunction.setEntity(index_sc);
			int index_esc = problem.getIndex(featureModel.getID("_r_25_26_27"));
			int index_isc = problem.getIndex(featureModel.getID("_r_25_29_30"));
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(50d, index_esc));
			listTerm.add(new Term(50d, index_isc));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Remote Control = 10*Remote Control Granted
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("90cd6f74-b1b8-4f7b-bbae-e84845f9282a"));// Remote Control
			int index_rcg = problem.getIndex(featureModel.getID("remote_control_granted"));
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(10d, index_rcg));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Intelligent_key_chain = 10*Intelligent_key_chain
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("7d86a9fa-d6af-4da8-9d46-180534f65196"));// Intelligent Key chain
			int index_ikg = problem.getIndex(featureModel.getID("intelligent_key-chain_granted"));
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(10d, index_ikg));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// block intruder vision system = 100 * block Intruder vision system
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("15d96075-8420-4484-98da-6dc65d88fd90"));
			int index_ivs = problem.getIndex(featureModel.getID("_r_7_8"));
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, index_ivs));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Intruder Notification System = 100*Intruder Notification System
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("8263f06a-bd0a-4fcc-a56f-86ac4fdc1d96"));// Intruder Notification
																								// system
			int index_ins = problem.getIndex(featureModel.getID("_r_7_9"));
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, index_ins));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// esta función hace que Matlab devuelva una solución que dice que no es
			// factible
			// Alarm System = 100*Alarm system
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("415d9fed-9428-458c-840e-5270ef90772e"));
			int index_as = problem.getIndex(featureModel.getID("_r_7_10"));
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, index_as));
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Threats resolved = 100*streaming + 75*video+50*picture
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("f21c9b6a-1775-489a-9b0f-955d5f2af4e2"));// threats resolved
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_15_17_18"))));// streaming
			listTerm.add(new Term(75d, problem.getIndex(featureModel.getID("_r_15_17_19"))));// video
			listTerm.add(new Term(50d, problem.getIndex(featureModel.getID("_r_15_17_20"))));// picture
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// photodetector = 100 * photodetector
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("efd191c0-1b99-409f-96cf-6d9d2b200426"));// "Photodetector"
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_25_29_31"))));// photo detector
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Infrarred Records = 100 * Infrarred
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("cd87c3e8-4f0f-4ccf-8ccf-1d656695827e"));// Infrarred records
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_15_21_22"))));// infrarred
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Camera records = 100 * real image
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("dcde2475-0026-488c-8cbd-d1f055b76797"));// Camera records
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_15_21_23"))));// real image
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Perimetral detector = 100 * perimetral detector
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("80965b68-84ab-43d7-a133-cf6c2ba61dc3"));// perimetral detector
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_25_26_28"))));// perimetral detector
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Shocksensor=10*number of shocksensor -> Si activamos esta la satisfacción es
			// de 50
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("d0353d9d-a5e6-4d12-a310-91e42b667447"));// shocksensor
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(10d, problem.getIndex(featureModel.getID("_r_25_29_32"))));// shock sensor
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Mobile App = 100 * Mobile App Call
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("8cd68352-718e-4ac1-9131-23aaaa9500a0"));// Mobile App
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_34_35"))));// mobile app call
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// Control Panel = 100 * control center call
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("a8eed54e-e87c-4f4c-90dd-35c8b1f6765f"));// control panel
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_34_27"))));// control center call
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// 3G connection = 100 * 3G
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("01c375db-fb95-4cd7-9ac8-9f4f829dca77"));// 3G connection
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_37_38"))));// 3G
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// GPRS connection = 100 * GSM
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("dfc2660b-eee9-4e48-a488-13c83e802b21"));// GPRS connection
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_37_41"))));// GPRS
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// GSM connection = 100 * GPRS
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("dfc2660b-eee9-4e48-a488-13c83e802b21"));// GSM connection
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_37_40"))));// GSM
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// To have sentinel system = 100 * Sentinel System
			mappingFunction = new MappingFunction();
			mappingFunction.setEntity(problem.getIndex("ad761097-9b7a-4441-8bb4-4283b7ff0f83"));// To have sentinel //
																								// // // system
			listTerm = new LinkedList<Term>();
			listTerm.add(new Term(100d, problem.getIndex(featureModel.getID("_r_24"))));// Sentinel System
			mappingFunction.setFeatures(listTerm);
			problem.loadConstraints(mappingFunction);

			// read results
			// System.out.println("Resultados para User");
			// problem.readResults("user-res.txt");
			

		} catch (ParserConfigurationException | SAXException | IOException | SxfmParserException e) {
			e.printStackTrace();
		}
	}

}
