package com.gtublog.automation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Keeps worker taxonomy IDs aligned with JSON Schema's integer type. */
public class StrictTaxonomyIdDeserializer extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Long.class, "Taxonomy IDs must be JSON integers.");
        }
        return parser.getLongValue();
    }
}
