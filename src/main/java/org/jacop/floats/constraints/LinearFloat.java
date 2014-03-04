/**
 *  LinearFloat.java 
 *  This file is part of JaCoP.
 *
 *  JaCoP is a Java Constraint Programming solver. 
 *	
 *	Copyright (C) 2000-2008 Krzysztof Kuchcinski and Radoslaw Szymanek
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  Notwithstanding any other provision of this License, the copyright
 *  owners of this work supplement the terms of this License with terms
 *  prohibiting misrepresentation of the origin of this work and requiring
 *  that modified versions of this work be marked in reasonable ways as
 *  different from the original version. This supplement of the license
 *  terms is in accordance with Section 7 of GNU Affero General Public
 *  License version 3.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.jacop.floats.constraints;

import java.util.ArrayList;
import java.util.HashMap;

import org.jacop.core.IntDomain;
import org.jacop.core.IntVar;
import org.jacop.core.Store;
import org.jacop.core.TimeStamp;
import org.jacop.core.Var;
import org.jacop.constraints.PrimitiveConstraint;

import org.jacop.floats.core.FloatVar;
import org.jacop.floats.core.FloatDomain;
import org.jacop.floats.core.FloatIntervalDomain;
import org.jacop.floats.core.FloatInterval;

/**
 * LinearFloat constraint implements the weighted summation over several
 * Variable's . It provides the weighted sum from all Variable's on the list.
 * 
 * @author Krzysztof Kuchcinski and Radoslaw Szymanek
 * @version 4.0
 */

public class LinearFloat extends PrimitiveConstraint {
    Store store;
    static int counter = 1;

    /**
     * Defines relations
     */
    final static byte eq=0, lt=1, le=2, ne=3, gt=4, ge=5;

    /**
     * Defines negated relations
     */
    final static byte[] negRel= {ne, //eq=0, 
				 ge, //lt=1, 
				 gt, //le=2, 
				 eq, //ne=3, 
				 le, //gt=4, 
				 lt  //ge=5;
    };

    /**
     * It specifies what relations is used by this constraint
     */

    public byte relationType;

    /**
     * It specifies a list of variables being summed.
     */
    public FloatVar list[];

    /**
     * It specifies a list of weights associated with the variables being summed.
     */
    public double weights[];

    /**
     * It specifies variable for the overall sum. 
     */
    public double sum;

    double lMin;

    double lMax;

    double[] lMinArray;

    double[] lMaxArray;

    HashMap<Var, Integer> positionMaping;

    boolean reified = true;

    /**
     * It specifies the arguments required to be saved by an XML format as well as 
     * the constructor being called to recreate an object from an XML format.
     */
    public static String[] xmlAttributes = {"list", "weights", "sum"};

    /**
     * @param list
     * @param weights
     * @param sum
     */
    public LinearFloat(Store store, FloatVar[] list, double[] weights, String rel, double sum) {

	commonInitialization(store, list, weights, sum);
	this.relationType = relation(rel);

    }
	
    private void commonInitialization(Store store, FloatVar[] list, double[] weights, double sum) {
	this.store=store;
	queueIndex = 1;

	assert ( list.length == weights.length ) : "\nLength of two vectors different in LinearFloat";

	numberArgs = (short) (list.length + 1);

	numberId = counter++;

	this.sum = sum;

	HashMap<FloatVar, Double> parameters = new HashMap<FloatVar, Double>();

	for (int i = 0; i < list.length; i++) {

	    assert (list[i] != null) : i + "-th element of list in LinearFloat constraint is null";
			
	    if (weights[i] != 0) {
		if (list[i].singleton()) 
		    this.sum -= list[i].value() * weights[i];
		else
		    if (parameters.get(list[i]) != null) {
			// variable ordered in the scope of the LinearFloat constraint.
			Double coeff = parameters.get(list[i]);
			Double sumOfCoeff = coeff + weights[i];
			parameters.put(list[i], sumOfCoeff);
		    }
		    else
			parameters.put(list[i], weights[i]);

	    }
	}

	this.list = new FloatVar[parameters.size()];
	this.weights = new double[parameters.size()];

	int i = 0;
	for (FloatVar var : parameters.keySet()) {
	    this.list[i] = var;
	    this.weights[i] = parameters.get(var);
	    i++;
	}

	int capacity = list.length*4/3+1;
	if (capacity < 16)
	    capacity = 16;
	positionMaping = new HashMap<Var, Integer>(capacity);

	store.registerRemoveLevelLateListener(this);

	lMinArray = new double[list.length];
	lMaxArray = new double[list.length];
	lMin = 0.0;
	lMax = 0.0;

	// recomputeBounds();

	for (int j = 0; j < this.list.length; j++) {

	    assert (positionMaping.get(this.list[j]) == null) : "The variable occurs twice in the list, not able to make a maping from the variable to its list index.";

	    positionMaping.put(this.list[j], j);
	    // queueVariable(store.level, this.list[j]);

			
	}

	checkForOverflow();

    }

    /**
     * It constructs the constraint LinearFloat. 
     * @param variables variables which are being multiplied by weights.
     * @param weights weight for each variable.
     * @param sum variable containing the sum of weighted variables.
     */
    public LinearFloat(Store store, ArrayList<? extends FloatVar> variables,
		       ArrayList<Double> weights, String rel, double sum) {

	double[] w = new double[weights.size()];
	for (int i = 0; i < weights.size(); i++)
	    w[i] = weights.get(i);
		
	commonInitialization(store, variables.toArray(new FloatVar[variables.size()]),
			     w,
			     sum);
	this.relationType = relation(rel);
    }


    @Override
    public ArrayList<Var> arguments() {

	ArrayList<Var> variables = new ArrayList<Var>(list.length + 1);

	for (Var v : list)
	    variables.add(v);

	return variables;
    }


    @Override
    public void consistency(Store store) {

	pruneRelation(store, relationType);

	if (relationType != eq)
	    if (satisfied())
		removeConstraint();
    }

    @Override
    public void notConsistency(Store store) {

	pruneRelation(store, negRel[relationType]);

	if (negRel[relationType] != eq)
	    if (notSatisfied()) 
		removeConstraint();
		
    }

    private void pruneRelation(Store store, byte rel) {

	// if (entailed(negRel[rel]))
	//     throw Store.failException;

	do {

	    store.propagationHasOccurred = false;

	    recomputeBounds();

	    assert (lMin <= lMax) : "==============WRONG============\n"+ lMin+".."+lMax+"\n==============";

	    double min = FloatDomain.down(sum - lMax);
	    double max = FloatDomain.up(sum - lMin);

	    for (int i = 0; i < list.length; i++) {

		FloatVar v = list[i];

		double d1, d2;
		double divMin, divMax;
		double min1, max1;

		switch (rel) {
		case eq : //============================================= 
		    if ((lMaxArray[i] > max + lMinArray[i]) || (lMinArray[i] < min + lMaxArray[i])) {

			// if (v.id() == "cost")
			//     System.out.println ("1. "+ v + ")");

			// System.out.println ("min="+min+", max="+max+", lMinArray[i]="+lMinArray[i]+", lMaxArray[i]="+lMaxArray[i]+", weights[i]="+weights[i]);

			min1 = FloatDomain.down(min + lMaxArray[i]);
			max1 = FloatDomain.up(max + lMinArray[i]);
			d1 = min1 / weights[i];
			d2 = max1 / weights[i];

			// System.out.println ("d1="+d1+", d2="+d2);

			if (d1 <= d2) {
			    divMin = FloatDomain.down(d1);
			    divMax = FloatDomain.up(d2);
			}
			else {
			    divMin = FloatDomain.down(d2);
			    divMax = FloatDomain.up(d1);
			}

			if (divMin > divMax) 
			    throw Store.failException;

			// if (v.id() == "z")
			//     System.out.println ("***"+v + " in " + divMin +".."+ divMax);

			v.domain.in(store.level, v, divMin, divMax);

			// System.out.println ("result="+v);

			// if (v.id() == "z")
			//     System.out.println ("2. "+ v);
		    }
		    break;
		case lt : //=============================================

		    if (lMaxArray[i] >= max + lMinArray[i]) {  // based on "Bounds Consistency Techniques for Long Linear Constraints", W. Harvey and J. Schimpf

			min1 = min + FloatDomain.down(lMaxArray[i]);
			max1 = max + FloatDomain.up(lMinArray[i]);
			d1 = min1 / weights[i];
			d2 = max1 / weights[i];

			if (weights[i] < 0) {
			    if (d1 <= d2) 
				divMin = d1;
			    else
				divMin = d2;

			    v.domain.inMin(store.level, v, FloatDomain.next(divMin));
			}
			else {
			    if (d1 <= d2) 
				divMax = d2;
			    else 
				divMax = d1;

			    v.domain.inMax(store.level, v, FloatDomain.previous(divMax));
			}
		    }
		    break;
		case le : //=============================================

		    if (lMaxArray[i] > max + lMinArray[i]) {  // based on "Bounds Consistency Techniques for Long Linear Constraints", W. Harvey and J. Schimpf

			min1 = min + FloatDomain.down(lMaxArray[i]);
			max1 = max + FloatDomain.up(lMinArray[i]);
			d1 = min1 / weights[i];
			d2 = max1 / weights[i];

			if (weights[i] < 0) {
			    if (d1 <= d2) 
				divMin = d1;
			    else
				divMin = d2;

			    v.domain.inMin(store.level, v, divMin);
			}
			else {
			    if (d1 <= d2)
				divMax = d2;
			    else
				divMax = d1;

			    v.domain.inMax(store.level, v, divMax);
			}
		    }
		    break;
		case ne : //=============================================

		    min1 = min + FloatDomain.down(lMaxArray[i]);
		    max1 = max + FloatDomain.up(lMinArray[i]);
		    d1 = min1 / weights[i];
		    d2 = max1 / weights[i];

		    if (d1 <= d2) {
			divMin = d1;
			divMax = d2;
		    }
		    else {
			divMin = d2;
			divMax = d1;
		    }

		    FloatInterval fi = new FloatInterval(divMin, divMax);

		    if ( fi.singleton() ) 
			v.domain.inComplement(store.level, v, divMin, divMax);
		    break;
		case gt : //=============================================

		    if (lMinArray[i] <= min + lMaxArray[i]) { // based on "Bounds Consistency Techniques for Long Linear Constraints", W. Harvey and J. Schimpf

			min1 = min + FloatDomain.down(lMaxArray[i]); 
			max1 = max + FloatDomain.up(lMinArray[i]);
			d1 = min1 / weights[i];
			d2 = max1 / weights[i];

			if (weights[i] < 0) {
			    if (d1 <= d2) 
				divMax = d2;
			    else
				divMax = d1;

			    v.domain.inMax(store.level, v, FloatDomain.previous(divMax));
			}
			else {
			    if (d1 <= d2)
				divMin = d1;
			    else
				divMin = d2;

			    v.domain.inMin(store.level, v, FloatDomain.next(divMin));
			}
		    }
		    break;
		case ge : //=============================================

		    if (lMinArray[i] < min + lMaxArray[i]) { // based on "Bounds Consistency Techniques for Long Linear Constraints", W. Harvey and J. Schimpf

			min1 = min + FloatDomain.down(lMaxArray[i]);
			max1 = max + FloatDomain.up(lMinArray[i]);
			d1 = min1 / weights[i];
			d2 = max1 / weights[i];

			if (weights[i] < 0) {
			    if (d1 <= d2)
				divMax = d2;
			    else
				divMax = d1;

			    v.domain.inMax(store.level, v, divMax);
			}
			else {
			    if (d1 <= d2)
				divMin = d1;
			    else
				divMin = d2;

			    v.domain.inMin(store.level, v, divMin);
			}
		    }
		    break;
		}
	    }

	} while (store.propagationHasOccurred);

	// if (entailed(negRel[rel]))
	//     throw Store.failException;

    }

    void recomputeBounds() {

	lMin = 0.0;
	lMax = 0.0;
	for (int i=0; i<list.length; i++) {

	    FloatDomain listDom = list[i].dom();

	    double min_i = listDom.min()*weights[i];
	    double max_i = listDom.max()*weights[i];

	    if (min_i <= max_i) {
		lMinArray[i] = FloatDomain.down(min_i);
		lMaxArray[i] = FloatDomain.up(max_i);
	    }
	    else {
		lMinArray[i] = FloatDomain.down(max_i);
		lMaxArray[i] = FloatDomain.up(min_i);
	    }

	    lMin = FloatDomain.down(lMin + lMinArray[i]);
	    lMax = FloatDomain.up(lMax + lMaxArray[i]);

	}

	// System.out.println (lMin+".."+lMax);

    }

    @Override
    public int getConsistencyPruningEvent(Var var) {

	// If consistency function mode
	if (consistencyPruningEvents != null) {
	    Integer possibleEvent = consistencyPruningEvents.get(var);
	    if (possibleEvent != null)
		return possibleEvent;
	}
	return IntDomain.BOUND;
    }

    @Override
    public int getNestedPruningEvent(Var var, boolean mode) {

	// If consistency function mode
	if (mode) {
	    if (consistencyPruningEvents != null) {
		Integer possibleEvent = consistencyPruningEvents.get(var);
		if (possibleEvent != null)
		    return possibleEvent;
	    }
	    return IntDomain.BOUND;
	}

	// If notConsistency function mode
	else {
	    if (notConsistencyPruningEvents != null) {
		Integer possibleEvent = notConsistencyPruningEvents.get(var);
		if (possibleEvent != null)
		    return possibleEvent;
	    }
	    return IntDomain.BOUND;
	}

    }

    @Override
    public int getNotConsistencyPruningEvent(Var var) {

	// If notConsistency function mode
	if (notConsistencyPruningEvents != null) {
	    Integer possibleEvent = notConsistencyPruningEvents.get(var);
	    if (possibleEvent != null)
		return possibleEvent;
	}
	return IntDomain.BOUND;
		
    }

    @Override
    public void impose(Store store) {

	if (list == null)
	    return;

	reified = false;

	for (Var V : list)
	    V.putModelConstraint(this, getConsistencyPruningEvent(V));

	store.addChanged(this);
	store.countConstraint();
    }

    @Override
    public void removeConstraint() {
	for (Var v : list)
	    v.removeConstraint(this);
    }

    @Override
    public boolean satisfied() {

	if (reified) 
	    recomputeBounds();

	return entailed(relationType);

    }

    @Override
    public boolean notSatisfied() {

	if (reified)
	    recomputeBounds();

	return entailed(negRel[relationType]);

    }

    private boolean entailed(byte rel) {
	    
	switch (rel) {
	case eq : 
	    FloatInterval fi_lMinlMax = new FloatInterval(lMin, lMax);

	    if (fi_lMinlMax.singleton() && lMin <= sum && sum <= lMax)
		return true;
	    break;
	case lt : 
	    if (lMax < sum)
		return true;
	    break;
	case le : 
	    if (lMax <= sum)
		return true;
	    break;
	case ne : 
	    if (lMin > sum || lMax < sum)
		return true;
	    break;
	case gt : 
	    if (lMin > sum)
		return true;
	    break;
	case ge : 
	    if (lMin >= sum)
		return true;
	    break;
	}

	return false;
    }

    void checkForOverflow() {

	double sumMin=0, sumMax=0;
	for (int i=0; i<list.length; i++) {
	    double n1 = list[i].min() * weights[i];
	    double n2 = list[i].max() * weights[i];
	    if (Double.isInfinite(n1) || Double.isInfinite(n2))
		throw new ArithmeticException("Overflow occurred in floating point operations");

	    if (n1 <= n2) {
		sumMin += n1;
		sumMax += n2;
	    }
	    else {
		sumMin += n2;
		sumMax += n1;
	    }

	    if (Double.isInfinite(sumMin) || Double.isInfinite(sumMax))
		throw new ArithmeticException("Overflow occurred in floating point operations");

	}
    }

    public byte relation(String r) {
	if (r.equals("==")) 
	    return eq;
	else if (r.equals("=")) 
	    return eq;
	else if (r.equals("<"))
	    return lt;
	else if (r.equals("<="))
	    return le;
	else if (r.equals("=<"))
	    return le;
	else if (r.equals("!="))
	    return ne;
	else if (r.equals(">"))
	    return gt;
	else if (r.equals(">="))
	    return ge;
	else if (r.equals("=>"))
	    return ge;
	else {
	    System.err.println ("Wrong relation symbol in LinearFloat constraint " + r + "; assumed ==");
	    return eq;
	}
    }

    public String rel2String() {
	switch (relationType) {
	case eq : return "==";
	case lt : return "<";
	case le : return "<=";
	case ne : return "!=";
	case gt : return ">";
	case ge : return ">=";
	}

	return "?";
    }


    @Override
    public String toString() {

	StringBuffer result = new StringBuffer( id() );
	result.append(" : LinearFloat( [ ");

	for (int i = 0; i < list.length; i++) {
	    result.append(list[i]);
	    if (i < list.length - 1)
		result.append(", ");
	}
	result.append("], [");

	for (int i = 0; i < weights.length; i++) {
	    result.append( weights[i] );
	    if (i < weights.length - 1)
		result.append( ", " );
	}

	result.append( "], ").append(rel2String()).append(", ").append(sum).append( " )" );

	return result.toString();

    }

    @Override
    public void increaseWeight() {
	if (increaseWeight) {
	    for (Var v : list) v.weight++;
	}
    }
}
