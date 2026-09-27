package com.example.resumerag.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RequirementExpression.Concept.class, name = "Concept"),
        @JsonSubTypes.Type(value = RequirementExpression.AllOf.class, name = "AllOf"),
        @JsonSubTypes.Type(value = RequirementExpression.AnyOf.class, name = "AnyOf")
})
public sealed interface RequirementExpression
        permits RequirementExpression.Concept,
                RequirementExpression.AllOf,
                RequirementExpression.AnyOf {

    record Concept(String name) implements RequirementExpression {
        public Concept {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Concept name cannot be blank.");
            }

            name = name.trim();
        }
    }

    record AllOf(List<RequirementExpression> children)
            implements RequirementExpression {

        public AllOf {
            if (children == null || children.isEmpty()) {
                throw new IllegalArgumentException(
                        "AllOf must contain at least one expression."
                );
            }

            children = List.copyOf(children);
        }
    }

    record AnyOf(List<RequirementExpression> children)
            implements RequirementExpression {

        public AnyOf {
            if (children == null || children.isEmpty()) {
                throw new IllegalArgumentException(
                        "AnyOf must contain at least one expression."
                );
            }

            children = List.copyOf(children);
        }
    }
}
