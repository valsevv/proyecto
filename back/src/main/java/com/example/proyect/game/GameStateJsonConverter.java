package com.example.proyect.game;

import com.example.proyect.persistence.classes.GameState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class GameStateJsonConverter implements AttributeConverter<GameState, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(GameState attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error serializando GameState a JSON", e);
        }
    }

    @Override
    public GameState convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, GameState.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error deserializando JSON a GameState", e);
        }
    }
}