package com.demo.githubcopilotwithcursor.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

@Component
public class WebFormValidator {

    private final Validator validator;

    public WebFormValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> Optional<ConstraintViolation<T>> firstViolation(T target) {
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        if (violations.isEmpty()) {
            return Optional.empty();
        }
        return violations.stream()
            .min(Comparator.comparingInt(WebFormValidator::constraintOrder))
            .map(Optional::of)
            .orElse(Optional.empty());
    }

    public <T> Optional<String> firstMessage(T target) {
        return firstViolation(target).map(WebFormValidator::violationMessage);
    }

    private static <T> String violationMessage(ConstraintViolation<T> violation) {
        String message = violation.getMessage();
        if (message == null || message.isBlank()) {
            return "요청 값이 올바르지 않습니다.";
        }
        return message;
    }

    public Optional<String> firstBindingError(BindingResult bindingResult) {
        if (bindingResult == null || !bindingResult.hasErrors()) {
            return Optional.empty();
        }
        FieldError fieldError = bindingResult.getFieldError();
        if (fieldError == null) {
            return Optional.of("요청 값이 올바르지 않습니다.");
        }
        String message = fieldError.getDefaultMessage();
        if (message == null || message.isBlank()) {
            return Optional.of(fieldError.getField() + ": 요청 값이 올바르지 않습니다.");
        }
        return Optional.of(fieldError.getField() + ": " + message);
    }

    private static int constraintOrder(ConstraintViolation<?> violation) {
        Class<? extends java.lang.annotation.Annotation> annotationType =
            violation.getConstraintDescriptor().getAnnotation().annotationType();
        if (NotBlank.class.equals(annotationType) || NotNull.class.equals(annotationType)) {
            return 0;
        }
        return 1;
    }
}
