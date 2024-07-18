package org.springframework.graphql.data.analysis;


@FunctionalInterface
public interface ComplexityScoreStrategy {

	ComplexityScoreCalculator calculator(ComplexityScoringContext context);

}
