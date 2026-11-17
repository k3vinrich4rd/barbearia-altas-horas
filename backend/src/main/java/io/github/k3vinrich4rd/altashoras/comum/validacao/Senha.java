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
@Constraint(validatedBy = Senha.Validador.class)
public @interface Senha {

    String message() default "deve ter de 10 a 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Senha, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            if (valor == null) {
                return true;
            }
            int bytes = valor.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            return bytes >= 10 && bytes <= 72;
        }
    }
}
