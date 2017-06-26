/*
 * Copyright (C) 2009 Istvan Fehervari, Wilfried Elmenreich
 * Original project page: http://www.frevotool.tk
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License Version 3 as published
 * by the Free Software Foundation http://www.gnu.org/licenses/gpl-3.0.txt
 *
 * There is no warranty for this free software. The GPL requires that 
 * modified versions be marked as changed, so that their problems will
 * not be attributed erroneously to authors of previous versions.
 */
package core;

import java.util.ArrayList;
import java.util.Hashtable;

import utils.NESRandom;

/** Abstract superclass for ranking/sorting candidates. Rankings are used to sort a list of candidates in a decreasing order of fitness.
 * <p>
 * {@link AbstractSingleProblem}s can only use the {@link AbsoluteRanking} component while {@link AbstractMultiProblem}s can use various more sophisticated algorithms.
 * 
 * @author Istvan Fehervari*/
public abstract class AbstractRanking extends AbstractComponent {
	
	public AbstractRanking(Hashtable<String, XMLFieldEntry> properties) {
		this.setProperties(properties);
	}
    
	/** Sorts the given array of representations in a descending order of fitness and returns the number of evaluation that was required for the ranking to finish.
	 * Evaluation of the candidates is done by the provided <i>problem</i> component.
	 * @param representations The population to be sorted.
	 * @param problem The problem descriptor to be used for evaluation.
	 * @param random The random generator object used for sorting.
	 * @return the number of evaluations that were needed to rank the given set of representations*/
	public abstract int sortCandidates (final ArrayList<AbstractRepresentation> representations, final ProblemXMLData problem, NESRandom random);

	public abstract AbstractRepresentation getBestCandidate();
}
