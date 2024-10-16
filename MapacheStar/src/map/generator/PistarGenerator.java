package map.generator;

import java.io.File;

import map.ilp.ILP;

public class PistarGenerator {
	// fichero original
	private String goalModelFile;
	// solución de vuelta por Matlab
	private String matlabSol;
	private ILP problem;
	
	public PistarGenerator(String gmFile,String resFile) {
		goalModelFile=gmFile;
		matlabSol=resFile;
	}
	
	public void generateGoalModel(String outPutFile) {
		File inputGoalModel=new File(goalModelFile);
		File resFile=new File(matlabSol);
		File outputGoalModel=new File(outPutFile);
	}

}
