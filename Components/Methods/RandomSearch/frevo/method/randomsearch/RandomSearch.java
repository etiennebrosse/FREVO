package frevo.method.randomsearch;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import main.FrevoMain;

import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;

import utils.NESRandom;
import utils.StatKeeper;
import core.AbstractMethod;
import core.AbstractRepresentation;
import core.ComponentType;
import core.ComponentXMLData;
import core.PopulationDiversity;
import core.ProblemXMLData;
import core.XMLFieldEntry;
import core.XMLMethodStep;

/**
 * Evolutionary method which uses random generation of new members 
 * to find the most adapted to current problem. This method is very simple 
 * so it is better not to use in the experiments as the primary method.
 * 
 * @author Sergii Zhevzhyk
 *
 */
public class RandomSearch extends AbstractMethod {

	/**
	 * Parameters of the method for current experiment.  
	 */
	RanEvoParameters parameters;

	/**
	 * Populations for the current experiment
	 */
	ArrayList<RanEvoPopulation> populations = new ArrayList<>();
	
	/** Entries contain the best fitness over all populations */
	private StatKeeper bestFitnessStats;
	
	/** Entries contain information about the number of evaluations */
	private StatKeeper numEvaluations;
	
	// Statistics about population diversity
	private StatKeeper diversity;
	private StatKeeper maxDiversity;
	private StatKeeper minDiversity;
	private StatKeeper standardDeviation;
	
	/**
	 * Constructs a new {@link RandomSearch} object
	 * 	
	 * @param random the generator of random numbers
	 */
	public RandomSearch(NESRandom random) {
		super(random);
		parameters = new RanEvoParameters(this);		
	}
	
	@Override
	public void runOptimization(ProblemXMLData problemData,
			ComponentXMLData representationData, ComponentXMLData rankingData,
			Hashtable<String, XMLFieldEntry> properties) {
		
		parameters.initialize(getProperties());
		
		if (!createPopulations(problemData, representationData)) {
			return;
		}		
		
		createStatistics();
		
		try {
			RanEvoStep step = new RanEvoStep(problemData, rankingData);
			
			// Iterate through generations
			for (int generation = 0; generation < parameters.getGenerations(); generation++) {
				
				step.setGeneration(generation);
				
				if (!evolve(step)) {
					break;
				}
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (Exception e) {		
			e.printStackTrace();
		}

		// finalize progress
		setProgress(1f);
	}
	
	@Override
	public void continueOptimization(ProblemXMLData problemData,
			ComponentXMLData representationData, ComponentXMLData rankingData,
			Hashtable<String, XMLFieldEntry> properties,
			Document doc) {
						
		// load and calculate parameters
		parameters.initialize(getProperties());

		if (!loadFromDoc(problemData, representationData, doc)) {
			return;
		}	
		
		// record the best fitness over the evolution
		Node dpopulations = doc.selectSingleNode("/frevo/populations");
		double best_fitness = Double.parseDouble(dpopulations.valueOf("./@best_fitness"));
		int lastGeneration  = Integer.parseInt(dpopulations.valueOf("./@generation"));				

		createStatistics();
	
		// Mutate all populations	
		for (int j = 0; j < parameters.getPopulationNumber(); j++) {
			try {
				populations.get(j).evolve(lastGeneration);
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}
		
		try {
			RanEvoStep step = new RanEvoStep(problemData, rankingData);
			
			step.setBestFitness(best_fitness);
			
			// Iterate through generations
			for (int generation = lastGeneration + 1; generation < parameters.getGenerations(); generation++) {
				
				step.setGeneration(generation);
				
				if (!evolve(step)) {
					break;
				}
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (Exception e) {		
			e.printStackTrace();
		}				
		
		// finalize progress
		setProgress(1f);
	}
	
	@Override
	public ArrayList<ArrayList<AbstractRepresentation>> loadFromXML(Document doc) {
		// final list to be returned
		ArrayList<ArrayList<AbstractRepresentation>> populations = new ArrayList<ArrayList<AbstractRepresentation>>();

		// get population root node
		Node dpopulations = doc.selectSingleNode("/frevo/populations");

		// get number of populations
		int populationCount = Integer
				.parseInt(dpopulations.valueOf("./@count"));
		// get number of current generation
		int currentGeneration = Integer
				.parseInt(dpopulations.valueOf("./@generation"));

		// get population size
		@SuppressWarnings("unchecked")
		List<? extends Node> populationsNode = dpopulations
				.selectNodes(".//population");
		int populationSize = populationsNode.get(0).selectNodes("*").size();

		// calculate total representations
		int totalRepresentations = populationCount * populationSize;
		int currentPopulation = 0;
		int currentRepresentation = 0;

		// Iterate through the population nodes
		Iterator<?> populationIterator = populationsNode.iterator();
		while (populationIterator.hasNext()) {
			Node populationNode = (Node) populationIterator.next();

			// create list of candidate representations for this population
			ArrayList<AbstractRepresentation> result = new ArrayList<AbstractRepresentation>();

			// track current progress
			currentRepresentation = 0;
			try {
				// Obtain an iterator over the representations
				List<?> representations = populationNode.selectNodes("./*");
				Iterator<?> representationsIterator = representations
						.iterator();

				while (representationsIterator.hasNext()) {
					// calculate current position for progress reporting
					int currentItem = currentPopulation * populationSize
							+ currentRepresentation + 1;

					// report loading state
					FrevoMain.setLoadingProgress((float) currentItem
							/ totalRepresentations);

					// step to next node
					Node net = (Node) representationsIterator.next();

					// construct representation based on loaded representation data
					ComponentXMLData representation = FrevoMain
							.getSelectedComponent(ComponentType.FREVO_REPRESENTATION);
					AbstractRepresentation member = representation
							.getNewRepresentationInstance(0, 0, null);

					// load representation data from the XML into the instance
					member.loadFromXML(net);
					
					// add data to current population list
					result.add(member);

					// increment tracker
					currentRepresentation++;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			populations.add(result);

			currentPopulation++;
		}
		
		// Load the number of generations
		XMLFieldEntry gensize = getProperties().get("generations");
		if (gensize != null){
			int generations = Integer.parseInt(gensize.getValue());
			// TODO check max fitness also
			// set boolean value which shows possibility of continuation of experiment
			// if maximum number of generations hasn't been reached.
			setCanContinue(currentGeneration + 1 < generations);
		}
		
		return populations;	
	}
	
	/**
	 * Evolve population by one generation
	 * 
	 * @param step contains information about current step and some global variables for execution of an experiment 
	 * @return true if evolution has been finished successfully, false - otherwise
	 * @throws Exception error has been occurred during evolution process
	 */
	private boolean evolve(RanEvoStep step) throws Exception {
		// check for pause flag
		if (handlePause())
			return false;

		// sets our current progress
		setProgress((float) step.getGeneration() / (float) parameters.getGenerations());

		long currentSeed = getRandom().getSeed();
		
		long tempSeed = currentSeed;
		
		// indicates if we have to save this generation
		boolean saveThisGeneration = false;

		// record the best fitness over all populations
		double best_fitness_pop = Double.MIN_VALUE;
		
		// Rank each population using the provided ranking
		for (RanEvoPopulation population : populations) {

			// check for pause flag
			if (handlePause())
				return false;

			// sort population
			int evaluations = step.getRanking().sortCandidates(population.getMembers(), step.getProblemData(), new NESRandom(tempSeed)); 
			AbstractRepresentation bestCandidate = step.getRanking().getBestCandidate();
		
			numEvaluations.add(evaluations);
			
			tempSeed++;
		
			if (bestCandidate.getFitness() > best_fitness_pop)
				best_fitness_pop = bestCandidate.getFitness();
		}				

		// add the best fitness of this generation to statistics
		bestFitnessStats.add(best_fitness_pop);
		
		PopulationDiversity diversityCalc = new PopulationDiversity(populations.get(0).getMembers()); 
		diversity.add(diversityCalc.getAverageDiversity());
		maxDiversity.add(diversityCalc.getMaxDiversity());
		minDiversity.add(diversityCalc.getMinDiversity());
		standardDeviation.add(diversityCalc.getStandardDeviation());

		// note, if there was an improvement (includes 0th
		// generation)
		if (best_fitness_pop > step.getBestFitness()) {
			step.setBestFitness(best_fitness_pop);
			saveThisGeneration = true;
		}

		// check last generation
		if (step.getGeneration() == parameters.getGenerations() - 1) {
			saveThisGeneration = true;
		}

		String fitnessstring;
		if (step.getProblemData().getComponentType() == ComponentType.FREVO_PROBLEM) {
			fitnessstring = " (" + step.getBestFitness() + ")";
		} else {
			// multiproblem
			fitnessstring = "";
		}
						
		String fileName = getFileName(step.getProblemData(), step.getGeneration(), fitnessstring);
		Element xmlLastState = saveResults(step.getGeneration());
		xmlLastState.addAttribute("best_fitness", String.valueOf(step.getBestFitness()));
		
		// save the last state of evaluation
		XMLMethodStep state = new XMLMethodStep(fileName, xmlLastState, this.seed, currentSeed);
		setLastResults(state);
		// save generation
		if (saveThisGeneration) {
			FrevoMain.saveResult(
					fileName , xmlLastState, this.seed, currentSeed
					);
		}				

		// Stop if maximum fitness has been achieved
		if (step.getBestFitness() >= step.getMaxFitness()) {
			System.out.println("Maximum fitness of (" + step.getMaxFitness()
					+ ") has been achieved.");
			return false;
		}				

		// Mutate all populations except when this is the last generation
		for (RanEvoPopulation population : populations) {
			population.evolve(step.getGeneration());
		}
		
		return true;		
	}
	
	/**
	 * Creates new population(s)
	 * @param problemData information about problem
	 * @param representationData information about representation
	 * @return true if population has been initialized successfully, false - otherwise
	 */
	private boolean createPopulations(ProblemXMLData problemData,
			ComponentXMLData representationData) {
		// obtain problem requirements
		int inputnumber = problemData.getRequiredNumberOfInputs();
		int outputnumber = problemData.getRequiredNumberOfOutputs();
		
		// generate initial population(s)
		for (int i = 0; i < parameters.getPopulationNumber(); i++) {
			try {
				populations.add(new RanEvoPopulation(representationData, parameters, inputnumber, outputnumber));
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Initializes population(s) from XML document
	 * @param problemData information about problem
	 * @param representationData information about representation
	 * @param doc XML document which contains data about population 
	 * @return true if population has been loaded successfully, false - otherwise
	 */
	private boolean loadFromDoc(ProblemXMLData problemData, ComponentXMLData representationData, Document doc) {
		// obtain problem requirements
		int inputnumber = problemData.getRequiredNumberOfInputs();
		int outputnumber = problemData.getRequiredNumberOfOutputs();

		// record the best fitness over the evolution
		Node dpopulations = doc.selectSingleNode("/frevo/populations");
		long randomseed = Long.parseLong(dpopulations.valueOf("./@randomseed"));
		getRandom().setSeed(randomseed);
										
		// load initial population(s)
		ArrayList<ArrayList<AbstractRepresentation>> loadedPops = loadFromXML(doc);
		for (int i = 0; i < parameters.getPopulationNumber(); i++) {
			try {
				populations.add(new RanEvoPopulation(representationData, parameters, inputnumber, outputnumber, loadedPops.get(i)));
			} catch (Exception e) {				
				e.printStackTrace();
				return false;
			}
		}			
		return true;		
	}
	
	/**
	 * Gets the file name for saving of current results
	 * @param problemData description of the problem
	 * @param generation number of current generation 
	 * @param fitnessstring max fitness for this generation
	 * @return file name
	 */
	private String getFileName(ProblemXMLData problemData, int generation,
			String fitnessstring) {
		
		DecimalFormat fm = new DecimalFormat("000");
		return problemData.getName() + "_g"
				+ fm.format(generation) + fitnessstring;
	}
	
	/**
	 * Saves all population data to a new XML element and returns it.
	 * @param generation number of current generation
	 * @return information about population in XML format  
	 */
	public Element saveResults(int generation) {
		Element dpopulations = DocumentFactory.getInstance().createElement(
				"populations");

		dpopulations.addAttribute("count", String.valueOf(parameters.getPopulationNumber()));
		dpopulations.addAttribute("generation", String.valueOf(generation));
		dpopulations.addAttribute("randomseed", String.valueOf(this.getSeed()));

		for (RanEvoPopulation pop : populations) {
			pop.exportXml(dpopulations);
		}
		
		return dpopulations;
	}
	
	/**
	 * Creates instances for statistics purposes
	 */
	private void createStatistics() {
		// create statistics
		bestFitnessStats = new StatKeeper(true, "Best Fitness ("
				+ FrevoMain.getCurrentRun() + ")", "Generations");

		numEvaluations = new StatKeeper(true, "numSimulations", "Generations");
		
		diversity = new StatKeeper(true, "Diversity", "Generations");
		maxDiversity = new StatKeeper(true, "Max. diversity", "Generations");
		minDiversity = new StatKeeper(true, "Min. diversity", "Generations");
		standardDeviation = new StatKeeper(true, "Deviation", "Generations");
		
		// register statistics
		FrevoMain.addStatistics(bestFitnessStats,true);
		FrevoMain.addStatistics(diversity,true);

		FrevoMain.addStatistics(numEvaluations,false);				
		FrevoMain.addStatistics(maxDiversity,false);
		FrevoMain.addStatistics(minDiversity,false);
		FrevoMain.addStatistics(standardDeviation,false);
	}

}
