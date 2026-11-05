package br.com.sentinela.infra.security;

import br.com.sentinela.enums.Feature;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Exige que o plano vigente inclua o recurso; a anotação no método prevalece sobre a da classe. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    Feature value();
}
