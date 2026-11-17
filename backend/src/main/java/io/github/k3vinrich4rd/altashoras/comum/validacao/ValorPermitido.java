package io.github.k3vinrich4rd.altashoras.comum.validacao;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * O número tem de ser um dos valores da lista (ex.: a granularidade da agenda: 5, 10, 15, 20, 30 ou 60 minutos).
 * Valor nulo passa: quem obriga a presença é o @NotNull do campo.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValorPermitido.Validador.class)
public @interface ValorPermitido {

    int[] value();

    String message() default "valor não permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ValorPermitido, Integer> {

        private int[] permitidos = new int[0];

        @Override
        public void initialize(ValorPermitido anotacao) {
            this.permitidos = anotacao.value();
        }

        @Override
        public boolean isValid(Integer valor, ConstraintValidatorContext contexto) {
            if (valor == null) {
                return true;
            }
            for (int permitido : permitidos) {
                if (permitido == valor) {
                    return true;
                }
            }
            return false;
        }
    }
}
