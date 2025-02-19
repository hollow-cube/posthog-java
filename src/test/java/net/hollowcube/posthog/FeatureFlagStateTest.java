package net.hollowcube.posthog;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FeatureFlagStateTest {

    @Test
    void emptyObject() {
        var state = new FeatureFlagState(new JsonObject(), new JsonObject(), "test");
        assertFalse(state.isEnabled());
        assertNull(state.getVariant());
    }

    @Test
    void presentFalse() {
        var json = new JsonObject();
        json.addProperty("test", false);
        var state = new FeatureFlagState(json, new JsonObject(), "test");
        assertFalse(state.isEnabled());
        assertNull(state.getVariant());
    }

    @Test
    void presentTrue() {
        var json = new JsonObject();
        json.addProperty("test", true);
        var state = new FeatureFlagState(json, new JsonObject(), "test");
        assertTrue(state.isEnabled());
        assertNull(state.getVariant());
    }

    @Test
    void presentString() {
        var json = new JsonObject();
        json.addProperty("test", "variant");
        var state = new FeatureFlagState(json, new JsonObject(), "test");
        assertTrue(state.isEnabled());
        assertEquals("variant", state.getVariant());
    }

    @Test
    void presentOtherA() {
        var json = new JsonObject();
        json.addProperty("test", 1);
        var state = new FeatureFlagState(json, new JsonObject(), "test");
        assertFalse(state.isEnabled());
        assertNull(state.getVariant());
    }

    @Test
    void presentOtherB() {
        var json = new JsonObject();
        json.add("test", new JsonObject());
        var state = new FeatureFlagState(json, new JsonObject(), "test");
        assertFalse(state.isEnabled());
        assertNull(state.getVariant());
    }

    @Test
    void presentOtherC() {
        var json = new JsonObject();
        json.add("test", new JsonArray());
        var state = new FeatureFlagState(json, new JsonObject(), "test");
        assertFalse(state.isEnabled());
        assertNull(state.getVariant());
    }

    @Test
    void presentOtherD() {
        var json = new JsonObject();
        json.add("test", JsonNull.INSTANCE);
        var state = new FeatureFlagState(json, new JsonObject(), "test");
        assertFalse(state.isEnabled());
        assertNull(state.getVariant());
    }

}
