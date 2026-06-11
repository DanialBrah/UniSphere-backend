package com.unisphere.backend.social.messaging.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MessagePayloadValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMessagePayload {
    String message() default "Message must have either content or a media URL";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
