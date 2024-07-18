package org.springframework.graphql.data.analysis;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 *
 * @author Brian Clozel
 * @since 1.4
 */
public @interface SchemaComplexityStrategy {

	Class<? extends ComplexityScoreStrategy> value();

}
