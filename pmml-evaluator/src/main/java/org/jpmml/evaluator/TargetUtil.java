/*
 * Copyright (c) 2013 Villu Ruusmann
 *
 * This file is part of JPMML-Evaluator
 *
 * JPMML-Evaluator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Evaluator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Evaluator.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.evaluator;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Objects;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.Target;
import org.dmg.pmml.TargetValue;
import org.dmg.pmml.Targets;

public class TargetUtil {

	private TargetUtil(){
	}

	static
	public Map<FieldName, ?> evaluateRegressionDefault(ModelEvaluationContext context){
		return evaluateRegression((Double)null, context);
	}

	static
	public Map<FieldName, ?> evaluateRegression(Double value, ModelEvaluationContext context){
		Evaluator evaluator = context.getModelEvaluator();

		return evaluateRegression(Collections.singletonMap(evaluator.getTargetField(), value), context);
	}

	/**
	 * <p>
	 * Evaluates the {@link Targets} element for {@link MiningFunctionType#REGRESSION regression} models.
	 * </p>
	 */
	static
	public Map<FieldName, ?> evaluateRegression(Map<FieldName, ? extends Double> predictions, ModelEvaluationContext context){
		ModelEvaluator<?> evaluator = context.getModelEvaluator();

		if(predictions.size() == 1 && predictions.containsKey(Evaluator.DEFAULT_TARGET)){
			Double doubleValue = predictions.get(Evaluator.DEFAULT_TARGET);

			Object value = doubleValue;

			DataField dataField = evaluator.getDataField();

			if(value != null){
				value = TypeUtil.cast(dataField.getDataType(), value);
			}

			context.declare(Evaluator.DEFAULT_TARGET, FieldValueUtil.createTargetValue(dataField, null, null, value));

			if(!Objects.equal(doubleValue, value)){
				return Collections.singletonMap(Evaluator.DEFAULT_TARGET, value);
			}

			return predictions;
		}

		Map<FieldName, Object> result = null;

		Collection<? extends Map.Entry<FieldName, ? extends Double>> entries = predictions.entrySet();
		for(Map.Entry<FieldName, ? extends Double> entry : entries){
			FieldName name = entry.getKey();
			Object value = entry.getValue();

			Target target = evaluator.getTarget(name);
			if(target != null){

				if(value == null){
					value = getDefaultValue(target);
				} // End if

				if(value != null){
					value = processValue(target, (Double)value);
				}
			}

			DataField dataField = evaluator.getDataField(name);
			if(dataField == null){
				throw new MissingFieldException(name);
			} // End if

			if(value != null){
				value = TypeUtil.cast(dataField.getDataType(), value);
			}

			MiningField miningField = evaluator.getMiningField(name);

			context.declare(name, FieldValueUtil.createTargetValue(dataField, miningField, target, value));

			if(predictions.size() == 1){
				return Collections.singletonMap(name, value);
			} // End if

			if(result == null){
				result = new LinkedHashMap<>();
			}

			result.put(name, value);
		}

		return result;
	}

	static
	public Map<FieldName, ? extends Classification> evaluateClassificationDefault(ModelEvaluationContext context){
		return evaluateClassification((Classification)null, context);
	}

	static
	public Map<FieldName, ? extends Classification> evaluateClassification(Classification value, ModelEvaluationContext context){
		Evaluator evaluator = context.getModelEvaluator();

		return evaluateClassification(Collections.singletonMap(evaluator.getTargetField(), value), context);
	}

	/**
	 * <p>
	 * Evaluates the {@link Targets} element for {@link MiningFunctionType#CLASSIFICATION classification} models.
	 * </p>
	 */
	static
	public Map<FieldName, ? extends Classification> evaluateClassification(Map<FieldName, ? extends Classification> predictions, ModelEvaluationContext context){
		ModelEvaluator<?> evaluator = context.getModelEvaluator();

		if(predictions.size() == 1 && predictions.containsKey(Evaluator.DEFAULT_TARGET)){
			Classification value = predictions.get(Evaluator.DEFAULT_TARGET);

			DataField dataField = evaluator.getDataField();

			if(value != null){
				value.computeResult(dataField.getDataType());
			}

			context.declare(Evaluator.DEFAULT_TARGET, FieldValueUtil.createTargetValue(dataField, null, null, value != null ? value.getResult() : null));

			return predictions;
		}

		Map<FieldName, Classification> result = null;

		Collection<? extends Map.Entry<FieldName, ? extends Classification>> entries = predictions.entrySet();
		for(Map.Entry<FieldName, ? extends Classification> entry : entries){
			FieldName name = entry.getKey();
			Classification value = entry.getValue();

			Target target = evaluator.getTarget(name);
			if(target != null){

				if(value == null){
					value = getPriorProbabilities(target);
				}
			}

			DataField dataField = evaluator.getDataField(name);
			if(dataField == null){
				throw new MissingFieldException(name);
			} // End if

			if(value != null){
				value.computeResult(dataField.getDataType());
			}

			MiningField miningField = evaluator.getMiningField(name);

			context.declare(name, FieldValueUtil.createTargetValue(dataField, miningField, target, value != null ? value.getResult() : null));

			if(predictions.size() == 1){
				return Collections.singletonMap(name, value);
			} // End if

			if(result == null){
				result = new LinkedHashMap<>();
			}

			result.put(name, value);
		}

		return result;
	}

	static
	public Double processValue(Target target, Double value){
		double result = value.doubleValue();

		Double min = target.getMin();
		if(min != null){
			result = Math.max(result, min.doubleValue());
		}

		Double max = target.getMax();
		if(max != null){
			result = Math.min(result, max.doubleValue());
		}

		result = (result * target.getRescaleFactor()) + target.getRescaleConstant();

		Target.CastInteger castInteger = target.getCastInteger();
		if(castInteger == null){
			return result;
		}

		switch(castInteger){
			case ROUND:
				return (double)Math.round(result);
			case CEILING:
				return Math.ceil(result);
			case FLOOR:
				return Math.floor(result);
			default:
				throw new UnsupportedFeatureException(target, castInteger);
		}
	}

	static
	public TargetValue getTargetValue(Target target, Object value){
		DataType dataType = TypeUtil.getDataType(value);

		List<TargetValue> targetValues = target.getTargetValues();
		for(TargetValue targetValue : targetValues){

			if(TypeUtil.equals(dataType, value, TypeUtil.parseOrCast(dataType, targetValue.getValue()))){
				return targetValue;
			}
		}

		return null;
	}

	static
	private Double getDefaultValue(Target target){
		List<TargetValue> values = target.getTargetValues();

		if(values.isEmpty()){
			return null;
		} // End if

		if(values.size() != 1){
			throw new InvalidFeatureException(target);
		}

		TargetValue value = values.get(0);

		// "The value and priorProbability attributes are used only if the optype of the field is categorical or ordinal"
		if(value.getValue() != null || value.getPriorProbability() != null){
			throw new InvalidFeatureException(value);
		}

		return value.getDefaultValue();
	}

	static
	private ProbabilityDistribution getPriorProbabilities(Target target){
		ProbabilityDistribution result = new ProbabilityDistribution();

		List<TargetValue> values = target.getTargetValues();
		for(TargetValue value : values){

			// "The defaultValue attribute is used only if the optype of the field is continuous"
			if(value.getDefaultValue() != null){
				throw new InvalidFeatureException(value);
			}

			String targetCategory = value.getValue();
			Double probability = value.getPriorProbability();

			if(targetCategory == null || probability == null){
				continue;
			}

			result.put(targetCategory, probability);
		}

		if(result.isEmpty()){
			return null;
		}

		return result;
	}
}