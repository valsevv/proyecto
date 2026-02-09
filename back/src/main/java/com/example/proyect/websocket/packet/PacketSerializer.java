package com.example.proyect.websocket.packet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Utility class for serializing/deserializing packets to/from JSON.
 */
public class PacketSerializer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private PacketSerializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Parse a JSON string into a Packet.
     */
    @SuppressWarnings("unchecked")
    public static Packet deserialize(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String typeStr = (String) map.get("type");
            
            if (typeStr == null) {
                return null;
            }
            
            PacketType type = PacketType.fromString(typeStr);
            if (type == null) {
                return null;
            }
            
            // Remove type from payload since it's stored separately
            map.remove("type");
            return new Packet(type, map);
            
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Serialize a Packet to JSON string.
     */
    public static String serialize(Packet packet) {
        try {
            return objectMapper.writeValueAsString(packet.toMap());
        } catch (JsonProcessingException e) {
            // Fallback to basic error JSON
            return "{\"type\":\"error\",\"message\":\"Serialization failed\"}";
        }
    }

    /**
     * Serialize a Map directly to JSON (for backwards compatibility).
     */
    public static String serialize(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"Serialization failed\"}";
        }
    }
}
