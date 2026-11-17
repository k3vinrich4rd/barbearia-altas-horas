package io.github.k3vinrich4rd.altashoras.comum.validacao;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Valor nulo passa: quem obriga a presença é o @NotNull/@NotBlank do campo. */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CepBr.Validador.class)
public @interface CepBr {

    String message() default "deve ter 8 dígitos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<CepBr, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            if (valor == null) {
                return true;
            }
            return io.github.k3vinrich4rd.altashoras.comum.valor.Cep.de(valor).valido();
        }
    }
}
